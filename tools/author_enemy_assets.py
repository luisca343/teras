#!/usr/bin/env python3
"""Generates the rigs, animations and textures the first-party bestiary needs.

    python3 tools/author_enemy_assets.py

Deterministic, like tools/author_cuevas_rooms.py: same run, same bytes. Nothing here is hand-edited
afterwards — an artist replacing a rig replaces the `.geo.json`, the `.animation.json` and the
`.png` together, and the entity keeps working because every hook it drives is named the same way.

What it writes:

  geo/dungeon_limo.geo.json            a blob rig — one squashable body bone, so a hop reads
  geo/dungeon_spider_cria.geo.json     juvenile arachnid: big head, short legs, light abdomen
  geo/dungeon_spider_tejedora.geo.json mature arachnid: heavy spinneret abdomen, long legs
  geo/dungeon_reina.geo.json           the queen — the same skeleton at boss mass
  geo/dungeon_lepisma.geo.json         a silverfish: segmented, low, six legs, no spider anatomy
  animations/*.animation.json          one per rig, clips named for DungeonGeoEnemy's controller
  textures/entity/dungeon/*.png        a base sheet and a `_glow` sheet per variant

The three arachnid rigs share a bone contract (see BONES) so they can share a gait generator and,
where it makes sense, an animation file. The silverfish deliberately does not: it is not a spider,
and putting it on the spider skeleton is what made it read as one.

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
    yaw = {0: -26.0, 1: -8.0, 2: 8.0, 3: 26.0}[row]
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
    eyes = []
    for i, (ox, oy) in enumerate(build['eye_rows']):
        for j, (dx, dy) in enumerate(((0, 0), (0.0, -1.2))):
            eyes.append({
                'origin': [round(ox + dx - ex / 2, 3), round(body_y + oy + dy - ey / 2, 3),
                           round(-bz / 2 - hz - ez, 3)],
                'size': [ex, ey, ez],
                'uv': sheet.slot((ex, ey, ez), 'eye'),
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
                'rotation': [0, {0: -22.0, 1: 0.0, 2: 22.0}[row], swing_to_json(drop, sx)],
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
                    if part in ('body', 'abdomen', 'segment') and ((y - fy) // 2) % 2 == 0:
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
                    if part == 'leg' and (x - fx) % 4 == 3:
                        c = shade(dark, -6)   # joint banding along the segment
                    if part == 'eye':
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

LEPISMA_PALETTE = {
    'segment': ((162, 154, 138), (112, 104, 92), (196, 188, 172)),
    'head': ((152, 144, 130), (104, 98, 88), (186, 178, 164)),
    'eye': ((92, 178, 188), (48, 106, 116), (150, 226, 234)),
    'antenna': ((132, 124, 112), (88, 82, 74), (168, 160, 146)),
    'leg': ((124, 117, 105), (82, 77, 69), (158, 150, 138)),
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


def keyframes(pairs):
    return {str(t): v for t, v in pairs}


def limo_animation():
    """idle / walk / attack / jump, all built from scaling one bone.

    `walk` exists and is deliberately the same gentle pulse as `idle`: a HOPPER never plays it —
    the controller picks `jump` while airborne — but the clip must exist, because the audit holds
    every rig to the full set the controller can request.
    """
    pulse = {
        'loop': True,
        'animation_length': 1.2,
        'bones': {
            'body': {
                'scale': keyframes([
                    (0.0, [1.0, 1.0, 1.0]),
                    (0.6, [1.06, 0.9, 1.06]),
                    (1.2, [1.0, 1.0, 1.0]),
                ]),
            },
        },
    }
    return {
        'format_version': '1.8.0',
        'animations': {
            'idle': pulse,
            'walk': json.loads(json.dumps(pulse)),
            # The landing: flattened hard, then overshoot tall, then settle.
            'jump': {
                'loop': False,
                'animation_length': 0.8,
                'bones': {
                    'body': {
                        'scale': keyframes([
                            (0.0, [1.25, 0.6, 1.25]),
                            (0.2, [0.85, 1.35, 0.85]),
                            (0.5, [1.1, 0.85, 1.1]),
                            (0.8, [1.0, 1.0, 1.0]),
                        ]),
                    },
                },
            },
            # A lunge: wind up short and wide, then throw the whole body forward.
            'attack': {
                'loop': False,
                'animation_length': 0.6,
                'bones': {
                    'body': {
                        'scale': keyframes([
                            (0.0, [1.0, 1.0, 1.0]),
                            (0.15, [1.2, 0.75, 1.2]),
                            (0.35, [0.9, 1.2, 0.9]),
                            (0.6, [1.0, 1.0, 1.0]),
                        ]),
                        'position': keyframes([
                            (0.0, [0, 0, 0]),
                            (0.35, [0, 0, -3]),
                            (0.6, [0, 0, 0]),
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

    for name, variants in (('cria', ['spider_cria']),
                           ('tejedora', ['spider_tejedora']),
                           ('reina', ['spider_reina'])):
        model = 'dungeon_reina' if name == 'reina' else f'dungeon_spider_{name}'
        data, sheet = arachnid_geo(model, BUILDS[name])
        path = os.path.join(ROOT, f'geo/{model}.geo.json')
        write_json(path, data)
        written.append(path)
        size = BUILDS[name]['sheet']
        seed = {'cria': 11, 'tejedora': 29, 'reina': 47}[name]
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

    for path in written:
        print('wrote', os.path.relpath(path, os.path.join(ROOT, '..', '..', '..', '..')))
    print('\nclips in dungeon_spider:', ', '.join(spider_clips))
    print('clips in dungeon_reina: ', ', '.join(reina_clips))
    print('clips in dungeon_lepisma:', ', '.join(lepisma_animation()['animations']))


if __name__ == '__main__':
    main()
