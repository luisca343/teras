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
import json
import math
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

# What each kind of room wears on its roof, read from the air in creative or spectator.
#
# Chosen for legibility at altitude against a field of grey stone, which rules out grey, brown and
# anything desaturated, and which is why minerals appear at all: gloss and texture separate two
# blocks that hue alone would not. Footprint and position already name several rooms without help —
# the boss is always 2x2, the sello is the 2x2 appended behind it, and the pacto and the Orden are
# its two flanks — so obsidian against lime across that wing reads as the fork it is.
#
# Normal rooms wear nothing. They are the bulk of every floor, and a marking that covers everything
# marks nothing.
CROWNS = {
    'start':        'minecraft:light_blue_concrete',
    'boss':         'minecraft:red_concrete',
    'boss_big':     'minecraft:red_concrete',
    'mini_boss':    'minecraft:orange_concrete',
    'shop':         'minecraft:emerald_block',
    'treasure':     'minecraft:gold_block',
    'secret':       'minecraft:quartz_block',
    'super_secret': 'minecraft:pink_concrete',
    'challenge':    'minecraft:magenta_concrete',
    'curse':        'minecraft:purple_concrete',
    'sacrifice':    'minecraft:redstone_block',
    'arcade':       'minecraft:cyan_concrete',
    'devil_deal':   'minecraft:obsidian',
    'exit':         'minecraft:lapis_block',
    'orden':        'minecraft:lime_concrete',
}

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
        self.block_nbt = {}         # (x,y,z) -> block entity nbt (signs); markers carry their own
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

    def auto_spawns(self, count, ranged=0):
        """Scatters `count` floor spawns (and `ranged` perch spawns) over the room's usable space.

        Hand-placing these stopped being reasonable once the party's share reached the bar: a 2x2
        normal room has to hold 23. Positions are chosen deterministically and greedily spread —
        each new marker takes the candidate furthest from those already placed — so a wave fills
        the room rather than clumping in a corner, and the same seed gives the same room.
        """
        placed = []
        for (x, z, y) in self._candidates(ranged=False):
            if len(placed) >= count:
                break
            if self._far_enough(placed, x, z, count):
                self.spawn(x, z, y=y)
                placed.append((x, z))
        if len(placed) < count:
            # Second pass with no spacing requirement: a cramped room still has to meet the bar,
            # and two markers a block apart beat the spawner stacking a whole wave on one.
            for (x, z, y) in self._candidates(ranged=False):
                if len(placed) >= count:
                    break
                if (x, z) not in placed:
                    self.spawn(x, z, y=y)
                    placed.append((x, z))
        perches = []
        for (x, z, y) in self._candidates(ranged=True):
            if len(perches) >= ranged:
                break
            if self._far_enough(perches, x, z, max(1, ranged)):
                self.spawn(x, z, ranged=True, y=y)
                perches.append((x, z))
        return len(placed), len(perches)

    def _candidates(self, ranged):
        """Standable positions, in a stable order. A perch is anything at y>=5 with headroom.

        Filtered by whether the spot connects to a doorway, which plain standability does not
        answer: the top of a lone stalagmite has solid footing and two blocks of headroom, and an
        enemy left there is scenery. `repisa` shipped with one such marker from §27 onward.

        Perches are held to the stricter player rule as well — a ranged enemy on a ledge nobody can
        climb is not a fight, it is a war of attrition, which is exactly what rule 4 of the room
        checklist says and what nothing until now enforced."""
        out = []
        heights = self.walk_heights()
        entries = [e for e in self.door_entries() if e in heights]
        reachable = self._can_descend_to(heights, entries) if entries else set()
        climbable = self._parkour_reach(
            heights, self._reachable(heights, entries[0])) if entries else set()
        taken = {(mx, mz) for (_, mx, _, mz) in self.markers}
        for (cx, cz) in self.cells:
            for lx in range(2, S - 2):
                for lz in range(2, S - 2):
                    x, z = cx * S + lx, cz * S + lz
                    if (x, z) in self.keep_clear or (x, z) in taken or self.is_wall(x, z):
                        continue
                    for y in range(1, H - 3):
                        if not self.solid_at(x, y - 1, z) or self.solid_at(x, y, z):
                            continue
                        if self.in_zone(x, y, z):
                            continue
                        # Headroom, so nothing spawns inside the ceiling or a shelf above it.
                        if self.solid_at(x, y + 1, z) or self.solid_at(x, y + 2, z):
                            continue
                        high = y >= 5
                        if high == ranged and (x, z) in reachable \
                                and (not ranged or (x, z) in climbable):
                            out.append((x, z, y))
                        break
        return out

    # How far up a player can take a ledge with ParCool, which ships in the pack: wall-run and
    # cling-to-cliff put a four-block lip inside everybody's reach. Deliberately short of what a
    # chained wall-jump can do — this bounds where a RANGED ENEMY may stand, and the cost of being
    # generous is an archer nobody can answer.
    PARKOUR_CLIMB = 4

    @staticmethod
    def _parkour_reach(heights, walkable):
        """Walkable tiles, plus the ledges a player can climb to from them, plus ledges off those.

        The walking rule (one block up per step) is the floor a room is held to and stays that way —
        it is what a player with no client mod can do, and doorways still have to satisfy it. But it
        is no longer the ceiling: a perch that only walking could not take is not out of reach, it is
        four blocks and some stamina away, and holding ranged spawns to the walking rule would forbid
        every free-standing perch the parkour pass was built to introduce.
        """
        seen = set(walkable)
        frontier = list(walkable)
        while frontier:
            nxt = []
            for (x, z) in frontier:
                for n in Room._neighbours(x, z):
                    if n in seen or n not in heights:
                        continue
                    if heights[n] - heights[(x, z)] > Room.PARKOUR_CLIMB:
                        continue
                    seen.add(n)
                    nxt.append(n)
            frontier = nxt
        return seen

    @staticmethod
    def _far_enough(placed, x, z, count):
        # Loosens as the room fills: a bar of 23 cannot also demand they stay far apart.
        spacing = 5 if count <= 8 else (4 if count <= 14 else 3)
        return all(abs(px - x) + abs(pz - z) >= spacing for (px, pz) in placed)

    def sign(self, x, y, z, facing, lines, color='black'):
        """A wall sign carrying carved text — the inscription wall's medium.

        Text goes in as flat JSON components, which is what SignText's codec reads at this
        DataVersion; missing fields (glow, wax, back face) take their codec defaults."""
        name = self.get_name(x, y, z)
        assert name in AIRLIKE, f'{self.key}: sign at {x},{y},{z} over {name}'
        self.set(x, y, z, self.block('minecraft:dark_oak_wall_sign', facing=facing))
        msgs = [json.dumps({'text': t}, ensure_ascii=False) for t in (list(lines) + [''] * 4)[:4]]
        self.block_nbt[(x, y, z)] = {'front_text': {'messages': msgs, 'color': color}}

    def chest(self, kind, x, z, y=1):
        """Where a chest stands (PISOS §69, §71).

        The marker is the chest's own block, not a stand under one: at runtime ChestPedestal puts a
        real chest there, on the ground, because a chest is a container and you walk up to a
        container. Pedestals are for items — the shop's wares, the treasure stands — and putting a
        chest on one is what made them read as an icon hovering over a plinth.

        So `y` is the block the chest occupies, and it needs something solid under it: the floor for
        most, a ledge or a deck for a proeza. Kind is libre / sellado / puas / trampa / proeza.
        Nothing here checks that a proeza is out of walking reach — the audit models walking on
        purpose — so mark the route with `parkour` and the next author will know it is load-bearing.
        """
        assert self.solid_at(x, y - 1, z), \
            f'{self.key}: chest at {x},{y},{z} has nothing under it'
        self.mark('cofre:' + kind, x, y, z)

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

    def crown(self, key, variant):
        """Paints the room's kind on the one surface a player inside can never see.

        The ring is the ceiling blocks sitting directly on top of the walls: their undersides are
        buried against the wall, their inner faces against the rest of the ceiling, and only the top
        and the outward faces are exposed — both of which are outside the room. Nothing else writes
        there either; rough_walls stops below H-1 and ceiling_relief skips wall columns.

        The ring is left broken for `variant` blocks at the template's own (0,0) corner. The length
        of the notch counts the variant, and where the notch ends up once the template is rotated is
        the only way to read a placement's rotation from the air.
        """
        block = CROWNS.get(key)
        if block is None:
            return
        idx = self.block(block)
        notch = {(x, 0) for x in range(max(1, min(variant, S - 2)))}
        for x in range(self.sx):
            for z in range(self.sz):
                if self.is_wall(x, z) and (x, z) not in notch:
                    self.set(x, H - 1, z, idx)

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

    def walk_heights(self):
        """The y a player stands at on each tile, or absent where nothing can stand.

        Lowest standable surface: solid underfoot, two blocks of air above. Water counts as solid,
        so a flooded tile stands on the surface — which is what a mob wading in it does."""
        heights = {}
        for (cx, cz) in self.cells:
            for lx in range(S):
                for lz in range(S):
                    x, z = cx * S + lx, cz * S + lz
                    if self.is_wall(x, z):
                        continue
                    for y in range(1, H - 2):
                        if not self.solid_at(x, y - 1, z):
                            continue
                        if self.solid_at(x, y, z) or self.solid_at(x, y + 1, z):
                            continue
                        heights[(x, z)] = y
                        break
        return heights

    def spread_from_doorways(self):
        """Tiles by their step distance from the nearest doorway apron.

        Used to cap how high terrain may be built: capping a tile's height at its distance means
        the ground can only ever rise one block per tile away from a door, so anything sculpted
        against an apron becomes a stair instead of a cliff. Both rooms that got this wrong built
        their terrain from the wall inward and forgot the aprons are held flat."""
        spread = {}
        frontier = [(x, z) for (x, z) in self.keep_clear
                    if self.owned(x, z) and not self.is_wall(x, z)]
        for cell in frontier:
            spread[cell] = 0
        while frontier:
            nxt = []
            for (x, z) in frontier:
                for n in self._neighbours(x, z):
                    if n in spread or not self.owned(*n) or self.is_wall(*n):
                        continue
                    spread[n] = spread[(x, z)] + 1
                    nxt.append(n)
            frontier = nxt
        return spread

    def door_entries(self):
        """One tile inside each doorway, where a player actually arrives."""
        out = []
        for (cx, cz) in self.cells:
            for side in self._exterior_sides(cx, cz):
                for i in range(INSET, INSET + DOOR_W):
                    x, z, dx, dz = self._wall_col(cx, cz, side, i)
                    out.append((x + dx * DEPTH, z + dz * DEPTH))
        return out

    @staticmethod
    def _neighbours(x, z):
        return ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))

    @staticmethod
    def _reachable(heights, start):
        """Where a player can walk from `start`: one block up or down per step, no more.

        Symmetric on purpose. Allowing free descent would pass a room you can enter and not leave,
        and a pit you cannot climb out of is worse than a wall."""
        seen = {start}
        queue = [start]
        while queue:
            (x, z) = queue.pop()
            for n in Room._neighbours(x, z):
                if n in seen or n not in heights:
                    continue
                if abs(heights[n] - heights[(x, z)]) > 1:
                    continue
                seen.add(n)
                queue.append(n)
        return seen

    @staticmethod
    def _can_descend_to(heights, entries):
        """Tiles from which an enemy could get to a doorway — climbing a block or dropping any.

        A different question from the player's, and it has to be: a mob on a two-block rock is not
        stranded, it just steps off. Holding spawns to the player's symmetric rule would condemn
        every perch in the game, including the ones the ranged enemies are meant to hold."""
        seen = set(entries)
        queue = list(entries)
        while queue:
            (x, z) = queue.pop()
            for n in Room._neighbours(x, z):
                if n in seen or n not in heights:
                    continue
                # Walking n -> (x,z) means climbing at most one; any drop is free.
                if heights[(x, z)] <= heights[n] + 1:
                    seen.add(n)
                    queue.append(n)
        return seen

    # Blocks that fall when what is under them goes away. A room's floor layer has the VOID under
    # it — the pad is a slot in an empty dimension — so one of these at y=0 does not settle, it
    # falls forever and leaves a hole the party drops through. `campamento` shipped with a gravel
    # floor and killed everyone on arrival, because a start room's hole is under the spawn point.
    GRAVITY_BLOCKS = {'minecraft:gravel', 'minecraft:sand', 'minecraft:red_sand',
                      'minecraft:suspicious_gravel', 'minecraft:suspicious_sand',
                      'minecraft:anvil', 'minecraft:chipped_anvil', 'minecraft:damaged_anvil',
                      'minecraft:pointed_dripstone'}

    # Markers whose fixture hangs a floating item and a label over itself, at roughly +1.2 and
    # +1.9. Anything solid in those two blocks stands in front of what the fixture is showing —
    # which is how a shop shipped with a lantern hung directly over every ware for sale.
    DISPLAY_MARKERS = {'shopslot', 'loot', 'premio', 'oferta', 'purga', 'cofre'}

    def audit(self):
        """RoomAudit mirrored, plus the physics checks the game never makes."""
        errors, warnings = [], []
        for (x, y, z), idx in self.grid.items():
            name = self.palette[idx][0]
            if name not in self.GRAVITY_BLOCKS:
                continue
            # Dripstone is in the set for its upward form only; the hanging kind is checked below.
            if name == 'minecraft:pointed_dripstone' \
                    and dict(self.palette[idx][1]).get('vertical_direction') != 'up':
                continue
            if y == 0:
                errors.append(f'{name} at {x},0,{z} is in the floor layer — it falls into the void')
            elif not self.solid_at(x, y - 1, z):
                errors.append(f'{name} at {x},{y},{z} has nothing under it and will fall')
        for (tag, x, y, z) in self.markers:
            if tag.split(':')[0] not in self.DISPLAY_MARKERS:
                continue
            for dy in (1, 2):
                if self.solid_at(x, y + dy, z):
                    errors.append(f'{tag} at {x},{y},{z} is covered at +{dy} — its floating item '
                                  f'and price would be hidden behind '
                                  f'{self.get_name(x, y + dy, z)}')
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

        # Can you actually walk the room? Every earlier rule checks the doorway is *open*; none
        # checked that what is behind it can be entered. anfiteatro shipped with terraces up to five
        # blocks high built right against its doorway aprons, so all four doors opened onto a wall
        # and the room could be seen and never entered. Nothing caught it because every individual
        # rule passed.
        heights = self.walk_heights()
        entries = [e for e in self.door_entries() if e in heights]
        if entries:
            walkable = self._reachable(heights, entries[0])
            for entry in entries[1:]:
                if entry not in walkable:
                    errors.append(f'doorway at {entry[0]},{entry[1]} cannot be walked to from '
                                  f'{entries[0][0]},{entries[0][1]} — the room is cut in two')
            # Spawns answer the mob's question, not the player's.
            fightable = self._can_descend_to(heights, entries)
            for (tag, x, y, z) in self.markers:
                if tag.startswith('spawn') and (x, z) in heights and (x, z) not in fightable:
                    errors.append(f'{tag} at {x},{z} is sealed off — whatever spawns there can '
                                  f'never reach the party')
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
            elif (x, y, z) in self.block_nbt:
                entry['nbt'] = self.block_nbt[(x, y, z)]
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


# Which markers each room key must carry. Read from the same file RoomKeys reads, not written out
# again here: this table and its Java twin were hand-maintained mirrors, and they drifted the first
# time it mattered — the fix that made secret rooms pay out added `loot` here and not in Java, so a
# secret authored in the in-game editor still shipped with no pedestal and paid nothing.
MARKERS_FILE = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..',
    'src/main/resources/data/teras/dungeon/required_markers.txt'))


def _load_required_markers():
    table = {}
    with open(MARKERS_FILE, encoding='utf-8') as handle:
        for raw in handle:
            line = raw.split('#', 1)[0].strip()
            if not line:
                continue
            key, _, rest = line.partition(':')
            table[key.strip()] = [m.strip() for m in rest.split(',') if m.strip()]
    return table


REQUIRED_MARKERS = _load_required_markers()


def required_markers(key):
    return REQUIRED_MARKERS.get(key, [])


# The largest wave EnemySpawner can ask a room for, mirroring RoomAuditor.waveMax:
# countMax (5) -> multi-cell count*cells/2+1 -> challenge growth (+30% per wave) -> and finally
# the party's share, capped at PartyScaling.MAX_PARTY_FACTOR. The party factor is the reason these
# numbers are large: the marker count is the only thing bounding a wave, so a room has to hold the
# biggest one a full group can pull, not the one a solo runner sees.
MAX_PARTY_FACTOR = 2.05


def wave_bar(key):
    # The base is the largest countMax any piso throws at a room of this shape — a shared template
    # has to satisfy its strictest floor. Single 'normal' rooms are used by Cuevas (countMax 5) and
    # Cuevas Infestadas (countMax 6), so the base is 6: at 5 the infested single rooms held 11 where
    # a full party's wave is 13, and the game's own audit flagged them.
    base = {'normal': 6, 'challenge': 7, 'normal_large': 6,
            'normal_l': 8, 'normal_big': 11}.get(key, 0)
    if base == 0:
        return 0
    return int(math.ceil(base * MAX_PARTY_FACTOR))

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
    # the arrival dais: the party lands on it, so it is raised and its top stays clear
    for x in range(9, 12):
        for z in range(9, 12):
            r.set(x, 1, z, r.block('minecraft:polished_andesite'))
    r.set(10, 1, 10, r.block('minecraft:calcite'))
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
    # Where the party lands: on the dais, not in it. Without this marker arrival falls back to the
    # room centre, which here is the dais block itself.
    r.mark('inicio', 10, 2, 10)
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
    r.auto_spawns(wave_bar('normal'), ranged=2)
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


# Where a shop's pedestals stand along the counter. Six, because ShopStock.planogram fills its
# shelves in priority order and stops at the counter's length — at four the gamble and the premium
# shelf can never appear at all. Clear of x 9..11, the north doorway band.
SHOP_SLOT_X = (3, 5, 7, 13, 15, 17)


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
    for i, px in enumerate(SHOP_SLOT_X, start=1):
        r.set(px, 1, 3, r.block('minecraft:chiseled_stone_bricks'))
        r.mark(f'shopslot:{i}', px, 2, 3)
    # Between the plinths, for the same reason as socavon: the ware and its price float in the
    # slot's own column, and anything hung there is in front of them.
    for px in (4, 6, 14, 16):
        r.set(px, 5, 3, beam)
        r.set(px, 4, 3, r.block('minecraft:lantern', hanging='true'))
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
    # The vault: three stands under one shaft of light, gold seams in the rock. Three, not one —
    # the treasure room is a visible choice and each player takes a single stand (§66), and the
    # qualifier on each `loot` marker is what names the archetype to TreasureChoice.
    for x in range(7, 14):
        for z in range(7, 14):
            if (x, z) not in r.keep_clear and max(abs(x - 10), abs(z - 10)) <= 3:
                r.set(x, 0, z, r.block('minecraft:polished_andesite'))
    r.set(10, 0, 10, r.block('minecraft:calcite'))
    for (px, pz, tag) in ((8, 10, 'loot:arma'), (10, 8, 'loot:vitalidad'),
                          (12, 10, 'loot:provision')):
        r.set(px, 1, pz, r.block('minecraft:chiseled_stone_bricks'))
        r.mark(tag, px, 2, pz)
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



def build_secret_alacena():
    """The other kind of secret: not a gap in the rock but a cache somebody squared off, stocked
    and walled up. rendija is natural, cramped and wet; this one is worked and dry, so finding
    either one reads as a different discovery rather than the same pocket twice."""
    r = Room('secret', 'single', 'cuevas:secret:alacena')
    r.shell()
    rng = r.rng
    # the roof presses down, as it does in every secret — you crawl into these
    for x in range(1, 20):
        for z in range(1, 20):
            for y in range(6, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    r.rough_walls(light_chance=0.0)
    brick = r.block('minecraft:stone_bricks')
    mossy = r.block('minecraft:mossy_stone_bricks')
    cracked = r.block('minecraft:cracked_stone_bricks')
    # the worked floor of the cache, in the corner the doorway bands leave alone
    for x in range(12, 19):
        for z in range(2, 9):
            if (x, z) in r.keep_clear:
                continue
            roll = rng.random()
            r.set(x, 0, z, brick if roll < 0.5 else (mossy if roll < 0.8 else cracked))
    # a low surround you step over, open toward the room so the cache can be walked into
    for (x, z) in ((12, 2), (12, 3), (12, 4), (12, 5), (12, 6), (12, 7), (12, 8),
                   (13, 8), (14, 8), (15, 8), (16, 8), (17, 8), (18, 8)):
        if (x, z) not in r.keep_clear:
            r.set(x, 1, z, r.block('minecraft:stone_brick_slab', type='bottom'))
    # shelving against the back wall, and the light someone left burning
    for z in (3, 5, 7):
        r.set(18, 1, z, r.block('minecraft:stone_brick_wall'))
        r.set(18, 2, z, r.block('minecraft:stone_brick_slab', type='top'))
    # y=5, so they hang from the ceiling at y=6 rather than from nothing
    r.set(16, 5, 2, r.block('minecraft:lantern', hanging='true'))
    r.set(14, 5, 7, r.block('minecraft:lantern', hanging='true'))
    # the pedestal
    r.set(15, 1, 5, r.block('minecraft:chiseled_stone_bricks'))
    r.mark('loot', 15, 2, 5)
    # rubble where it was broken open, spilling away from the cache
    for (x, z) in ((10, 6), (9, 8), (11, 9), (8, 5)):
        if (x, z) not in r.keep_clear:
            r.set(x, 1, z, r.block('minecraft:cobblestone'))
    r.stalagmite(4, 15, 2)
    r.stalagmite(6, 4, 2)
    r.lichen(1, 3, 12, 'west')
    r.ore_seam(2)
    r.enforce_aprons()
    r.deco('decoracion:suelo', 5, 1, 9)
    r.deco('decoracion:pared', 1, 3, 6)
    return r



def build_secret_veta():
    """Someone was mining toward this and stopped. The ore is the reward's excuse: a seam running
    out of the rock, a squared-off face where the work ended, and the cache left at it."""
    r = Room('secret', 'single', 'cuevas:secret:veta')
    r.shell()
    rng = r.rng
    for x in range(1, 20):
        for z in range(1, 20):
            for y in range(6, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    r.rough_walls(ore_chance=0.28, light_chance=0.0)
    ores = [r.block(name) for name in ('minecraft:iron_ore', 'minecraft:copper_ore',
                                       'minecraft:gold_ore', 'minecraft:deepslate_iron_ore')]
    # the seam itself, running across the floor toward the face
    for i, (x, z) in enumerate(((4, 4), (5, 5), (6, 5), (6, 6), (7, 7), (8, 7),
                                (9, 8), (10, 8), (11, 9), (12, 10), (13, 10), (14, 11))):
        if (x, z) in r.keep_clear:
            continue
        r.set(x, 0, z, ores[i % len(ores)])
        if rng.random() < 0.4:
            r.set(x, 1, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    # the cut face: worked, flat, and abandoned mid-swing
    for z in range(3, 9):
        for y in range(1, 5):
            r.set(16, y, z, r.block('minecraft:polished_andesite')
                  if rng.random() < 0.6 else r.block('minecraft:andesite'))
    for z in (4, 7):
        r.set(15, 1, z, r.block('minecraft:cobblestone'))
        r.set(15, 2, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    r.set(15, 4, 5, r.block('minecraft:lantern', hanging='false'))
    r.set(15, 3, 5, r.block('minecraft:cobblestone'))
    # the cache at the face
    r.set(13, 1, 6, r.block('minecraft:chiseled_stone_bricks'))
    r.mark('loot', 13, 2, 6)
    r.ore_seam(6)
    r.stalagmite(5, 15, 2)
    r.lichen(1, 3, 14, 'west')
    r.enforce_aprons()
    r.deco('decoracion:suelo', 7, 1, 14)
    r.deco('decoracion:pared', 19, 3, 8)
    return r


def build_secret_derrumbado():
    """The passage that came down. You climb the spill rather than walk in, and the cache is at the
    top of it — the only secret whose reward you have to scramble for."""
    r = Room('secret', 'single', 'cuevas:secret:derrumbado')
    r.shell()
    rng = r.rng
    for x in range(1, 20):
        for z in range(1, 20):
            for y in range(7, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    r.rough_walls(light_chance=0.0)
    rubble = [r.block(name) for name in ('minecraft:cobblestone', 'minecraft:andesite',
                                         'minecraft:tuff', 'minecraft:cobblestone')]
    # The spill, deepest at the northwest and thinning across the room. Capped at 4 so the peak
    # still has two blocks of headroom under a ceiling that starts at 7 — and capped again by how
    # far each tile is from a doorway, so the spill rises a block at a time instead of walling the
    # north and west doors off behind three blocks of rock. Same cap as anfiteatro, for the same
    # reason: the aprons are held clear at floor level, so anything built beside them is a cliff.
    spread = r.spread_from_doorways()
    for x in range(1, 20):
        for z in range(1, 20):
            if (x, z) in r.keep_clear or r.is_wall(x, z):
                continue
            d = x + z
            top = 4 if d <= 10 else (3 if d <= 15 else (2 if d <= 20 else (1 if d <= 25 else 0)))
            top = min(top, spread.get((x, z), 0))
            for y in range(1, top + 1):
                r.set(x, y, z, rubble[(x + z + y) % len(rubble)])
    # loose stone on top of the spill, and the cache half-buried at its crest
    for (x, z) in ((6, 3), (3, 7), (8, 5), (5, 9)):
        if (x, z) not in r.keep_clear:
            r.set(x, 5, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    r.set(4, 5, 4, r.block('minecraft:chiseled_stone_bricks'))
    # An alcove over the cache. Without it the lowered ceiling sits on the marker and the reward —
    # a floating item and its label — is inside the rock: visible only by clipping the camera in.
    r.clear(4, 6, 4, 4, 8, 4)
    r.mark('loot', 4, 6, 4)
    r.set(7, 6, 7, r.block('minecraft:lantern', hanging='true'))
    r.stalactite(14, 14, 2)
    r.stalactite(16, 8, 2)
    r.lichen(19, 3, 14, 'east')
    r.ore_seam(3)
    r.enforce_aprons()
    r.deco('decoracion:suelo', 15, 1, 16)
    r.deco('decoracion:pared', 1, 3, 17)
    return r


def build_secret_burbuja():
    """A void the rock closed around: smooth pale calcite, curved to the walls, with the cache dead
    centre. Deliberately austere — the other four secrets are cluttered, and one that is empty and
    clean reads as older than all of them.

    Calcite only, no amethyst: that belongs to the super secret, and a secret that borrowed it
    would blunt the room it was borrowed from."""
    r = Room('secret', 'single', 'cuevas:secret:burbuja')
    r.shell()
    rng = r.rng
    calcite = r.block('minecraft:calcite')
    basalt = r.block('minecraft:smooth_basalt')
    for x in range(1, 20):
        for z in range(1, 20):
            d = max(abs(x - 10), abs(z - 10))
            top = 8 if d <= 3 else (7 if d <= 5 else (6 if d <= 7 else 5))
            for y in range(top, 11):
                r.set(x, y, z, calcite if rng.random() < 0.8 else basalt)
            r.set(x, 0, z, calcite if rng.random() < 0.85 else basalt)
    for x in range(21):
        for z in range(21):
            if r.is_wall(x, z):
                for y in range(H):
                    r.set(x, y, z, calcite if rng.random() < 0.75 else basalt)
    # the cache, on the one thing standing in the room
    r.set(10, 1, 10, r.block('minecraft:polished_basalt', axis='y'))
    r.mark('loot', 10, 2, 10)
    # y=7: the dome starts at 8 over the middle, so this is what they can hang from
    for (x, z) in ((7, 7), (13, 13)):
        r.set(x, 7, z, r.block('minecraft:lantern', hanging='true'))
    r.lichen(1, 3, 10, 'west')
    r.lichen(19, 3, 10, 'east')
    r.enforce_aprons()
    r.deco('decoracion:suelo', 6, 1, 13)
    r.deco('decoracion:pared', 15, 3, 2)
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
    r.auto_spawns(wave_bar('challenge'), ranged=2)
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
    # The shrine is the purge stand: coins back for an affliction shed. It keeps the centre of the
    # room because undoing a bargain is the thing you walk in staring at.
    r.set(10, 1, 14, r.block('minecraft:chiseled_polished_blackstone'))
    r.mark('purga', 10, 2, 14)
    r.set(9, 1, 14, r.block('minecraft:crying_obsidian'))
    r.set(11, 1, 14, r.block('minecraft:crying_obsidian'))
    r.set(9, 2, 14, r.block('minecraft:redstone_torch'))
    r.set(11, 2, 14, r.block('minecraft:redstone_torch'))
    # Three offers in a forecourt facing the shrine, far enough apart that a click can only ever
    # match one of them (CurseMarket matches within a block).
    for ox in (6, 10, 14):
        r.set(ox, 1, 11, r.block('minecraft:polished_blackstone'))
        r.mark('oferta', ox, 2, 11)
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
    r.auto_spawns(wave_bar('normal_large'), ranged=2)
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
    r.auto_spawns(wave_bar('normal_l'), ranged=3)
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
    r.auto_spawns(wave_bar('normal_big'), ranged=4)
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


# ---------------------------------------------------------------------------- Cuevas Infestadas

# The palette swap that turns a cave into an infested one. Infestadas is the second kind of Isaac
# variant (DUNGEONS_PISOS.md §10): the same layouts, dressed — so every apron, marker and shelf the
# base room earned is inherited exactly, and only the surface changes.
INFEST_PALETTE = {
    'minecraft:andesite': 'minecraft:deepslate',
    'minecraft:stone': 'minecraft:deepslate',
    'minecraft:cobblestone': 'minecraft:cobbled_deepslate',
    'minecraft:tuff': 'minecraft:cobbled_deepslate',
    'minecraft:polished_andesite': 'minecraft:polished_deepslate',
    'minecraft:andesite_slab': 'minecraft:cobbled_deepslate_slab',
    'minecraft:cobblestone_slab': 'minecraft:cobbled_deepslate_slab',
    'minecraft:cobblestone_stairs': 'minecraft:cobbled_deepslate_stairs',
    'minecraft:mossy_cobblestone': 'minecraft:cobbled_deepslate',
    'minecraft:moss_block': 'minecraft:sculk',
    'minecraft:moss_carpet': 'minecraft:sculk_vein',
    'minecraft:calcite': 'minecraft:sculk',
    # The one light the piso keeps, and it is dimmer and rarer than Cuevas' shroomlight.
    'minecraft:shroomlight': 'minecraft:ochre_froglight',
    'minecraft:glow_lichen': 'minecraft:sculk_vein',
}

# Ceiling and corner cobweb: decorative, vanilla, and never on a walkable route (§10). It is
# unbreakable and that is harmless precisely because nothing ever has to cross it.
INFEST_WEB_CHANCE = 0.18


# --------------------------------------------------------------- more normals

def build_normal_pozo():
    """The basin room: the middle is water, so the fight happens on the ring around it."""
    r = Room('normal', 'single', 'cuevas:normal:pozo')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=8, dripstone=6)
    r.pool(7, 7, 13, 13)
    # A low rock in the middle: a landmark, deliberately too low to be a perch. This is the one
    # normal room with no high ground at all — its pressure is footing, not sightlines.
    for x in range(9, 12):
        for z in range(9, 12):
            r.set(x, 1, z, r.block('minecraft:mossy_cobblestone'))
    r.set(10, 2, 10, r.block('minecraft:shroomlight'))
    for (x, z) in ((3, 3), (17, 3), (3, 17), (17, 17)):
        r.stalagmite(x, z, rng.randint(2, 3))
    for (x, z) in ((5, 8), (16, 12), (8, 16)):
        r.set(x, 1, z, r.floor_blend(rng))
    r.ore_seam(4)
    r.lichen(1, 3, 7, 'west')
    r.lichen(19, 3, 13, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=0)
    r.deco('decoracion:techo', 4, 9, 16)
    r.deco('decoracion:techo', 16, 9, 4)
    r.deco('decoracion:suelo', 3, 1, 13)
    r.deco('decoracion:pared', 1, 3, 16)
    return r


def build_normal_columnas():
    """The column forest: sightlines break constantly, so ranged loses and ambush gains."""
    r = Room('normal', 'single', 'cuevas:normal:columnas')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=11, dripstone=7)
    for (x, z) in ((4, 4), (5, 4), (4, 5), (7, 7), (8, 7),
                   (13, 4), (14, 4), (14, 5), (16, 7), (17, 7),
                   (4, 13), (4, 14), (5, 14), (7, 14), (8, 14),
                   (13, 16), (14, 16), (14, 17), (16, 13), (17, 13),
                   (10, 10)):
        r.column(x, z)
    r.stalagmite(11, 6, 2)
    r.stalagmite(9, 15, 2)
    r.ore_seam(3)
    r.lichen(1, 4, 12, 'west')
    r.lichen(19, 4, 8, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=0)
    r.deco('decoracion:techo', 6, 9, 10)
    r.deco('decoracion:techo', 15, 9, 10)
    r.deco('decoracion:suelo', 12, 1, 8)
    r.deco('decoracion:pared', 19, 3, 4)
    return r


def build_normal_derrumbe():
    """The collapse: a raised quarter with a rubble slope, so the high ground is asymmetric
    and both sides can take it."""
    r = Room('normal', 'single', 'cuevas:normal:derrumbe')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=9, dripstone=5)
    r.shelf(12, 1, 19, 8, top=5)
    r.ramp(10, 7, 1, 0, top=5)
    # the spill: rock that came down with it, thinning away from the shelf
    for (x, z, n) in ((11, 10, 3), (9, 12, 2), (12, 12, 2), (7, 9, 1), (14, 13, 1)):
        for i in range(n):
            r.set(x + i, 1, z, r.block('minecraft:cobblestone'))
        r.set(x, 2, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    r.stalagmite(4, 4, 3)
    r.stalagmite(6, 16, 2)
    r.ore_seam(4)
    r.lichen(1, 3, 9, 'west')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:techo', 5, 9, 6)
    r.deco('decoracion:techo', 14, 9, 15)
    r.deco('decoracion:suelo', 4, 1, 12)
    r.deco('decoracion:pared', 1, 3, 15)
    return r


def build_normal_balcon():
    """The archer room: a real balcony wrapping one corner, and a long walk to contest it."""
    r = Room('normal', 'single', 'cuevas:normal:balcon')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.01)
    r.ceiling_relief(blobs=7, dripstone=4)
    r.shelf(1, 1, 7, 7, top=5, light=False)
    r.ramp(8, 6, -1, 0, top=5)
    # lit from under the lip, so the balcony reads as a shelf and not as a wall
    for z in (2, 4, 6):
        r.set(8, 4, z, r.block('minecraft:shroomlight'))
    for x in (2, 4, 6):
        r.set(x, 4, 8, r.block('minecraft:shroomlight'))
    r.stalagmite(15, 15, 3)
    r.stalagmite(12, 17, 2)
    r.stalactite(16, 5, 3)
    r.ore_seam(3)
    r.lichen(19, 3, 12, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=3)
    r.deco('decoracion:techo', 13, 9, 6)
    r.deco('decoracion:techo', 16, 9, 14)
    r.deco('decoracion:suelo', 17, 1, 13)
    r.deco('decoracion:pared', 19, 3, 17)
    return r


# ----------------------------------------------------------- more 2x1 chambers

def build_normal_large_columnata():
    """One long hall with a colonnade down it — no neck, which is what garganta always gives."""
    r = Room('normal_large', 'large', 'cuevas:normal_large:columnata')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=15, dripstone=9)
    for x in (4, 9, 14, 19, 24, 29, 34, 39):
        for z in (7, 13):
            r.column(x, z)
    r.shelf(1, 1, 6, 6, top=5, light=False)
    r.ramp(7, 3, -1, 0, top=5)
    r.shelf(35, 14, 40, 19, top=5, light=False)
    r.ramp(34, 16, 1, 0, top=5)
    for (x, z) in ((3, 3), (38, 17)):
        r.set(x, 5, z, r.block('minecraft:shroomlight'))
    r.ore_seam(6)
    r.lichen(1, 4, 15, 'west')
    r.lichen(40, 4, 5, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_large'), ranged=3)
    r.deco('decoracion:techo', 12, 9, 10)
    r.deco('decoracion:techo', 27, 9, 10)
    r.deco('decoracion:suelo', 20, 1, 4)
    r.deco('decoracion:pared', 1, 3, 5)
    return r


def build_normal_large_manantial():
    """A ledge that overhangs a basin, with a spring on top of it seeping through the rock: a wet
    half and a dry half, and a perch worth taking.

    Authored as a spring rather than the waterfall it started as, because a waterfall cannot pass
    the audit: every water block must be walled on all four sides, and a fall is by definition open
    on the side you can see it from. The rule is stricter than the game needs and deliberately so —
    nothing here can verify where loose water ends up, and a flooded room is discovered in play. So
    the water is contained and the *reading* is carried by the undercut lip and the dripstone
    hanging off it.
    """
    r = Room('normal_large', 'large', 'cuevas:normal_large:manantial')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.02)
    r.ceiling_relief(blobs=12, dripstone=7)
    r.shelf(22, 1, 29, 8, top=6, light=False)
    r.ramp(21, 4, 1, 0, top=6)
    # undercut the lip, so the ledge overhangs the basin and the seep has something to hang from
    for x in range(23, 29):
        r.clear(x, 1, 8, x, 5, 8)
    for x in (24, 26, 27):
        r.set(x, 5, 8, r.block('minecraft:pointed_dripstone',
                               vertical_direction='down', thickness='tip'))
    r.pool(23, 10, 28, 15)
    # The spring sits directly over the undercut lip, and clear of the ramp lane at z=4..5 — a
    # ramp cuts headroom above every step, so a basin there would lose its own rim.
    water = r.block('minecraft:water', level='0')
    for (x, z) in ((27, 6), (28, 6), (27, 7), (28, 7)):
        r.set(x, 6, z, water)
    for (x, z) in ((24, 3), (27, 6)):
        r.set(x, 6, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((5, 5), (9, 15), (15, 8)):
        r.column(x, z)
    r.stalagmite(6, 12, 3)
    r.stalagmite(16, 16, 2)
    r.ore_seam(5)
    r.lichen(1, 3, 12, 'west')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_large'), ranged=3)
    r.deco('decoracion:techo', 8, 9, 6)
    r.deco('decoracion:techo', 34, 9, 12)
    r.deco('decoracion:suelo', 12, 1, 4)
    r.deco('decoracion:pared', 40, 3, 15)
    return r


# ------------------------------------------------------------- another L, and 2x2s

def build_normal_l_mirador():
    """The outer corner raised instead of the inner: the vantage no longer covers both arms,
    so holding it is a choice rather than the default."""
    r = Room('normal_l', 'l', 'cuevas:normal_l:mirador')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=18, dripstone=11)
    r.shelf(33, 1, 40, 7, top=6, light=False)
    r.ramp(32, 6, 1, 0, top=6)
    for (x, z) in ((35, 3), (38, 6)):
        r.set(x, 6, z, r.block('minecraft:shroomlight'))
    # the elbow stays open, marked only by columns — the contrast with codo is the whole point
    for (x, z) in ((14, 14), (15, 14), (14, 15), (18, 18), (13, 19), (19, 13)):
        r.column(x, z)
    for (x, z) in ((6, 30), (7, 30), (6, 31), (14, 35)):
        r.column(x, z)
    r.stalagmite(4, 6, 3)
    r.stalagmite(8, 36, 3)
    r.stalagmite(25, 14, 2)
    r.ore_seam(7)
    r.lichen(1, 4, 6, 'west')
    r.lichen(19, 4, 34, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_l'), ranged=3)
    r.deco('decoracion:techo', 10, 9, 14)
    r.deco('decoracion:techo', 26, 9, 6)
    r.deco('decoracion:techo', 10, 9, 30)
    r.deco('decoracion:suelo', 4, 1, 25)
    r.deco('decoracion:pared', 1, 3, 33)
    return r


def build_normal_big_anfiteatro():
    """A stepped bowl: the party fights at the bottom and is looked down on — the inverse of
    terrazas. Every step is one block over two tiles, so the whole rim is walkable and needs
    no ramp cut into it."""
    r = Room('normal_big', 'big', 'cuevas:normal_big:anfiteatro')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=22, dripstone=12)
    # Capped by distance from the nearest doorway approach, so the ground climbs at most one block
    # per tile away from a door and every seat of the bowl can be reached. Without this the rim
    # simply walled the doors off: the aprons are held clear at floor level, and the terrace beside
    # them went straight to two or three blocks. Shipped that way, and all four doors opened onto a
    # wall.
    spread = r.spread_from_doorways()
    for x in range(1, 41):
        for z in range(1, 41):
            if (x, z) in r.keep_clear or r.is_wall(x, z) or not r.owned(x, z):
                continue
            d = min(x, z, 41 - x, 41 - z)
            if d > 9:
                continue
            top = min(5 - (d // 2), spread.get((x, z), 0))
            for y in range(1, top + 1):
                r.set(x, y, z, r.stone_blend(y, rng))
    for (x, z) in ((3, 20), (38, 20), (20, 3), (20, 38), (3, 3), (38, 38)):
        r.set(x, 5, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((20, 20), (21, 20), (20, 21), (21, 21)):
        r.set(x, 1, z, r.block('minecraft:polished_andesite'))
    r.ore_seam(8)
    r.lichen(1, 7, 20, 'west')
    r.lichen(40, 7, 21, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_big'), ranged=4)
    r.deco('decoracion:techo', 14, 9, 14)
    r.deco('decoracion:techo', 27, 9, 27)
    r.deco('decoracion:techo', 14, 9, 27)
    r.deco('decoracion:suelo', 24, 1, 17)
    r.deco('decoracion:pared', 1, 3, 25)
    return r


def build_normal_big_cuatro_pilares():
    """A big room that does not read as one open field: four column clusters quarter it, and the
    perches sit in the corners rather than in the middle."""
    r = Room('normal_big', 'big', 'cuevas:normal_big:cuatro_pilares')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=20, dripstone=12)
    for (cx, cz) in ((13, 13), (28, 13), (13, 28), (28, 28)):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                r.column(cx + dx, cz + dz)
    for (x0, z0, x1, z1, rx, rz, dx, dz) in (
            (1, 1, 7, 7, 8, 4, -1, 0),
            (34, 1, 40, 7, 33, 4, 1, 0),
            (1, 34, 7, 40, 8, 37, -1, 0),
            (34, 34, 40, 40, 33, 37, 1, 0)):
        r.shelf(x0, z0, x1, z1, top=5, light=False)
        r.ramp(rx, rz, dx, dz, top=5)
        r.set((x0 + x1) // 2, 5, (z0 + z1) // 2, r.block('minecraft:shroomlight'))
    r.stalagmite(20, 20, 3)
    r.stalagmite(21, 25, 2)
    r.stalagmite(25, 20, 2)
    r.ore_seam(8)
    r.lichen(1, 4, 20, 'west')
    r.lichen(40, 4, 21, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_big'), ranged=4)
    r.deco('decoracion:techo', 20, 9, 8)
    r.deco('decoracion:techo', 20, 9, 33)
    r.deco('decoracion:techo', 8, 9, 20)
    r.deco('decoracion:suelo', 18, 1, 18)
    r.deco('decoracion:pared', 40, 3, 25)
    return r


# ------------------------------------------------- the normal family, deepened for ParCool
#
# The room a player actually walks. Five variants was the least depth in the piso and the most
# visible, and all five were the same idea — flat floor plus a piece of wall-backed high ground,
# reached by a ramp.
#
# ParCool ships in the pack, which is what makes that one idea thin rather than merely repeated: a
# wall-backed shelf is a wall-run away, so the ramp is decoration and the perch is three seconds
# from the door. These eight are built for the moveset the party actually has:
#
#   - a perch meant to be CONTESTED is free-standing with an overhanging lip, which is what
#     defeats wall-run and cling-to-cliff; a wall-backed shelf is now a courtesy, not a cost;
#   - breakfall kills fall damage, so height is position and never a hazard;
#   - a 1-block obstacle is vaulted, so low walls are 2; a 1-block gap is a legitimate route,
#     because slide and crawl exist;
#   - cat-leap and dive extend a jump, so a gap gates only if it is wide or has no run-up;
#   - mobs do NOT parkour, so every reachable perch owes a ranged spawn or the room is a place to
#     stand and be safe in.

def build_normal_lago():
    """Water is the only movement denial the cave has, and nothing used it. The dry ground is the
    cross between the pools, so the fight is over footing rather than over height."""
    r = Room('normal', 'single', 'cuevas:normal:lago')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=8, dripstone=5)
    for (x0, z0, x1, z1) in ((2, 2, 7, 7), (13, 2, 18, 7), (2, 13, 7, 18)):
        r.pool(x0, z0, x1, z1)
    # Stepping stones: a dry line across the widest pool for anyone unwilling to wade.
    for (x, z) in ((3, 5), (5, 5), (7, 5)):
        r.set(x, 2, z, r.block('minecraft:mossy_cobblestone'))
    # The dry quarter, raised just enough to be worth holding, and lit so it reads from the door.
    r.shelf(13, 13, 18, 18, top=2, light=False)
    r.ramp(12, 15, 1, 0, top=2)
    r.set(16, 2, 16, r.block('minecraft:shroomlight'))
    r.chest('libre', 16, 12)
    r.stalagmite(10, 16, 3)
    r.stalactite(10, 4, 3)
    r.ore_seam(4)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:techo', 15, 9, 8)
    r.deco('decoracion:suelo', 9, 1, 12)
    return r


def build_normal_puente():
    """Four raised quarters and a trench cross between them, bridged twice. It splits a party —
    the trench and the bridges do not see each other — and the pillar at the crossing holds a
    proeza chest that only a leap off a bridge reaches."""
    r = Room('normal', 'single', 'cuevas:normal:puente')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    for (x0, z0, x1, z1) in ((1, 1, 7, 7), (13, 1, 19, 7), (1, 13, 7, 19), (13, 13, 19, 19)):
        r.shelf(x0, z0, x1, z1, top=4, light=False)
    # One ramp per quarter, so the trench is a choice and not a trap for anyone out of stamina.
    r.ramp(8, 4, -1, 0, top=4)
    r.ramp(12, 4, 1, 0, top=4)
    r.ramp(8, 16, -1, 0, top=4)
    r.ramp(12, 16, 1, 0, top=4)
    # The two bridges, clear of the doorway bands they cross beside.
    for bx in (7, 14):
        for z in range(8, 13):
            r.set(bx, 4, z, r.block('minecraft:cobblestone'))
            r.clear(bx, 5, z, bx, 7, z)
    for (x, z) in ((4, 4), (16, 4), (4, 16), (16, 16)):
        r.set(x, 4, z, r.block('minecraft:shroomlight'))
    # The pillar: floor to y=5, standing alone in the crossing. Nothing touches it, so wall-run
    # cannot climb it and cling has no lip to catch — the only way on is a leap from a bridge.
    for y in range(1, 6):
        r.set(10, y, 10, r.block('minecraft:cobbled_deepslate'))
    r.chest('proeza', 10, 10, y=6)
    r.mark('parkour', 8, 6, 10)
    r.ore_seam(4)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=3)
    r.deco('decoracion:techo', 10, 9, 4)
    r.deco('decoracion:pared', 1, 3, 15)
    return r


def build_normal_laberinto():
    """Low walls in a loose grid. Two blocks, not one — a one-block wall is vaulted and stops being
    a wall at all. The gaps at knee height are the payoff: slide and crawl are routes the maze
    rewards knowing, and the enemies chasing you have neither."""
    r = Room('normal', 'single', 'cuevas:normal:laberinto')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=6, dripstone=4)
    wall = r.block('minecraft:cobblestone')
    for gx in (4, 8, 12, 16):
        for z in range(2, 19):
            if (gx, z) in r.keep_clear or rng.random() < 0.25:
                continue
            # A quarter of the standing walls are undercut: solid at head height, open at the floor.
            if rng.random() < 0.25:
                r.set(gx, 2, z, wall)
            else:
                r.set(gx, 1, z, wall)
                r.set(gx, 2, z, wall)
    for gz in (6, 14):
        for x in range(2, 19):
            if (x, gz) in r.keep_clear or rng.random() < 0.35:
                continue
            r.set(x, 1, gz, wall)
            r.set(x, 2, gz, wall)
    for (x, z) in ((6, 4), (14, 4), (6, 16), (14, 16), (10, 10)):
        if (x, z) not in r.keep_clear:
            r.set(x, 3, z, r.block('minecraft:shroomlight'))
    r.chest('trampa', 18, 18)
    r.ore_seam(4)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:techo', 10, 9, 10)
    r.deco('decoracion:suelo', 2, 1, 15)
    return r


def build_normal_crater():
    """The high ground belongs to the enemies. Every other room hands the party a shelf; this one
    puts a lit rim around a sunken floor and the shooters on it, so the fight starts with the party
    looked down at. The rim's lip overhangs, which is what makes climbing out a decision."""
    r = Room('normal', 'single', 'cuevas:normal:crater')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.shelf(1, 1, 19, 3, top=3, light=False)
    r.shelf(1, 17, 19, 19, top=3, light=False)
    r.shelf(1, 4, 3, 16, top=3, light=False)
    r.shelf(17, 4, 19, 16, top=3, light=False)
    # The lip: one course out over the bowl at head height, so the rim cannot be clung to.
    for (x, z) in [(4, z) for z in range(4, 17)] + [(16, z) for z in range(4, 17)] \
            + [(x, 4) for x in range(5, 16)] + [(x, 16) for x in range(5, 16)]:
        if (x, z) not in r.keep_clear and not r.near_clear(x, z):
            r.set(x, 4, z, r.block('minecraft:cobbled_deepslate'))
    r.ramp(4, 6, -1, 0, top=3)
    r.ramp(16, 14, 1, 0, top=3)
    for (x, z) in ((2, 6), (18, 14), (6, 2), (14, 18)):
        r.set(x, 3, z, r.block('minecraft:shroomlight'))
    r.chest('puas', 10, 10)
    r.stalactite(8, 8, 3)
    r.stalactite(12, 12, 3)
    r.ore_seam(3)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=4)
    r.deco('decoracion:techo', 6, 9, 12)
    return r


def build_normal_hongos():
    """A grove. The caps are cover at head height and platforms above it, and they are the one
    variant justified by how it looks rather than how it fights — ten shades of grey stone is its
    own kind of repetition."""
    r = Room('normal', 'single', 'cuevas:normal:hongos')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=5, dripstone=3)
    cap = r.block('minecraft:brown_mushroom_block', up='true', north='true', south='true',
                  east='true', west='true')
    stem = r.block('minecraft:mushroom_stem')
    for (sx, sz, h) in ((5, 5, 4), (15, 6, 5), (6, 15, 5), (16, 16, 4), (10, 3, 3), (3, 12, 3)):
        if (sx, sz) in r.keep_clear:
            continue
        for y in range(1, h):
            r.set(sx, y, sz, stem)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if r.owned(sx + dx, sz + dz) and not r.is_wall(sx + dx, sz + dz):
                    r.set(sx + dx, h, sz + dz, cap)
        r.set(sx, h - 1, sz, r.block('minecraft:shroomlight'))
    for (x, z) in ((8, 8), (12, 12), (7, 17)):
        if (x, z) not in r.keep_clear:
            r.set(x, 1, z, r.block('minecraft:moss_block'))
            r.set(x, 2, z, r.block('minecraft:red_mushroom'))
    r.chest('libre', 15, 6, y=6)
    r.mark('parkour', 13, 5, 6)
    r.pool(2, 17, 4, 18)
    r.ore_seam(3)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:suelo', 11, 1, 8)
    r.deco('decoracion:techo', 10, 9, 16)
    return r


def build_normal_l_recodo():
    """The elbow, cut in two by a walled cistern at its inner corner, so each arm is fought on its
    own instead of as one long room seen from either end. The island in the water is the only place
    a sealed chest could stand where nobody stumbles on it by walking past."""
    r = Room('normal_l', 'l', 'cuevas:normal_l:recodo')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=14, dripstone=8)
    r.pool(13, 13, 18, 18)
    for (x, z) in ((12, 12), (19, 12), (12, 19), (19, 19)):
        for y in range(1, 5):
            r.set(x, y, z, r.block('minecraft:mossy_cobblestone'))
    r.set(15, 1, 15, r.block('minecraft:cobblestone'))
    r.set(16, 1, 15, r.block('minecraft:cobblestone'))
    r.chest('sellado', 15, 15, y=2)
    # A free-standing perch in each arm: no wall behind it, and a lip over its edge.
    for (px, pz) in ((6, 30), (30, 6)):
        for x in range(px - 1, px + 2):
            for z in range(pz - 1, pz + 2):
                for y in range(1, 5):
                    r.set(x, y, z, r.stone_blend(y, rng))
        for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            r.set(px + dx, 4, pz + dz, r.block('minecraft:cobbled_deepslate'))
        r.set(px, 5, pz, r.block('minecraft:shroomlight'))
    r.stalagmite(9, 9, 3)
    r.stalactite(30, 30, 3)
    r.ore_seam(6)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_l'), ranged=4)
    r.deco('decoracion:techo', 8, 9, 30)
    r.deco('decoracion:techo', 30, 9, 8)
    r.deco('decoracion:pared', 1, 3, 26)
    return r


def build_normal_l_andamios():
    """Somebody worked this corner and left the scaffolding. Two levels of planking, ground access
    by ramp, the top deck by parkour, and a chain strung between the towers. The climbers are what
    make it a fight rather than a playground — they are the only thing on the floor that follows."""
    r = Room('normal_l', 'l', 'cuevas:normal_l:andamios')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    plank = r.block('minecraft:oak_planks')
    fence = r.block('minecraft:oak_fence')
    for (x0, z0, x1, z1, level) in ((3, 24, 12, 33, 3), (24, 3, 33, 12, 3),
                                    (5, 26, 10, 31, 7), (26, 5, 31, 10, 7)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in r.keep_clear or not r.owned(x, z) or r.is_wall(x, z):
                    continue
                r.set(x, level, z, plank)
                r.clear(x, level + 1, z, x, min(H - 2, level + 3), z)
        for (px, pz) in ((x0, z0), (x1, z1), (x0, z1), (x1, z0)):
            for y in range(1, level):
                r.set(px, y, pz, fence)
    r.ramp(13, 28, -1, 0, top=3)
    r.ramp(28, 13, 0, -1, top=3)
    # The zipline, and the deck it serves. Both are parkour routes and neither is the only way in.
    for x in range(12, 26):
        r.set(x, 8, 8, r.block('minecraft:chain', axis='x'))
    r.mark('parkour', 12, 8, 9)
    r.set(8, 4, 28, r.block('minecraft:lantern', hanging='false'))
    r.set(28, 4, 8, r.block('minecraft:lantern', hanging='false'))
    r.set(8, 8, 28, r.block('minecraft:shroomlight'))
    r.chest('proeza', 28, 8, y=8)
    r.ore_seam(6)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_l'), ranged=5)
    r.deco('decoracion:techo', 16, 9, 16)
    r.deco('decoracion:suelo', 18, 1, 26)
    return r


def build_normal_large_desfiladero():
    """A gorge down the long axis with a shelf on each side. Every other large room is two chambers
    or a hall; this one is a corridor, fought the length of it, and the two shelves face each other
    across a gap wide enough that crossing is a wall-run and not a step."""
    r = Room('normal_large', 'large', 'cuevas:normal_large:desfiladero')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=14, dripstone=8)
    r.shelf(1, 1, 40, 6, top=4, light=False)
    r.shelf(1, 14, 40, 19, top=4, light=False)
    # Notches through the shelves at every north and south doorway. The aprons themselves are held
    # flat, but the tile just past them is not, so a shelf built wall-to-wall walls its own doors in
    # — the failure anfiteatro shipped with, and the reason the walk check exists (§39).
    for bx in (9, 10, 11, 30, 31, 32):
        for z in (6, 14):
            r.clear(bx, 1, z, bx, 7, z)
    r.ramp(4, 7, 0, -1, top=4)
    r.ramp(36, 13, 0, 1, top=4)
    for x in (6, 14, 26, 34):
        r.set(x, 4, 6, r.block('minecraft:shroomlight'))
        r.set(x, 4, 14, r.block('minecraft:shroomlight'))
    # A ledge halfway up the north face, out of walking reach and under the shelf's own lip.
    for x in range(18, 23):
        r.set(x, 7, 5, r.block('minecraft:cobbled_deepslate'))
        r.clear(x, 8, 5, x, 10, 5)
    r.chest('proeza', 20, 5, y=8)
    r.mark('parkour', 20, 8, 7)
    r.stalagmite(10, 10, 3)
    r.stalagmite(31, 11, 3)
    r.pool(24, 9, 28, 11)
    r.ore_seam(6)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal_large'), ranged=4)
    r.deco('decoracion:techo', 20, 9, 10)
    r.deco('decoracion:pared', 1, 3, 15)
    r.deco('decoracion:suelo', 34, 1, 10)
    return r


# ------------------------------------------------- the once-per-floor rooms, deepened
#
# Two sets live here. The first six are the §66 rooms — start, treasure and shop variants — which
# shipped as .nbt with no builder behind them; a template with no source cannot be regenerated,
# audited on change, or dressed by infest(), so they are authored here and the old files are
# overwritten. `--check` refuses the reverse case from now on.
#
# The rest are second variants for the keys that were singletons. They are approached rooms, not
# recognized ones (§66): the boss arena and la sala del sello stay at one variant forever, and every
# other room that a run meets once a floor is better for not being the same room every floor.
# VariantDraw deals these from a run-long bag, so two variants is already enough for a six-floor
# descent to never repeat one.

def build_start_falla():
    """The other side of the fault is a step up, not a cliff: the whole west half of the chamber
    sits one block higher, split from the east by a seam of deepslate. One block is deliberate —
    the party arrives here, and an arrival room that needs a ramp to leave is a room that can strand
    somebody who logs in on the wrong side of it."""
    r = Room('start', 'single', 'cuevas:start:falla')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=7, dripstone=4)
    r.shelf(1, 1, 7, 19, top=1, light=False)
    seam = r.block('minecraft:cobbled_deepslate')
    for z in range(1, 20):
        if (8, z) in r.keep_clear:
            continue
        r.set(8, 1, z, seam)
        if rng.random() < 0.4:
            r.set(9, 1, z, r.block('minecraft:deepslate'))
    # Light rides the fault line, so the seam is the first thing read on arrival.
    for z in (4, 8, 12, 16):
        if (8, z) not in r.keep_clear:
            r.set(8, 2, z, r.block('minecraft:shroomlight'))
    for x in range(12, 15):
        for z in range(12, 15):
            r.set(x, 1, z, r.block('minecraft:polished_andesite'))
    r.set(13, 1, 13, r.block('minecraft:calcite'))
    r.stalagmite(16, 4, 3)
    r.stalagmite(4, 16, 2)
    for (x, z) in ((3, 3), (17, 17), (16, 3)):
        r.stalactite(x, z, 3)
    r.ore_seam(4)
    r.lichen(19, 3, 14, 'east')
    r.lichen(14, 3, 19, 'south')
    r.enforce_aprons()
    r.mark('inicio', 13, 2, 13)
    r.deco('decoracion:techo', 5, 9, 5)
    r.deco('decoracion:pared', 19, 3, 6)
    return r


def build_start_campamento():
    """Somebody camped here and did not come back. The dais is their fire pit's flagstones, which
    is the only reason a start room may have a feature at its landing point: the party lands on the
    flat the campers levelled, not on the fire."""
    r = Room('start', 'single', 'cuevas:start:campamento')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=6, dripstone=5)
    for x in range(7, 14):
        for z in range(7, 14):
            if (x, z) not in r.keep_clear:
                # Coarse dirt, not gravel: the floor layer has the void under it, so a gravity
                # block there falls forever and takes the party's arrival tile with it.
                r.set(x, 0, z, r.block('minecraft:coarse_dirt') if rng.random() < 0.6
                      else r.block('minecraft:cobblestone'))
    r.set(10, 1, 12, r.block('minecraft:campfire', lit='true', facing='north'))
    for (x, z) in ((9, 12), (11, 12), (9, 13), (11, 13), (10, 13)):
        r.set(x, 1, z, r.block('minecraft:cobblestone_slab', type='bottom'))
    # Bedrolls and stores in the corner the fire's light reaches, left where they were dropped.
    for (x, z) in ((14, 15), (16, 15)):
        r.set(x, 1, z, r.block('minecraft:white_wool'))
        r.set(x, 1, z + 1, r.block('minecraft:white_wool'))
        r.set(x, 2, z, r.block('minecraft:oak_slab', type='bottom'))
    r.set(15, 1, 17, r.block('minecraft:barrel', facing='up'))
    r.set(17, 1, 16, r.block('minecraft:barrel', facing='up'))
    r.set(16, 1, 17, r.block('minecraft:lantern', hanging='false'))
    for y in range(1, 4):
        r.set(5, y, 5, r.block('minecraft:spruce_fence'))
    r.set(5, 4, 5, r.block('minecraft:lantern', hanging='false'))
    r.set(4, 1, 5, r.block('minecraft:barrel', facing='up'))
    r.stalactite(4, 15, 3)
    r.stalactite(16, 5, 2)
    r.ore_seam(3)
    r.lichen(1, 3, 8, 'west')
    r.enforce_aprons()
    r.mark('inicio', 10, 1, 9)
    r.deco('decoracion:techo', 6, 9, 14)
    r.deco('decoracion:suelo', 3, 1, 12)
    return r


def build_treasure_veta():
    """The three stands are cut into a mining face, which is what makes the choice legible before
    you are close enough to read the labels: one is set in ore, one over water, one on the crates
    the miners left. Three `loot` markers, qualified — see TreasureChoice."""
    r = Room('treasure', 'single', 'cuevas:treasure:veta')
    r.shell()
    rng = r.rng
    r.rough_walls(ore_chance=0.18, light_chance=0.0)
    r.ceiling_relief(blobs=5, dripstone=3)
    # the worked face along the north quadrants, squared off and lit
    for x in list(range(2, 8)) + list(range(13, 19)):
        for z in range(2, 6):
            r.set(x, 0, z, r.block('minecraft:stone_bricks') if (x + z) % 3
                  else r.block('minecraft:polished_andesite'))
    for (x, z, ore) in ((3, 3, 'minecraft:gold_ore'), (6, 4, 'minecraft:iron_ore'),
                        (15, 3, 'minecraft:copper_ore'), (17, 4, 'minecraft:gold_ore')):
        for y in range(1, 4):
            r.set(x, y, z, r.block(ore if y == 2 else 'minecraft:stone'))
    for (px, pz, tag, cap) in ((4, 4, 'loot:arma', 'minecraft:chiseled_stone_bricks'),
                               (10, 6, 'loot:vitalidad', 'minecraft:calcite'),
                               (16, 4, 'loot:provision', 'minecraft:chiseled_stone_bricks')):
        r.set(px, 1, pz, r.block(cap))
        r.mark(tag, px, 2, pz)
    # Lit from beside the stands, never over them: each reward floats in its own column.
    for (px, pz) in ((6, 3), (13, 3), (8, 6)):
        r.set(px, 5, pz, r.block('minecraft:chain'))
        r.set(px, 4, pz, r.block('minecraft:lantern', hanging='true'))
    r.pool(14, 14, 16, 16)
    r.set(6, 1, 15, r.block('minecraft:barrel', facing='up'))
    r.set(5, 1, 16, r.block('minecraft:barrel', facing='up'))
    r.stalagmite(8, 16, 3)
    r.stalactite(12, 15, 3)
    r.ore_seam(5)
    r.enforce_aprons()
    r.deco('decoracion:techo', 4, 9, 12)
    r.deco('decoracion:suelo', 17, 1, 12)
    return r


def build_treasure_ofrenda():
    """Three bowls on a stepped platform, which somebody built to be knelt in front of. The stands
    ring the centre rather than lining a wall: the party walks in and is surrounded by the choice
    instead of queueing at it."""
    r = Room('treasure', 'single', 'cuevas:treasure:ofrenda')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    calcite = r.block('minecraft:calcite')
    for x in range(6, 15):
        for z in range(6, 15):
            if (x, z) in r.keep_clear:
                continue
            d = max(abs(x - 10), abs(z - 10))
            r.set(x, 0, z, calcite if d <= 2 else r.block('minecraft:smooth_basalt'))
    for (px, pz, tag) in ((7, 10, 'loot:arma'), (10, 7, 'loot:vitalidad'),
                          (13, 10, 'loot:provision')):
        r.set(px, 1, pz, r.block('minecraft:polished_basalt', axis='y'))
        r.mark(tag, px, 2, pz)
    for (x, z) in ((7, 7), (13, 7), (7, 13), (13, 13)):
        r.set(x, 1, z, r.block('minecraft:polished_basalt', axis='y'))
        r.set(x, 2, z, r.block('minecraft:candle', candles='3', lit='true'))
    for (x, z) in ((9, 10), (11, 10), (10, 9), (10, 11), (10, 10)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    r.set(10, 1, 13, calcite)
    r.set(10, 2, 13, r.block('minecraft:end_rod', facing='up'))
    for (x, z) in ((4, 4), (16, 16), (4, 16)):
        r.stalactite(x, z, 3)
    r.ore_seam(3)
    r.lichen(1, 3, 12, 'west')
    r.enforce_aprons()
    r.deco('decoracion:techo', 15, 9, 5)
    r.deco('decoracion:pared', 1, 3, 15)
    return r


def build_shop_caravana():
    """A trader who travels: the counter is a row of crates under an awning, pitched against the
    north wall. North matters — DungeonMaterializer.shopFacing turns the template so the authored
    counter lands on a doorless wall, and it reads the north face to do it."""
    r = Room('shop', 'single', 'cuevas:shop:caravana')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=5, dripstone=3)
    for x in range(2, 19):
        for z in range(1, 7):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, r.block('minecraft:packed_mud') if (x + z) % 4
                      else r.block('minecraft:mud_bricks'))
    for i, px in enumerate(SHOP_SLOT_X, start=1):
        r.set(px, 1, 3, r.block('minecraft:barrel', facing='up'))
        r.mark(f'shopslot:{i}', px, 2, 3)
    # The awning: wool stretched between two poles, so the counter reads as pitched, not built.
    for (px, pz) in ((2, 2), (7, 2), (13, 2), (18, 2)):
        for y in range(1, 6):
            r.set(px, y, pz, r.block('minecraft:spruce_fence'))
        r.set(px, 6, pz, r.block('minecraft:spruce_log', axis='x'))
    for x in range(2, 19):
        if (x, 2) not in r.keep_clear:
            r.set(x, 7, 2, r.block('minecraft:brown_wool'))
        if (x, 2) not in r.keep_clear and x % 4 == 0:
            r.set(x, 6, 2, r.block('minecraft:lantern', hanging='true'))
    for (x, z) in ((3, 16), (4, 17), (17, 16)):
        r.set(x, 1, z, r.block('minecraft:hay_block', axis='y'))
    r.set(16, 1, 17, r.block('minecraft:cauldron'))
    r.stalagmite(9, 16, 3)
    r.ore_seam(3)
    r.enforce_aprons()
    r.deco('decoracion:techo', 10, 9, 14)
    r.deco('decoracion:suelo', 6, 1, 13)
    return r


def build_shop_socavon():
    """The shop as an adit driven into the north rock: six alcoves cut in a timbered face, a rail
    running past them. Same counter contract as the others — the wares sit on the north wall."""
    r = Room('shop', 'single', 'cuevas:shop:socavon')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    deepslate = r.block('minecraft:cobbled_deepslate')
    for x in range(1, 20):
        for z in range(1, 7):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, deepslate if (x * z) % 5 else r.block('minecraft:polished_deepslate'))
    for i, px in enumerate(SHOP_SLOT_X, start=1):
        r.set(px, 1, 4, r.block('minecraft:deepslate_bricks'))
        r.mark(f'shopslot:{i}', px, 2, 4)
    # Light BETWEEN the alcoves, never over one: the ware floats two blocks above its slot, so a
    # lantern in that column is a lantern standing in front of the thing for sale.
    for px in (4, 6, 14, 16):
        r.set(px, 5, 4, r.block('minecraft:chain'))
        r.set(px, 4, 4, r.block('minecraft:lantern', hanging='true'))
    # The timbering: uprights between the alcoves, a cap beam over the whole face.
    for px in (2, 6, 8, 12, 14, 18):
        for y in range(1, 6):
            r.set(px, y, 5, r.block('minecraft:spruce_log', axis='y'))
    for x in range(2, 19):
        if (x, 5) not in r.keep_clear:
            r.set(x, 6, 5, r.block('minecraft:spruce_log', axis='x'))
    for z in range(8, 18):
        if (5, z) not in r.keep_clear:
            r.set(5, 1, z, r.block('minecraft:rail', shape='north_south'))
    r.set(5, 1, 18, r.block('minecraft:rail', shape='north_south'))
    r.set(15, 1, 15, r.block('minecraft:barrel', facing='up'))
    r.set(15, 1, 16, r.block('minecraft:barrel', facing='up'))
    r.stalactite(16, 12, 3)
    r.ore_seam(4)
    r.enforce_aprons()
    r.deco('decoracion:techo', 12, 9, 12)
    r.deco('decoracion:pared', 19, 3, 14)
    return r


def build_mini_boss_estanque():
    """The elite stands in water. Wading is the whole idea: the dry rim is where the party wants to
    fight from and the pool is where the mini-boss is, so the room asks whether to go in after it or
    hold the edge and lose the damage. Its `boss` marker sits on the island, not in the water —
    a spawn in a pool is a spawn that may drift."""
    r = Room('mini_boss', 'single', 'cuevas:mini_boss:estanque')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=6, dripstone=4)
    r.pool(3, 3, 7, 7)
    r.pool(13, 13, 17, 17)
    for x in range(8, 13):
        for z in range(8, 13):
            if (x, z) not in r.keep_clear:
                r.set(x, 1, z, r.block('minecraft:mossy_cobblestone'))
    r.set(10, 2, 10, r.block('minecraft:shroomlight'))
    for (x, z) in ((6, 14), (14, 6)):
        r.stalagmite(x, z, 3)
    r.set(4, 6, 4, r.block('minecraft:shroomlight'))
    r.set(16, 6, 16, r.block('minecraft:shroomlight'))
    r.ore_seam(3)
    r.enforce_aprons()
    r.mark('boss', 10, 3, 10)
    r.deco('decoracion:techo', 10, 9, 4)
    r.deco('decoracion:suelo', 15, 1, 6)
    return r


def build_mini_boss_grieta():
    """A crack in the floor of the cave, walled on two sides, with the elite between the party and
    the far door. No perch and no cover: this is the flat one, and it is here so the mini-boss is
    not always a fight around a column."""
    r = Room('mini_boss', 'single', 'cuevas:mini_boss:grieta')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.02)
    r.ceiling_relief(blobs=9, dripstone=6)
    for (x0, z0, x1, z1) in ((2, 2, 6, 6), (14, 14, 18, 18)):
        r.shelf(x0, z0, x1, z1, top=2, light=False)
    r.ramp(7, 4, -1, 0, top=2)
    r.ramp(13, 16, 1, 0, top=2)
    for (x, z) in ((4, 4), (16, 16)):
        r.set(x, 2, z, r.block('minecraft:shroomlight'))
    for (x, z) in ((8, 14), (14, 8), (12, 15)):
        r.stalagmite(x, z, 2)
    r.set(10, 10, 10, r.block('minecraft:shroomlight'))
    r.ore_seam(4)
    r.enforce_aprons()
    r.mark('boss', 10, 1, 10)
    r.deco('decoracion:techo', 6, 9, 15)
    r.deco('decoracion:pared', 1, 3, 12)
    return r


def build_challenge_cisterna():
    """The plate stands on an island in a flooded cistern. The waves wade; the party does too if it
    steps off. Its spawn markers are on the dry banks, because a wave that stands up in water is a
    wave that arrives late and in single file."""
    r = Room('challenge', 'single', 'cuevas:challenge:cisterna')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    for (x0, z0, x1, z1) in ((3, 3, 6, 6), (14, 3, 17, 6), (3, 14, 6, 17), (14, 14, 17, 17)):
        r.pool(x0, z0, x1, z1)
    for x in range(8, 13):
        for z in range(8, 13):
            if (x, z) in r.keep_clear:
                continue
            r.set(x, 1, z, r.block('minecraft:stone_bricks') if (x + z) % 2
                  else r.block('minecraft:mossy_stone_bricks'))
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12)):
        r.set(x, 2, z, r.block('minecraft:stone_brick_wall'))
    for (x, z) in ((6, 10), (14, 10), (10, 6), (10, 14)):
        r.set(x, 11, z, r.block('minecraft:shroomlight'))
    r.ore_seam(3)
    r.enforce_aprons()
    r.mark('challenge', 10, 2, 10)
    r.auto_spawns(wave_bar('challenge'), ranged=2)
    r.deco('decoracion:techo', 10, 9, 10)
    return r


def build_curse_osario():
    """The same market, kept by the dead. Its stands are laid out exactly as the shrine's — three
    offers in a forecourt, the purga behind them — because the curse room is a shop and moving the
    counter around between variants is how a player learns to distrust the layout."""
    r = Room('curse', 'single', 'cuevas:curse:osario')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    bone = r.block('minecraft:bone_block', axis='y')
    for x in range(1, 20):
        for z in range(1, 20):
            d = abs(x - 10) + abs(z - 14)
            if d < 9 and rng.random() < (9 - d) / 16:
                r.set(x, 0, z, r.block('minecraft:deepslate_tiles') if rng.random() < 0.6
                      else r.block('minecraft:cracked_deepslate_tiles'))
    for (px, pz) in ((6, 15), (14, 15)):
        for y in range(1, 5):
            r.set(px, y, pz, bone)
        r.set(px, 5, pz, r.block('minecraft:soul_lantern', hanging='false'))
    r.set(10, 1, 14, r.block('minecraft:chiseled_deepslate'))
    r.mark('purga', 10, 2, 14)
    r.set(9, 1, 14, r.block('minecraft:soul_sand'))
    r.set(11, 1, 14, r.block('minecraft:soul_sand'))
    r.set(9, 2, 14, r.block('minecraft:soul_fire'))
    r.set(11, 2, 14, r.block('minecraft:soul_fire'))
    for ox in (6, 10, 14):
        r.set(ox, 1, 11, r.block('minecraft:polished_deepslate'))
        r.mark('oferta', ox, 2, 11)
    for (x, z) in ((4, 5), (16, 6), (5, 17)):
        r.set(x, 1, z, bone)
    r.stalactite(6, 8, 3)
    r.stalactite(15, 9, 2)
    r.enforce_aprons()
    r.deco('decoracion:techo', 15, 9, 15)
    return r


def build_sacrifice_pozo():
    """The altar over a shaft instead of on a platform: the spikes ring a well the light comes out
    of. Same stand, same contract — what changes is that you stand at the edge of something to use
    it, which is the fiction the room already had and the altar version did not show."""
    r = Room('sacrifice', 'single', 'cuevas:sacrifice:pozo')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    basalt = r.block('minecraft:smooth_basalt')
    for x in range(5, 16):
        for z in range(5, 16):
            if (x, z) in r.keep_clear:
                continue
            d = max(abs(x - 10), abs(z - 10))
            if d <= 2:
                r.set(x, 0, z, r.block('minecraft:magma_block'))
            elif d <= 5:
                r.set(x, 1, z, basalt if rng.random() < 0.75
                      else r.block('minecraft:polished_basalt', axis='y'))
    # The rim: a wall course around the shaft, open where the stand is approached.
    for (x, z) in ((7, 8), (7, 12), (13, 8), (13, 12), (8, 7), (12, 7), (8, 13), (12, 13)):
        r.set(x, 2, z, r.block('minecraft:polished_blackstone_wall'))
    r.set(10, 1, 13, r.block('minecraft:chiseled_polished_blackstone'))
    r.mark('sacrifice', 10, 2, 13)
    r.stalactite(5, 5, 2)
    r.stalactite(15, 15, 3)
    r.ore_seam(3)
    r.enforce_aprons()
    r.deco('decoracion:techo', 5, 9, 15)
    return r


def build_arcade_tragaperras():
    """The machine wedged into a niche somebody widened for it, rather than standing in the open.
    The plinth is the same height and the marker the same block above it — ArcadeMachine matches on
    proximity, so the only thing a variant may move is the scenery around it."""
    r = Room('arcade', 'single', 'cuevas:arcade:tragaperras')
    r.shell()
    rng = r.rng
    r.rough_walls()
    r.ceiling_relief(blobs=6, dripstone=5)
    for x in range(13, 19):
        for z in range(13, 19):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, r.block('minecraft:polished_deepslate'))
    for y in range(1, 5):
        r.set(13, y, 16, r.block('minecraft:deepslate_bricks'))
        r.set(16, y, 13, r.block('minecraft:deepslate_bricks'))
    r.set(16, 1, 16, r.block('minecraft:quartz_block'))
    r.mark('arcade', 16, 2, 16)
    for (x, z) in ((15, 15), (17, 17)):
        r.set(x, 1, z, r.block('minecraft:quartz_slab', type='bottom'))
    r.set(16, 5, 16, r.block('minecraft:chain'))
    r.set(16, 4, 16, r.block('minecraft:soul_lantern', hanging='true'))
    r.pool(4, 4, 6, 6)
    r.stalagmite(8, 15, 3)
    r.ore_seam(4)
    r.enforce_aprons()
    r.deco('decoracion:suelo', 6, 1, 14)
    r.deco('decoracion:techo', 8, 9, 8)
    return r


def build_super_secret_ahogada():
    """The other wrongness: not a geode but a flooded vault the cave grew around. It reads as an
    older building than anything else in the dungeon, which is the only job a super secret has —
    look like somewhere you were not meant to reach."""
    r = Room('super_secret', 'single', 'cuevas:super_secret:ahogada')
    r.shell()
    rng = r.rng
    bricks = r.block('minecraft:stone_bricks')
    mossy = r.block('minecraft:mossy_stone_bricks')
    cracked = r.block('minecraft:cracked_stone_bricks')
    for x in range(1, 20):
        for z in range(1, 20):
            roll = rng.random()
            r.set(x, 0, z, mossy if roll < 0.4 else (cracked if roll < 0.6 else bricks))
    for x in range(21):
        for z in range(21):
            if r.is_wall(x, z):
                for y in range(1, H - 1):
                    r.set(x, y, z, mossy if rng.random() < 0.5 else bricks)
    for (px, pz) in ((5, 5), (15, 5), (5, 15), (15, 15)):
        for y in range(1, H - 1):
            r.set(px, y, pz, r.block('minecraft:stone_brick_wall') if y > 4 else bricks)
    r.pool(8, 8, 12, 12)
    for (x, z) in ((7, 4), (13, 16)):
        r.set(x, 1, z, r.block('minecraft:chiseled_stone_bricks'))
        r.set(x, 2, z, r.block('minecraft:candle', candles='2', lit='true'))
    r.set(4, 8, 10, r.block('minecraft:shroomlight'))
    r.set(16, 8, 10, r.block('minecraft:shroomlight'))
    r.set(5, 1, 16, r.block('minecraft:chiseled_stone_bricks'))
    r.mark('loot', 5, 2, 16)
    r.lichen(2, 3, 10, 'west')
    r.lichen(18, 3, 10, 'east')
    r.enforce_aprons()
    r.deco('decoracion:techo', 10, 9, 6)
    return r


# ------------------------------------------------- rooms only Cuevas Infestadas has
#
# These are the answer to "is Infestadas a place or a filter". Every other infested room is
# derived from a Cuevas one by infest(), which means geometry is shared and only the palette
# differs — the themes idea the pisos redesign deleted, arrived at from the other direction.
# A room that exists in one piso and not the other cannot be that, and the folder layout makes
# it cost nothing: a file in cuevas_infestadas/normal/ needs no registration anywhere.

def build_infestadas_nidal():
    """The nest is the terrain. A web-choked mound holds the room's height and its nido markers,
    so taking the high ground and clearing the nests are the same job."""
    r = Room('normal', 'single', 'infestadas:normal:nidal')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.01)
    r.ceiling_relief(blobs=8, dripstone=4)
    r.shelf(7, 7, 13, 13, top=5, light=False)
    r.ramp(6, 10, 1, 0, top=5)
    r.ramp(14, 10, -1, 0, top=5)
    for (x, z) in ((8, 8), (12, 12)):
        r.set(x, 5, z, r.block('minecraft:shroomlight'))
    for (x, y, z) in ((9, 6, 9), (12, 6, 8), (8, 6, 12), (11, 6, 12)):
        r.mark('nido', x, y, z)
    for (x, y, z) in ((4, 1, 4), (17, 1, 5), (4, 1, 16), (16, 1, 17)):
        r.mark('nido', x, y, z)
    r.stalagmite(3, 9, 2)
    r.stalagmite(18, 12, 2)
    r.ore_seam(3)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:techo', 5, 9, 14)
    r.deco('decoracion:pared', 19, 3, 6)
    return r


def build_infestadas_telar():
    """The queen's chamber, built as her fight instead of dressed as it.

    It REPLACES the derived arena rather than joining it. A boss room earns its power from being the
    same place every time (§66), so a piso may only have one — and Infestadas' one should be hers.

    Three things the room teaches, all of them rules the floor already runs on: the web curtains
    break the flanks, which is where FLANK_RAGE lives (§55) and where a party learns not to circle
    her; the centre is open, because a boss that splits needs legible ground; and the egg shelves
    are hers, not a perch — they are reachable, so the nests can be answered."""
    r = Room('boss', 'single', 'infestadas:boss:telar')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    web = r.block('minecraft:cobweb')
    # Egg shelves in the corners, each with its own way up: her clutch is contestable.
    for (x0, z0, x1, z1, rx, rz, dx, dz) in ((1, 1, 5, 5, 6, 3, -1, 0),
                                             (15, 15, 19, 19, 14, 17, 1, 0)):
        r.shelf(x0, z0, x1, z1, top=3, light=False)
        r.ramp(rx, rz, dx, dz, top=3)
    for (x, y, z) in ((3, 4, 3), (17, 4, 17), (4, 1, 16), (16, 1, 4)):
        r.mark('nido', x, y, z)
    # The curtains: hung on the flanks and nowhere near the centre, so the ground she splits on
    # stays clean and the ways around her do not.
    for (x, z) in ((6, 10), (14, 10), (7, 7), (13, 13), (7, 13), (13, 7)):
        if (x, z) in r.keep_clear:
            continue
        for y in range(2, 6):
            r.set(x, y, z, web)
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12)):
        r.set(x, 11, z, r.block('minecraft:ochre_froglight'))
    r.set(10, 1, 10, r.block('minecraft:smooth_basalt'))
    r.enforce_aprons()
    r.mark('boss', 10, 2, 10)
    r.mark('trapdoor', 10, 1, 15)
    r.deco('decoracion:techo', 5, 9, 10)
    r.deco('decoracion:techo', 15, 9, 10)
    return r


def build_infestadas_nidada():
    """The elite over a live clutch. Same lesson as nidal at the scale of one fight: the nests keep
    hatching while it is alive, so the room asks whether to kill the thing in front of you or the
    thing making more of them."""
    r = Room('mini_boss', 'single', 'infestadas:mini_boss:nidada')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.01)
    r.ceiling_relief(blobs=6, dripstone=3)
    for x in range(7, 14):
        for z in range(7, 14):
            if (x, z) not in r.keep_clear:
                r.set(x, 1, z, r.block('minecraft:moss_block'))
    for (x, y, z) in ((8, 2, 8), (12, 2, 12), (8, 2, 12), (12, 2, 8)):
        r.mark('nido', x, y, z)
    web = r.block('minecraft:cobweb')
    for (x, z) in ((5, 15), (15, 5), (4, 4)):
        for y in range(2, 5):
            r.set(x, y, z, web)
    r.set(10, 10, 10, r.block('minecraft:ochre_froglight'))
    r.chest('trampa', 17, 17)
    r.ore_seam(3)
    r.enforce_aprons()
    r.mark('boss', 10, 2, 10)
    r.deco('decoracion:techo', 14, 9, 6)
    return r


def build_infestadas_mudas():
    """A moulting gallery: shed skins on ledges up the walls, and the floor beneath them clear.
    Reads as a room the spiders use rather than one they wandered into."""
    r = Room('normal', 'single', 'infestadas:normal:mudas')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    r.ceiling_relief(blobs=7, dripstone=5)
    for (x0, z0, x1, z1, level) in ((2, 2, 6, 4, 3), (14, 16, 18, 18, 3), (16, 3, 18, 7, 6)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x, z) in r.keep_clear or r.is_wall(x, z):
                    continue
                for y in range(1, level + 1):
                    r.set(x, y, z, r.stone_blend(y, rng))
    r.ramp(7, 3, -1, 0, top=3)
    r.ramp(13, 17, 1, 0, top=3)
    web = r.block('minecraft:cobweb')
    for (x, z) in ((4, 3), (16, 17), (17, 5)):
        r.set(x, 1, z, r.block('minecraft:dead_bush'))
        r.set(x + 1, 4, z, web)
    for (x, y, z) in ((3, 4, 3), (17, 7, 5), (5, 1, 12)):
        r.mark('nido', x, y, z)
    r.set(9, 8, 9, r.block('minecraft:ochre_froglight'))
    r.chest('sellado', 10, 14)
    r.ore_seam(3)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=3)
    r.deco('decoracion:suelo', 8, 1, 6)
    r.deco('decoracion:techo', 12, 9, 12)
    return r


def build_infestadas_sumidero():
    """Standing water under the nest, and everything that lives here wades it happily. The one
    room on the floor where the party is slower than what is chasing it."""
    r = Room('normal', 'single', 'infestadas:normal:sumidero')
    r.shell()
    rng = r.rng
    r.rough_walls(light_chance=0.0)
    for (x0, z0, x1, z1) in ((2, 2, 7, 7), (13, 13, 18, 18), (2, 13, 7, 18)):
        r.pool(x0, z0, x1, z1)
    r.shelf(13, 2, 18, 7, top=4, light=False)
    r.ramp(12, 4, 1, 0, top=4)
    for (x, y, z) in ((15, 5, 4), (9, 1, 9), (10, 1, 12)):
        r.mark('nido', x, y, z)
    web = r.block('minecraft:cobweb')
    for (x, z) in ((10, 4), (10, 16)):
        if (x, z) not in r.keep_clear:
            for y in range(3, 6):
                r.set(x, y, z, web)
    r.set(16, 5, 5, r.block('minecraft:ochre_froglight'))
    r.ore_seam(3)
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=2)
    r.deco('decoracion:techo', 10, 9, 10)
    return r


def build_infestadas_capullos():
    """The ceiling presses down and the webbing hangs in curtains, so sightlines break
    vertically as well as horizontally — the room where a climbing tejedora is at its worst."""
    r = Room('normal', 'single', 'infestadas:normal:capullos')
    r.shell()
    rng = r.rng
    for x in range(1, 20):
        for z in range(1, 20):
            if (x, z) in r.keep_clear:
                continue
            d = max(abs(x - 10), abs(z - 10))
            top = 7 if d >= 7 else 8
            for y in range(top, 11):
                r.set(x, y, z, r.stone_blend(y, rng))
    r.rough_walls(light_chance=0.0)
    web = r.block('minecraft:cobweb')
    # Curtains, not a fog: six short columns you walk through, hung where nobody has to fight.
    for (x, z) in ((5, 7), (7, 4), (14, 6), (16, 13), (6, 15), (13, 16)):
        if (x, z) in r.keep_clear:
            continue
        for y in range(4, 7):
            r.set(x, y, z, web)
    for (x, y, z) in ((4, 1, 5), (16, 1, 4), (5, 1, 16), (17, 1, 15), (10, 1, 6)):
        r.mark('nido', x, y, z)
    r.stalagmite(9, 13, 2)
    r.stalagmite(12, 9, 2)
    r.ore_seam(2)
    r.lichen(1, 3, 10, 'west')
    r.lichen(19, 3, 10, 'east')
    r.enforce_aprons()
    r.auto_spawns(wave_bar('normal'), ranged=0)
    r.deco('decoracion:techo', 8, 6, 8)
    r.deco('decoracion:pared', 1, 3, 15)
    return r


# ----------------------------------------------------------- the first shared room

def build_comun_pacto():
    """The devil's room, shared by every piso that inherits `comun`.

    A shared template is fixed blocks in every place that draws it, which is wrong for a cave and
    right for this: the deal room is *supposed* to look like it does not belong to the floor. That
    is why it is the one worth sharing first.
    """
    r = Room('devil_deal', 'single', 'comun:devil_deal:pacto')
    r.shell()
    rng = r.rng
    black = r.block('minecraft:blackstone')
    polished = r.block('minecraft:polished_blackstone')
    bricks = r.block('minecraft:polished_blackstone_bricks')
    for x in range(1, 20):
        for z in range(1, 20):
            r.set(x, 0, z, black if rng.random() < 0.7 else polished)
            for y in range(9, 11):
                r.set(x, y, z, black if rng.random() < 0.8 else polished)
    for x in range(21):
        for z in range(21):
            if r.is_wall(x, z):
                for y in range(H):
                    r.set(x, y, z, black if rng.random() < 0.75 else polished)
    # the pact floor: a stepped dais with a gilded rim, and chains of iron overhead
    for x in range(7, 14):
        for z in range(7, 14):
            if (x, z) in r.keep_clear:
                continue
            edge = max(abs(x - 10), abs(z - 10))
            r.set(x, 0, z, bricks if edge <= 1 else polished)
            if edge == 3:
                r.set(x, 1, z, r.block('minecraft:gilded_blackstone'))
    r.set(10, 1, 10, r.block('minecraft:chiseled_polished_blackstone'))
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12)):
        r.set(x, 1, z, r.block('minecraft:blackstone_wall'))
        r.set(x, 2, z, r.block('minecraft:soul_lantern', hanging='false'))
    for (x, z) in ((5, 10), (15, 10), (10, 5), (10, 15)):
        for y in range(6, 9):
            r.set(x, y, z, r.block('minecraft:chain', axis='y'))
    r.mark('deal', 10, 2, 10)
    r.enforce_aprons()
    r.deco('decoracion:techo', 4, 8, 4)
    r.deco('decoracion:techo', 16, 8, 16)
    return r


def build_comun_orden():
    """La sala de la Orden (PISOS §63b–e): the grace chamber on the sello's far flank.

    Shared for the same reason the pacto is, and more so — she is not of the floor either, and the
    two rooms are a matched pair seen across one wing: the same 21x21 footprint, the same dais at the
    same centre, and everything else inverted. The pacto is blackstone under soul-blue; this is the
    Orden's own deepslate under warm lantern light, with calcite where the other has gilding. A party
    standing between the two open doors should be able to read the choice from the light alone.

    The register is deliberately the sello's (build_exit): she built that chamber too, and the wing
    ought to look like one hand made both ends of it.
    """
    r = Room('orden', 'single', 'comun:orden:capilla')
    r.shell()
    rng = r.rng
    slab = r.block('minecraft:polished_deepslate')
    tiles = r.block('minecraft:deepslate_tiles')
    frame = r.block('minecraft:chiseled_deepslate')
    calcite = r.block('minecraft:calcite')
    for x in range(1, 20):
        for z in range(1, 20):
            r.set(x, 0, z, slab if rng.random() < 0.65 else tiles)
    for x in range(21):
        for z in range(21):
            if r.is_wall(x, z):
                for y in range(H):
                    r.set(x, y, z, slab if rng.random() < 0.7 else tiles)
    # The dais, at the pacto's exact centre and height so the pair rhymes: calcite steps in place of
    # the gilded rim, and a font of chiseled quartz where the other has its chiseled blackstone.
    for x in range(7, 14):
        for z in range(7, 14):
            if (x, z) in r.keep_clear:
                continue
            edge = max(abs(x - 10), abs(z - 10))
            r.set(x, 0, z, calcite if edge <= 1 else tiles)
            if edge == 3:
                r.set(x, 1, z, r.block('minecraft:calcite'))
    r.set(10, 1, 10, r.block('minecraft:chiseled_quartz_block'))
    # Warm light on four posts, against the pacto's four soul lanterns: the same fixture, the other
    # temperature. Nothing here is emissive terrain — the room is lit, not glowing.
    for (x, z) in ((8, 8), (12, 8), (8, 12), (12, 12)):
        r.set(x, 1, z, r.block('minecraft:deepslate_tile_wall'))
        r.set(x, 2, z, r.block('minecraft:lantern', hanging='false'))
    # A framed band on the north wall for the Orden's script, the sello's trophy gallery in
    # miniature — the one surface that says who this room belongs to.
    for x in range(5, 16):
        r.set(x, 4, 1, frame if x % 3 == 0 else calcite)
    for (x, z) in ((5, 10), (15, 10), (10, 5), (10, 15)):
        for y in range(6, 9):
            r.set(x, y, z, r.block('minecraft:chain', axis='y'))
    r.mark('gracia', 10, 2, 10)
    r.enforce_aprons()
    r.deco('decoracion:techo', 4, 8, 16)
    r.deco('decoracion:techo', 16, 8, 4)
    return r


def build_exit():
    """La sala del sello (PRODUCCION §10.6): the chamber the Orden built over the floor's seal pin.

    A 2×2 chamber appended AFTER generation (PostRooms), sealed behind solid wall until the boss
    dies — nothing announces it, in the world or on the map. The seal glyph IS the ring around the
    pit: a chiseled-deepslate frame whose polished-basalt runes the run engine swaps to the lit
    block on boss death, while the iron grate over the 2x2 pit retracts and the wall to the arena
    is carved open.

    The rune block is a contract with DungeonsConfig.bloqueRunaSello: the engine finds the runes by
    block id, so infest() must never reskin polished basalt (it does not) and neither may a later
    palette pass.
    """
    r = Room('exit', 'big', 'cuevas:exit')
    r.shell()
    rng = r.rng
    frame = r.block('minecraft:chiseled_deepslate')
    slab = r.block('minecraft:polished_deepslate')
    tiles = r.block('minecraft:deepslate_tiles')
    # Directional, not symmetric: the materializer rotates this whole template so its +z (south)
    # wall — the entrance the grand door carves — always faces the boss. So the front (south) is
    # the boss side, the back (north) is the trophy gallery, and the east/west walls are the
    # flanks the satellite doors will one day open.
    for x in range(6, 36):
        for z in range(6, 36):
            if (x, z) not in r.keep_clear:
                r.set(x, 0, z, r.block('minecraft:polished_deepslate' if rng.random() < 0.6
                                       else 'minecraft:deepslate_tiles'))
    # The Poneglyph: a solid 5x5x5 cube of dark stone dead-centre, its faces left blank for the
    # Orden's script (carved by hand, not authored here). It is the seal-stone and the lore in one;
    # the ring of runes at its foot is what lights when the boss falls.
    for x in range(18, 23):
        for z in range(18, 23):
            for y in range(1, 6):
                r.set(x, y, z, frame if y == 5 else slab)
    # The pit opens at the Poneglyph's foot, toward the entrance the party came through: a chiseled
    # frame around the 3x3, iron grate over it. Odd width on purpose, so the pit — and the grand
    # door the reveal carves beyond it — share the cube's centre line (x=20).
    for x in range(18, 23):
        for z in range(23, 28):
            if not (19 <= x <= 21 and 24 <= z <= 26):
                r.set(x, 0, z, frame)
    for x in range(19, 22):
        for z in range(24, 27):
            r.set(x, 0, z, r.block('minecraft:iron_bars',
                                   north=str(z > 24).lower(), south=str(z < 26).lower(),
                                   west=str(x > 19).lower(), east=str(x < 21).lower()))
    # The seal runes ring the Poneglyph's foot and the pit. All clear of the door approaches —
    # enforce_aprons refills five blocks in from each wall band, and a rune it repaves never lights.
    rune = r.block('minecraft:polished_basalt')
    for (x, z) in ((16, 20), (24, 20), (16, 21), (24, 21),
                   (18, 16), (22, 16), (20, 15), (21, 15),
                   (18, 28), (22, 28), (20, 29), (21, 29),
                   (14, 14), (27, 14), (14, 27), (27, 27)):
        r.set(x, 0, z, rune)
    # A tiled medallion in the ceiling answers the seal from above.
    for x in range(18, 23):
        for z in range(18, 23):
            if x in (18, 22) or z in (18, 22):
                r.set(x, H - 2, z, tiles)

    # The trophy gallery — the far (north) wall, a framed band the party faces last. Presentation
    # only; the first-clear ceremony is the title stack.
    for x in range(4, 38):
        r.set(x, 4, 1, frame if x % 3 == 0 else slab)
    # The results backdrop framing the entrance (south) wall, flanking where the grand door opens.
    for x in list(range(3, 14)) + list(range(28, 39)):
        for y in range(1, 5):
            r.set(x, y, 40, frame if y == 4 else slab)

    # The boss reward stand, on a dais near the entrance so claiming it is a stop on the way in,
    # off to the west flank and clear of the central pit.
    for (x, z) in ((9, 28), (10, 28), (9, 29), (10, 29)):
        r.set(x, 0, z, slab)
    r.set(9, 0, 28, frame)
    r.mark('premio', 9, 1, 28)

    # Corner pilasters and soul light, until the seal itself becomes the lamp. The east and west
    # wall centres stay clear for the satellite doors.
    post = r.block('minecraft:polished_deepslate_wall')
    lantern = r.block('minecraft:soul_lantern', hanging='false')
    for (x, z) in ((3, 3), (38, 3), (3, 38), (38, 38)):
        for y in range(1, 9):
            r.set(x, y, z, slab)
    for (x, z) in ((8, 8), (33, 8), (8, 33), (33, 33), (14, 34), (27, 34)):
        r.set(x, 1, z, post)
        r.set(x, 2, z, lantern)

    r.enforce_aprons()
    # The CENTRE of the pit, not its corner: this template is rotated so its front faces the boss,
    # and a corner marker would still rotate correctly while the engine's +x/+z extent around it
    # would not — the hole would slide off the grate on every rotation but NONE. A centre survives
    # all four. Shares the Poneglyph's centre line (x=20).
    r.mark('trapdoor', 20, 1, 25)
    return r


def infest(base):
    """Dresses a finished Cuevas room as its infested twin, in place.

    Geometry is untouched on purpose. Everything that makes a room work — the doorway aprons, the
    spawn markers, the shelves and their ramps — was verified on the base room, and re-deriving any
    of it here would be a second chance to get it wrong for no gain.
    """
    r = base
    r.key = base.key
    rng = random.Random('infestadas:' + base.key)

    # 1. Palette. Rebuilt entry by entry so block states (stairs facing, slab type) survive.
    for i, (name, props) in enumerate(list(r.palette)):
        swapped = INFEST_PALETTE.get(name)
        if swapped:
            r.palette[i] = (swapped, props)
    # The index map is stale after a rename; rebuild it so later lookups do not resurrect a
    # Cuevas block under an Infestadas name.
    r.pal_index = {}
    for i, key in enumerate(r.palette):
        r.pal_index.setdefault(key, i)

    # 2. Thin the lighting: an infested cave is darker than the one it grew in.
    froglight = r.block('minecraft:ochre_froglight')
    for pos, idx in list(r.grid.items()):
        if r.palette[idx][0] == 'minecraft:ochre_froglight' and rng.random() < 0.45:
            r.grid[pos] = r.block('minecraft:deepslate')

    # 3. Cobweb in the ceiling and in the upper corners, where nobody walks.
    web = r.block('minecraft:cobweb')
    for (cx, cz) in r.cells:
        for lx in range(1, S - 1):
            for lz in range(1, S - 1):
                x, z = cx * S + lx, cz * S + lz
                if r.is_wall(x, z):
                    continue
                for y in range(H - 4, H - 1):
                    if r.solid_at(x, y, z) or r.in_zone(x, y, z):
                        continue
                    # Only hanging from something solid, and only in the ceiling band.
                    if r.solid_at(x, y + 1, z) and rng.random() < INFEST_WEB_CHANCE:
                        r.set(x, y, z, web)
                        break

    # 4. Nests. A `nido` marker replaces some decoration markers — the mechanic's content, and the
    # reason Infestadas fights differently rather than merely looking different.
    replaced = 0
    for i, (tag, x, y, z) in enumerate(list(r.markers)):
        if tag.startswith('decoracion') and rng.random() < 0.55:
            r.markers[i] = ('nido', x, y, z)
            replaced += 1
    if replaced == 0 and r.markers:
        for i, (tag, x, y, z) in enumerate(list(r.markers)):
            if tag.startswith('decoracion'):
                r.markers[i] = ('nido', x, y, z)
                break
    return r




# Every template that ships, as folder -> file -> builder. A room key is a folder and each .nbt
# inside it is a peer variant, so a name has to say what the room IS: "normal.nbt" inside normal/
# would be the only file in the folder that told you nothing.
VARIANTS = {
    'start':        {'cupula': build_start,                     # the domed arrival chamber
                     'falla': build_start_falla,                # the fault, one half stepped up
                     'campamento': build_start_campamento},     # somebody else's camp
    'normal':       {'repisa': build_normal,                    # shelf along the east wall
                     'pozo': build_normal_pozo,                 # central basin, no high ground
                     'columnas': build_normal_columnas,         # column forest, broken sightlines
                     'derrumbe': build_normal_derrumbe,         # collapsed quarter, rubble slope
                     'balcon': build_normal_balcon,             # a real balcony to contest
                     'lago': build_normal_lago,                 # water as movement denial
                     'puente': build_normal_puente,             # trench cross, two bridges
                     'laberinto': build_normal_laberinto,       # 2-high walls, slide gaps
                     'crater': build_normal_crater,             # the rim is theirs, not yours
                     'hongos': build_normal_hongos},            # the grove
    'boss':         {'anillo': build_boss},                     # rimmed arena — one variant, by §66
    'mini_boss':    {'columna': build_mini_boss,                # one column, one shelf
                     'estanque': build_mini_boss_estanque,      # the elite stands in water
                     'grieta': build_mini_boss_grieta},         # flat ground, no cover
    'shop':         {'alcoba': build_shop,                      # the worked, paved end of a cave
                     'caravana': build_shop_caravana,           # crates under an awning
                     'socavon': build_shop_socavon},            # alcoves in a timbered adit
    'treasure':     {'pedestal': build_treasure,                # pedestal under a shaft of light
                     'veta': build_treasure_veta,               # three stands in a mining face
                     'ofrenda': build_treasure_ofrenda},        # three bowls, ringing the centre
    'secret':       {'rendija': build_secret,                   # cramped natural pocket
                     'alacena': build_secret_alacena,           # a walled-up cache
                     'veta': build_secret_veta,                 # an abandoned mining face
                     'derrumbado': build_secret_derrumbado,     # a collapsed passage to climb
                     'burbuja': build_secret_burbuja},          # a smooth calcite void
    'super_secret': {'geoda': build_super_secret,               # calcite and amethyst
                     'ahogada': build_super_secret_ahogada},    # a flooded vault, older than here
    'challenge':    {'galerias': build_challenge,               # corner galleries
                     'cisterna': build_challenge_cisterna},     # an island in flooded ground
    'curse':        {'santuario': build_curse,                  # blackstone shrine
                     'osario': build_curse_osario},             # the same market, kept by the dead
    'sacrifice':    {'altar': build_sacrifice,                  # basalt altar, magma channels
                     'pozo': build_sacrifice_pozo},             # the altar at the lip of a shaft
    'arcade':       {'vitrina': build_arcade,                   # a lit plinth in a dim cave
                     'tragaperras': build_arcade_tragaperras},  # wedged into a widened niche
    'devil_deal':   {'circulo': build_devil_deal},              # gilded circle, cage arcs
    'normal_large': {'garganta': build_normal_large,            # two chambers, arched neck
                     'columnata': build_normal_large_columnata, # one hall, colonnade
                     'manantial': build_normal_large_manantial, # overhung ledge, spring, basin
                     'desfiladero': build_normal_large_desfiladero},   # a corridor, not a hall
    'normal_l':     {'codo': build_normal_l,                    # elbow massif, inner perch
                     'mirador': build_normal_l_mirador,         # outer perch instead
                     'recodo': build_normal_l_recodo,           # cistern splits the two arms
                     'andamios': build_normal_l_andamios},      # two decks and a zipline
    'normal_big':   {'terrazas': build_normal_big,              # three terraces, ring route
                     'anfiteatro': build_normal_big_anfiteatro, # stepped bowl, looked down on
                     'cuatro_pilares': build_normal_big_cuatro_pilares},
    'boss_big':     {'oculo': build_boss_big},                  # the arena under a great oculus
    'exit':         {'exit': build_exit},                       # la sala del sello, the way down
}

# Rooms that exist ONLY in Cuevas Infestadas, authored rather than derived. See the comment above
# build_infestadas_nidal for why a piso needs at least one of these to be a place and not a filter.
INFESTADAS_ONLY = {
    'normal': {'nidal': build_infestadas_nidal,
               'capullos': build_infestadas_capullos,
               'mudas': build_infestadas_mudas,
               'sumidero': build_infestadas_sumidero},
    'mini_boss': {'nidada': build_infestadas_nidada},
}

# Keys whose derived variants Infestadas DROPS in favour of its own.
#
# Only the boss, and the reason is §66's axis rather than a preference for authored over derived: a
# recognized room earns its power from being the same place every time, so a piso may only have one
# arena — and a floor whose boss is the queen should not fight her in a cave that happens to have
# webs in it. Everything else stays derived and merely gains: mini_boss and normal are approached
# rooms, where a wider draw is upside, so nidada and the two new normals ADD to the derived set.
#
# boss_big is knowingly left derived: Infestadas' 2x2 arena is a real build and this pass did not
# make it. It is the next thing this piso owes.
INFESTADAS_REPLACES = {
    'boss': {'telar': build_infestadas_telar},
}

# Rooms in a shared set, drawn by every piso whose `hereda` names it.
SHARED = {
    'comun': {'devil_deal': {'pacto': build_comun_pacto},
              'orden': {'capilla': build_comun_orden}},
}


def variants_for(piso):
    """folder -> file -> builder for one piso. Infestadas takes every Cuevas room dressed by
    infest(), plus its own — it declares all four families now, so it owes all 17 keys."""
    if piso == 'cuevas':
        return {k: dict(v) for k, v in VARIANTS.items()}
    out = {}
    for key, named in VARIANTS.items():
        if key in INFESTADAS_REPLACES:
            out[key] = dict(INFESTADAS_REPLACES[key])
            continue
        out[key] = {name: (lambda b=builder: infest(b())) for name, builder in named.items()}
    for key, named in INFESTADAS_ONLY.items():
        out.setdefault(key, {}).update(
            {name: (lambda b=builder: infest(b())) for name, builder in named.items()})
    return out


def check_disk(out, builders):
    """Fails when the shipped templates are not exactly what this tool builds.

    The six §66 rooms — start/falla, treasure/veta, shop/caravana and the rest — were committed as
    .nbt with no builder behind them, so regenerating cuevas produced a quietly different piso than
    the one that shipped, and infest() could not derive their Infestadas twins either. A template
    with no source is not authored content, it is a binary nobody can change. Run this in CI or
    before a release; --room and --variante are ignored, since a subset cannot answer the question.
    """
    expected = {os.path.join(key, name + '.nbt')
                for key, named in builders.items() for name in named}
    found = set()
    for folder, _, files in os.walk(out):
        for name in files:
            if name.endswith('.nbt'):
                found.add(os.path.relpath(os.path.join(folder, name), out))
    orphans = sorted(found - expected)
    missing = sorted(expected - found)
    for path in orphans:
        print(f'x orphan   {path} — on disk with no builder', file=sys.stderr)
    for path in missing:
        print(f'x missing  {path} — a builder exists but nothing is shipped', file=sys.stderr)
    if orphans or missing:
        return 1
    print(f'{len(found)} templates, all authored by this tool')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true', help='print floor plans, write nothing')
    parser.add_argument('--check', action='store_true',
                        help='write nothing; fail if the templates on disk are not exactly the '
                             'ones this tool builds')
    parser.add_argument('--room', help='build only this room key')
    parser.add_argument('--piso', default='cuevas',
                        choices=['cuevas', 'cuevas_infestadas', 'comun'],
                        help='which set to author; infestadas dresses the cuevas rooms, and '
                             'comun is the shared set every piso inherits by default')
    parser.add_argument('--variante', help='build only this variant of --room')
    parser.add_argument('--out')
    args = parser.parse_args()

    out = args.out or os.path.join(
        os.path.dirname(os.path.abspath(__file__)), '..',
        'src/main/resources/data/teras/structure/dungeon', args.piso)
    builders = SHARED[args.piso] if args.piso in SHARED else variants_for(args.piso)

    if args.check:
        return check_disk(out, builders)

    failed = False
    for key, named in builders.items():
        if args.room and key != args.room:
            continue
        for variant, (name, builder) in enumerate(named.items(), start=1):
            if args.variante and name != args.variante:
                continue
            room = builder()
            # After the builder, never inside it: the notch counts this room's place in its key's
            # variant list, which only the loop knows.
            room.crown(key, variant)
            errors, warnings = room.audit()
            plain = sum(1 for (t, _, _, _) in room.markers if t == 'spawn')
            ranged = sum(1 for (t, _, _, _) in room.markers if t == 'spawn:ranged')
            nests = sum(1 for (t, _, _, _) in room.markers if t == 'nido')
            crown = CROWNS.get(key)
            print(f'{key:14} {name:15} {room.sx}x{H}x{room.sz}  markers={len(room.markers)}'
                  f' (spawn {plain}+{ranged}r{f", nido {nests}" if nests else ""})'
                  f'  palette={len(room.palette)}'
                  f'  {f"crown={crown.split(chr(58))[1]}/{variant}" if crown else "crown=-"}')
            for w in warnings:
                print(f'    ! {w}')
            for e in errors:
                print(f'    x {e}')
                failed = True
            if args.preview:
                print(room.preview())
                print()
            elif not errors:
                folder = os.path.join(out, key)
                os.makedirs(folder, exist_ok=True)
                write_nbt(os.path.join(folder, name + '.nbt'), room.to_nbt())
    if failed:
        print('ERRORS - nothing written for failing rooms', file=sys.stderr)
        return 1
    if not args.preview:
        print(f'\nwrote {args.piso} templates to {os.path.abspath(out)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
