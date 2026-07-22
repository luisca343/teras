#!/usr/bin/env python3
"""Generates the rigs, animations and textures the first-party bestiary needs.

    python3 tools/author_enemy_assets.py

Deterministic, like tools/author_cuevas_rooms.py: same run, same bytes. Nothing here is hand-edited
afterwards — an artist replacing a rig replaces the `.geo.json`, the `.animation.json` and the
`.png` together, and the entity keeps working because every hook it drives is named the same way.

What it writes:

  geo/dungeon_limo.geo.json            a blob rig — one squashable body bone, so a hop reads
  geo/dungeon_raider.geo.json          cave humanoids: lean, long-armed, leaning into the walk
  geo/dungeon_guardian.geo.json        crypt humanoids: pauldrons, short legs, upright
  geo/dungeon_golem.geo.json           the geode — a slab on stubby legs, with glowing seams
  geo/dungeon_spider_cria.geo.json     juvenile arachnid: big head, short legs, light abdomen
  geo/dungeon_spider_tejedora.geo.json mature arachnid: heavy spinneret abdomen, long legs
  geo/dungeon_cazadora.geo.json        the huntress — long-legged, light-bodied, no web
  geo/dungeon_reina.geo.json           the queen — the same skeleton at boss mass
  geo/dungeon_lepisma.geo.json         a silverfish: segmented, low, six legs, no spider anatomy
  animations/*.animation.json          one per rig, clips named for DungeonGeoEnemy's controller
  textures/entity/dungeon/*.png        a base sheet and a `_glow` sheet per variant

The arachnid rigs share a bone contract (see BONES) so they can share a gait generator and, where it
makes sense, an animation file; the upright rigs share theirs (see BIPED_BONES) for the same reason.
The silverfish deliberately shares nothing: it is not a spider, and putting it on the spider skeleton
is what made it read as one.

Sharing a contract is not sharing a body. Two rigs on one contract drive the same clip names with
different proportions and different timing, which is what lets a floor field a scavenger and an
armoured heavy without either one being the other at a different scale.

Clip names are the ones DungeonGeoEnemy's controller can request. BestiaryAudit derives the
required set per variant and BestiaryAuditTest holds each rig's file to it, so a clip that goes
missing fails the build rather than playing nothing in game.
"""
import json
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'src/main/resources/assets/teras')

# Blockbench works on a 24 fps grid and every reference rig keyframes on it. Sampling off the grid
# produces times like 0.3333333333333333 in the JSON, which are both noisy to diff and slightly
# wrong against a clip authored in the editor later.
FPS = 24


def snap(t):
    """A time on the 24 fps grid, rounded so JSON never carries float noise."""
    return round(round(t * FPS) / FPS, 5)


def r3(v):
    return [round(x, 4) for x in v]


# ---------------------------------------------------------------------------- PNG

def write_png(path, pixels, width, height):
    """pixels: list of (r,g,b,a) rows, top to bottom."""
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            raw += bytes(pixels[y][x])

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', header)
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)


def noise(seed):
    """A tiny deterministic hash → 0..1, so textures vary without importing random state."""
    state = seed * 1103515245 + 12345
    state ^= (state >> 16)
    return ((state * 2654435761) & 0xffff) / 65535.0


def blob_texture(path, base, dark, light, size=64):
    """A mottled blob sheet: flat colour with per-pixel dapple and a lighter top band."""
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            n = noise(x * 7919 + y * 104729)
            if y < size // 3:
                r, g, b = light
            elif n < 0.18:
                r, g, b = dark
            else:
                r, g, b = base
            jitter = int((n - 0.5) * 18)
            row.append((max(0, min(255, r + jitter)),
                        max(0, min(255, g + jitter)),
                        max(0, min(255, b + jitter)),
                        255))
        rows.append(row)
    write_png(path, rows, size, size)

# ---------------------------------------------------------------------------- UV packing

class Sheet:
    """A shelf packer that hands out box-UV slots and remembers what each one was for.

    Box UV is the format the rest of the bestiary uses: a cube carries `uv: [u, v]` and Minecraft
    unwraps the six faces from there in a fixed net. That fixes the slot size at
    `2*(sx+sz)` by `sy+sz`, and it is the reason the packer exists at all — the alternative is
    per-face UV, which is four times the JSON and cannot be painted by walking rectangles.

    Painting reads `regions` back, so the texture is generated from the same geometry the model
    uses. Every earlier enemy sheet was noise at the right resolution and nothing more; a sheet that
    knows where the abdomen is can put a marking on it.
    """

    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.shelf_y = 0
        self.shelf_h = 0
        self.x = 0
        self.regions = []

    def slot(self, size, part):
        sx, sy, sz = (int(math.ceil(v)) for v in size)
        w, h = 2 * (sx + sz), sy + sz
        if self.x + w > self.width:
            self.shelf_y += self.shelf_h
            self.shelf_h = 0
            self.x = 0
        if self.shelf_y + h > self.height:
            raise ValueError(f'texture sheet {self.width}x{self.height} is full')
        u, v = self.x, self.shelf_y
        self.x += w
        self.shelf_h = max(self.shelf_h, h)
        self.regions.append({'uv': (u, v), 'size': (sx, sy, sz), 'part': part})
        return [u, v]


def faces_of(region):
    """The six face rectangles of a box-UV slot, as (x, y, w, h, face-name).

    The net Minecraft unwraps: depth-wide columns either side of two width-wide ones, with the two
    caps sitting above them. Written out rather than derived because getting one rectangle wrong
    paints the abdomen's marking onto its underside, which is invisible until it is in front of a
    player.
    """
    u, v = region['uv']
    sx, sy, sz = region['size']
    return [
        (u + sz, v, sx, sz, 'top'),
        (u + sz + sx, v, sx, sz, 'bottom'),
        (u, v + sz, sz, sy, 'east'),
        (u + sz, v + sz, sx, sy, 'north'),
        (u + sz + sx, v + sz, sz, sy, 'west'),
        (u + 2 * sz + sx, v + sz, sx, sy, 'south'),
    ]

# ---------------------------------------------------------------------------- arachnid rig

# The bone contract every arachnid rig honours. The gait generator addresses legs by name and the
# animation files are shared across builds, so a rig that renames a bone silently stops animating
# the part it renamed — which is why this is one list rather than a convention per rig.
LEGS = ['leg_l1', 'leg_l2', 'leg_l3', 'leg_l4',
        'leg_r1', 'leg_r2', 'leg_r3', 'leg_r4']
# The two lower segments of each leg, addressable so the gait can flex a knee.
FEMURS = [leg + '_femur' for leg in LEGS]
TIBIAE = [leg + '_tibia' for leg in LEGS]
BONES = ['root', 'body', 'abdomen', 'spinneret', 'head', 'eyes',
         'fang_left', 'fang_right', 'palp_left', 'palp_right', 'legs'] + LEGS + FEMURS + TIBIAE


# Proportions per build. The three arachnids are the same animal at three ages, so everything here
# is a measurement rather than a shape: changing `abdomen` alone turns the juvenile into the weaver.
BUILDS = {
    'cria': {
        # A juvenile reads by head-to-body ratio, not by scale — shrinking the adult produced an
        # enemy players could not tell from a distant weaver, which is the whole readability problem
        # a shared rig has.
        'body': (7, 4, 7), 'body_y': 6,
        'abdomen': (7, 6, 7), 'abdomen_z': 4,
        'head': (6, 4, 4),
        'leg_len': (3, 4), 'leg_thick': 1,
        'leg_spread': 4, 'leg_rows': (-3, -1, 1, 3),
        'spinneret': (2, 2, 2),
        'palp': (1, 1, 3),
        'fang': (1, 2, 1),
        'eye_rows': ((-2.5, 1.5), (-1, 2), (1, 2), (2.5, 1.5)),
        'eye': (1, 1, 1),
        'sheet': 128,
    },
    'tejedora': {
        # The weaver carries the mass behind: a heavy abdomen and a spinneret you can see is what
        # makes "this is the one that shoots" learnable before it shoots.
        'body': (8, 5, 8), 'body_y': 7,
        'abdomen': (10, 9, 11), 'abdomen_z': 5,
        'head': (6, 4, 5),
        'leg_len': (4, 6), 'leg_thick': 1,
        'leg_spread': 5, 'leg_rows': (-4, -1, 2, 5),
        'spinneret': (3, 3, 3),
        'palp': (1, 1, 4),
        'fang': (1, 3, 1),
        'eye_rows': ((-3, 2), (-1, 2.5), (1, 2.5), (3, 2)),
        'eye': (1, 1, 1),
        'sheet': 128,
    },
    'cazadora': {
        # The hunter. Everything the weaver spends behind her, this one spends on reach: a light
        # abdomen, a spinneret barely there, and a low body carried high off the floor on long thin
        # legs. She has no web to make, and the silhouette has to say so before the fight does —
        # she is the one Infestadas enemy that closes rather than holding height.
        'body': (8, 4, 9), 'body_y': 10,
        'abdomen': (8, 6, 10), 'abdomen_z': 5,
        'head': (7, 5, 5),
        'leg_len': (5, 8), 'leg_thick': 1,
        'leg_spread': 6, 'leg_rows': (-5, -2, 2, 5),
        'spinneret': (2, 2, 2),
        'palp': (1, 1, 4),
        'fang': (2, 4, 1),
        # The one build with unequal eyes: the two forward ones are doubled, the other six are the
        # bestiary's usual pinpricks. That pair is a hunting spider's anterior median eyes, and at
        # mini-boss distance in an unlit room the glow sheet arrives before the silhouette does —
        # so how she is recognised across a room is those two lights sitting above six small ones.
        'eye_rows': ((-3, 2.5), (-1, 3), (1, 3), (3, 2.5)),
        'eye': (1, 1, 1),
        'eye_major': (1, 2),
        'eye_major_size': (2, 2, 1),
        'sheet': 128,
    },
    'reina': {
        # The boss is not the weaver scaled up: GeoEnemyVariant already scales her 2.2x, so doing it
        # here as well would put her hitbox and her silhouette in different places. What changes is
        # proportion — a longer reach and a far heavier egg-carrying abdomen.
        'body': (10, 6, 10), 'body_y': 8,
        'abdomen': (13, 12, 14), 'abdomen_z': 6,
        'head': (7, 5, 6),
        'leg_len': (5, 8), 'leg_thick': 2,
        'leg_spread': 6, 'leg_rows': (-5, -1, 3, 7),
        'spinneret': (4, 4, 4),
        'palp': (2, 2, 5),
        'fang': (2, 4, 2),
        'eye_rows': ((-3.5, 2.5), (-1.5, 3), (1.5, 3), (3.5, 2.5)),
        'eye': (1, 1, 1),
        'sheet': 128,
    },
}


# Elevation of the first two segments above horizontal, in degrees, as a person looking at the
# spider would describe them: the coxa leaves the body slightly downward, the femur climbs to the
# knee. Cumulative, not relative — this is the angle each segment sits at, and the chain's per-bone
# deltas are worked out from them.
COXA_ELEVATION = -10.0
FEMUR_ELEVATION = 38.0
# How steeply the tibia comes back down. Not quite vertical, so the foot lands inside the knee and
# the stance reads as braced rather than as stilts.
TIBIA_ELEVATION = -80.0
SEGMENT_NAMES = ('', '_femur', '_tibia')


def tibia_length(build):
    """How long the last segment has to be for the foot to reach the floor.

    Derived rather than authored, because the alternative is authoring it per build and getting it
    wrong per build: the first version had every spider standing about a quarter of a block above
    the ground, which reads as floating and is invisible in a JSON diff. The knee's height above
    y=0 is fixed by the body height and the two segments above, so the drop the tibia has to cover
    is arithmetic — and a rig whose feet are on the floor by construction cannot regress to hovering.
    """
    coxa, femur = build['leg_len']
    knee = (build['body_y']
            + coxa * math.sin(math.radians(COXA_ELEVATION))
            + femur * math.sin(math.radians(FEMUR_ELEVATION)))
    return max(1, round(knee / -math.sin(math.radians(TIBIA_ELEVATION))))


def segment_plan(build):
    """(length, cumulative elevation) per segment, foot on the floor."""
    coxa, femur = build['leg_len']
    return ((coxa, COXA_ELEVATION),
            (femur, FEMUR_ELEVATION),
            (tibia_length(build), TIBIA_ELEVATION))


def swing_to_json(degrees, sx):
    """A visible swing, in the sign GeckoLib will actually rotate by.

    GeckoLib mirrors a model in X on load — `origin.x` becomes `-(origin.x + size.x)` and
    `pivot.x` becomes `-pivot.x` — while negating only the X and Y components of a rotation. A Z
    rotation therefore runs <b>backwards</b> relative to the coordinates the file is written in,
    because reflecting a rotation through a plane reverses it.

    That one sign is what put the legs of every spider in the air: authored as "up", applied as
    "down", and then a hand-computed knee position that assumed the authored reading, so the
    segments came apart as well. Verify any change to it with tools/preview_rig.py, which
    reimplements the load path rather than trusting this comment.
    """
    return -degrees * sx


def leg_bones(build, sheet, side, row, pivot, prefix):
    """One leg as a chain of three bones: coxa out, femur up to the knee, tibia down to the floor.

    A chain, and not three rotated cubes in one bone, because a chain <b>cannot come apart</b>. A
    child bone's pivot is its parent's resting end, and the parent carries it wherever it rotates,
    so the joint holds for free at any angle. Doing it with per-cube rotation means computing each
    joint's position by hand under the load-time mirror described in {@link swing_to_json} — which
    is a closed-form solution nobody can check by reading, and which was wrong.

    The cost is sixteen extra bones across the rig. What it buys back, beyond correctness, is a
    knee: the gait can flex the femur and tibia against each other, which a single rigid bone with
    the bend baked into its cubes can never do.
    """
    t = build['leg_thick']
    sx = 1 if side == 'l' else -1
    px, py, pz = pivot
    # The splay: front pair reaching forward, rear pair back. Signed per side, because the load-time
    # mirror of {@link swing_to_json} negates Y as well as Z — a yaw written identically on both
    # flanks fans one of them and folds the other into a bundle of four legs leaving one point.
    yaw = {0: 26.0, 1: 8.0, 2: -8.0, 3: -26.0}[row] * sx
    bones = []
    reach = 0.0
    previous = 0.0
    for i, (length, elevation) in enumerate(segment_plan(build)):
        # Each pivot is the previous segment's end in the *rest* pose. The hierarchy supplies the
        # rotation, so this never needs a cosine.
        x = px + sx * reach
        # A child inherits its parent's rotation, so what it declares is the difference between the
        # two elevations rather than its own.
        bones.append({
            'name': prefix + SEGMENT_NAMES[i],
            'parent': prefix + SEGMENT_NAMES[i - 1] if i else 'legs',
            'pivot': [round(x, 3), py, pz],
            # Splay only on the first segment: the rest of the chain inherits it.
            'rotation': [0, yaw if i == 0 else 0, swing_to_json(elevation - previous, sx)],
            'cubes': [{
                'origin': [round(x if sx > 0 else x - length, 3), round(py - t / 2, 3), pz - t / 2],
                'size': [length, t, t],
                'uv': sheet.slot((length, t, t), 'leg'),
            }],
        })
        reach += length
        previous = elevation
    return bones


def arachnid_geo(name, build):
    """The shared arachnid skeleton at one set of proportions."""
    size = build['sheet']
    sheet = Sheet(size, size)
    bx, by, bz = build['body']
    body_y = build['body_y']
    ax, ay, az = build['abdomen']
    hx, hy, hz = build['head']

    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, body_y, 0], 'cubes': [
            {
                'origin': [-bx / 2, body_y - by / 2, -bz / 2],
                'size': [bx, by, bz],
                'uv': sheet.slot((bx, by, bz), 'body'),
            },
            # The carapace: a narrower plate riding the cephalothorax. One cube, and it is most of
            # what stops the body reading as a box with legs.
            {
                'origin': [-(bx - 2) / 2, body_y + by / 2 - 1, -bz / 2 + 1],
                'size': [bx - 2, 2, bz - 2],
                'uv': sheet.slot((bx - 2, 2, bz - 2), 'body'),
            },
        ]},
        # Pivoted at its front face so the breathing scale in `idle` swells it backwards, away from
        # the body, instead of pushing it through the cephalothorax.
        {'name': 'abdomen', 'parent': 'body', 'pivot': [0, body_y, build['abdomen_z']], 'cubes': [
            {
                'origin': [-ax / 2, body_y - ay / 2, build['abdomen_z']],
                'size': [ax, ay, az],
                'uv': sheet.slot((ax, ay, az), 'abdomen'),
            },
            # The hump the marking sits on. An abdomen that is one flat-topped box takes the stripe
            # across a surface nobody sees from the front.
            {
                'origin': [-(ax - 3) / 2, body_y + ay / 2 - 1, build['abdomen_z'] + 1],
                'size': [ax - 3, 3, az - 3],
                'uv': sheet.slot((ax - 3, 3, az - 3), 'abdomen'),
            },
        ]},
        {'name': 'spinneret', 'parent': 'abdomen',
         'pivot': [0, body_y, build['abdomen_z'] + az], 'cubes': [{
             'origin': [-build['spinneret'][0] / 2, body_y - build['spinneret'][1] / 2,
                        build['abdomen_z'] + az],
             'size': list(build['spinneret']),
             'uv': sheet.slot(build['spinneret'], 'spinneret'),
         }]},
        {'name': 'head', 'parent': 'body', 'pivot': [0, body_y, -bz / 2], 'cubes': [{
            'origin': [-hx / 2, body_y - hy / 2, -bz / 2 - hz],
            'size': [hx, hy, hz],
            'uv': sheet.slot((hx, hy, hz), 'head'),
        }]},
    ]

    # The eye cluster is its own bone so `idle` can rotate all eight together — the cheapest tell
    # that something is alive and looking at you, and the bone the glow layer is really for.
    ex, ey, ez = build['eye']
    # A build may enlarge the upper eye of named rows: a hunting spider's anterior pair really is
    # bigger than the other six. Enlarging all eight instead lights a face's worth of texels, which
    # reads as a glowing box rather than a stare — BestiaryAuditTest caps emissive area for that
    # reason, and caught exactly this.
    major = set(build.get('eye_major', ()))
    major_size = build.get('eye_major_size', build['eye'])
    eyes = []
    for i, (ox, oy) in enumerate(build['eye_rows']):
        for j, (dx, dy) in enumerate(((0, 0), (0.0, -1.2))):
            sx, sy, sz = major_size if (j == 0 and i in major) else (ex, ey, ez)
            eyes.append({
                'origin': [round(ox + dx - sx / 2, 3), round(body_y + oy + dy - sy / 2, 3),
                           round(-bz / 2 - hz - sz, 3)],
                'size': [sx, sy, sz],
                'uv': sheet.slot((sx, sy, sz), 'eye'),
            })
    bones.append({'name': 'eyes', 'parent': 'head',
                  'pivot': [0, body_y + 1, -bz / 2 - hz], 'cubes': eyes})

    fx, fy, fz = build['fang']
    for side, sx in (('left', 1), ('right', -1)):
        bones.append({
            'name': f'fang_{side}',
            'parent': 'head',
            'pivot': [sx * fx, body_y - hy / 2, -bz / 2 - hz],
            'cubes': [{
                'origin': [sx * fx - fx / 2, body_y - hy / 2 - fy, -bz / 2 - hz],
                'size': [fx, fy, fz],
                'uv': sheet.slot((fx, fy, fz), 'fang'),
            }],
        })

    # Two segments each, because the palps are what the idle twitch is spent on and a single box
    # waving is the tell that nothing underneath it is articulated.
    px, py_, pz_ = build['palp']
    tip = max(1, pz_ - 1)
    for side, sx in (('left', 1), ('right', -1)):
        root_x = sx * (hx / 2)
        root_y = body_y - 1
        root_z = -bz / 2 - hz
        bones.append({
            'name': f'palp_{side}',
            'parent': 'head',
            'pivot': [root_x, root_y, root_z],
            'rotation': [-18.0, sx * 12.0, 0],
            'cubes': [
                {
                    'origin': [root_x - px / 2, root_y - py_ / 2, root_z - pz_],
                    'size': [px, py_, pz_],
                    'uv': sheet.slot((px, py_, pz_), 'palp'),
                },
                # The second segment is shorter and turns down, so the pair reads as folded in front
                # of the fangs. Equal-length segments made a pedipalp as long as the head, which put
                # two bars out in front of the spider and was the first thing the eye went to.
                {
                    'origin': [root_x - px / 2, round(root_y - py_ / 2 - tip, 3),
                               round(root_z - pz_ - px, 3)],
                    'size': [px, tip, px],
                    'uv': sheet.slot((px, tip, px), 'palp'),
                },
            ],
        })

    # An empty parent for the eight legs. It costs one bone and buys `climb` and `jump` the ability
    # to pitch the whole stance at once without touching the body's own rotation.
    bones.append({'name': 'legs', 'parent': 'body', 'pivot': [0, body_y, 0]})
    spread = build['leg_spread']
    for side in ('l', 'r'):
        sx = 1 if side == 'l' else -1
        for row, zoff in enumerate(build['leg_rows']):
            bones += leg_bones(build, sheet, side, row, (sx * spread, body_y, zoff),
                               f'leg_{side}{row + 1}')

    reach = build['leg_spread'] + sum(length for length, _ in segment_plan(build))
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': f'geometry.{name}',
                'texture_width': size,
                'texture_height': size,
                'visible_bounds_width': round(reach / 6.0, 2),
                'visible_bounds_height': round((body_y + ay) / 6.0, 2),
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': bones,
        }],
    }
    return geo, sheet

# ---------------------------------------------------------------------------- gait

# Per-leg gait parameters. `freq` is sub-steps per one-second loop and it is the whole reason this
# table is written out rather than derived from a phase offset: front and rear legs take one long
# stride while the middle pairs take three short ones, and that mismatch is what reads as an insect
# rather than as a machine. Base phases are the alternating tetrapod — l1/r2/l3/r4 against
# r1/l2/r3/l4 — with a small per-leg offset so no two legs ever land on the same frame.
GAIT = {
    'leg_l1': {'freq': 1, 'phase': 0.00, 'yaw': 26, 'lift': 15, 'reach': 0.8},
    'leg_l2': {'freq': 3, 'phase': 0.53, 'yaw': 15, 'lift': 10, 'reach': 0.4},
    'leg_l3': {'freq': 3, 'phase': 0.07, 'yaw': 16, 'lift': 11, 'reach': 0.4},
    'leg_l4': {'freq': 1, 'phase': 0.58, 'yaw': 22, 'lift': 13, 'reach': 0.7},
    'leg_r1': {'freq': 1, 'phase': 0.52, 'yaw': 25, 'lift': 14, 'reach': 0.8},
    'leg_r2': {'freq': 3, 'phase': 0.11, 'yaw': 16, 'lift': 10, 'reach': 0.4},
    'leg_r3': {'freq': 3, 'phase': 0.61, 'yaw': 15, 'lift': 11, 'reach': 0.4},
    'leg_r4': {'freq': 1, 'phase': 0.05, 'yaw': 23, 'lift': 12, 'reach': 0.7},
}

# A leg spends most of the cycle on the ground pushing back and snaps forward in the rest. Equal
# halves is what a plain sine gives and it is the tell that a walk was generated: real legs have no
# reason to return slowly, and the asymmetry is most of what separates the two.
STANCE = 0.62


def stride(u):
    """Fore/aft position of a foot at cycle fraction `u`, in -1..1. +1 is forward."""
    u %= 1.0
    if u < STANCE:
        # Stance: planted, travelling backwards at a constant rate as the body moves over it.
        return 1.0 - 2.0 * (u / STANCE)
    # Swing: eased forward, so the leg accelerates off the ground and settles onto it.
    s = (u - STANCE) / (1.0 - STANCE)
    return -1.0 + 2.0 * (0.5 - 0.5 * math.cos(math.pi * s))


def lift(u):
    """How far the foot is off the ground at `u`, in 0..1. Zero for the whole stance."""
    u %= 1.0
    if u < STANCE:
        return 0.0
    s = (u - STANCE) / (1.0 - STANCE)
    return math.sin(math.pi * s)


def sample_frames(freq, frames):
    """Frame indices for one loop: dense through each swing, sparse through each stance.

    Keyframe density is where the reference rigs spend their quality — a couple of hundred per clip,
    no easing curves, no expressions. Sampling the swing twice as often is that, arrived at from the
    shape of the motion rather than by keyframing every frame.

    Everything here counts <b>frames</b>, never seconds. A cycle sampled in seconds and then snapped
    to the grid puts its last key past the end of a clip whose length is not itself a whole number of
    frames — which a looping clip pays for once per loop, as a hitch at the seam, and which is
    invisible in the JSON because every individual number looks right.
    """
    out = {0, frames}
    per = frames / freq
    for step in range(freq):
        k = 0.0
        while k <= per:
            out.add(min(frames, round(step * per + k)))
            k += 2 if (k / per) < STANCE else 1
    return sorted(out)


def leg_channels(name, side_sign, frames, scale=1.0, forward=1.0):
    """Rotation and position tracks for one leg over one loop.

    Yaw carries the stride because the bones point sideways — swinging a sideways bone forward is a
    rotation about Y, and animating X the way a biped's leg would is why the previous walk looked
    like rowing. Roll lifts, pitch adds a small twist so the eight are never identical, and the
    position track slides the foot a little: a leg that only rotates reads as hinged to the body.
    """
    g = GAIT[name]
    rot, pos = {}, {}
    for f in sample_frames(g['freq'], frames):
        # freq is a whole number of sub-steps per loop, so f=0 and f=frames land on the same phase
        # and the loop closes exactly rather than nearly.
        u = (f / frames) * g['freq'] + g['phase']
        s = stride(u)
        l = lift(u)
        t = str(snap(f / FPS))
        rot[t] = r3([
            4.0 * l * s * scale,
            side_sign * g['yaw'] * s * scale * forward,
            -side_sign * g['lift'] * l * scale,
        ])
        pos[t] = r3([
            side_sign * g['reach'] * 0.35 * l * scale,
            g['reach'] * 0.9 * l * scale,
            -g['reach'] * s * scale * forward,
        ])
    return {'rotation': rot, 'position': pos}


def walk_clip(scale=1.0, forward=1.0, frames=24):
    """The gait. Legs desynchronised per GAIT, body bobbing at twice the stride frequency."""
    bones = {}
    for name in LEGS:
        bones[name] = leg_channels(name, 1 if '_l' in name else -1, frames, scale, forward)

    body_rot, body_pos = {}, {}
    for f in range(0, frames + 1, 2):
        t = str(snap(f / FPS))
        ph = f / frames
        # Twice the leg frequency: the body rises once per pair of legs planting, which is what
        # couples the bob to the gait instead of leaving it a separate wobble on top.
        body_pos[t] = r3([
            0.35 * math.sin(2 * math.pi * ph) * scale,
            0.45 * abs(math.sin(2 * math.pi * ph)) * scale,
            0,
        ])
        body_rot[t] = r3([
            1.5 * math.sin(4 * math.pi * ph) * scale,
            2.0 * math.sin(2 * math.pi * ph) * scale,
            2.5 * math.sin(2 * math.pi * ph) * scale,
        ])
    bones['body'] = {'rotation': body_rot, 'position': body_pos}
    bones['abdomen'] = {'rotation': {
        str(snap(f / FPS)): r3([2.0 * math.sin(2 * math.pi * f / frames), 0,
                                3.0 * math.sin(2 * math.pi * f / frames)])
        for f in range(0, frames + 1, 4)}}
    return {'loop': True, 'animation_length': snap(frames / FPS), 'bones': bones}


def idle_clip():
    """Breathing, and the small movements that keep a still enemy from reading as a prop.

    The abdomen scales rather than rotates — a rotating abdomen swings, and what an abdomen does at
    rest is swell. The palps and the eye cluster carry the rest: eight eyes rolling a couple of
    degrees is most of what makes a spider unpleasant to stand near.
    """
    breathe = {}
    for k in range(0, 73, 6):
        t = snap(k / FPS)
        p = math.sin(2 * math.pi * k / 72)
        breathe[str(t)] = r3([1 + 0.045 * p, 1 + 0.055 * p, 1 + 0.04 * p])
    return {
        'loop': True,
        'animation_length': 3.0,
        'bones': {
            'abdomen': {
                'scale': breathe,
                'rotation': {'0.0': [0, 0, 0], '1.5': [2.5, 0, 0], '3.0': [0, 0, 0]},
            },
            'body': {
                'position': {'0.0': [0, 0, 0], '1.5': [0, 0.25, 0], '3.0': [0, 0, 0]},
                'scale': {'0.0': [1, 1, 1], '1.5': [1.015, 1.01, 1.0], '3.0': [1, 1, 1]},
            },
            'eyes': {'rotation': {
                '0.0': [0, 0, 0], '0.75': [0, -6, 0], '1.25': [0, -6, 0],
                '1.75': [3, 7, 0], '2.25': [3, 7, 0], '3.0': [0, 0, 0]}},
            'palp_left': {'rotation': {
                '0.0': [0, 0, 0], '0.5': [-14, 6, 0], '0.9': [-4, 2, 0],
                '1.9': [-17, 9, 0], '2.4': [-3, 0, 0], '3.0': [0, 0, 0]}},
            'palp_right': {'rotation': {
                '0.0': [0, 0, 0], '0.6': [-16, -7, 0], '1.0': [-5, -2, 0],
                '2.0': [-12, -5, 0], '2.5': [-2, 0, 0], '3.0': [0, 0, 0]}},
            'head': {'rotation': {
                '0.0': [0, 0, 0], '1.0': [0, 4, 1], '2.0': [1, -5, -1], '3.0': [0, 0, 0]}},
            # Only the front legs fidget at rest: all eight would read as walking on the spot.
            'leg_l1': {'rotation': {'0.0': [0, 0, 0], '1.1': [0, 5, -7], '1.6': [0, 1, -2],
                                    '3.0': [0, 0, 0]}},
            'leg_r1': {'rotation': {'0.0': [0, 0, 0], '2.1': [0, -6, 7], '2.6': [0, -1, 2],
                                    '3.0': [0, 0, 0]}},
        },
    }


def attack_clip():
    """Rear back onto the hind legs, then throw the whole body forward behind the fangs."""
    return {
        'loop': False,
        'animation_length': 0.7,
        'bones': {
            'body': {
                'rotation': {'0.0': [0, 0, 0], '0.21': [-26, 0, 0], '0.33': [18, 0, 0],
                             '0.46': [-4, 0, 0], '0.7': [0, 0, 0]},
                'position': {'0.0': [0, 0, 0], '0.21': [0, 2.5, 1.5], '0.33': [0, -0.5, -3.5],
                             '0.7': [0, 0, 0]},
            },
            'head': {'rotation': {'0.0': [0, 0, 0], '0.21': [14, 0, 0], '0.33': [-22, 0, 0],
                                  '0.7': [0, 0, 0]}},
            'fang_left': {'rotation': {'0.0': [0, 0, 0], '0.17': [-38, 0, -12],
                                       '0.33': [26, 0, 6], '0.7': [0, 0, 0]}},
            'fang_right': {'rotation': {'0.0': [0, 0, 0], '0.17': [-38, 0, 12],
                                        '0.33': [26, 0, -6], '0.7': [0, 0, 0]}},
            'palp_left': {'rotation': {'0.0': [0, 0, 0], '0.21': [-34, 20, 0], '0.7': [0, 0, 0]}},
            'palp_right': {'rotation': {'0.0': [0, 0, 0], '0.21': [-34, -20, 0], '0.7': [0, 0, 0]}},
            'abdomen': {'rotation': {'0.0': [0, 0, 0], '0.21': [16, 0, 0], '0.38': [-10, 0, 0],
                                     '0.7': [0, 0, 0]}},
            # The front legs come off the floor with the rear-up; the back two take the weight.
            'leg_l1': {'rotation': {'0.0': [0, 0, 0], '0.21': [0, -18, -34], '0.7': [0, 0, 0]}},
            'leg_r1': {'rotation': {'0.0': [0, 0, 0], '0.21': [0, 18, 34], '0.7': [0, 0, 0]}},
            'leg_l2': {'rotation': {'0.0': [0, 0, 0], '0.21': [0, -10, -20], '0.7': [0, 0, 0]}},
            'leg_r2': {'rotation': {'0.0': [0, 0, 0], '0.21': [0, 10, 20], '0.7': [0, 0, 0]}},
            'leg_l4': {'rotation': {'0.0': [0, 0, 0], '0.25': [0, 6, 14], '0.7': [0, 0, 0]}},
            'leg_r4': {'rotation': {'0.0': [0, 0, 0], '0.25': [0, -6, -14], '0.7': [0, 0, 0]}},
        },
    }


def shoot_clip():
    """Abdomen up and forward over the body, spinneret aimed — the web spit.

    Also the queen's ceiling-web clip: SpiderCeilingWebGoal fires Action.SHOOT for each strand, so
    what this has to read as is "webbing leaves the spinneret", not "the enemy is facing you".
    """
    return {
        'loop': False,
        'animation_length': 0.8,
        'bones': {
            'body': {
                'rotation': {'0.0': [0, 0, 0], '0.25': [16, 0, 0], '0.46': [-8, 0, 0],
                             '0.8': [0, 0, 0]},
                'position': {'0.0': [0, 0, 0], '0.25': [0, -1, 1], '0.8': [0, 0, 0]},
            },
            'abdomen': {
                'rotation': {'0.0': [0, 0, 0], '0.29': [-46, 0, 0], '0.42': [-52, 0, 0],
                             '0.63': [-20, 0, 0], '0.8': [0, 0, 0]},
                'scale': {'0.0': [1, 1, 1], '0.25': [1.12, 1.12, 1.1], '0.42': [0.9, 0.9, 0.92],
                          '0.8': [1, 1, 1]},
            },
            'spinneret': {
                'rotation': {'0.0': [0, 0, 0], '0.33': [22, 0, 0], '0.8': [0, 0, 0]},
                'scale': {'0.0': [1, 1, 1], '0.38': [1.4, 1.4, 1.5], '0.54': [0.85, 0.85, 0.8],
                          '0.8': [1, 1, 1]},
            },
            'head': {'rotation': {'0.0': [0, 0, 0], '0.25': [-12, 0, 0], '0.8': [0, 0, 0]}},
            'eyes': {'rotation': {'0.0': [0, 0, 0], '0.29': [-8, 0, 0], '0.8': [0, 0, 0]}},
            # The stance widens to brace: an abdomen thrown over the back has to be paid for.
            'leg_l1': {'rotation': {'0.0': [0, 0, 0], '0.29': [0, -12, -16], '0.8': [0, 0, 0]}},
            'leg_r1': {'rotation': {'0.0': [0, 0, 0], '0.29': [0, 12, 16], '0.8': [0, 0, 0]}},
            'leg_l4': {'rotation': {'0.0': [0, 0, 0], '0.29': [0, 14, 18], '0.8': [0, 0, 0]}},
            'leg_r4': {'rotation': {'0.0': [0, 0, 0], '0.29': [0, -14, -18], '0.8': [0, 0, 0]}},
        },
    }


def climb_clip(frames=18):
    """The wall loop: body pitched into the surface, legs reaching over it in two alternating sets.

    Faster than the walk on purpose — a climber that ascends at walking pace looks like it is being
    dragged up, and the ledges this enemy exists to contest are only contested if it gets there.
    """
    end = snap(frames / FPS)
    half = snap((frames // 2) / FPS)
    bones = {}
    for i, name in enumerate(LEGS):
        side = 1 if '_l' in name else -1
        # Two sets, not eight phases: on a wall the legs that matter are the ones reaching, and the
        # alternation has to be legible from below.
        phase = 0.0 if (i % 4) in (0, 2) else 0.5
        rot, pos = {}, {}
        for f in range(frames + 1):
            u = (f / frames + phase) % 1.0
            t = str(snap(f / FPS))
            rot[t] = r3([-34 * lift(u), side * 20 * stride(u), -side * (16 + 22 * lift(u))])
            pos[t] = r3([0, 1.1 * lift(u), -0.9 * stride(u)])
        bones[name] = {'rotation': rot, 'position': pos}
    bones['body'] = {
        'rotation': {'0.0': [-52, 0, 0], str(half): [-56, 0, 0], str(end): [-52, 0, 0]},
        'position': {'0.0': [0, 0, 0], str(snap(4 / FPS)): [0, 0.4, -0.3],
                     str(snap(13 / FPS)): [0, -0.4, 0.3], str(end): [0, 0, 0]},
    }
    bones['abdomen'] = {'rotation': {'0.0': [12, 0, 0], str(half): [16, 0, 3],
                                     str(end): [12, 0, 0]}}
    bones['head'] = {'rotation': {'0.0': [22, 0, 0], str(end): [22, 0, 0]}}
    return {'loop': True, 'animation_length': end, 'bones': bones}


def jump_clip():
    """The pounce: gather onto every leg, extend, tuck airborne, then absorb the landing.

    Non-looping and longer than the goal's 12-tick trigger, because what has to read is the
    telegraph — SpiderPounceGoal exists so the leap is a decision the party can answer.
    """
    bones = {
        'body': {
            'rotation': {'0.0': [0, 0, 0], '0.21': [22, 0, 0], '0.38': [-30, 0, 0],
                         '0.63': [-12, 0, 0], '0.83': [10, 0, 0], '1.0': [0, 0, 0]},
            'position': {'0.0': [0, 0, 0], '0.21': [0, -2.5, 1.5], '0.42': [0, 3, -2],
                         '0.71': [0, 1.5, -1], '0.83': [0, -1.5, 0], '1.0': [0, 0, 0]},
            'scale': {'0.0': [1, 1, 1], '0.21': [1.08, 0.88, 1.06], '0.42': [0.94, 1.12, 0.96],
                      '0.83': [1.06, 0.92, 1.04], '1.0': [1, 1, 1]},
        },
        'abdomen': {'rotation': {'0.0': [0, 0, 0], '0.21': [-14, 0, 0], '0.46': [24, 0, 0],
                                 '0.79': [-8, 0, 0], '1.0': [0, 0, 0]}},
        'head': {'rotation': {'0.0': [0, 0, 0], '0.21': [10, 0, 0], '0.38': [-18, 0, 0],
                              '1.0': [0, 0, 0]}},
        'fang_left': {'rotation': {'0.0': [0, 0, 0], '0.38': [-32, 0, -10], '1.0': [0, 0, 0]}},
        'fang_right': {'rotation': {'0.0': [0, 0, 0], '0.38': [-32, 0, 10], '1.0': [0, 0, 0]}},
    }
    for i, name in enumerate(LEGS):
        side = 1 if '_l' in name else -1
        front = (i % 4) < 2
        gather = -40 if front else -24
        extend = 34 if front else 46
        bones[name] = {'rotation': {
            '0.0': [0, 0, 0],
            # Crouch: every leg folds under the body at once. This is the telegraph.
            '0.21': [0, side * (-14 if front else 10), -side * gather],
            # Extension: the push that launches it.
            '0.38': [0, side * (18 if front else -12), -side * extend],
            # Airborne tuck, forelegs reaching for what it is about to land on.
            '0.58': [0, side * (-8 if front else 6), -side * (10 if front else 30)],
            '0.83': [0, 0, -side * (26 if front else 16)],
            '1.0': [0, 0, 0],
        }}
    return {'loop': False, 'animation_length': 1.0, 'bones': bones}


def cast_clip():
    """The summon: reared up, abdomen pumping, legs splayed wide. The queen laying her brood.

    Fired through Action.CAST when the SUMMON ability trips, so it has to hold for the whole
    telegraph rather than snapping — a boss that spawns adds instantly reads as a bug.
    """
    bones = {
        'body': {
            'rotation': {'0.0': [0, 0, 0], '0.25': [-34, 0, 0], '0.75': [-38, 0, 0],
                         '1.08': [-30, 0, 0], '1.4': [0, 0, 0]},
            'position': {'0.0': [0, 0, 0], '0.25': [0, 3.5, 2], '0.75': [0, 4, 2.5],
                         '1.4': [0, 0, 0]},
        },
        'abdomen': {
            'rotation': {'0.0': [0, 0, 0], '0.25': [26, 0, 0], '0.5': [34, 0, 0],
                         '0.75': [26, 0, 0], '1.0': [32, 0, 0], '1.4': [0, 0, 0]},
            # The pumping is the ability: three swells, one per add the shipped table spawns.
            'scale': {'0.0': [1, 1, 1], '0.33': [1.22, 1.2, 1.24], '0.5': [0.94, 0.95, 0.92],
                      '0.71': [1.2, 1.18, 1.22], '0.88': [0.95, 0.96, 0.93],
                      '1.08': [1.18, 1.16, 1.2], '1.25': [0.97, 0.98, 0.96], '1.4': [1, 1, 1]},
        },
        'spinneret': {'scale': {'0.0': [1, 1, 1], '0.42': [1.5, 1.5, 1.6], '0.63': [1, 1, 1],
                                '0.79': [1.45, 1.45, 1.55], '1.0': [1, 1, 1], '1.4': [1, 1, 1]}},
        'head': {'rotation': {'0.0': [0, 0, 0], '0.25': [24, 0, 0], '0.75': [28, 0, 0],
                              '1.4': [0, 0, 0]}},
        'eyes': {'rotation': {'0.0': [0, 0, 0], '0.33': [-10, 0, 0], '1.0': [-10, 0, 0],
                              '1.4': [0, 0, 0]}},
        'fang_left': {'rotation': {'0.0': [0, 0, 0], '0.29': [-42, 0, -16], '1.0': [-38, 0, -14],
                                   '1.4': [0, 0, 0]}},
        'fang_right': {'rotation': {'0.0': [0, 0, 0], '0.29': [-42, 0, 16], '1.0': [-38, 0, 14],
                                    '1.4': [0, 0, 0]}},
        'palp_left': {'rotation': {'0.0': [0, 0, 0], '0.29': [-46, 26, 0], '0.83': [-30, 18, 0],
                                   '1.4': [0, 0, 0]}},
        'palp_right': {'rotation': {'0.0': [0, 0, 0], '0.29': [-46, -26, 0], '0.83': [-30, -18, 0],
                                    '1.4': [0, 0, 0]}},
    }
    for i, name in enumerate(LEGS):
        side = 1 if '_l' in name else -1
        front = (i % 4) < 2
        splay = 40 if front else 22
        bones[name] = {'rotation': {
            '0.0': [0, 0, 0],
            '0.25': [0, side * (-22 if front else 14), -side * splay],
            '0.63': [0, side * (-26 if front else 18), -side * (splay + 8)],
            '1.0': [0, side * (-20 if front else 12), -side * splay],
            '1.4': [0, 0, 0],
        }}
    return {'loop': False, 'animation_length': 1.4, 'bones': bones}


def on_grid(clips):
    """Snaps every keyframe onto the 24 fps grid and refuses one past its clip's end.

    The sampled clips are already frame-exact by construction; the hand-authored ones (idle, attack,
    the queen's cast) are written in readable seconds and would otherwise sit between frames. Doing
    it here rather than at each call site is what makes "every keyframe is on the grid" a property of
    the file instead of a claim in a docstring.

    A key past the end is a hard error, not a snap. It means a clip length and its sampling disagree,
    and a looping clip pays for that at the seam once per loop — the kind of fault that is invisible
    in the JSON because every individual number looks correct.
    """
    for name, clip in clips.items():
        length = snap(clip['animation_length'])
        clip['animation_length'] = length
        for bone, channels in clip.get('bones', {}).items():
            for kind, track in list(channels.items()):
                if not isinstance(track, dict):
                    continue
                snapped = {}
                for t, value in sorted(track.items(), key=lambda kv: float(kv[0])):
                    at = snap(float(t))
                    if at > length + 1e-9:
                        raise ValueError(
                            f'{name}.{bone}.{kind}: key at {at} is past the clip end {length}')
                    snapped[str(at)] = value
                channels[kind] = snapped
    return clips


def arachnid_animation(clips):
    return {'format_version': '1.8.0', 'animations': on_grid(clips)}

# ---------------------------------------------------------------------------- silverfish rig

def lepisma_geo():
    """A silverfish: a low segmented body, six short legs, antennae, three tail filaments.

    Deliberately not the spider skeleton. It was on it because the rig existed, and a GROUND-moving
    cave scavenger sharing an anatomy with the climbing arachnids is what made the swarm read as
    small spiders — which flattened the one contrast between Cuevas and Infestadas.
    """
    sheet = Sheet(64, 64)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 2, 0]},
    ]
    # Three tapering segments. The taper is the silhouette: a silverfish is a wedge, and three
    # equal boxes read as a caterpillar.
    segs = [('thorax', 5, 3, 5, -4), ('mid', 4, 3, 5, 1), ('tail', 3, 2, 5, 6)]
    parent = 'body'
    for name, sx, sy, sz, z in segs:
        bones.append({
            'name': name, 'parent': parent, 'pivot': [0, 2, z],
            'cubes': [{
                'origin': [-sx / 2, 2 - sy / 2, z],
                'size': [sx, sy, sz],
                'uv': sheet.slot((sx, sy, sz), 'segment'),
            }],
        })
        parent = name
    bones.append({
        'name': 'head', 'parent': 'body', 'pivot': [0, 2, -4],
        'cubes': [{'origin': [-2, 1, -7], 'size': [4, 3, 3], 'uv': sheet.slot((4, 3, 3), 'head')}],
    })
    bones.append({
        'name': 'eyes', 'parent': 'head', 'pivot': [0, 2.5, -7],
        'cubes': [
            {'origin': [-1.5, 2, -7.5], 'size': [1, 1, 1], 'uv': sheet.slot((1, 1, 1), 'eye')},
            {'origin': [0.5, 2, -7.5], 'size': [1, 1, 1], 'uv': sheet.slot((1, 1, 1), 'eye')},
        ],
    })
    for side, sx in (('left', 1), ('right', -1)):
        bones.append({
            'name': f'antenna_{side}', 'parent': 'head', 'pivot': [sx, 3, -7],
            'cubes': [{
                'origin': [sx - 0.5, 3, -11], 'size': [1, 1, 4],
                'uv': sheet.slot((1, 1, 4), 'antenna'),
                'pivot': [sx, 3, -7], 'rotation': [-8.0, sx * 18.0, 0],
            }],
        })
    # Three filaments, which is the detail that names the animal.
    for i, (dx, yaw) in enumerate(((-1.5, -20.0), (0, 0.0), (1.5, 20.0))):
        bones.append({
            'name': f'filament_{i}', 'parent': 'tail', 'pivot': [dx, 2, 11],
            'cubes': [{
                'origin': [dx - 0.5, 1.5, 11], 'size': [1, 1, 4],
                'uv': sheet.slot((1, 1, 4), 'antenna'),
                'pivot': [dx, 2, 11], 'rotation': [6.0, yaw, 0],
            }],
        })
    for side, sx in (('l', 1), ('r', -1)):
        for row, z in enumerate((-3, 0, 3)):
            # Splay and bend both on the bone, and no cube rotation anywhere: one segment per leg,
            # so there is no joint to come apart, and the gait's yaw adds to the rest pose rather
            # than replacing it. The bend goes through swing_to_json for the same reason the
            # arachnids' does — authored "down" is applied as "up" without it.
            # Angled so the foot lands exactly on y=0, the same rule tibia_length applies to the
            # arachnids. At a flat -28 degrees the swarm stood a block and a bit into the floor.
            drop = math.degrees(math.asin(max(-1.0, min(1.0, -1.0 / 4.0))))
            bones.append({
                'name': f'leg_{side}{row + 1}', 'parent': 'thorax', 'pivot': [sx * 2, 1, z],
                'rotation': [0, sx * {0: 22.0, 1: 0.0, 2: -22.0}[row], swing_to_json(drop, sx)],
                'cubes': [{
                    'origin': [sx * 2 if sx > 0 else sx * 2 - 4, 0.5, z - 0.5],
                    'size': [4, 1, 1],
                    'uv': sheet.slot((4, 1, 1), 'leg'),
                }],
            })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_lepisma',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 2, 'visible_bounds_height': 1,
                'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': bones,
        }],
    }, sheet


LEPISMA_LEGS = [f'leg_{s}{i}' for s in ('l', 'r') for i in (1, 2, 3)]


def lepisma_animation():
    """idle / walk / attack. A silverfish has no ranged move and does not climb, so it has no more.

    The walk is the same desynchronised gait the arachnids use, at six legs and a much higher
    frequency: what makes a silverfish unnerving is that it moves in bursts, faster than its size
    suggests it should.
    """
    # Twelve frames — half a second — with three leg sub-steps inside it. Whole frames, so the loop
    # closes on the grid; see sample_frames for what happens when a clip length is not one.
    frames, substeps = 12, 3
    walk_bones = {}
    for i, name in enumerate(LEPISMA_LEGS):
        side = 1 if '_l' in name else -1
        phase = (0.0 if i % 2 == 0 else 0.5) + 0.11 * i
        rot, pos = {}, {}
        for f in range(frames + 1):
            u = (f / frames * substeps + phase) % 1.0
            t = str(snap(f / FPS))
            rot[t] = r3([0, side * 24 * stride(u), -side * (28 + 16 * lift(u))])
            pos[t] = r3([0, 0.5 * lift(u), -0.4 * stride(u)])
        walk_bones[name] = {'rotation': rot, 'position': pos}
    # The body whips side to side — the fish part of silverfish, and the reason it is not a beetle.
    # Each segment lags the one in front, which is what turns a sway into a ripple.
    for bone, amp, lag in (('body', 7, 0.0), ('mid', -9, 0.6), ('tail', -13, 1.2)):
        walk_bones[bone] = {'rotation': {
            str(snap(f / FPS)): r3([0, amp * math.sin(2 * math.pi * f / frames - lag), 0])
            for f in range(frames + 1)}}

    return arachnid_animation({
        'walk': {'loop': True, 'animation_length': snap(frames / FPS), 'bones': walk_bones},
        'idle': {
            'loop': True,
            'animation_length': 2.4,
            'bones': {
                'body': {'rotation': {'0.0': [0, 0, 0], '1.2': [0, 3, 0], '2.4': [0, 0, 0]},
                         'scale': {'0.0': [1, 1, 1], '1.2': [1.02, 1.04, 1.0], '2.4': [1, 1, 1]}},
                'tail': {'rotation': {'0.0': [0, 0, 0], '0.8': [0, -7, 0], '1.7': [0, 6, 0],
                                      '2.4': [0, 0, 0]}},
                'mid': {'rotation': {'0.0': [0, 0, 0], '1.0': [0, 4, 0], '2.4': [0, 0, 0]}},
                'antenna_left': {'rotation': {'0.0': [0, 0, 0], '0.5': [-12, 14, 0],
                                              '1.3': [4, -6, 0], '2.4': [0, 0, 0]}},
                'antenna_right': {'rotation': {'0.0': [0, 0, 0], '0.7': [-14, -12, 0],
                                               '1.6': [3, 7, 0], '2.4': [0, 0, 0]}},
                'head': {'rotation': {'0.0': [0, 0, 0], '1.2': [0, -5, 0], '2.4': [0, 0, 0]}},
            },
        },
        'attack': {
            'loop': False,
            'animation_length': 0.5,
            'bones': {
                'body': {
                    'rotation': {'0.0': [0, 0, 0], '0.13': [-16, 0, 0], '0.25': [14, 0, 0],
                                 '0.5': [0, 0, 0]},
                    'position': {'0.0': [0, 0, 0], '0.13': [0, 0.5, 1.5], '0.25': [0, 0, -2.5],
                                 '0.5': [0, 0, 0]},
                },
                'head': {'rotation': {'0.0': [0, 0, 0], '0.13': [12, 0, 0], '0.25': [-20, 0, 0],
                                      '0.5': [0, 0, 0]}},
                'antenna_left': {'rotation': {'0.0': [0, 0, 0], '0.13': [-26, 22, 0],
                                              '0.5': [0, 0, 0]}},
                'antenna_right': {'rotation': {'0.0': [0, 0, 0], '0.13': [-26, -22, 0],
                                               '0.5': [0, 0, 0]}},
                'tail': {'rotation': {'0.0': [0, 0, 0], '0.19': [0, 12, 0], '0.5': [0, 0, 0]}},
            },
        },
    })

# ---------------------------------------------------------------------------- biped rig

# The bone contract the upright rigs honour, for the same reason the arachnids have one: the clip
# generators address bones by name and the entity's controller plays clips by name, so a rig that
# renames a bone silently stops animating the part it renamed.
#
# These names are the ones the shipped `dungeon_guardian` rig already used. Keeping them is not
# nostalgia — an animation file and a model that disagree about a bone produce no error anywhere,
# just a limb that never moves.
BIPED_BONES = ['root', 'body', 'head', 'arm_left', 'arm_right', 'leg_left', 'leg_right']

# Three builds on that contract. Every humanoid in the bestiary was one 8x12x4 torso with 4x12x4
# limbs — the vanilla player box — worn by seven variants that differed only in texture and scale,
# which is the four-spiders-one-mesh problem with more enemies riding it: the cave raider, the crypt
# guardian and both bosses were the same body at four zoom levels.
#
# `hunch` is most of what separates the first two before anything moves. A scavenger leans into its
# walk; an armoured guardian does not, because armour does not let it.
BIPEDS = {
    'raider': {
        # Cave scavengers: narrow, long-armed, leaning forward. The head is the narrowest part of
        # the silhouette and the shoulder wrap the widest, so the figure tapers upward — which is
        # most of what makes it read as smaller than a guardian at the same distance.
        'torso': (7, 10, 4), 'hip': 11,
        'arm': (3, 11, 3), 'leg': (3, 11, 3),
        'head': (6, 6, 6), 'brow': (7, 2, 7),
        'plate': (9, 2, 5),
        'hunch': 12.0, 'head_lift': -9.0, 'stance': 2, 'arm_gap': 0,
        'shards': (),
        'sheet': 64,
        'walk_frames': 20, 'leg_swing': 42.0, 'arm_swing': 34.0, 'bob': 0.6,
    },
    'guardian': {
        # The crypt's heavies. Wide where the raider is narrow: pauldrons a third again as broad as
        # the chest, short legs under a deep torso. Height alone does not read as heavy — the old
        # rig was this tall and read as a tall man — so the mass is in the ratios.
        'torso': (12, 12, 7), 'hip': 11,
        'arm': (5, 13, 5), 'leg': (5, 11, 6),
        'head': (8, 7, 7), 'brow': (11, 3, 10),
        'plate': (16, 4, 9),
        'hunch': 4.0, 'head_lift': -2.0, 'stance': 3.5, 'arm_gap': 1,
        'shards': (),
        'sheet': 128,
        'walk_frames': 32, 'leg_swing': 30.0, 'arm_swing': 22.0, 'bob': 0.9,
    },
    'golem': {
        # The geode. Stubby legs under a slab of a torso and arms that reach the floor: mass low and
        # forward, so it reads as something that walks through a fight rather than around it.
        'torso': (14, 13, 10), 'hip': 8,
        'arm': (6, 16, 6), 'leg': (5, 8, 7),
        'head': (6, 5, 6), 'brow': (8, 2, 8),
        'plate': (16, 4, 12),
        'hunch': 8.0, 'head_lift': -4.0, 'stance': 4, 'arm_gap': 1,
        # Crystal seams: (centre x, y above the hip, centre z, then the cube's own w, h, d). Each
        # sits flush in a face of the torso — half a block proud of it — rather than near it: a
        # seam floating beside the body is a crystal that has come loose, and one placed at the
        # shoulder's x lands inside the arm and z-fights it.
        #
        # Deliberately one block wide. The emissive pass draws whatever is tagged `shard` at full
        # brightness, and a face's worth of that is a glowing box rather than a glowing crack — the
        # cap the eye sheets are held to. Six thin seams light 108 texels; three fat ones, 360.
        'shards': ((-4, 9, -5.5, 1, 4, 1), (4, 9, -5.5, 1, 4, 1), (0, 10, 5.5, 1, 4, 1),
                   (-7, 4, 0, 1, 4, 1), (7, 4, 0, 1, 4, 1), (0, 2, -5.5, 1, 4, 1)),
        'sheet': 128,
        'walk_frames': 40, 'leg_swing': 22.0, 'arm_swing': 14.0, 'bob': 1.4,
    },
}


def biped_geo(name, build):
    """The shared upright skeleton at one set of proportions."""
    size = build['sheet']
    sheet = Sheet(size, size)
    tw, th, td = build['torso']
    hip = build['hip']
    aw, ah, ad = build['arm']
    lw, lh, ld = build['leg']
    hw, hh, hd = build['head']
    bw, bh, bd = build['brow']
    pw, ph, pd = build['plate']
    shoulder = hip + th

    torso = [
        {'origin': [-tw / 2, hip, -td / 2], 'size': [tw, th, td],
         'uv': sheet.slot((tw, th, td), 'body')},
        # The shoulder plate. One cube, and it is what stops a torso reading as a box: it is wider
        # than the chest, so the arms hang under an overhang rather than off a flat side.
        {'origin': [-pw / 2, shoulder - ph, -pd / 2], 'size': [pw, ph, pd],
         'uv': sheet.slot((pw, ph, pd), 'plate')},
    ]
    for (sx, sy, sz, cw, ch, cd) in build['shards']:
        torso.append({'origin': [sx - cw / 2, hip + sy, sz - cd / 2], 'size': [cw, ch, cd],
                      'uv': sheet.slot((cw, ch, cd), 'shard')})

    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        # The hunch lives on the rest pose rather than in every clip: a lean the animation has to
        # re-state is a lean that disappears the moment a clip forgets it.
        {'name': 'body', 'parent': 'root', 'pivot': [0, hip, 0],
         'rotation': [build['hunch'], 0, 0], 'cubes': torso},
        # `head_lift` takes back part of the hunch. A pitched torso carries the skull with it, so a
        # leaning enemy without this walks looking at its own feet — which reads as defeated rather
        # than as predatory, and is the difference between a stalker and a man who has dropped
        # something.
        {'name': 'head', 'parent': 'body', 'pivot': [0, shoulder, 0],
         'rotation': [build['head_lift'], 0, 0], 'cubes': [
            {'origin': [-hw / 2, shoulder, -hd / 2], 'size': [hw, hh, hd],
             'uv': sheet.slot((hw, hh, hd), 'head')},
            # A brow ridge, sitting proud of the skull. Faces are where a player looks first and a
            # flat cube is where a generated humanoid looks most generated.
            {'origin': [-bw / 2, shoulder + hh - 1, -bd / 2], 'size': [bw, bh, bd],
             'uv': sheet.slot((bw, bh, bd), 'plate')},
        ]},
    ]

    for side, sx in (('left', 1), ('right', -1)):
        ax = sx * (tw / 2 + aw / 2 + build['arm_gap'])
        bones.append({
            'name': f'arm_{side}', 'parent': 'body',
            'pivot': [ax, shoulder - ph, 0],
            'cubes': [{'origin': [ax - aw / 2, shoulder - ph - ah, -ad / 2],
                       'size': [aw, ah, ad], 'uv': sheet.slot((aw, ah, ad), 'arm')}],
        })
        lx = sx * build['stance']
        bones.append({
            # Legs hang off `root`, not off `body`: the hunch is a property of the upper body, and
            # parenting them to a pitched torso would tip the feet off the floor by that angle.
            'name': f'leg_{side}', 'parent': 'root',
            'pivot': [lx, hip, 0],
            'cubes': [{'origin': [lx - lw / 2, hip - lh, -ld / 2],
                       'size': [lw, lh, ld], 'uv': sheet.slot((lw, lh, ld), 'leg')}],
        })

    height = hip + th + hh + bh
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': f'geometry.{name}',
                'texture_width': size,
                'texture_height': size,
                'visible_bounds_width': round(max(pw, tw + 2 * aw) / 6.0, 2),
                'visible_bounds_height': round(height / 6.0, 2),
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': bones,
        }],
    }, sheet


def biped_walk(build):
    """A stride, on the same asymmetric cycle the arachnid gait uses.

    Legs swing about X because they point down — the mirror image of the spiders, whose legs point
    sideways and therefore stride in yaw. What carries over unchanged is {@link STANCE}: 62% planted
    and pushing back, then a snap through. Equal halves is what a plain sine gives, and it is the
    difference between a walk and a metronome.

    Arms counter-swing against the diagonal leg, which is the whole of why a biped walk reads as a
    walk rather than as a shuffle.
    """
    frames = build['walk_frames']
    swing, arm_swing, bob = build['leg_swing'], build['arm_swing'], build['bob']
    bones = {}
    for name, phase, sign in (('leg_left', 0.0, 1), ('leg_right', 0.5, -1),
                              ('arm_right', 0.0, -1), ('arm_left', 0.5, 1)):
        limb = name.startswith('leg')
        amp = swing if limb else -arm_swing
        rot, pos = {}, {}
        for f in sample_frames(1, frames):
            u = f / frames + phase
            t = str(snap(f / FPS))
            rot[t] = r3([amp * stride(u), 0, 0])
            if limb:
                # The foot lifts as well as swings; a leg that only rotates drags its toe through
                # the floor on the way forward.
                pos[t] = r3([0, bob * lift(u), 0])
        bones[name] = {'rotation': rot, 'position': pos} if limb else {'rotation': rot}

    body_rot, body_pos = {}, {}
    for f in range(0, frames + 1, max(1, frames // 8)):
        t = str(snap(f / FPS))
        ph = f / frames
        # Twice the stride frequency: the body rises once per foot planted, which couples the bob to
        # the gait instead of leaving it a wobble laid over the top.
        body_pos[t] = r3([0, bob * 0.5 * abs(math.sin(2 * math.pi * ph)), 0])
        body_rot[t] = r3([1.5 * math.sin(4 * math.pi * ph), 3.0 * math.sin(2 * math.pi * ph), 0])
    bones['body'] = {'rotation': body_rot, 'position': body_pos}
    bones['head'] = {'rotation': {
        str(snap(f / FPS)): r3([0, -2.0 * math.sin(2 * math.pi * f / frames), 0])
        for f in range(0, frames + 1, max(1, frames // 4))}}
    return {'loop': True, 'animation_length': snap(frames / FPS), 'bones': bones}


def biped_idle(build):
    """Breathing, a slow head turn, and arms that hang rather than freeze."""
    length = 4.0 if build['hip'] > 10 else 3.0
    keys = [snap(k / FPS) for k in range(0, int(length * FPS) + 1, 12)]
    breathe, sway_l, sway_r, head = {}, {}, {}, {}
    for i, t in enumerate(keys):
        ph = i / (len(keys) - 1)
        breathe[str(t)] = r3([0, 0.35 * math.sin(2 * math.pi * ph), 0])
        # The two arms are deliberately out of phase with each other and with the breath: three
        # things moving on one period is a mechanism, not a body.
        sway_l[str(t)] = r3([4.0 * math.sin(2 * math.pi * ph), 0, 0])
        sway_r[str(t)] = r3([4.0 * math.sin(2 * math.pi * ph + 2.2), 0, 0])
        head[str(t)] = r3([2.0 * math.sin(2 * math.pi * ph + 1.1),
                           9.0 * math.sin(math.pi * ph), 0])
    return {'loop': True, 'animation_length': snap(length), 'bones': {
        'body': {'position': breathe},
        'head': {'rotation': head},
        'arm_left': {'rotation': sway_l},
        'arm_right': {'rotation': sway_r},
    }}


def biped_attack():
    """One overhead swing. Wind-up, strike, recover — with the body turning into it.

    The turn is what sells the weight: an arm swinging on a torso that never moves is a lever, and
    the shipped clip did exactly that for every humanoid in the bestiary.
    """
    return {'loop': False, 'animation_length': 0.5, 'bones': {
        'arm_right': {'rotation': {'0.0': [0, 0, 0], '0.13': [-155, 0, 0], '0.25': [25, 0, 0],
                                   '0.38': [10, 0, 0], '0.5': [0, 0, 0]}},
        'arm_left': {'rotation': {'0.0': [0, 0, 0], '0.13': [20, 0, 0], '0.25': [-18, 0, 0],
                                  '0.5': [0, 0, 0]}},
        'body': {'rotation': {'0.0': [0, 0, 0], '0.13': [-8, 18, 0], '0.25': [12, -16, 0],
                              '0.5': [0, 0, 0]},
                 'position': {'0.0': [0, 0, 0], '0.25': [0, 0, -1.0], '0.5': [0, 0, 0]}},
        'head': {'rotation': {'0.0': [0, 0, 0], '0.13': [-6, 12, 0], '0.25': [10, -10, 0],
                              '0.5': [0, 0, 0]}},
    }}


def biped_shoot():
    """Draw and loose. The lead arm holds the bow out; the draw arm comes back past the cheek."""
    return {'loop': False, 'animation_length': 0.55, 'bones': {
        'arm_left': {'rotation': {'0.0': [0, 0, 0], '0.13': [92, 0, 0], '0.42': [92, 0, 0],
                                  '0.55': [0, 0, 0]}},
        'arm_right': {'rotation': {'0.0': [0, 0, 0], '0.17': [70, 0, 0], '0.33': [58, 0, 0],
                                   '0.42': [96, 0, 0], '0.55': [0, 0, 0]}},
        'body': {'rotation': {'0.0': [0, 0, 0], '0.17': [0, -14, 0], '0.42': [0, -18, 0],
                              '0.55': [0, 0, 0]}},
        'head': {'rotation': {'0.0': [0, 0, 0], '0.17': [0, 14, 0], '0.42': [0, 16, 0],
                              '0.55': [0, 0, 0]}},
    }}


def biped_cast():
    """Both arms up, then down into the ground. What VOLLEY looks like from outside."""
    return {'loop': False, 'animation_length': 1.0, 'bones': {
        'arm_left': {'rotation': {'0.0': [0, 0, 0], '0.29': [-150, 0, 0], '0.58': [-150, 0, 0],
                                  '0.71': [30, 0, 0], '1.0': [0, 0, 0]}},
        'arm_right': {'rotation': {'0.0': [0, 0, 0], '0.29': [-150, 0, 0], '0.58': [-150, 0, 0],
                                   '0.71': [30, 0, 0], '1.0': [0, 0, 0]}},
        'body': {'rotation': {'0.0': [0, 0, 0], '0.29': [-12, 0, 0], '0.58': [-14, 0, 0],
                              '0.71': [22, 0, 0], '1.0': [0, 0, 0]},
                 'position': {'0.0': [0, 0, 0], '0.58': [0, 1.0, 0], '0.71': [0, -1.5, 0],
                              '1.0': [0, 0, 0]}},
        'head': {'rotation': {'0.0': [0, 0, 0], '0.29': [-18, 0, 0], '0.71': [16, 0, 0],
                              '1.0': [0, 0, 0]}},
    }}


def biped_jump():
    """Crouch, extend, land. The crouch is the telegraph — a leap with no wind-up is a teleport."""
    return {'loop': False, 'animation_length': 0.75, 'bones': {
        'body': {'rotation': {'0.0': [0, 0, 0], '0.17': [16, 0, 0], '0.33': [-14, 0, 0],
                              '0.58': [8, 0, 0], '0.75': [0, 0, 0]},
                 'position': {'0.0': [0, 0, 0], '0.17': [0, -1.6, 0], '0.33': [0, 1.2, 0],
                              '0.58': [0, -0.8, 0], '0.75': [0, 0, 0]}},
        'leg_left': {'rotation': {'0.0': [0, 0, 0], '0.17': [-26, 0, 0], '0.33': [34, 0, 0],
                                  '0.58': [-14, 0, 0], '0.75': [0, 0, 0]}},
        'leg_right': {'rotation': {'0.0': [0, 0, 0], '0.17': [-26, 0, 0], '0.33': [22, 0, 0],
                                   '0.58': [-18, 0, 0], '0.75': [0, 0, 0]}},
        'arm_left': {'rotation': {'0.0': [0, 0, 0], '0.17': [34, 0, 0], '0.33': [-120, 0, 0],
                                  '0.75': [0, 0, 0]}},
        'arm_right': {'rotation': {'0.0': [0, 0, 0], '0.17': [34, 0, 0], '0.33': [-120, 0, 0],
                                   '0.75': [0, 0, 0]}},
    }}


def biped_animation(build, clips=('idle', 'walk', 'attack', 'shoot', 'cast', 'jump')):
    """One rig's clip file. Every build gets every clip it could ever be asked for: which are
    required is decided per variant by the behaviours it carries, and two variants on one rig do not
    have to carry the same ones."""
    made = {
        'idle': lambda: biped_idle(build),
        'walk': lambda: biped_walk(build),
        'attack': biped_attack,
        'shoot': biped_shoot,
        'cast': biped_cast,
        'jump': biped_jump,
    }
    return {'format_version': '1.8.0',
            'animations': on_grid({name: made[name]() for name in clips})}

# ---------------------------------------------------------------------------- cave fauna

def bat_geo():
    """A cave bat: a small body, big ears, and wings in two segments so a flap can fold.

    Wings are the whole rig. Two bones each — an arm out to the wrist and a membrane panel beyond it
    — because a single flat wing rotating about the shoulder sweeps like an oar, and what a bat
    actually does is beat the inner wing and let the outer one lag. That lag is one bone.
    """
    sheet = Sheet(64, 64)
    bw, bh, bd = 5, 6, 4
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        # Pivot at the top: it hangs from the ceiling as readily as it flies, and everything that
        # rotates on this rig rotates about where it would be hanging from.
        {'name': 'body', 'parent': 'root', 'pivot': [0, bh, 0], 'cubes': [
            {'origin': [-bw / 2, 0, -bd / 2], 'size': [bw, bh, bd],
             'uv': sheet.slot((bw, bh, bd), 'shell')},
        ]},
        {'name': 'head', 'parent': 'body', 'pivot': [0, bh - 1, -bd / 2], 'cubes': [
            {'origin': [-2, bh - 4, -bd / 2 - 3], 'size': [4, 4, 3],
             'uv': sheet.slot((4, 4, 3), 'head')},
        ]},
    ]
    for side, sx in (('left', 1), ('right', -1)):
        # Ears: oversized on purpose. At this scale the head is four pixels and the ears are the
        # only part of the silhouette that says "bat" from across a room.
        bones.append({
            'name': f'ear_{side}', 'parent': 'head',
            'pivot': [sx * 1.5, bh, -bd / 2 - 1],
            'cubes': [{'origin': [sx * 1.5 - 1, bh, -bd / 2 - 2], 'size': [2, 5, 1],
                       'uv': sheet.slot((2, 5, 1), 'ear')}],
        })
        # Rest angles matter more here than anywhere else on this rig. Flat wings make a silhouette
        # that is a straight bar through a body — a paper plane — and no amount of flapping fixes it,
        # because the pose it returns to every cycle is the pose that reads. The arm lifts slightly,
        # the membrane droops off it and sweeps back.
        bones.append({
            'name': f'wing_{side}', 'parent': 'body',
            'pivot': [sx * (bw / 2), bh - 1, 0],
            'rotation': [0, 0, sx * -12.0],
            'cubes': [{'origin': [sx * (bw / 2) if sx > 0 else sx * (bw / 2) - 6,
                                  bh - 3, -1], 'size': [6, 3, 2],
                       'uv': sheet.slot((6, 3, 2), 'wing')}],
        })
        bones.append({
            'name': f'wing_{side}_tip', 'parent': f'wing_{side}',
            'pivot': [sx * (bw / 2 + 6), bh - 1, 0],
            'rotation': [0, sx * -26.0, sx * 22.0],
            'cubes': [{'origin': [sx * (bw / 2 + 6) if sx > 0 else sx * (bw / 2 + 6) - 7,
                                  bh - 4, -1], 'size': [7, 5, 1],
                       'uv': sheet.slot((7, 5, 1), 'wing')}],
        })
    # Feet, and they are not decoration: this is a bat, so the pose everyone pictures is upside down
    # with these holding the ceiling. Two pixels, and the silhouette gains the part that says so.
    bones.append({
        'name': 'feet', 'parent': 'body', 'pivot': [0, 0, 0],
        'cubes': [{'origin': [-2, -2, -1], 'size': [4, 2, 2],
                   'uv': sheet.slot((4, 2, 2), 'ear')}],
    })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_murcielago',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 4, 'visible_bounds_height': 2,
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': bones,
        }],
    }, sheet


def bat_animation():
    """Flap, hover, and a bite. Everything is the wing pair against a body that answers them.

    The body rises as the wings come down and the tips trail a beat behind the arms, which is the
    only part of this worth writing down: a wing whose halves move together is a paddle, and a bat
    on a paddle reads as a bird made of cardboard.
    """
    def flap(frames, arm, tip, lift):
        wings, tips, body = {}, {}, {}
        for f in range(0, frames + 1):
            t = str(snap(f / FPS))
            ph = f / frames
            drive = math.sin(2 * math.pi * ph)
            wings[t] = r3([0, 0, arm * drive])
            # A quarter cycle behind: the membrane is dragged, it is not driven.
            tips[t] = r3([0, 0, tip * math.sin(2 * math.pi * ph - math.pi / 2)])
            body[t] = r3([0, lift * -drive, 0])
        return wings, tips, body

    def clip(frames, arm, tip, lift, loop=True):
        wings, tips, body = flap(frames, arm, tip, lift)
        mirror = {t: r3([v[0], v[1], -v[2]]) for t, v in wings.items()}
        mirror_tip = {t: r3([v[0], v[1], -v[2]]) for t, v in tips.items()}
        return {
            'loop': loop,
            'animation_length': snap(frames / FPS),
            'bones': {
                'wing_left': {'rotation': wings},
                'wing_right': {'rotation': mirror},
                'wing_left_tip': {'rotation': tips},
                'wing_right_tip': {'rotation': mirror_tip},
                'body': {'position': body},
            },
        }

    return {'format_version': '1.8.0', 'animations': on_grid({
        # Hovering: slower and shallower than travel, because a bat holding station beats less far
        # and more often than one crossing a room.
        'idle': clip(10, 34, 22, 0.5),
        'walk': clip(8, 58, 34, 0.9),
        'attack': {
            'loop': False,
            'animation_length': snap(10 / FPS),
            'bones': {
                'body': {'rotation': keyframes([
                    (0.0, [0, 0, 0]), (snap(4 / FPS), [34, 0, 0]),
                    (snap(10 / FPS), [0, 0, 0])])},
                'head': {'rotation': keyframes([
                    (0.0, [0, 0, 0]), (snap(4 / FPS), [22, 0, 0]),
                    (snap(10 / FPS), [0, 0, 0])])},
                # The wings sweep forward with the dive rather than continuing to beat: a bat that
                # keeps flapping through its own attack is playing two clips at once.
                'wing_left': {'rotation': keyframes([
                    (0.0, [0, 0, 0]), (snap(4 / FPS), [0, -40, -30]),
                    (snap(10 / FPS), [0, 0, 0])])},
                'wing_right': {'rotation': keyframes([
                    (0.0, [0, 0, 0]), (snap(4 / FPS), [0, 40, 30]),
                    (snap(10 / FPS), [0, 0, 0])])},
            },
        },
    })}


# Six legs, in the tripod gait every insect walks: front and rear of one side move with the middle
# of the other, so three feet are always planted. It is the plainest rule in this file and the one
# that does the most work — a beetle keyed leg-by-leg looks broken, and a beetle keyed in two
# alternating triples looks alive.
BEETLE_LEGS = (('leg_l1', 0.0, 1), ('leg_l2', 0.5, 1), ('leg_l3', 0.0, 1),
               ('leg_r1', 0.5, -1), ('leg_r2', 0.0, -1), ('leg_r3', 0.5, -1))


def beetle_geo():
    """A geode beetle: a domed shell over a low body, six legs, and crystal on its back.

    The shell is a second cube rather than a taller body, so the silhouette has a break in it at
    beetle scale — a single box with legs is a woodlouse. The crystals use the golem's `shard` part,
    which is what ties the two visually: the small one is where the lesson is taught, the big one is
    where it is tested.
    """
    sheet = Sheet(64, 64)
    bw, bh, bd = 7, 3, 10
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 2, 0], 'cubes': [
            {'origin': [-bw / 2, 2, -bd / 2], 'size': [bw, bh, bd],
             'uv': sheet.slot((bw, bh, bd), 'shell')},
            {'origin': [-(bw - 2) / 2, 2 + bh, -bd / 2 + 1], 'size': [bw - 2, 2, bd - 3],
             'uv': sheet.slot((bw - 2, 2, bd - 3), 'shell')},
        ]},
        {'name': 'head', 'parent': 'body', 'pivot': [0, 3, -bd / 2], 'cubes': [
            {'origin': [-2, 2, -bd / 2 - 3], 'size': [4, 3, 3],
             'uv': sheet.slot((4, 3, 3), 'head')},
        ]},
    ]
    for i, (cx, cz, size) in enumerate(((-2, -2, 2), (2, 1, 2), (0, 3, 2))):
        bones.append({
            'name': f'shard_{i}', 'parent': 'body',
            'pivot': [cx, 2 + bh, cz],
            'cubes': [{'origin': [cx - size / 2, 2 + bh + 1, cz - size / 2],
                       'size': [size, size + 1, size],
                       'uv': sheet.slot((size, size + 1, size), 'shard')}],
        })
    for name, _, sx in BEETLE_LEGS:
        row = int(name[-1]) - 1
        z = -3 + row * 3
        bones.append({
            'name': name, 'parent': 'body',
            'pivot': [sx * (bw / 2), 4, z],
            # Splayed down and out at rest. Legs leaving the body horizontally read as tabs on a
            # box — the difference between a beetle and a lunchbox is entirely that they angle to
            # the floor, and the gait then swings about a pose that already looks like standing.
            'rotation': [0, 0, sx * 34.0],
            'cubes': [{'origin': [sx * (bw / 2) if sx > 0 else sx * (bw / 2) - 5, 3, z - 1],
                       'size': [5, 2, 2], 'uv': sheet.slot((5, 2, 2), 'leg')}],
        })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_escarabajo',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 2, 'visible_bounds_height': 1,
                'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': bones,
        }],
    }, sheet


def beetle_animation():
    """Scuttle, idle, and the curl — which is the clip that matters.

    `attack` is not a bite. The beetle tucks its head, drops onto the shell and the crystals come
    up: it is the animation of *being hit*, played as an attack, because what this enemy does to a
    party is exist while they swing at it. A player who has seen the curl once knows what the golem
    is going to do before the golem does it.
    """
    frames = 16
    bones = {}
    for name, phase, sx in BEETLE_LEGS:
        rot = {}
        for f in sample_frames(1, frames):
            u = f / frames + phase
            rot[str(snap(f / FPS))] = r3([0, sx * 34 * stride(u), -sx * 16 * lift(u)])
        bones[name] = {'rotation': rot}
    body = {}
    for f in range(0, frames + 1, 2):
        ph = f / frames
        body[str(snap(f / FPS))] = r3([0, 0.25 * abs(math.sin(4 * math.pi * ph)), 0])
    bones['body'] = {'position': body}

    curl = {
        'loop': False,
        'animation_length': snap(16 / FPS),
        'bones': {
            'body': {
                'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [-26, 0, 0]),
                                       (snap(11 / FPS), [-20, 0, 0]), (snap(16 / FPS), [0, 0, 0])]),
                'position': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [0, -1, 0]),
                                       (snap(16 / FPS), [0, 0, 0])]),
                'scale': keyframes([(0.0, [1, 1, 1]), (snap(4 / FPS), [1.1, 1.15, 0.85]),
                                    (snap(11 / FPS), [1.08, 1.12, 0.88]),
                                    (snap(16 / FPS), [1, 1, 1])]),
            },
            'head': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [42, 0, 0]),
                                            (snap(16 / FPS), [0, 0, 0])])},
        },
    }
    idle = {'loop': True, 'animation_length': snap(48 / FPS), 'bones': {
        'body': {'position': keyframes([(0.0, [0, 0, 0]), (snap(24 / FPS), [0, 0.2, 0]),
                                        (snap(48 / FPS), [0, 0, 0])])},
        'head': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(12 / FPS), [0, 14, 0]),
                                        (snap(36 / FPS), [0, -14, 0]),
                                        (snap(48 / FPS), [0, 0, 0])])},
    }}
    return {'format_version': '1.8.0', 'animations': on_grid({
        'idle': idle,
        'walk': {'loop': True, 'animation_length': snap(frames / FPS), 'bones': bones},
        'attack': curl,
    })}


def fungus_geo():
    """A walking spore sac: a heavy cap over a stalk, on two stubby legs.

    Top-heavy on purpose. The cap is wider than the body is tall, so the thing reads as something
    carrying a load it can barely manage — which is the read that makes a player believe it will
    burst, before it ever does.
    """
    sheet = Sheet(64, 64)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 3, 0], 'cubes': [
            {'origin': [-2.5, 3, -2.5], 'size': [5, 5, 5], 'uv': sheet.slot((5, 5, 5), 'stalk')},
        ]},
        # Its own bone so the idle can swell it: what a spore sac does at rest is fill.
        {'name': 'cap', 'parent': 'body', 'pivot': [0, 8, 0], 'cubes': [
            {'origin': [-5, 8, -5], 'size': [10, 5, 10], 'uv': sheet.slot((10, 5, 10), 'cap')},
            {'origin': [-3, 12, -3], 'size': [6, 2, 6], 'uv': sheet.slot((6, 2, 6), 'cap')},
            # The pores, and they are the only part that lights. A glowing cap is a lantern — 520
            # texels of one, which the emissive cap in BestiaryAuditTest refused and was right to.
            # Four pinpricks say "this is full of something" and leave the glow vocabulary meaning
            # what it means everywhere else in the bestiary.
            *[{'origin': [x, 13, z], 'size': [1, 1, 1], 'uv': sheet.slot((1, 1, 1), 'pore')}
              for x, z in ((-2, -1), (1, -2), (-1, 1), (2, 1))],
        ]},
    ]
    for side, sx in (('left', 1), ('right', -1)):
        bones.append({
            'name': f'leg_{side}', 'parent': 'body',
            'pivot': [sx * 1.5, 3, 0],
            'cubes': [{'origin': [sx * 1.5 - 1, 0, -1], 'size': [2, 3, 2],
                       'uv': sheet.slot((2, 3, 2), 'leg')}],
        })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_hongo',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 2, 'visible_bounds_height': 1.5,
                'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': bones,
        }],
    }, sheet


def fungus_animation():
    """A waddle, a breath, and a lunge that is mostly the cap arriving late."""
    frames = 20
    legs_l, legs_r = {}, {}
    body_rot = {}
    for f in sample_frames(1, frames):
        u = f / frames
        t = str(snap(f / FPS))
        legs_l[t] = r3([30 * stride(u), 0, 0])
        legs_r[t] = r3([30 * stride(u + 0.5), 0, 0])
    for f in range(0, frames + 1, 2):
        ph = f / frames
        # The waddle is a roll, not a bob: two short legs under a wide cap cannot help but rock.
        body_rot[str(snap(f / FPS))] = r3([0, 0, 7 * math.sin(2 * math.pi * ph)])
    cap_lag = {str(snap(f / FPS)): r3([0, 0, 5 * math.sin(2 * math.pi * f / frames - 0.9)])
               for f in range(0, frames + 1, 2)}
    return {'format_version': '1.8.0', 'animations': on_grid({
        'idle': {'loop': True, 'animation_length': snap(60 / FPS), 'bones': {
            'cap': {'scale': keyframes([(0.0, [1, 1, 1]), (snap(30 / FPS), [1.07, 1.1, 1.07]),
                                        (snap(60 / FPS), [1, 1, 1])])},
            'body': {'position': keyframes([(0.0, [0, 0, 0]), (snap(30 / FPS), [0, 0.2, 0]),
                                            (snap(60 / FPS), [0, 0, 0])])},
        }},
        'walk': {'loop': True, 'animation_length': snap(frames / FPS), 'bones': {
            'leg_left': {'rotation': legs_l},
            'leg_right': {'rotation': legs_r},
            'body': {'rotation': body_rot},
            'cap': {'rotation': cap_lag},
        }},
        'attack': {'loop': False, 'animation_length': snap(14 / FPS), 'bones': {
            'body': {
                'rotation': keyframes([(0.0, [0, 0, 0]), (snap(3 / FPS), [-18, 0, 0]),
                                       (snap(7 / FPS), [30, 0, 0]), (snap(14 / FPS), [0, 0, 0])]),
                'position': keyframes([(0.0, [0, 0, 0]), (snap(7 / FPS), [0, 0, -2]),
                                       (snap(14 / FPS), [0, 0, 0])]),
            },
            'cap': {'scale': keyframes([(0.0, [1, 1, 1]), (snap(7 / FPS), [1.15, 0.85, 1.15]),
                                        (snap(14 / FPS), [1, 1, 1])])},
        }},
    })}


def moss_geo():
    """The grabber: a mound of moss with tendrils, and nothing under it that walks.

    Built low and wide with the tendrils curled inward, so at rest it is scenery. It has no walk to
    speak of and its whole design problem is that a player has to be able to tell it from the
    decoration *once they know what to look for* — never before. The tendrils are what gives it away
    and they only move in `idle`.
    """
    sheet = Sheet(64, 64)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 0, 0], 'cubes': [
            {'origin': [-6, 0, -6], 'size': [12, 4, 12], 'uv': sheet.slot((12, 4, 12), 'moss')},
            {'origin': [-4, 4, -4], 'size': [8, 2, 8], 'uv': sheet.slot((8, 2, 8), 'moss')},
        ]},
        # The mouth sits proud of the mound rather than inside it. Buried, it was invisible until the
        # grab clip scaled it out through the moss — which meant the enemy had no tell at all, and a
        # trap with no tell is a trap nobody learns, only one they resent.
        {'name': 'maw', 'parent': 'body', 'pivot': [0, 5, 0], 'cubes': [
            {'origin': [-3, 5, -3], 'size': [6, 2, 6], 'uv': sheet.slot((6, 2, 6), 'maw')},
        ]},
    ]
    for i, (cx, cz) in enumerate(((-5, -4), (5, -3), (-4, 5), (4, 4))):
        bones.append({
            'name': f'tendril_{i}', 'parent': 'body',
            'pivot': [cx, 3, cz],
            'cubes': [{'origin': [cx - 0.5, 3, cz - 0.5], 'size': [1, 6, 1],
                       'uv': sheet.slot((1, 6, 1), 'tendril')}],
        })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_musgo',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 2, 'visible_bounds_height': 1,
                'visible_bounds_offset': [0, 0.5, 0],
            },
            'bones': bones,
        }],
    }, sheet


def moss_animation():
    """Breathe, and grab. `walk` is the idle again, because it never goes anywhere."""
    idle_bones = {
        'body': {'scale': keyframes([(0.0, [1, 1, 1]), (snap(36 / FPS), [1.03, 0.94, 1.03]),
                                     (snap(72 / FPS), [1, 1, 1])])},
    }
    for i, lean in enumerate((11, -9, 8, -12)):
        idle_bones[f'tendril_{i}'] = {'rotation': keyframes([
            (0.0, [0, 0, 0]),
            # Each on its own period: four tendrils on one timer is a fan, not a plant.
            (snap((24 + 6 * i) / FPS), [lean, lean / 2, -lean]),
            (snap(72 / FPS), [0, 0, 0])])}
    idle = {'loop': True, 'animation_length': snap(72 / FPS), 'bones': idle_bones}

    grab_bones = {
        'maw': {'scale': keyframes([(0.0, [1, 1, 1]), (snap(3 / FPS), [1.3, 2.2, 1.3]),
                                    (snap(9 / FPS), [0.7, 0.6, 0.7]), (snap(16 / FPS), [1, 1, 1])])},
        'body': {'scale': keyframes([(0.0, [1, 1, 1]), (snap(3 / FPS), [0.94, 1.16, 0.94]),
                                     (snap(9 / FPS), [1.1, 0.86, 1.1]), (snap(16 / FPS), [1, 1, 1])])},
    }
    for i in range(4):
        grab_bones[f'tendril_{i}'] = {'rotation': keyframes([
            (0.0, [0, 0, 0]), (snap(3 / FPS), [-40, 0, 0]), (snap(9 / FPS), [55, 0, 0]),
            (snap(16 / FPS), [0, 0, 0])])}
    return {'format_version': '1.8.0', 'animations': on_grid({
        'idle': idle,
        'walk': json.loads(json.dumps(idle)),
        'attack': {'loop': False, 'animation_length': snap(16 / FPS), 'bones': grab_bones},
    })}


def mastiff_geo():
    """The smugglers' dog: a quadruped, chest-heavy, built to arrive.

    Legs are single bones — a dog at this scale has no room for a knee, and the gait sells itself on
    timing rather than articulation. What carries the weight is the ratio: a deep chest, a short
    back, a low head. A quadruped with a level back and even legs reads as a table.
    """
    sheet = Sheet(64, 64)
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'body', 'parent': 'root', 'pivot': [0, 9, 0], 'cubes': [
            # Chest and hindquarters as two cubes: the chest is deeper, which is the whole animal.
            {'origin': [-4, 7, -8], 'size': [8, 8, 9], 'uv': sheet.slot((8, 8, 9), 'shell')},
            {'origin': [-3.5, 8, 1], 'size': [7, 6, 6], 'uv': sheet.slot((7, 6, 6), 'shell')},
        ]},
        {'name': 'head', 'parent': 'body', 'pivot': [0, 12, -8], 'cubes': [
            {'origin': [-3, 9, -13], 'size': [6, 5, 5], 'uv': sheet.slot((6, 5, 5), 'head')},
            # The muzzle, and the reason it is a mastiff rather than a wolf: short and blunt.
            {'origin': [-2, 9, -16], 'size': [4, 3, 3], 'uv': sheet.slot((4, 3, 3), 'head')},
        ]},
        {'name': 'tail', 'parent': 'body', 'pivot': [0, 13, 7], 'cubes': [
            {'origin': [-1, 12, 7], 'size': [2, 2, 5], 'uv': sheet.slot((2, 2, 5), 'leg')},
        ]},
    ]
    for side, sx in (('left', 1), ('right', -1)):
        for name, z, length in (('front', -6, 8), ('back', 5, 7)):
            bones.append({
                'name': f'leg_{name}_{side}', 'parent': 'root',
                'pivot': [sx * 2.5, length, z],
                'cubes': [{'origin': [sx * 2.5 - 1.5, 0, z - 1.5], 'size': [3, length, 3],
                           'uv': sheet.slot((3, length, 3), 'leg')}],
            })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_mastin',
                'texture_width': 64, 'texture_height': 64,
                'visible_bounds_width': 2, 'visible_bounds_height': 1.5,
                'visible_bounds_offset': [0, 0.75, 0],
            },
            'bones': bones,
        }],
    }, sheet


def mastiff_animation():
    """A trot, a pant, and a pounce.

    The trot is diagonal pairs — front-left with back-right — which is what a dog actually does and
    what separates it from the biped stride two functions up. The back rises and falls with the
    hindquarters rather than staying level, because a level back is the tell of a rig that was
    animated leg-first.
    """
    frames = 14
    pairs = (('leg_front_left', 0.0), ('leg_back_right', 0.0),
             ('leg_front_right', 0.5), ('leg_back_left', 0.5))
    bones = {}
    for name, phase in pairs:
        rot = {}
        for f in sample_frames(1, frames):
            u = f / frames + phase
            rot[str(snap(f / FPS))] = r3([46 * stride(u), 0, 0])
        bones[name] = {'rotation': rot}
    body_pos, body_rot = {}, {}
    for f in range(0, frames + 1, 2):
        ph = f / frames
        body_pos[str(snap(f / FPS))] = r3([0, 0.5 * abs(math.sin(2 * math.pi * ph)), 0])
        body_rot[str(snap(f / FPS))] = r3([3.5 * math.sin(2 * math.pi * ph), 0, 0])
    bones['body'] = {'position': body_pos, 'rotation': body_rot}
    bones['tail'] = {'rotation': {str(snap(f / FPS)): r3([0, 16 * math.sin(4 * math.pi * f / frames), 0])
                                  for f in range(0, frames + 1, 2)}}
    return {'format_version': '1.8.0', 'animations': on_grid({
        'idle': {'loop': True, 'animation_length': snap(56 / FPS), 'bones': {
            'body': {'position': keyframes([(0.0, [0, 0, 0]), (snap(28 / FPS), [0, 0.25, 0]),
                                            (snap(56 / FPS), [0, 0, 0])])},
            'head': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(14 / FPS), [0, 13, 0]),
                                            (snap(42 / FPS), [0, -11, 0]),
                                            (snap(56 / FPS), [0, 0, 0])])},
            'tail': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(14 / FPS), [0, 26, 0]),
                                            (snap(28 / FPS), [0, 0, 0]), (snap(42 / FPS), [0, -26, 0]),
                                            (snap(56 / FPS), [0, 0, 0])])},
        }},
        'walk': {'loop': True, 'animation_length': snap(frames / FPS), 'bones': bones},
        'attack': {'loop': False, 'animation_length': snap(12 / FPS), 'bones': {
            'body': {
                'rotation': keyframes([(0.0, [0, 0, 0]), (snap(3 / FPS), [-22, 0, 0]),
                                       (snap(6 / FPS), [18, 0, 0]), (snap(12 / FPS), [0, 0, 0])]),
                'position': keyframes([(0.0, [0, 0, 0]), (snap(6 / FPS), [0, 0, -2.5]),
                                       (snap(12 / FPS), [0, 0, 0])]),
            },
            'head': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(3 / FPS), [-26, 0, 0]),
                                            (snap(6 / FPS), [26, 0, 0]), (snap(12 / FPS), [0, 0, 0])])},
        }},
        # The pounce: gather, extend, land. Front legs reach and the back legs trail, which is the
        # difference between a leap and a hop.
        'jump': {'loop': False, 'animation_length': snap(20 / FPS), 'bones': {
            'body': {
                'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [20, 0, 0]),
                                       (snap(9 / FPS), [-24, 0, 0]), (snap(20 / FPS), [0, 0, 0])]),
                'position': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [0, -1.5, 0]),
                                       (snap(9 / FPS), [0, 1.5, 0]), (snap(20 / FPS), [0, 0, 0])]),
            },
            'leg_front_left': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [40, 0, 0]),
                                                      (snap(9 / FPS), [-52, 0, 0]),
                                                      (snap(20 / FPS), [0, 0, 0])])},
            'leg_front_right': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [40, 0, 0]),
                                                       (snap(9 / FPS), [-52, 0, 0]),
                                                       (snap(20 / FPS), [0, 0, 0])])},
            'leg_back_left': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [-30, 0, 0]),
                                                     (snap(9 / FPS), [40, 0, 0]),
                                                     (snap(20 / FPS), [0, 0, 0])])},
            'leg_back_right': {'rotation': keyframes([(0.0, [0, 0, 0]), (snap(4 / FPS), [-30, 0, 0]),
                                                      (snap(9 / FPS), [40, 0, 0]),
                                                      (snap(20 / FPS), [0, 0, 0])])},
        }},
    })}

# ---------------------------------------------------------------------------- textures

def shade(colour, amount):
    return tuple(max(0, min(255, int(c + amount))) for c in colour)


def paint(sheet, palette, seed, size):
    """Paints a sheet from the packed regions, so the texture matches the model it wraps.

    Per-part palettes rather than one noise field: an abdomen with a marking, plates that darken
    toward the underside, joints that read as joints. A generated sheet cannot be art, but it can
    stop being random, and the difference is entirely that this walks `sheet.regions` instead of the
    whole image.
    """
    rows = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for region in sheet.regions:
        part = region['part']
        base, dark, light = palette[part]
        for (fx, fy, fw, fh, face) in faces_of(region):
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    if x >= size or y >= size:
                        continue
                    n = noise(x * 7919 + y * 104729 + seed)
                    # Underside darker, top catching what little light a dungeon has.
                    if face == 'bottom':
                        c = shade(dark, -14)
                    elif face == 'top':
                        c = light
                    else:
                        c = base
                    # Chitin plating: a coarse band across the part, not per-pixel noise.
                    if part in ('body', 'abdomen', 'segment', 'plate') and ((y - fy) // 2) % 2 == 0:
                        c = shade(c, -10)
                    if part == 'abdomen' and face in ('top', 'south'):
                        # The marking. Centre stripe plus a pair of blotches — the part of a spider
                        # anyone actually looks at.
                        cx = fx + fw / 2
                        if abs(x - cx) < max(1, fw // 8):
                            c = shade(light, 26)
                        elif abs(abs(x - cx) - fw / 3.2) < max(1, fw // 9) \
                                and (y - fy) % max(3, fh // 3) < max(1, fh // 4):
                            c = shade(dark, -18)
                    if part in ('leg', 'arm') and (x - fx) % 4 == 3:
                        c = shade(dark, -6)   # joint banding along the segment
                    if part in ('eye', 'shard'):
                        # Both are read through an emissive pass as well, so the base sheet has to
                        # agree with it: a dark crystal on the base and a bright one on the glow
                        # sheet is a seam that changes colour when the light does.
                        c = light
                    jitter = int((n - 0.5) * 12)
                    rows[y][x] = (max(0, min(255, c[0] + jitter)),
                                  max(0, min(255, c[1] + jitter)),
                                  max(0, min(255, c[2] + jitter)),
                                  255)
    return rows


def glow_rows(sheet, colour, size, parts=('eye',)):
    """The emissive sheet: transparent everywhere the base sheet is lit normally.

    Rendered a second time through RenderType.eyes, which ignores block light — the only way a
    climbing enemy on an unlit ceiling is visible before it is on top of someone. Nearly every pixel
    is empty, which is why this costs almost nothing.
    """
    rows = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for region in sheet.regions:
        if region['part'] not in parts:
            continue
        for (fx, fy, fw, fh, face) in faces_of(region):
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    if x < size and y < size:
                        rows[y][x] = colour
    return rows


# base / dark / light per part. Three arachnids at three depths of the same palette, so they read
# as one species: the swarm pale, the weaver darker, the queen nearly black with a red marking.
PALETTES = {
    'cria': {
        'body': ((78, 70, 84), (52, 46, 58), (104, 94, 110)),
        'abdomen': ((92, 80, 96), (58, 50, 62), (126, 110, 128)),
        'spinneret': ((70, 62, 74), (46, 40, 50), (92, 82, 96)),
        'head': ((72, 64, 78), (48, 42, 54), (98, 88, 104)),
        'eye': ((198, 96, 72), (120, 48, 36), (236, 132, 96)),
        'fang': ((188, 176, 168), (120, 110, 104), (222, 212, 206)),
        'palp': ((70, 62, 74), (46, 40, 50), (94, 84, 98)),
        'leg': ((64, 57, 68), (40, 35, 44), (88, 79, 92)),
    },
    'tejedora': {
        'body': ((54, 46, 62), (32, 27, 38), (76, 66, 86)),
        'abdomen': ((62, 48, 70), (36, 28, 42), (94, 74, 104)),
        'spinneret': ((48, 40, 54), (28, 23, 32), (68, 58, 76)),
        'head': ((50, 43, 58), (30, 25, 35), (72, 62, 82)),
        'eye': ((236, 196, 92), (140, 112, 44), (255, 232, 148)),
        'fang': ((196, 186, 174), (124, 116, 108), (232, 224, 214)),
        'palp': ((48, 41, 55), (28, 24, 33), (70, 60, 78)),
        'leg': ((44, 38, 51), (26, 22, 30), (64, 55, 72)),
    },
    'cazadora': {
        # Warm and dusty against the infestation's cold purples — she hunts the floor rather than
        # hanging in the webbing, and reading as a different animal is the whole point of her.
        'body': ((70, 52, 44), (44, 32, 27), (98, 74, 62)),
        'abdomen': ((84, 60, 46), (52, 36, 28), (118, 86, 66)),
        'spinneret': ((62, 46, 38), (38, 28, 23), (86, 64, 54)),
        'head': ((66, 48, 40), (40, 30, 25), (92, 70, 58)),
        # Green: the one eye colour nothing else in the bestiary uses, so the glow alone says which
        # of the two big spiders is in the room with you.
        'eye': ((128, 232, 130), (68, 140, 72), (186, 255, 188)),
        'fang': ((214, 202, 186), (136, 128, 118), (244, 238, 228)),
        'palp': ((62, 46, 38), (38, 28, 23), (88, 66, 55)),
        'leg': ((58, 43, 36), (36, 26, 22), (82, 62, 52)),
    },
    'reina': {
        'body': ((36, 28, 34), (20, 15, 19), (54, 43, 52)),
        'abdomen': ((44, 26, 34), (24, 13, 18), (92, 40, 48)),
        'spinneret': ((34, 24, 30), (18, 12, 16), (52, 38, 46)),
        'head': ((34, 26, 32), (18, 14, 17), (52, 41, 50)),
        'eye': ((238, 72, 64), (146, 34, 30), (255, 128, 112)),
        'fang': ((206, 194, 180), (132, 122, 112), (240, 232, 222)),
        'palp': ((34, 25, 31), (18, 13, 17), (52, 39, 48)),
        'leg': ((30, 23, 28), (16, 12, 15), (46, 36, 44)),
    },
}

# One palette per humanoid *variant*, not per rig: the whole point of a shared rig is that a texture
# is the cheapest way to make another enemy, and these are the seven that were already shipping.
BIPED_PALETTES = {
    'raider_saqueador': {
        'body': ((92, 74, 54), (58, 46, 33), (124, 102, 76)),
        'plate': ((74, 58, 42), (46, 36, 26), (102, 82, 60)),
        'head': ((132, 112, 92), (88, 74, 60), (166, 146, 124)),
        'arm': ((84, 68, 50), (52, 42, 30), (114, 94, 70)),
        'leg': ((70, 57, 42), (44, 35, 26), (96, 79, 60)),
    },
    'raider_arquero': {
        'body': ((62, 78, 62), (38, 50, 39), (88, 108, 86)),
        'plate': ((50, 62, 50), (30, 40, 31), (72, 88, 70)),
        'head': ((126, 112, 96), (84, 74, 62), (158, 144, 126)),
        'arm': ((58, 72, 58), (35, 46, 36), (82, 100, 80)),
        'leg': ((48, 60, 48), (29, 38, 30), (68, 84, 68)),
    },
    'guardian_husk': {
        'body': ((104, 96, 74), (66, 60, 45), (136, 126, 100)),
        'plate': ((86, 80, 62), (54, 50, 38), (114, 106, 84)),
        'head': ((118, 110, 86), (76, 70, 54), (150, 140, 112)),
        'arm': ((96, 89, 69), (60, 55, 42), (126, 117, 93)),
        'leg': ((82, 76, 59), (51, 47, 36), (108, 100, 79)),
    },
    'guardian_bone': {
        'body': ((196, 192, 178), (140, 136, 124), (228, 224, 212)),
        'plate': ((176, 172, 158), (124, 120, 110), (208, 204, 190)),
        'head': ((208, 204, 190), (150, 146, 134), (238, 234, 222)),
        'arm': ((186, 182, 168), (132, 128, 117), (218, 214, 200)),
        'leg': ((168, 164, 151), (118, 115, 105), (198, 194, 180)),
    },
    'guardian_warden': {
        'body': ((52, 60, 74), (32, 37, 47), (76, 86, 104)),
        'plate': ((42, 49, 62), (25, 30, 38), (64, 73, 90)),
        'head': ((58, 66, 80), (36, 41, 51), (84, 94, 112)),
        'arm': ((48, 55, 69), (29, 34, 43), (70, 80, 97)),
        'leg': ((40, 47, 59), (24, 29, 37), (60, 69, 84)),
    },
    # The two humanoids the cave gains, both on the raider rig: the smugglers' outfit is a texture
    # set, not a rig set, which is the point of a shared bone contract.
    'raider_carronero': {
        # Rags and mismatched leather — someone who is here to take things, not to hold ground.
        'body': ((96, 84, 62), (60, 52, 38), (128, 114, 86)),
        'plate': ((78, 66, 48), (48, 40, 29), (106, 92, 68)),
        'head': ((138, 118, 96), (92, 78, 63), (170, 150, 126)),
        'arm': ((88, 76, 56), (54, 47, 34), (118, 104, 78)),
        'leg': ((72, 62, 46), (44, 38, 28), (98, 86, 64)),
    },
    'raider_vigia': {
        # The one with the horn: darker cloth and a red sash, so the enemy you are meant to kill
        # first is the one that stands out in the wave.
        'body': ((72, 46, 46), (44, 27, 27), (102, 66, 66)),
        'plate': ((122, 44, 40), (76, 26, 24), (162, 66, 60)),
        'head': ((132, 112, 92), (88, 74, 60), (164, 144, 122)),
        'arm': ((66, 44, 44), (40, 26, 26), (94, 64, 64)),
        'leg': ((56, 38, 38), (34, 22, 22), (80, 56, 56)),
    },
    'murcielago_gruta': {
        'shell': ((66, 54, 62), (40, 32, 38), (92, 76, 88)),
        'head': ((74, 60, 68), (46, 37, 42), (102, 84, 96)),
        'ear': ((96, 72, 82), (60, 45, 52), (128, 100, 112)),
        'wing': ((52, 42, 50), (30, 24, 29), (76, 62, 74)),
    },
    'escarabajo_geoda': {
        # Basalt again, deliberately: it is a small piece of the golem, and the shared palette is
        # how a player connects the two before either of them does anything.
        'shell': ((60, 56, 62), (37, 34, 39), (84, 78, 88)),
        'head': ((54, 50, 57), (33, 30, 35), (76, 71, 80)),
        'leg': ((48, 44, 50), (29, 27, 31), (68, 63, 71)),
        'shard': ((186, 118, 236), (118, 68, 158), (226, 176, 255)),
    },
    'hongo_bombardero': {
        # A sick, luminous yellow-green on a pale stalk. It should look wrong to stand near.
        'cap': ((150, 168, 74), (96, 108, 46), (194, 214, 112)),
        'pore': ((214, 240, 120), (150, 176, 70), (238, 255, 176)),
        'stalk': ((206, 198, 178), (148, 142, 128), (232, 226, 210)),
        'leg': ((176, 168, 150), (122, 117, 104), (204, 196, 180)),
    },
    'musgo_agarrador': {
        # The colour of the decoration it is pretending to be. That is the design.
        'moss': ((64, 92, 52), (38, 56, 31), (92, 128, 74)),
        'maw': ((104, 52, 58), (62, 30, 34), (142, 76, 82)),
        'tendril': ((78, 108, 60), (48, 66, 37), (108, 146, 84)),
    },
    'mastin_contrabandista': {
        'shell': ((78, 62, 50), (48, 38, 30), (108, 86, 70)),
        'head': ((70, 55, 44), (42, 33, 26), (98, 78, 62)),
        'leg': ((62, 49, 39), (37, 29, 23), (88, 70, 56)),
    },
    'gran_limo': {
        # Darker and more mineral than the chaff slimes it splits into — the same animal after a
        # long time eating the floor. `rock` is what it swallowed; `shell` takes no banding, so the
        # blob stays a blob.
        'shell': ((58, 96, 74), (34, 60, 45), (86, 132, 100)),
        'rock': ((84, 78, 88), (52, 48, 55), (116, 108, 120)),
        'eye': ((246, 236, 140), (150, 142, 76), (255, 250, 200)),
    },
    'golem_geoda': {
        # Basalt, so the seams have something dark to be bright against. A crystal golem painted in
        # crystal colours is a lantern; the rock is what makes the light read as coming from inside.
        'body': ((58, 54, 60), (36, 33, 38), (80, 75, 84)),
        'plate': ((48, 45, 52), (29, 27, 32), (68, 64, 74)),
        'head': ((52, 48, 55), (32, 29, 34), (74, 69, 78)),
        'arm': ((54, 50, 57), (33, 30, 35), (76, 71, 80)),
        'leg': ((46, 43, 49), (28, 26, 30), (66, 62, 70)),
        'shard': ((186, 118, 236), (118, 68, 158), (226, 176, 255)),
    },
}

LEPISMA_PALETTE = {
    'segment': ((162, 154, 138), (112, 104, 92), (196, 188, 172)),
    'head': ((152, 144, 130), (104, 98, 88), (186, 178, 164)),
    'eye': ((92, 178, 188), (48, 106, 116), (150, 226, 234)),
    'antenna': ((132, 124, 112), (88, 82, 74), (168, 160, 146)),
    'leg': ((124, 117, 105), (82, 77, 69), (158, 150, 138)),
}

# The crystal crawler: the silverfish rig, in the geode's colours. A retexture and not a rig, which
# is the honest kind of reuse — it is the same animal that lives in the same rock, and the palette is
# what says which part of the cave it came out of. Its eyes take the amethyst, so the swarm and the
# golem read as one family the moment either is lit.
CRISTAL_PALETTE = {
    'segment': ((72, 62, 84), (44, 37, 52), (100, 86, 116)),
    'head': ((66, 57, 78), (40, 34, 48), (92, 80, 108)),
    'eye': ((196, 128, 244), (124, 74, 162), (232, 184, 255)),
    'antenna': ((88, 74, 102), (54, 45, 63), (118, 100, 136)),
    'leg': ((62, 53, 72), (37, 32, 44), (86, 74, 100)),
}

# ---------------------------------------------------------------------------- limo (unchanged)

def limo_geo():
    """A blob: one body bone carrying the whole silhouette, so scaling that bone squashes it.

    Everything the hop animation does is a scale on `body`, which is why the rig is this plain —
    a squash-and-stretch blob needs one deformable bone and nothing else. `core` is the darker
    inner cube read through the shell.
    """
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_limo',
                'texture_width': 64,
                'texture_height': 64,
                'visible_bounds_width': 3,
                'visible_bounds_height': 3,
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': [
                {'name': 'root', 'pivot': [0, 0, 0]},
                # Pivot at the floor: a scale on this bone squashes the blob down onto the ground
                # rather than shrinking it about its middle, which is what makes a hop read.
                {'name': 'body', 'parent': 'root', 'pivot': [0, 0, 0], 'cubes': [
                    {'origin': [-6, 0, -6], 'size': [12, 11, 12], 'uv': [0, 0], 'inflate': 0},
                ]},
                {'name': 'core', 'parent': 'body', 'pivot': [0, 4, 0], 'cubes': [
                    {'origin': [-3, 2, -3], 'size': [6, 5, 6], 'uv': [0, 40]},
                ]},
                {'name': 'eye_left', 'parent': 'body', 'pivot': [-2, 7, -6], 'cubes': [
                    {'origin': [-3, 6, -7], 'size': [2, 2, 1], 'uv': [32, 40]},
                ]},
                {'name': 'eye_right', 'parent': 'body', 'pivot': [2, 7, -6], 'cubes': [
                    {'origin': [1, 6, -7], 'size': [2, 2, 1], 'uv': [32, 44]},
                ]},
            ],
        }],
    }


def gran_limo_geo():
    """The floor boss: the cave slimes' own animal three sizes on, with the cave inside it.

    Same bone contract as the small blobs — one deformable `body` carrying everything, so the squash
    of a hop is a scale on one bone — and the same silhouette rule as every other family here: a boss
    may not be its chaff at a bigger scale. What makes this one legible is what it has swallowed.
    Rock chunks sit half out of the shell rather than suspended inside it, because an inner cube is
    only visible through a translucent outer one and this rig is drawn opaque — the old `core` cube
    has never been seen by anybody.

    No emissive sheet, deliberately. In this bestiary a glow means "this one has a rule you need to
    know before you touch it": climbers you would otherwise not see, and a golem you should not hit.
    A boss under a boss bar in a lit arena is already announced, and spending the emissive vocabulary
    on it would cost the meaning everywhere else.
    """
    sheet = Sheet(128, 128)
    bw, bh, bd = 24, 13, 24
    dw, dh, dd = 17, 8, 17
    ex, ey, ez = 4, 4, 1
    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        # Pivot on the floor, like the small ones: scaling about the middle shrinks a blob, scaling
        # about its feet squashes it, and only one of those reads as landing.
        #
        # Two stacked cubes, not one. A single box at boss size is a wall — the small blob gets away
        # with being a cube because it is small, and the first version of this did not: a wide base
        # under a narrower dome is the least geometry that reads as something sitting under its own
        # weight.
        {'name': 'body', 'parent': 'root', 'pivot': [0, 0, 0], 'cubes': [
            {'origin': [-bw / 2, 0, -bd / 2], 'size': [bw, bh, bd],
             'uv': sheet.slot((bw, bh, bd), 'shell')},
            {'origin': [-dw / 2, bh, -dd / 2], 'size': [dw, dh, dd],
             'uv': sheet.slot((dw, dh, dd), 'shell')},
        ]},
    ]
    # (centre x, y, z, cube size). Centres sit ON a face, so most of each chunk is outside the shell:
    # buried ones showed as slivers, and this rig is drawn opaque, so anything inside it is simply
    # not there.
    for i, (cx, cy, cz, size) in enumerate(((-12, 6, -4, 6), (12, 8, 5, 5),
                                            (3, 21, -8, 5), (-5, 4, 12, 5))):
        bones.append({
            'name': f'chunk_{i}', 'parent': 'body',
            'pivot': [cx, cy, cz],
            'cubes': [{'origin': [cx - size / 2, cy - size / 2, cz - size / 2],
                       'size': [size, size, size], 'uv': sheet.slot((size, size, size), 'rock')}],
        })
    # On the dome's front face, wide apart. Eyes are where a face is, and the face of a blob this
    # size is the part of it that is above the mass.
    for side, sx in (('left', 1), ('right', -1)):
        bones.append({
            'name': f'eye_{side}', 'parent': 'body',
            'pivot': [sx * 4, bh + 3, -dd / 2],
            'cubes': [{'origin': [sx * 4 - ex / 2, bh + 2, -dd / 2 - ez],
                       'size': [ex, ey, ez], 'uv': sheet.slot((ex, ey, ez), 'eye')}],
        })
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.dungeon_gran_limo',
                'texture_width': 128,
                'texture_height': 128,
                'visible_bounds_width': round(bw / 6.0, 2),
                'visible_bounds_height': round((bh + dh) / 6.0, 2),
                'visible_bounds_offset': [0, 1, 0],
            },
            'bones': bones,
        }],
    }, sheet


def keyframes(pairs):
    return {str(t): v for t, v in pairs}


def limo_animation(heavy=False):
    """idle / walk / attack / jump, all built from scaling one bone.

    `walk` exists and is deliberately the same gentle pulse as `idle`: a HOPPER never plays it —
    the controller picks `jump` while airborne — but the clip must exist, because the audit holds
    every rig to the full set the controller can request.

    `heavy` is the boss: everything slower and deeper. Mass is read from how long a thing takes to
    recover from its own landing, so the boss flattens further and takes half again as long to come
    back out of it. A boss playing the chaff's timing is chaff that happens to be large.
    """
    t = 1.5 if heavy else 1.0
    squash = 1.5 if heavy else 1.0

    def s(v):
        """A scale keyframe, exaggerated for the heavy build about its resting size of 1."""
        return [round(1 + (x - 1) * squash, 3) for x in v]

    pulse = {
        'loop': True,
        'animation_length': snap(1.2 * t),
        'bones': {
            'body': {
                'scale': keyframes([
                    (0.0, [1.0, 1.0, 1.0]),
                    (snap(0.6 * t), s([1.06, 0.9, 1.06])),
                    (snap(1.2 * t), [1.0, 1.0, 1.0]),
                ]),
            },
        },
    }
    # The split, and only for the boss: SUMMON drives Action.CAST for 28 ticks, so this clip is
    # what the party sees when two thirds of the fight becomes two more enemies. Without it the
    # adds arrive out of a boss that visibly did nothing, which reads as a bug rather than a move —
    # the same reason the queen got one. The small blobs summon nothing and so are not given a clip
    # they could never play.
    split = {
        'loop': False,
        # 28 ticks is what AbilityEngine holds the CAST action for, and 28/24 is what that is in
        # seconds. Snapped like everything else here, so the clip ends on a frame.
        'animation_length': snap(28 / FPS),
        'bones': {
            'body': {
                'scale': keyframes([
                    (0.0, [1.0, 1.0, 1.0]),
                    # Swell: it draws itself up and holds, which is the tell to back off.
                    (snap(7 / FPS), [1.12, 1.22, 1.12]),
                    (snap(14 / FPS), [1.08, 1.26, 1.08]),
                    # Convulse: flat and wide, and the pieces are out.
                    (snap(17 / FPS), [1.42, 0.62, 1.42]),
                    (snap(21 / FPS), [0.92, 1.14, 0.92]),
                    (snap(28 / FPS), [1.0, 1.0, 1.0]),
                ]),
            },
        },
    }

    clips = {}
    if heavy:
        clips['cast'] = split
    return {
        'format_version': '1.8.0',
        'animations': {
            **clips,
            'idle': pulse,
            'walk': json.loads(json.dumps(pulse)),
            # The landing: flattened hard, then overshoot tall, then settle.
            'jump': {
                'loop': False,
                'animation_length': snap(0.8 * t),
                'bones': {
                    'body': {
                        'scale': keyframes([
                            (0.0, s([1.25, 0.6, 1.25])),
                            (snap(0.2 * t), s([0.85, 1.35, 0.85])),
                            (snap(0.5 * t), s([1.1, 0.85, 1.1])),
                            (snap(0.8 * t), [1.0, 1.0, 1.0]),
                        ]),
                    },
                },
            },
            # A lunge: wind up short and wide, then throw the whole body forward.
            'attack': {
                'loop': False,
                'animation_length': snap(0.6 * t),
                'bones': {
                    'body': {
                        'scale': keyframes([
                            (0.0, [1.0, 1.0, 1.0]),
                            (snap(0.15 * t), s([1.2, 0.75, 1.2])),
                            (snap(0.35 * t), s([0.9, 1.2, 0.9])),
                            (snap(0.6 * t), [1.0, 1.0, 1.0]),
                        ]),
                        'position': keyframes([
                            (0.0, [0, 0, 0]),
                            (snap(0.35 * t), [0, 0, -3 * squash]),
                            (snap(0.6 * t), [0, 0, 0]),
                        ]),
                    },
                },
            },
        },
    }

# ---------------------------------------------------------------------------- main

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)
        f.write('\n')


def main():
    written = []
    tex = os.path.join(ROOT, 'textures/entity/dungeon')

    # --- the blob, unchanged ------------------------------------------------------------------
    geo = os.path.join(ROOT, 'geo/dungeon_limo.geo.json')
    anim = os.path.join(ROOT, 'animations/dungeon_limo.animation.json')
    write_json(geo, limo_geo())
    write_json(anim, limo_animation())
    blob_texture(os.path.join(tex, 'limo_cueva.png'), (86, 158, 92), (58, 112, 64), (128, 196, 130))
    blob_texture(os.path.join(tex, 'limo_mayor.png'), (62, 108, 78), (38, 70, 52), (96, 150, 108))
    written += [geo, anim, os.path.join(tex, 'limo_cueva.png'),
                os.path.join(tex, 'limo_mayor.png')]

    # The floor boss, on the blobs' bone contract but not their mesh or their timing.
    data, sheet = gran_limo_geo()
    geo = os.path.join(ROOT, 'geo/dungeon_gran_limo.geo.json')
    write_json(geo, data)
    anim = os.path.join(ROOT, 'animations/dungeon_gran_limo.animation.json')
    write_json(anim, limo_animation(heavy=True))
    base = os.path.join(tex, 'gran_limo.png')
    write_png(base, paint(sheet, BIPED_PALETTES['gran_limo'], 83, 128), 128, 128)
    written += [geo, anim, base]

    # --- the humanoids and the golem ----------------------------------------------------------
    # Three rigs on one bone contract, replacing the single vanilla-proportioned box that seven
    # variants wore. Each gets its own clip file rather than sharing one, because the thing that
    # separates them is timing as much as proportion: the raider takes 20 frames to a stride, the
    # guardian 32 and the golem 40, and a heavy that walks at the chaff's cadence is light.
    for rig, palettes in (('raider', ['raider_saqueador', 'raider_arquero',
                                      'raider_carronero', 'raider_vigia']),
                          ('guardian', ['guardian_husk', 'guardian_bone', 'guardian_warden']),
                          ('golem', ['golem_geoda'])):
        build = BIPEDS[rig]
        data, sheet = biped_geo(f'dungeon_{rig}', build)
        geo = os.path.join(ROOT, f'geo/dungeon_{rig}.geo.json')
        write_json(geo, data)
        # The golem only ever walks up and hits: giving it a bow draw and a spell it can never play
        # is how a rig accumulates clips nobody checks.
        clips = (('idle', 'walk', 'attack') if rig == 'golem'
                 else ('idle', 'walk', 'attack', 'shoot', 'cast', 'jump'))
        anim = os.path.join(ROOT, f'animations/dungeon_{rig}.animation.json')
        write_json(anim, biped_animation(build, clips))
        written += [geo, anim]
        size = build['sheet']
        for i, name in enumerate(palettes):
            base = os.path.join(tex, f'{name}.png')
            write_png(base, paint(sheet, BIPED_PALETTES[name], 61 + 7 * i, size), size, size)
            written.append(base)
            if build['shards']:
                glow = os.path.join(tex, f'{name}_glow.png')
                vein = BIPED_PALETTES[name]['shard'][2] + (255,)
                write_png(glow, glow_rows(sheet, vein, size, ('shard',)), size, size)
                written.append(glow)

    # --- the arachnids ------------------------------------------------------------------------
    # cria and tejedora share one animation file: identical bone contract, and the two clips that
    # would differ (a juvenile's shorter stride) are a scale on the same curves, not new keyframes.
    spider_clips = {
        'idle': idle_clip(),
        'walk': walk_clip(),
        'attack': attack_clip(),
        'shoot': shoot_clip(),
        'climb': climb_clip(),
        'jump': jump_clip(),
        'cast': cast_clip(),
    }
    spider_anim = os.path.join(ROOT, 'animations/dungeon_spider.animation.json')
    write_json(spider_anim, arachnid_animation(spider_clips))
    written.append(spider_anim)

    # The queen gets her own file as well as her own rig: her stride is slower and longer, and a
    # boss sharing a walk cycle with the chaff around her is the thing that makes a boss look small.
    reina_clips = dict(spider_clips)
    # 36 frames against the chaff's 24: a longer, heavier stride covering less ground per step.
    reina_clips['walk'] = walk_clip(scale=1.25, forward=0.85, frames=36)
    reina_anim = os.path.join(ROOT, 'animations/dungeon_reina.animation.json')
    write_json(reina_anim, arachnid_animation(reina_clips))
    written.append(reina_anim)

    # The huntress runs the same clips faster and further: 18 frames against the chaff's 24, with a
    # longer fore/aft throw. A mini-boss moving at the speed of the hatchlings around her is the
    # same mistake as a boss walking their cycle, one rank down.
    cazadora_clips = dict(spider_clips)
    cazadora_clips['walk'] = walk_clip(scale=1.1, forward=1.15, frames=18)
    cazadora_anim = os.path.join(ROOT, 'animations/dungeon_cazadora.animation.json')
    write_json(cazadora_anim, arachnid_animation(cazadora_clips))
    written.append(cazadora_anim)

    for name, variants in (('cria', ['spider_cria']),
                           ('tejedora', ['spider_tejedora']),
                           ('cazadora', ['spider_cazadora']),
                           ('reina', ['spider_reina'])):
        # `dungeon_spider_*` names a rig that plays the shared clip file; a build with clips of its
        # own is named after itself. The convention is load-bearing — preview_rig resolves which
        # animation to pose a rig with from its name.
        model = (f'dungeon_{name}' if name in ('reina', 'cazadora')
                 else f'dungeon_spider_{name}')
        data, sheet = arachnid_geo(model, BUILDS[name])
        path = os.path.join(ROOT, f'geo/{model}.geo.json')
        write_json(path, data)
        written.append(path)
        size = BUILDS[name]['sheet']
        seed = {'cria': 11, 'tejedora': 29, 'cazadora': 37, 'reina': 47}[name]
        for variant in variants:
            base = os.path.join(tex, f'{variant}.png')
            write_png(base, paint(sheet, PALETTES[name], seed, size), size, size)
            glow = os.path.join(tex, f'{variant}_glow.png')
            eye = PALETTES[name]['eye'][2] + (255,)
            # Eyes and nothing else, on every variant. The queen's spinneret glowed too for one
            # revision, for a telegraph visible from directly underneath her while she webbed a
            # ceiling. It was dropped while hunting a displaced glow that turned out to be the
            # renderer squaring her scale on the layer pass, not anything about this sheet — but it
            # stays dropped, because it was the one emissive surface in the bestiary big enough to
            # be mistaken for a bug, and a telegraph that reads at the eyes reads.
            write_png(glow, glow_rows(sheet, eye, size, ('eye',)), size, size)
            written += [base, glow]

    # --- the cave's own fauna -----------------------------------------------------------------
    # One rig each, because each of these is a different animal and the whole lesson of §44 is that
    # a shared mesh makes four enemies read as one. What they share is the generator, not the body.
    for name, (geo_fn, anim_fn, texture, glow_parts) in {
        'murcielago': (bat_geo, bat_animation, 'murcielago_gruta', None),
        'escarabajo': (beetle_geo, beetle_animation, 'escarabajo_geoda', ('shard',)),
        'hongo': (fungus_geo, fungus_animation, 'hongo_bombardero', ('pore',)),
        'musgo': (moss_geo, moss_animation, 'musgo_agarrador', None),
        'mastin': (mastiff_geo, mastiff_animation, 'mastin_contrabandista', None),
    }.items():
        data, sheet = geo_fn()
        geo = os.path.join(ROOT, f'geo/dungeon_{name}.geo.json')
        write_json(geo, data)
        anim = os.path.join(ROOT, f'animations/dungeon_{name}.animation.json')
        write_json(anim, anim_fn())
        base = os.path.join(tex, f'{texture}.png')
        write_png(base, paint(sheet, BIPED_PALETTES[texture], 97 + 13 * len(name), 64), 64, 64)
        written += [geo, anim, base]
        if glow_parts:
            glow = os.path.join(tex, f'{texture}_glow.png')
            part = BIPED_PALETTES[texture][glow_parts[0]][2] + (255,)
            write_png(glow, glow_rows(sheet, part, 64, glow_parts), 64, 64)
            written.append(glow)

    # --- the silverfish -----------------------------------------------------------------------
    data, sheet = lepisma_geo()
    path = os.path.join(ROOT, 'geo/dungeon_lepisma.geo.json')
    write_json(path, data)
    anim = os.path.join(ROOT, 'animations/dungeon_lepisma.animation.json')
    write_json(anim, lepisma_animation())
    base = os.path.join(tex, 'lepisma_cueva.png')
    write_png(base, paint(sheet, LEPISMA_PALETTE, 5, 64), 64, 64)
    glow = os.path.join(tex, 'lepisma_cueva_glow.png')
    write_png(glow, glow_rows(sheet, LEPISMA_PALETTE['eye'][2] + (255,), 64), 64, 64)
    written += [path, anim, base, glow]
    # Same rig, same sheet layout, different rock.
    crystal = os.path.join(tex, 'cristal_rastrero.png')
    write_png(crystal, paint(sheet, CRISTAL_PALETTE, 23, 64), 64, 64)
    crystal_glow = os.path.join(tex, 'cristal_rastrero_glow.png')
    write_png(crystal_glow, glow_rows(sheet, CRISTAL_PALETTE['eye'][2] + (255,), 64), 64, 64)
    written += [crystal, crystal_glow]

    for path in written:
        print('wrote', os.path.relpath(path, os.path.join(ROOT, '..', '..', '..', '..')))
    print('\nclips in dungeon_spider:', ', '.join(spider_clips))
    print('clips in dungeon_reina: ', ', '.join(reina_clips))
    print('clips in dungeon_cazadora:', ', '.join(cazadora_clips))
    print('clips in dungeon_lepisma:', ', '.join(lepisma_animation()['animations']))


if __name__ == '__main__':
    main()
