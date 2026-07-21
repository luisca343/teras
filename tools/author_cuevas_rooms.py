#!/usr/bin/env python3
"""Authors the production Cuevas room set — all 17 templates — as structure .nbt files.

Deterministic: every room is sculpted from a fixed per-room seed, so re-running the tool
reproduces the shipped bytes. Editing a room means editing its builder here and re-running:

    python3 tools/author_cuevas_rooms.py            # writes src/main/resources/.../cuevas/
    python3 tools/author_cuevas_rooms.py --preview  # ASCII floor plans, no files written

The tool enforces the same contract the game does — a Python mirror of RoomAudit and
DoorwayZone (DUNGEONS_PISOS.md §21) plus physics checks the audit does not make (dripstone
support, contained water, supported carpets and lanterns). A rule violation aborts the run;
nothing broken can be written.

Cell geometry (build/DungeonsConfig defaults): 21×21×12 cells, 3×3 doors centred at inset 9,
doorway zones 4 deep. The floor-spawn bar per key mirrors EnemySpawner: single 5, challenge 7
(two waves at +30%), large 6, L 8, big 11 — every combat room carries at least that many
plain `spawn` markers so the spawner never stacks a wave on one block.
"""
import argparse
import gzip
import io
import os
import random
import struct
import sys

S = 21          # roomSize
H = 12          # roomHeight
DOOR_W = 3
DOOR_H = 3
DEPTH = 4       # DoorwayZone.DEPTH
INSET = (S - DOOR_W) // 2          # 9 -> band 9..11
DATA_VERSION = 3955                # 1.21.1

SHAPES = {
    'single': [(0, 0)],
    'large':  [(0, 0), (1, 0)],
    'l':      [(0, 0), (1, 0), (0, 1)],           # L_TOP_LEFT: quadrant (1,1) is unowned
    'big':    [(0, 0), (1, 0), (0, 1), (1, 1)],
}

AIRLIKE = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void'}

# ---------------------------------------------------------------------------- NBT writing

def _tag(out, tag_id, name):
    out.write(struct.pack('>B', tag_id))
    raw = name.encode('utf-8')
    out.write(struct.pack('>H', len(raw)))
    out.write(raw)

def _string(out, value):
    raw = value.encode('utf-8')
    out.write(struct.pack('>H', len(raw)))
    out.write(raw)

def _type_of(value):
    if isinstance(value, dict):
        return 10
    if isinstance(value, str):
        return 8
    if isinstance(value, int):
        return 3
    if isinstance(value, list):
        return 9
    raise TypeError(repr(value))

def _payload(out, value):
    if isinstance(value, dict):
        for k, v in value.items():
            _tag(out, _type_of(v), k)
            _payload(out, v)
        out.write(b'\x00')
    elif isinstance(value, str):
        _string(out, value)
    elif isinstance(value, int):
        out.write(struct.pack('>i', value))
    elif isinstance(value, list):
        elem = _type_of(value[0]) if value else 0
        out.write(struct.pack('>B', elem))
        out.write(struct.pack('>i', len(value)))
        for v in value:
            _payload(out, v)
    else:
        raise TypeError(repr(value))

def write_nbt(path, root):
    out = io.BytesIO()
    _tag(out, 10, '')
    _payload(out, root)
    with open(path, 'wb') as f:
        # mtime=0 keeps the bytes reproducible run to run
        with gzip.GzipFile(fileobj=f, mode='wb', mtime=0) as gz:
            gz.write(out.getvalue())

# ---------------------------------------------------------------------------- geometry

def zone_contains(cells, cell, lx, ly, lz):
    """DoorwayZone.contains, mirrored exactly."""
    if ly > DOOR_H + 1:
        return False
    in_band_x = INSET <= lx < INSET + DOOR_W
    in_band_z = INSET <= lz < INSET + DOOR_W
    far = S - 1 - DEPTH
    cx, cz = cell
    def owns(dx, dz):
        return (cx + dx, cz + dz) in cells
    if in_band_x and lz <= DEPTH and not owns(0, -1):
        return True
    if in_band_x and lz >= far and not owns(0, 1):
        return True
    if in_band_z and lx <= DEPTH and not owns(-1, 0):
        return True
    return in_band_z and lx >= far and not owns(1, 0)

# ---------------------------------------------------------------------------- the builder

class Room:
    def __init__(self, key, family, seed):
        self.key = key
        self.family = family
        self.cells = list(SHAPES[family])
        self.sx = (max(c[0] for c in self.cells) + 1) * S
        self.sz = (max(c[1] for c in self.cells) + 1) * S
        self.rng = random.Random(seed)
        self.palette = []
        self.pal_index = {}
        self.grid = {}              # (x,y,z) -> palette index; absent = not emitted
        self.markers = []           # (tag, x, y, z)
        self.air = self.block('minecraft:air')
        for (cx, cz) in self.cells:
            for lx in range(S):
                for lz in range(S):
                    for y in range(H):
                        self.grid[(cx * S + lx, y, cz * S + lz)] = self.air
        # columns sculpting must leave flat: every doorway-zone footprint plus the extra
        # approach block enforce_aprons clears (depth DEPTH+1)
        self.keep_clear = set()
        for (cx, cz) in self.cells:
            for lx in range(S):
                for lz in range(S):
                    if zone_contains(self.cells, (cx, cz), lx, 1, lz):
                        self.keep_clear.add((cx * S + lx, cz * S + lz))
            for side in self._exterior_sides(cx, cz):
                for i in range(INSET, INSET + DOOR_W):
                    x, z, dx, dz = self._wall_col(cx, cz, side, i)
                    self.keep_clear.add((x + dx * (DEPTH + 1), z + dz * (DEPTH + 1)))

    # -- palette / cells -----------------------------------------------------

    def block(self, name, **props):
        key = (name, tuple(sorted((k, str(v)) for k, v in props.items())))
        if key not in self.pal_index:
            self.pal_index[key] = len(self.palette)
            self.palette.append(key)
        return self.pal_index[key]

    def owned(self, x, z):
        return 0 <= x and 0 <= z and (x // S, z // S) in self.cells

    def cell_of(self, x, z):
        return (x // S, z // S)

    def is_wall(self, x, z):
        if not self.owned(x, z):
            return False
        lx, lz = x % S, z % S
        cx, cz = self.cell_of(x, z)
        return ((lx == 0 and (cx - 1, cz) not in self.cells)
                or (lx == S - 1 and (cx + 1, cz) not in self.cells)
                or (lz == 0 and (cx, cz - 1) not in self.cells)
                or (lz == S - 1 and (cx, cz + 1) not in self.cells))

    def in_zone(self, x, y, z):
        if not self.owned(x, z):
            return True
        return zone_contains(self.cells, self.cell_of(x, z), x % S, y, z % S)

    def near_clear(self, x, z):
        """Within one block of a doorway-zone column — the margin rims and shelves keep."""
        return any((x + dx, z + dz) in self.keep_clear
                   for dx in (-1, 0, 1) for dz in (-1, 0, 1))

    # -- placement -----------------------------------------------------------

    def set(self, x, y, z, idx):
        if 0 <= y < H and self.owned(x, z):
            self.grid[(x, y, z)] = idx

    def get_name(self, x, y, z):
        idx = self.grid.get((x, y, z))
        return None if idx is None else self.palette[idx][0]

    def solid_at(self, x, y, z):
        name = self.get_name(x, y, z)
        return name is not None and name not in AIRLIKE

    def fill(self, x0, y0, z0, x1, y1, z1, idx):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, idx)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, self.air)

    def mark(self, tag, x, y, z):
        assert self.owned(x, z), f'{self.key}: marker {tag} at {x},{y},{z} outside owned cells'
        assert not self.in_zone(x, y, z), f'{self.key}: marker {tag} at {x},{y},{z} in doorway zone'
        name = self.get_name(x, y, z)
        assert name in AIRLIKE, f'{self.key}: marker {tag} at {x},{y},{z} over {name}'
        self.markers.append((tag, x, y, z))
        self.set(x, y, z, self.block('minecraft:structure_block', mode='data'))

    def spawn(self, x, z, ranged=False, y=1):
        """A spawn marker with guaranteed footing: solid below, headroom cleared above."""
        assert self.solid_at(x, y - 1, z), f'{self.key}: spawn at {x},{y},{z} floats'
        self.clear(x, y, z, x, min(H - 2, y + 2), z)
        self.mark('spawn:ranged' if ranged else 'spawn', x, y, z)

    def deco(self, tag, x, y, z):
        """A decoration marker; whatever sculpting left there yields to it, since the
        Decorator replaces the marker block with the rolled feature anyway."""
        self.clear(x, y, z, x, y, z)
        self.mark(tag, x, y, z)

    # -- material blends -----------------------------------------------------

    def stone_blend(self, y, r):
        if y <= 2:
            return self.block(('minecraft:deepslate', 'minecraft:cobbled_deepslate',
                               'minecraft:tuff', 'minecraft:deepslate')[r.randrange(4)])
        if y <= 7:
            return self.block(('minecraft:andesite', 'minecraft:stone', 'minecraft:andesite',
                               'minecraft:tuff', 'minecraft:cobblestone')[r.randrange(5)])
        return self.block(('minecraft:stone', 'minecraft:andesite',
                           'minecraft:stone', 'minecraft:deepslate')[r.randrange(4)])

    def floor_blend(self, r):
        return self.block(('minecraft:stone', 'minecraft:andesite', 'minecraft:andesite',
                           'minecraft:cobblestone', 'minecraft:tuff',
                           'minecraft:cobbled_deepslate')[r.randrange(6)])

    ORES = ('minecraft:coal_ore', 'minecraft:copper_ore', 'minecraft:copper_ore',
            'minecraft:iron_ore')

    # -- construction passes -------------------------------------------------

    def shell(self):
        r = self.rng
        for (cx, cz) in self.cells:
            for lx in range(S):
                for lz in range(S):
                    x, z = cx * S + lx, cz * S + lz
                    self.set(x, 0, z, self.floor_blend(r))
                    self.set(x, H - 1, z, self.stone_blend(H - 1, r))
                    if self.is_wall(x, z):
                        for y in range(1, H - 1):
                            self.set(x, y, z, self.stone_blend(y, r))

    def rough_walls(self, ore_chance=0.05, light_chance=0.03):
        """Crags: rock protruding 1-2 blocks from every exterior wall, ore and shroomlight
        embedded in the exposed faces. Doorway zones and their margin stay untouched."""
        r = self.rng
        for (cx, cz) in self.cells:
            for side in self._exterior_sides(cx, cz):
                depth = 0
                for i in range(1, S - 1):
                    x, z, dx, dz = self._wall_col(cx, cz, side, i)
                    depth = max(0, min(2, depth + r.choice((-1, -1, 0, 0, 0, 1, 1))))
                    if depth == 0:
                        continue
                    top = r.choice((3, 4, 5, 6, 8, 10))
                    for d in range(1, depth + 1):
                        px, pz = x + dx * d, z + dz * d
                        if (px, pz) in self.keep_clear or not self.owned(px, pz) \
                                or self.is_wall(px, pz):
                            continue
                        h = max(2, top - d * 2)
                        for y in range(1, h + 1):
                            if y < H - 1:
                                self.set(px, y, pz, self.stone_blend(y, r))
                        if r.random() < ore_chance:
                            self.set(px, r.randint(1, 3), pz, self.block(r.choice(self.ORES)))
                        if r.random() < light_chance:
                            self.set(px, min(h, 6), pz, self.block('minecraft:shroomlight'))

    def _exterior_sides(self, cx, cz):
        return [side for side, (dx, dz)
                in (('n', (0, -1)), ('s', (0, 1)), ('w', (-1, 0)), ('e', (1, 0)))
                if (cx + dx, cz + dz) not in self.cells]

    def _wall_col(self, cx, cz, side, i):
        if side == 'n':
            return cx * S + i, cz * S + 0, 0, 1
        if side == 's':
            return cx * S + i, cz * S + S - 1, 0, -1
        if side == 'w':
            return cx * S + 0, cz * S + i, 1, 0
        return cx * S + S - 1, cz * S + i, -1, 0

    def ceiling_relief(self, blobs=10, dripstone=6):
        r = self.rng
        for _ in range(blobs):
            x = r.randrange(2, self.sx - 2)
            z = r.randrange(2, self.sz - 2)
            for px in range(x, min(x + r.randint(2, 4), self.sx - 1)):
                for pz in range(z, min(z + r.randint(2, 4), self.sz - 1)):
                    if self.owned(px, pz) and not self.is_wall(px, pz):
                        self.set(px, H - 2, pz, self.stone_blend(H - 2, r))
                        if r.random() < 0.25:
                            self.set(px, H - 3, pz, self.stone_blend(H - 2, r))
        for _ in range(dripstone):
            x = r.randrange(2, self.sx - 2)
            z = r.randrange(2, self.sz - 2)
            if self.owned(x, z) and not self.is_wall(x, z):
                self.stalactite(x, z, r.randint(2, 3))

    def stalactite(self, x, z, length):
        base = H - 2 if self.solid_at(x, H - 2, z) else H - 1
        y = base - 1
        self.set(x, y, z, self.block('minecraft:dripstone_block'))
        for i in range(length):
            py = y - 1 - i
            if py <= 5:
                break
            thick = 'tip' if i == length - 1 else ('frustum' if i == length - 2 else 'base')
            self.set(x, py, z, self.block('minecraft:pointed_dripstone',
                                          vertical_direction='down', thickness=thick))

    def stalagmite(self, x, z, height):
        if (x, z) in self.keep_clear:
            return
        self.set(x, 1, z, self.block('minecraft:dripstone_block'))
        for i in range(height):
            thick = 'tip' if i == height - 1 else ('frustum' if i == height - 2 else 'base')
            self.set(x, 2 + i, z, self.block('minecraft:pointed_dripstone',
                                             vertical_direction='up', thickness=thick))

    def column(self, x, z):
        r = self.rng
        for y in range(1, H - 1):
            self.set(x, y, z, self.block('minecraft:dripstone_block')
                     if y in (1, 2, H - 2, H - 3) else self.stone_blend(y, r))

    def shelf(self, x0, z0, x1, z1, top=4, light=True):
        """A raised rock mass with a walkable top — the ranged perch. Needs a ramp."""
        r = self.rng
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in self.keep_clear or not self.owned(x, z) or self.is_wall(x, z):
                    continue
                for y in range(1, top + 1):
                    self.set(x, y, z, self.stone_blend(y, r))
        if light:
            for x in range(x0 + 1, x1, 4):
                for z in range(z0 + 1, z1, 4):
                    if (x, z) not in self.keep_clear and self.owned(x, z) \
                            and not self.is_wall(x, z):
                        self.set(x, top, z, self.block('minecraft:shroomlight'))

    def ramp(self, x, z, dx, dz, top, material='minecraft:cobblestone'):
        """Steps rising toward (dx,dz), two wide, cutting headroom through anything above —
        so a ramp may climb straight into a shelf's edge and read as a carved stair."""
        r = self.rng
        facing = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}[(dx, dz)]
        px, pz = x, z
        for step in range(1, top + 1):
            for lateral in range(2):
                sx = px + abs(dz) * lateral
                sz = pz + abs(dx) * lateral
                if (sx, sz) in self.keep_clear or not self.owned(sx, sz) \
                        or self.is_wall(sx, sz):
                    continue
                for y in range(1, step):
                    self.set(sx, y, sz, self.stone_blend(y, r))
                self.set(sx, step, sz, self.block(material + '_stairs',
                                                  facing=facing, half='bottom'))
                self.clear(sx, step + 1, sz, sx, min(H - 2, step + 3), sz)
            px, pz = px + dx, pz + dz

    def pool(self, x0, z0, x1, z1):
        """A shallow basin: a mossy rim holding water sources at y=1 on the solid floor.
        The whole footprint must sit clear of aprons, or the rim would be breached."""
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                assert (x, z) not in self.keep_clear and self.owned(x, z) \
                    and not self.is_wall(x, z), \
                    f'{self.key}: pool rim at {x},{z} clashes with an apron or wall'
        water = self.block('minecraft:water', level='0')
        rim = self.block('minecraft:mossy_cobblestone')
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                inside = x0 <= x <= x1 and z0 <= z <= z1
                self.set(x, 1, z, water if inside else rim)
                if inside:
                    self.set(x, 0, z, self.block('minecraft:moss_block'))
        for x in range(x0 - 1, x1 + 2, 2):
            if self.solid_at(x, 1, z0 - 1) \
                    and self.get_name(x, 1, z0 - 1) == 'minecraft:mossy_cobblestone':
                self.set(x, 2, z0 - 1, self.block('minecraft:moss_carpet'))

    def lichen(self, x, y, z, face):
        """Glow lichen at (x,y,z) attached toward `face` — the side holding a solid block."""
        dx, dz = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}[face]
        if self.solid_at(x + dx, y, z + dz) and not self.solid_at(x, y, z):
            self.set(x, y, z, self.block('minecraft:glow_lichen', **{face: 'true'}))

    def ore_seam(self, count=4):
        """Ore embedded in the exposed face of exterior walls, at pickable heights."""
        r = self.rng
        placed = 0
        for _ in range(count * 20):
            if placed >= count:
                break
            cx, cz = r.choice(self.cells)
            sides = self._exterior_sides(cx, cz)
            if not sides:
                continue
            x, z, dx, dz = self._wall_col(cx, cz, r.choice(sides), r.randrange(2, S - 2))
            y = r.randint(1, 5)
            if self.solid_at(x, y, z) and not self.solid_at(x + dx, y, z + dz):
                self.set(x, y, z, self.block(r.choice(self.ORES)))
                placed += 1

    def enforce_aprons(self):
        """The §21 doorway contract, stamped after all sculpting so no pass can break it:
        solid floor, clear approach, and a solid wall band for DoorCarver to cut through."""
        r = self.rng
        for (cx, cz) in self.cells:
            for side in self._exterior_sides(cx, cz):
                for i in range(INSET, INSET + DOOR_W):
                    x, z, dx, dz = self._wall_col(cx, cz, side, i)
                    for y in range(1, DOOR_H + 2):
                        self.set(x, y, z, self.stone_blend(y, r))
                    for d in range(1, DEPTH + 2):
                        px, pz = x + dx * d, z + dz * d
                        if not self.owned(px, pz):
                            continue
                        self.set(px, 0, pz, self.floor_blend(r))
                        for y in range(1, DOOR_H + 2):
                            self.set(px, y, pz, self.air)

    # -- verification ----------------------------------------------------------

    def audit(self):
        """RoomAudit mirrored, plus the physics checks the game never makes."""
        errors, warnings = [], []
        for (cx, cz) in self.cells:
            for lx in range(S):
                for lz in range(S):
                    x, z = cx * S + lx, cz * S + lz
                    if not self.solid_at(x, 0, z):
                        errors.append(f'floor hole at {x},0,{z}')
                    if self.is_wall(x, z):
                        continue
                    for y in range(1, DOOR_H + 1):
                        if zone_contains(self.cells, (cx, cz), lx, y, lz) \
                                and self.solid_at(x, y, z):
                            errors.append(f'apron blocked at {x},{y},{z}')
        for (tag, x, y, z) in self.markers:
            if self.in_zone(x, y, z):
                errors.append(f'marker {tag} in doorway zone at {x},{y},{z}')
        kinds = {t.split(':')[0] for (t, _, _, _) in self.markers}
        for required in required_markers(self.key):
            if required not in kinds:
                warnings.append(f'missing required marker {required}')
        bar = wave_bar(self.key)
        plain = sum(1 for (t, _, _, _) in self.markers if t == 'spawn')
        if bar and plain < bar:
            errors.append(f'{plain} plain spawn markers for waves up to {bar}')
        for (x, y, z), idx in self.grid.items():
            name, props = self.palette[idx]
            props = dict(props)
            if name == 'minecraft:pointed_dripstone':
                dy = -1 if props.get('vertical_direction') == 'up' else 1
                if not self.solid_at(x, y + dy, z):
                    errors.append(f'unsupported dripstone at {x},{y},{z}')
            elif name == 'minecraft:water':
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = self.get_name(x + dx, y, z + dz)
                    if n is None or n in AIRLIKE:
                        errors.append(f'water spills at {x},{y},{z}')
                if not self.solid_at(x, y - 1, z):
                    errors.append(f'water floats at {x},{y},{z}')
            elif name in ('minecraft:moss_carpet', 'minecraft:redstone_torch'):
                if not self.solid_at(x, y - 1, z):
                    errors.append(f'{name} unsupported at {x},{y},{z}')
            elif name in ('minecraft:lantern', 'minecraft:soul_lantern'):
                support = y + 1 if props.get('hanging') == 'true' else y - 1
                if not self.solid_at(x, support, z):
                    errors.append(f'{name} unsupported at {x},{y},{z}')
        for (cx, cz) in self.cells:
            for side in self._exterior_sides(cx, cz):
                for i in range(INSET, INSET + DOOR_W):
                    x, z, _, _ = self._wall_col(cx, cz, side, i)
                    for y in range(1, DOOR_H + 1):
                        if not self.solid_at(x, y, z):
                            errors.append(f'door band open at {x},{y},{z} — carves into air')
        return errors, warnings

    # -- output ----------------------------------------------------------------

    def to_nbt(self):
        marker_by_pos = {(x, y, z): tag for (tag, x, y, z) in self.markers}
        blocks = []
        for (x, y, z), idx in sorted(self.grid.items(),
                                     key=lambda e: (e[0][1], e[0][2], e[0][0])):
            entry = {'pos': [x, y, z], 'state': idx}
            if (x, y, z) in marker_by_pos:
                entry['nbt'] = {
                    'mode': 'DATA', 'metadata': marker_by_pos[(x, y, z)],
                    'name': '', 'author': 'teras',
                    'rotation': 'NONE', 'mirror': 'NONE',
                    'posX': 0, 'posY': 1, 'posZ': 0,
                    'sizeX': 0, 'sizeY': 0, 'sizeZ': 0,
                }
            blocks.append(entry)
        palette = []
        for (name, props) in self.palette:
            entry = {'Name': name}
            if props:
                entry['Properties'] = {k: v for k, v in props}
            palette.append(entry)
        return {
            'size': [self.sx, H, self.sz],
            'entities': [],
            'blocks': blocks,
            'palette': palette,
            'DataVersion': DATA_VERSION,
        }

    def preview(self):
        rows = []
        for z in range(self.sz):
            row = []
            for x in range(self.sx):
                if not self.owned(x, z):
                    row.append(' ')
                    continue
                tag = next((t for (t, mx, _, mz) in self.markers if mx == x and mz == z), None)
                if tag:
                    row.append('*' if tag.startswith('spawn') else '@')
                elif self.solid_at(x, 4, z):
                    row.append('#')
                elif self.get_name(x, 1, z) == 'minecraft:water':
                    row.append('~')
                elif self.solid_at(x, 1, z):
                    row.append('▒')
                else:
                    row.append('.')
            rows.append(''.join(row))
        return '\n'.join(rows)


def required_markers(key):
    if key.startswith('boss'):
        return ['boss', 'trapdoor']
    return {
        'mini_boss': ['boss'], 'treasure': ['loot'], 'shop': ['shopslot'],
        'curse': ['loot'], 'sacrifice': ['sacrifice'], 'arcade': ['arcade'],
        'devil_deal': ['deal'], 'challenge': ['spawn', 'challenge'],
        'normal': ['spawn'], 'normal_large': ['spawn'], 'normal_l': ['spawn'],
        'normal_big': ['spawn'],
    }.get(key, [])


def wave_bar(key):
    """Largest wave EnemySpawner can ask of the room: countMax 5, multi-cell
    count*cells/2+1, challenge two waves growing 30%."""
    return {'normal': 5, 'challenge': 7, 'normal_large': 6,
            'normal_l': 8, 'normal_big': 11}.get(key, 0)

# ---------------------------------------------------------------------------- the rooms

def build_start():
    r = Room('start', 'single', 'cuevas:start')
    r.shell()
    rng = r.rng
    # domed ceiling: low at the rim, full height over the centre
    for x in range(1, 20):
        for z in range(1, 20):
            d = max(abs(x - 10), abs(z - 10))
            top = 11 if d <= 4 else (10 if d <= 6 else 9)
            for y in range(top, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    r.rough_walls(light_chance=0.0)
    # warm dome light at the spring line
    for (x, z) in ((7, 7), (13, 7), (7, 13), (13, 13), (10, 6), (10, 14), (6, 10), (14, 10)):
        r.set(x, 9 if max(abs(x - 10), abs(z - 10)) <= 4 else 8, z,
              r.block('minecraft:shroomlight'))
    # the low natural plinth — one worked touch in a wild room
    for x in range(9, 12):
        for z in range(9, 12):
            r.set(x, 1, z, r.block('minecraft:polished_andesite'))
    r.set(10, 2, 10, r.block('minecraft:calcite'))
    for (x, z) in ((8, 10), (12, 10), (10, 8), (10, 12)):
        r.set(x, 1, z, r.block('minecraft:andesite_slab', type='bottom'))
    for (x, z) in ((3, 3), (17, 3), (3, 17), (17, 17), (5, 15), (15, 5)):
        r.stalactite(x, z, rng.randint(2, 3))
    r.stalagmite(3, 5, 2)
    r.stalagmite(17, 15, 2)
    r.ore_seam(3)
    r.lichen(1, 3, 6, 'west')
    r.lichen(19, 4, 13, 'east')
    r.lichen(6, 3, 19, 'south')
    r.enforce_aprons()
    for (x, z) in ((4, 4), (16, 4), (4, 16), (16, 16)):
        r.deco('decoracion:techo', x, 8, z)
    r.deco('decoracion:pared', 2, 2, 6)
    r.deco('decoracion:pared', 18, 2, 14)
    return r


def build_normal():
    r = Room('normal', 'single', 'cuevas:normal')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=9, dripstone=5)
    # the shelf along the east wall, lip lit from beneath, stair carved at its south end
    r.shelf(15, 4, 19, 14, top=4)
    r.ramp(15, 15, 0, -1, top=4)
    for z in (6, 10, 14):
        r.set(15, 3, z, r.block('minecraft:shroomlight'))
    r.pool(3, 3, 5, 6)
    r.stalagmite(7, 15, 3)
    r.stalagmite(5, 12, 2)
    for (x, z) in ((12, 4), (13, 16), (4, 9)):
        r.set(x, 1, z, r.floor_blend(rng))
        r.set(x + 1, 1, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    r.ore_seam(4)
    r.enforce_aprons()
    for (x, z) in ((4, 8), (6, 16), (8, 6), (12, 14), (13, 7), (7, 12)):
        r.spawn(x, z)
    r.spawn(17, 6, ranged=True, y=5)
    r.spawn(17, 12, ranged=True, y=5)
    r.deco('decoracion:techo', 4, 9, 4)
    r.deco('decoracion:techo', 14, 9, 16)
    r.deco('decoracion:suelo', 3, 1, 12)
    r.deco('decoracion:suelo', 16, 1, 16)
    r.deco('decoracion:pared', 1, 3, 6)
    r.deco('decoracion:pared', 19, 3, 16)
    return r


def build_boss():
    r = Room('boss', 'single', 'cuevas:boss')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    # raised rim ringing the arena, gapped around every door band
    rim = r.block('minecraft:cobbled_deepslate')
    for x in range(1, 20):
        for z in range(1, 20):
            if (x in (1, 2) or x in (18, 19) or z in (1, 2) or z in (18, 19)) \
                    and not r.near_clear(x, z):
                r.set(x, 1, z, rim)
    # light falls from above onto the arena's heart
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12), (10, 7), (10, 13), (7, 10), (13, 10)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((4, 4), (16, 4), (4, 16), (16, 16)):
        r.stalactite(x, z, 3)
    r.ore_seam(2)
    r.enforce_aprons()
    r.mark('boss', 10, 1, 10)
    r.mark('trapdoor', 10, 1, 15)
    r.deco('decoracion:techo', 5, 9, 10)
    r.deco('decoracion:techo', 15, 9, 10)
    return r


def build_mini_boss():
    r = Room('mini_boss', 'single', 'cuevas:mini_boss')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=6, dripstone=3)
    # one column, one shelf: cover the boss can use and a vantage the party can take
    for (x, z) in ((6, 14), (7, 14), (6, 15), (7, 15)):
        r.column(x, z)
    r.shelf(1, 1, 6, 5, top=4)
    r.ramp(7, 2, -1, 0, top=4)
    r.set(3, 4, 3, r.block('minecraft:shroomlight'))
    r.set(14, 6, 14, r.block('minecraft:shroomlight'))
    r.ore_seam(3)
    r.enforce_aprons()
    r.mark('boss', 10, 1, 10)
    r.deco('decoracion:techo', 14, 9, 6)
    r.deco('decoracion:pared', 19, 3, 14)
    return r


def build_shop():
    r = Room('shop', 'single', 'cuevas:shop')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    log = r.block('minecraft:spruce_log', axis='y')
    beam = r.block('minecraft:spruce_log', axis='x')
    brick = r.block('minecraft:stone_bricks')
    # the worked floor: someone levelled and paved this end of the cave
    for x in range(3, 18):
        for z in range(1, 6):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, r.block('minecraft:polished_andesite')
                      if (x + z) % 3 else brick)
    # counter row along the north side, split by the door band: plinths under lanterns
    for i, (px, pz) in enumerate(((4, 3), (7, 3), (13, 3), (16, 3)), start=1):
        r.set(px, 1, pz, r.block('minecraft:chiseled_stone_bricks'))
        r.mark(f'shopslot:{i}', px, 2, pz)
        r.set(px, 5, pz, beam)
        r.set(px, 4, pz, r.block('minecraft:lantern', hanging='true'))
    for (px, pz) in ((3, 2), (8, 2), (12, 2), (17, 2)):
        for y in range(1, 6):
            r.set(px, y, pz, log)
    for x in range(3, 18):
        if (x, 2) not in r.keep_clear:
            r.set(x, 6, 2, beam)
    # the keeper's corner: barrels and standing lanterns
    r.set(2, 1, 17, r.block('minecraft:barrel', facing='up'))
    r.set(3, 1, 18, r.block('minecraft:barrel', facing='up'))
    r.set(2, 1, 18, r.block('minecraft:lantern', hanging='false'))
    r.set(17, 1, 17, r.block('minecraft:lantern', hanging='false'))
    r.stalactite(10, 15, 2)
    r.enforce_aprons()
    r.deco('decoracion:techo', 6, 9, 12)
    return r


def build_treasure():
    r = Room('treasure', 'single', 'cuevas:treasure')
    r.shell()
    rng = r.rng
    r.rough_walls(ore_chance=0.10)
    # the vault: a pedestal under a shaft of light, gold seams in the rock
    for (x, z) in ((9, 9), (11, 11), (9, 11), (11, 9)):
        r.set(x, 0, z, r.block('minecraft:polished_andesite'))
    r.set(10, 0, 10, r.block('minecraft:calcite'))
    r.set(10, 1, 10, r.block('minecraft:chiseled_stone_bricks'))
    r.mark('loot', 10, 2, 10)
    for (x, z) in ((9, 10), (11, 10), (10, 9), (10, 11), (10, 10)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((5, 5), (15, 5), (5, 15), (15, 15)):
        r.stalactite(x, z, 3)
    r.stalagmite(5, 7, 2)
    r.stalagmite(15, 13, 2)
    r.set(0, 2, 7, r.block('minecraft:gold_ore'))
    r.set(0, 3, 7, r.block('minecraft:gold_ore'))
    r.set(20, 2, 8, r.block('minecraft:gold_ore'))
    r.ore_seam(4)
    r.enforce_aprons()
    r.deco('decoracion:techo', 4, 9, 15)
    r.deco('decoracion:techo', 16, 9, 5)
    return r


def build_secret():
    r = Room('secret', 'single', 'cuevas:secret')
    r.shell()
    rng = r.rng
    # a cramped pocket: the ceiling presses down to y=5, lichen glow, a seep of water
    for x in range(1, 20):
        for z in range(1, 20):
            for y in range(6, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
            if rng.random() < 0.2 and (x, z) not in r.keep_clear:
                r.set(x, 5, z, r.stone_blend(8, rng))
    r.rough_walls(light_chance=0.0)
    r.pool(14, 14, 16, 16)
    r.lichen(2, 2, 8, 'west')
    r.lichen(18, 3, 12, 'east')
    r.lichen(9, 2, 18, 'south')
    r.set(5, 1, 5, r.block('minecraft:cobblestone'))
    r.mark('loot', 5, 2, 5)
    r.enforce_aprons()
    r.deco('decoracion:suelo', 12, 1, 6)
    return r


def build_super_secret():
    r = Room('super_secret', 'single', 'cuevas:super_secret')
    r.shell()
    rng = r.rng
    calcite = r.block('minecraft:calcite')
    amethyst = r.block('minecraft:amethyst_block')
    # wrong for the piso: a calcite pocket veined with amethyst, one alien light
    for x in range(1, 20):
        for z in range(1, 20):
            r.set(x, 0, z, calcite if rng.random() < 0.7 else amethyst)
            for y in range(5, 11):
                r.set(x, y, z, calcite if rng.random() < 0.8 else r.stone_blend(y, rng))
    for x in range(21):
        for z in range(21):
            if r.is_wall(x, z):
                for y in range(H):
                    r.set(x, y, z, calcite if rng.random() < 0.6 else amethyst)
    for _ in range(10):
        x, z = rng.randrange(2, 19), rng.randrange(2, 19)
        if (x, z) in r.keep_clear:
            continue
        r.set(x, 4, z, amethyst)
        if rng.random() < 0.5:
            r.set(x, 3, z, r.block('minecraft:small_amethyst_bud', facing='down'))
    for (x, z) in ((4, 15), (16, 5), (15, 16)):
        r.set(x, 1, z, r.block('minecraft:budding_amethyst'))
        r.set(x, 2, z, r.block('minecraft:amethyst_cluster', facing='up'))
    # the strange glow: a single end rod on an amethyst plinth
    r.set(10, 1, 14, amethyst)
    r.set(10, 2, 14, r.block('minecraft:end_rod', facing='up'))
    r.set(5, 1, 5, amethyst)
    r.mark('loot', 5, 2, 5)
    r.enforce_aprons()
    return r


def build_challenge():
    r = Room('challenge', 'single', 'cuevas:challenge')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    # corner galleries ring the arena — perches with carved stairs, gapped at the bands
    for (x0, z0, x1, z1, rx, rz, dx, dz) in (
            (1, 1, 6, 6, 7, 2, -1, 0),
            (14, 1, 19, 6, 13, 5, 1, 0),
            (1, 14, 6, 19, 7, 15, -1, 0),
            (14, 14, 19, 19, 13, 18, 1, 0)):
        r.shelf(x0, z0, x1, z1, top=4, light=False)
        r.ramp(rx, rz, dx, dz, top=4)
    # even light: shroomlight flush in the gallery faces and the ceiling
    for (x, z) in ((6, 3), (14, 3), (6, 17), (14, 17), (3, 6), (3, 14), (17, 6), (17, 14)):
        r.set(x, 3, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((8, 8), (12, 12), (12, 8), (8, 12)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    r.enforce_aprons()
    r.mark('challenge', 10, 1, 10)
    for (x, z) in ((6, 8), (8, 6), (12, 6), (14, 8), (6, 12), (8, 14), (12, 14), (14, 12)):
        r.spawn(x, z)
    r.spawn(3, 3, ranged=True, y=5)
    r.spawn(17, 17, ranged=True, y=5)
    r.deco('decoracion:techo', 10, 9, 6)
    r.deco('decoracion:techo', 10, 9, 14)
    return r


def build_curse():
    r = Room('curse', 'single', 'cuevas:curse')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    black = r.block('minecraft:blackstone')
    pol = r.block('minecraft:polished_blackstone')
    # the shrine against the south rock; blackstone creeps out into the cave floor
    for x in range(6, 15):
        for z in range(12, 16):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, pol if rng.random() < 0.5 else black)
    for x in range(1, 20):
        for z in range(1, 20):
            d = abs(x - 10) + abs(z - 14)
            if d < 8 and rng.random() < (8 - d) / 14:
                r.set(x, 0, z, black)
    for (px, pz) in ((7, 14), (13, 14)):
        for y in range(1, 5):
            r.set(px, y, pz, r.block('minecraft:basalt', axis='y'))
        r.set(px, 5, pz, r.block('minecraft:polished_basalt', axis='y'))
    r.set(10, 1, 14, r.block('minecraft:chiseled_polished_blackstone'))
    r.mark('loot', 10, 2, 14)
    r.set(9, 1, 14, r.block('minecraft:crying_obsidian'))
    r.set(11, 1, 14, r.block('minecraft:crying_obsidian'))
    r.set(9, 2, 14, r.block('minecraft:redstone_torch'))
    r.set(11, 2, 14, r.block('minecraft:redstone_torch'))
    r.stalactite(5, 5, 3)
    r.stalactite(15, 6, 2)
    r.enforce_aprons()
    r.deco('decoracion:techo', 5, 9, 13)
    return r


def build_sacrifice():
    r = Room('sacrifice', 'single', 'cuevas:sacrifice')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    basalt = r.block('minecraft:smooth_basalt')
    pol = r.block('minecraft:polished_basalt', axis='y')
    # the altar platform: raised smooth basalt, channels open to a magma glow below
    for x in range(6, 15):
        for z in range(6, 15):
            if (x, z) in r.keep_clear:
                continue
            in_channel = (x == 10 and z in (7, 8, 12, 13)) or (z == 10 and x in (7, 8, 12, 13))
            if in_channel:
                r.set(x, 0, z, r.block('minecraft:magma_block'))
            else:
                r.set(x, 1, z, basalt if rng.random() < 0.8 else pol)
    r.set(10, 1, 10, r.block('minecraft:chiseled_polished_blackstone'))
    r.mark('sacrifice', 10, 2, 10)
    for (x, z) in ((6, 6), (14, 6), (6, 14), (14, 14)):
        r.set(x, 2, z, r.block('minecraft:polished_blackstone_wall'))
    r.stalactite(4, 16, 2)
    r.stalactite(16, 4, 2)
    r.enforce_aprons()
    r.deco('decoracion:techo', 15, 9, 15)
    return r


def build_arcade():
    r = Room('arcade', 'single', 'cuevas:arcade')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=5, dripstone=4)
    # the curiosity: a lit plinth in a dim cave, quartz trim nothing here explains
    for x in range(8, 13):
        for z in range(8, 13):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, r.block('minecraft:polished_andesite'))
    r.set(10, 1, 10, r.block('minecraft:quartz_block'))
    r.mark('arcade', 10, 2, 10)
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12)):
        r.set(x, 1, z, r.block('minecraft:quartz_slab', type='bottom'))
    for (px, pz) in ((7, 10), (13, 10)):
        for y in range(1, 4):
            r.set(px, y, pz, r.block('minecraft:spruce_fence'))
        r.set(px, 4, pz, r.block('minecraft:lantern', hanging='false'))
    r.ore_seam(3)
    r.enforce_aprons()
    r.deco('decoracion:suelo', 4, 1, 15)
    return r


def build_devil_deal():
    r = Room('devil_deal', 'single', 'cuevas:devil_deal')
    r.shell()
    rng = r.rng
    black = r.block('minecraft:blackstone')
    # near-dark: blackstone floor, a gilded circle, iron cage arcs, two cold lights
    for x in range(1, 20):
        for z in range(1, 20):
            r.set(x, 0, z, black if rng.random() < 0.75
                  else r.block('minecraft:polished_blackstone'))
    for (x, z) in ((9, 9), (11, 9), (9, 11), (11, 11)):
        r.set(x, 0, z, r.block('minecraft:gilded_blackstone'))
    r.set(10, 0, 10, r.block('minecraft:polished_blackstone_bricks'))
    r.mark('deal', 10, 1, 10)
    bars_ns = r.block('minecraft:iron_bars', north='true', south='true')
    for (bx, z0, z1) in ((6, 8, 12), (14, 8, 12)):
        for z in range(z0, z1 + 1):
            for y in range(1, 4):
                r.set(bx, y, z, bars_ns)
    for (x, z) in ((6, 6), (14, 14)):
        r.set(x, 6, z, black)
        r.set(x, 5, z, r.block('minecraft:soul_lantern', hanging='true'))
    r.enforce_aprons()
    return r


def build_normal_large():
    r = Room('normal_large', 'large', 'cuevas:normal_large')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=16, dripstone=9)
    # two chambers joined by a narrow arched neck at the cell boundary
    for z in list(range(1, 7)) + list(range(14, 20)):
        for x in range(19, 23):
            for y in range(1, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    for x in range(19, 23):
        for z in range(7, 14):
            for y in range(8, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    # west chamber: pool and stalagmites; east chamber: shelf running the north wall
    r.pool(4, 13, 7, 16)
    r.stalagmite(13, 5, 3)
    r.stalagmite(15, 7, 2)
    r.shelf(26, 1, 39, 5, top=4)
    r.ramp(26, 6, 0, -1, top=4)
    for x in (28, 34, 38):
        r.set(x, 3, 5, r.block('minecraft:shroomlight'))
    r.set(5, 6, 5, r.block('minecraft:shroomlight'))
    r.set(36, 6, 15, r.block('minecraft:shroomlight'))
    r.ore_seam(6)
    r.enforce_aprons()
    for (x, z) in ((5, 8), (8, 15), (13, 12), (26, 14), (31, 8), (36, 15), (16, 6)):
        r.spawn(x, z)
    r.spawn(27, 3, ranged=True, y=5)
    r.spawn(37, 3, ranged=True, y=5)
    r.deco('decoracion:techo', 8, 9, 8)
    r.deco('decoracion:techo', 33, 9, 12)
    r.deco('decoracion:suelo', 13, 1, 16)
    r.deco('decoracion:suelo', 28, 1, 10)
    r.deco('decoracion:pared', 1, 3, 14)
    r.deco('decoracion:pared', 40, 3, 8)
    return r


def build_normal_l():
    r = Room('normal_l', 'l', 'cuevas:normal_l')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=20, dripstone=12)
    # the elbow massif: a perch at the inner corner overlooking both arms
    r.shelf(14, 14, 19, 19, top=5)
    r.ramp(13, 15, 1, 0, top=5)      # carved stair up the massif's west face
    r.ramp(15, 13, 0, 1, top=5)      # and up its north face
    r.set(14, 5, 17, r.block('minecraft:shroomlight'))
    r.set(17, 5, 14, r.block('minecraft:shroomlight'))
    # east arm: stalagmite field and a pool under the far wall
    r.stalagmite(27, 6, 3)
    r.stalagmite(30, 12, 2)
    r.stalagmite(34, 8, 3)
    r.pool(35, 14, 38, 17)
    # south arm: a seep pool and broken ground
    r.pool(7, 30, 10, 33)
    r.stalagmite(13, 28, 2)
    r.stalagmite(6, 25, 3)
    r.set(9, 6, 9, r.block('minecraft:shroomlight'))
    r.set(30, 6, 16, r.block('minecraft:shroomlight'))
    r.set(15, 6, 30, r.block('minecraft:shroomlight'))
    r.ore_seam(7)
    r.enforce_aprons()
    for (x, z) in ((6, 6), (12, 9), (8, 14), (26, 8), (31, 15), (36, 6),
                   (7, 27), (14, 31), (8, 36)):
        r.spawn(x, z)
    r.spawn(18, 16, ranged=True, y=6)
    r.spawn(16, 18, ranged=True, y=6)
    r.spawn(18, 18, ranged=True, y=6)
    r.deco('decoracion:techo', 8, 9, 8)
    r.deco('decoracion:techo', 33, 9, 10)
    r.deco('decoracion:techo', 10, 9, 33)
    r.deco('decoracion:suelo', 24, 1, 14)
    r.deco('decoracion:suelo', 10, 1, 24)
    r.deco('decoracion:pared', 1, 3, 16)
    r.deco('decoracion:pared', 40, 3, 12)
    r.deco('decoracion:pared', 14, 3, 40)
    return r


def build_normal_big():
    r = Room('normal_big', 'big', 'cuevas:normal_big')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=26, dripstone=14)
    # the central massif: three terraces the ring route circles, perches on top
    for (x0, z0, x1, z1, top) in ((15, 15, 26, 26, 2), (16, 16, 25, 25, 3), (17, 17, 24, 24, 4)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(1, top + 1):
                    r.set(x, y, z, r.stone_blend(y, rng))
    # carved stairs up the west and east faces: one stair per terrace, headroom cut
    for (steps, facing) in ((((14, 1), (15, 2), (16, 3), (17, 4)), 'east'),
                            (((27, 1), (26, 2), (25, 3), (24, 4)), 'west')):
        for z in (20, 21):
            for (sx, sy) in steps:
                for y in range(1, sy):
                    r.set(sx, y, z, r.stone_blend(y, rng))
                r.set(sx, sy, z, r.block('minecraft:cobblestone_stairs',
                                         facing=facing, half='bottom'))
                r.clear(sx, sy + 1, z, sx, min(H - 2, sy + 3), z)
    r.set(20, 4, 20, r.block('minecraft:shroomlight'))
    r.set(21, 4, 21, r.block('minecraft:shroomlight'))
    # corner set pieces around the ring
    r.pool(4, 4, 7, 7)
    r.pool(34, 34, 37, 37)
    r.stalagmite(34, 6, 3)
    r.stalagmite(37, 9, 2)
    r.stalagmite(6, 34, 3)
    r.stalagmite(9, 37, 2)
    for (x, z) in ((5, 20), (36, 20), (20, 5), (21, 36)):
        r.set(x, 6, z, r.block('minecraft:shroomlight'))
    r.ore_seam(8)
    r.enforce_aprons()
    for (x, z) in ((6, 12), (12, 6), (29, 6), (35, 12), (6, 29), (12, 35),
                   (29, 35), (35, 29), (13, 24), (28, 17), (24, 13), (17, 28)):
        r.spawn(x, z)
    for (x, z) in ((18, 18), (23, 23), (18, 23), (23, 18)):
        r.spawn(x, z, ranged=True, y=5)
    r.deco('decoracion:techo', 8, 9, 20)
    r.deco('decoracion:techo', 33, 9, 21)
    r.deco('decoracion:techo', 20, 9, 8)
    r.deco('decoracion:techo', 21, 9, 33)
    r.deco('decoracion:suelo', 10, 1, 15)
    r.deco('decoracion:suelo', 31, 1, 26)
    r.deco('decoracion:pared', 1, 3, 20)
    r.deco('decoracion:pared', 40, 3, 21)
    return r


def build_boss_big():
    r = Room('boss_big', 'big', 'cuevas:boss_big')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    # the arena scaled up: rim ring, framing columns at the quarters, a great oculus
    rim = r.block('minecraft:cobbled_deepslate')
    for x in range(1, 41):
        for z in range(1, 41):
            if (x in (1, 2, 39, 40) or z in (1, 2, 39, 40)) and not r.near_clear(x, z):
                r.set(x, 1, z, rim)
    for (cx, cz) in ((10, 10), (30, 10), (10, 30), (30, 30)):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            r.column(cx + dx, cz + dz)
        r.set(cx, 8, cz, r.block('minecraft:shroomlight'))
    for (x, z) in ((18, 18), (23, 18), (18, 23), (23, 23), (20, 16), (21, 16),
                   (20, 25), (21, 25), (16, 20), (16, 21), (25, 20), (25, 21)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((6, 6), (35, 6), (6, 35), (35, 35)):
        r.stalactite(x, z, 3)
    r.enforce_aprons()
    r.mark('boss', 21, 1, 21)
    r.mark('trapdoor', 21, 1, 27)
    r.deco('decoracion:techo', 10, 9, 21)
    r.deco('decoracion:techo', 31, 9, 21)
    r.deco('decoracion:techo', 21, 9, 10)
    r.deco('decoracion:techo', 21, 9, 31)
    return r


BUILDERS = {
    'start': build_start, 'normal': build_normal, 'boss': build_boss,
    'mini_boss': build_mini_boss, 'shop': build_shop, 'treasure': build_treasure,
    'secret': build_secret, 'super_secret': build_super_secret,
    'challenge': build_challenge, 'curse': build_curse, 'sacrifice': build_sacrifice,
    'arcade': build_arcade, 'devil_deal': build_devil_deal,
    'normal_large': build_normal_large, 'normal_l': build_normal_l,
    'normal_big': build_normal_big, 'boss_big': build_boss_big,
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true', help='print floor plans, write nothing')
    parser.add_argument('--room', help='build only this room key')
    parser.add_argument('--out', default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..',
        'src/main/resources/data/teras/structure/dungeon/cuevas'))
    args = parser.parse_args()

    failed = False
    for key, builder in BUILDERS.items():
        if args.room and key != args.room:
            continue
        room = builder()
        errors, warnings = room.audit()
        plain = sum(1 for (t, _, _, _) in room.markers if t == 'spawn')
        ranged = sum(1 for (t, _, _, _) in room.markers if t == 'spawn:ranged')
        print(f'{key:14} {room.sx}x{H}x{room.sz}  markers={len(room.markers)}'
              f' (spawn {plain}+{ranged}r)  palette={len(room.palette)}')
        for w in warnings:
            print(f'    ! {w}')
        for e in errors:
            print(f'    x {e}')
            failed = True
        if args.preview:
            print(room.preview())
            print()
        elif not errors:
            os.makedirs(args.out, exist_ok=True)
            write_nbt(os.path.join(args.out, key + '.nbt'), room.to_nbt())
    if failed:
        print('ERRORS - nothing written for failing rooms', file=sys.stderr)
        return 1
    if not args.preview:
        print(f'\nwrote templates to {os.path.abspath(args.out)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
