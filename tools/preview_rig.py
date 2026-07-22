#!/usr/bin/env python3
"""Renders a GeckoLib rig to a PNG so a model can be looked at without starting Minecraft.

    python3 tools/preview_rig.py                          # every rig, rest pose
    python3 tools/preview_rig.py dungeon_spider_tejedora  # one rig
    python3 tools/preview_rig.py dungeon_reina walk 0.5   # posed by a clip, at a time in seconds
    python3 tools/preview_rig.py dungeon_raider --tex raider_vigia   # with a texture on it
    python3 tools/preview_rig.py --textured               # every rig, wearing a default texture

Writes `build/rig-preview/<rig>.png`: front, side, top and a three-quarter view side by side, plus
whatever the audit found printed to stdout.

## Why this exists

Two bugs shipped that were only visible in game, and both were the same kind: geometry that is
correct in the JSON and wrong on screen. A rig is a few hundred numbers describing where boxes go,
and nothing between authoring it and standing in front of it in a dungeon ever draws it. The unit
tests can check that a bone exists and that a clip is named — they cannot see that a leg is pointing
at the sky.

## The transform, which is the part worth getting right

Taken from GeckoLib 4.9.2's own bytecode (`BakedModelFactory`), not from a wiki:

  * a rotation's **X and Y are negated** and its **Z is not**, for bones and cubes alike;
  * rotations compose Z, then Y, then X — the matrix is `Rz · Ry · Rx`, so X is applied to a vertex
    first;
  * a cube rotates about its own `pivot`, then inherits every bone above it, each rotating about
    the bone's own pivot.

That sign rule is exactly what this tool exists to keep honest. Nothing else in the repo depends on
it, and getting it wrong silently produces a rig that reads fine in a diff.
"""
import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/teras')
OUT = os.path.join(ROOT, 'build/rig-preview')


# ---------------------------------------------------------------------------- maths

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def rot_x(t):
    c, s = math.cos(t), math.sin(t)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def rot_y(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rot_z(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def rotation_matrix(deg):
    """GeckoLib's convention, verbatim: X and Y negated, Z as authored, composed Rz·Ry·Rx.

    Read out of `BakedModelFactory` in geckolib-neoforge-1.21.1-4.9.2, because the difference
    between this and the obvious guess is a rig that looks right in every JSON diff and stands in a
    dungeon with its legs in the air.
    """
    rx, ry, rz = (math.radians(d) for d in deg)
    return mat_mul(rot_z(rz), mat_mul(rot_y(-ry), rot_x(-rx)))


def mirror_point(p):
    """Model space is X-mirrored on load: `pivot.multiply(-1, 1, 1)`."""
    return [-p[0], p[1], p[2]]


def mirror_cube(origin, size):
    """A cube's min corner after the mirror: `-(origin.x + size.x)`, y and z untouched."""
    return [-(origin[0] + size[0]), origin[1], origin[2]]


def apply(m, v):
    return [sum(m[i][j] * v[j] for j in range(3)) for i in range(3)]


# An affine transform as (matrix, translation): a point goes to `matrix · p + translation`. Carrying
# the pair rather than a pivot is what makes composition associative — a pivot-and-rotation pair
# only composes correctly one level deep, which is exactly the bug that made the first version of
# this tool disagree with the game about a three-deep rig.
IDENTITY = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0])


def about(matrix, pivot, offset=(0, 0, 0)):
    """The affine that rotates about `pivot` by `matrix` and then translates by `offset`."""
    moved = apply(matrix, pivot)
    return matrix, [pivot[i] + offset[i] - moved[i] for i in range(3)]


def compose(outer, inner):
    """`outer` applied after `inner`."""
    m_o, t_o = outer
    m_i, t_i = inner
    return mat_mul(m_o, m_i), [apply(m_o, t_i)[i] + t_o[i] for i in range(3)]


def put(affine, point):
    m, t = affine
    return [apply(m, point)[i] + t[i] for i in range(3)]


# ---------------------------------------------------------------------------- animation

def track_value(track, time, default):
    """Linearly interpolated value of one keyed channel, the way GeckoLib reads a clip."""
    if track is None:
        return list(default)
    if not isinstance(track, dict):
        return list(track)
    keys = sorted(track.keys(), key=float)
    if not keys:
        return list(default)

    def value_at(k):
        v = track[k]
        if isinstance(v, dict):
            v = v.get('post') or v.get('vector') or v.get('pre') or default
        return list(v)

    if time <= float(keys[0]):
        return value_at(keys[0])
    if time >= float(keys[-1]):
        return value_at(keys[-1])
    for i in range(len(keys) - 1):
        a, b = float(keys[i]), float(keys[i + 1])
        if a <= time <= b:
            va, vb = value_at(keys[i]), value_at(keys[i + 1])
            f = 0.0 if b == a else (time - a) / (b - a)
            return [va[j] + (vb[j] - va[j]) * f for j in range(3)]
    return value_at(keys[-1])


def pose_of(clip, bone, time):
    """(rotation, position, scale) a clip asks of one bone at `time`."""
    channels = (clip or {}).get('bones', {}).get(bone, {})
    return (track_value(channels.get('rotation'), time, [0, 0, 0]),
            track_value(channels.get('position'), time, [0, 0, 0]),
            track_value(channels.get('scale'), time, [1, 1, 1]))


# ---------------------------------------------------------------------------- rig

def load(name):
    with open(os.path.join(ASSETS, 'geo', name + '.geo.json')) as f:
        geo = json.load(f)['minecraft:geometry'][0]
    bones = {b['name']: b for b in geo['bones']}
    return geo, bones


def cube_corners(cube):
    """The eight corners, already mirrored into the space GeckoLib rotates them in."""
    ox, oy, oz = cube['origin']
    sx, sy, sz = cube['size']
    inflate = cube.get('inflate', 0) or 0
    ox, oy, oz = ox - inflate, oy - inflate, oz - inflate
    sx, sy, sz = sx + 2 * inflate, sy + 2 * inflate, sz + 2 * inflate
    ox, oy, oz = mirror_cube([ox, oy, oz], [sx, sy, sz])
    return [[ox + dx * sx, oy + dy * sy, oz + dz * sz]
            for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]


FACES = [
    ((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)),
    ((0, 4, 5, 1), (0, -1, 0)), ((2, 3, 7, 6), (0, 1, 0)),
    ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1)),
]


def build(name, clip=None, time=0.0):
    """Every cube's eight world-space corners, with the bone it came from."""
    geo, bones = load(name)
    built = []

    def walk(bone_name, parent):
        bone = bones[bone_name]
        pivot = mirror_point(bone.get('pivot', [0, 0, 0]))
        rest = bone.get('rotation', [0, 0, 0])
        anim_rot, anim_pos, _ = pose_of(clip, bone_name, time)
        # A clip's rotation is added to the rest pose, not substituted for it — which is why the
        # splay can live on the bone and the gait still work. Its position is mirrored the same way
        # the geometry is, or an animated slide would run the wrong way along X.
        total = [rest[i] + anim_rot[i] for i in range(3)]
        world = compose(parent, about(rotation_matrix(total), pivot,
                                      [-anim_pos[0], anim_pos[1], anim_pos[2]]))

        for cube in bone.get('cubes', []):
            corners = cube_corners(cube)
            if 'rotation' in cube:
                local = about(rotation_matrix(cube['rotation']),
                              mirror_point(cube.get('pivot', [0, 0, 0])))
                corners = [put(local, c) for c in corners]
            built.append({'bone': bone_name, 'corners': [put(world, c) for c in corners],
                          'uv': cube.get('uv'), 'size': cube['size']})

        for child in geo['bones']:
            if child.get('parent') == bone_name:
                walk(child['name'], world)

    for bone in geo['bones']:
        if not bone.get('parent'):
            walk(bone['name'], IDENTITY)
    return geo, built


# ---------------------------------------------------------------------------- textures

def decode_png(path):
    """Reads an 8-bit RGBA PNG back to rows of tuples — the inverse of write_png, plus the four
    row filters any other encoder is free to have used."""
    with open(path, 'rb') as f:
        data = f.read()
    pos, w, h, idat = 8, 0, 0, b''
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b'IHDR':
            w, h, depth, colour = struct.unpack('>IIBB', body[:10])
            if depth != 8 or colour != 6:
                raise ValueError(f'{path}: only 8-bit RGBA is supported')
        elif tag == b'IDAT':
            idat += body
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = w * 4
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f0 = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if f0 == 1:
                line[i] = (line[i] + a) & 0xff
            elif f0 == 2:
                line[i] = (line[i] + b) & 0xff
            elif f0 == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xff
            elif f0 == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xff
        prev = line
        rows.append([tuple(line[i:i + 4]) for i in range(0, stride, 4)])
    return rows, w, h


# For each face of `FACES`, in order: the box-UV rect it samples and, per corner, which texture
# corner that model corner takes, as (dx,dy,dz) → (u,v) with v=0 at the rect's top. The v axis is
# certain — texture v runs down, model y runs up. The u direction per face is the box-UV net's
# convention as best reconstructed from vanilla sheets; if it is ever found backwards on one face
# the fix is local to this table, and a flipped face on a preview is a nuisance rather than a bug
# shipped — nothing in the game reads this file.
def face_uv(rect_name, dx, dy, dz):
    if rect_name == 'north':
        return dx, 1 - dy
    if rect_name == 'south':
        return 1 - dx, 1 - dy
    if rect_name == 'east':
        return 1 - dz, 1 - dy
    if rect_name == 'west':
        return dz, 1 - dy
    if rect_name == 'top':
        return dx, dz
    return dx, 1 - dz          # bottom


# FACES entries carry normals in the mirrored space build() works in; dx=0 is the mirrored -X face.
FACE_RECTS = {(-1, 0, 0): 'east', (1, 0, 0): 'west', (0, -1, 0): 'bottom',
              (0, 1, 0): 'top', (0, 0, -1): 'north', (0, 0, 1): 'south'}


def uv_rects(uv, size):
    """The six face rectangles of one cube's box-UV slot, keyed by name — faces_of in the
    authoring tool, restated here from the same net."""
    u, v = uv
    sx, sy, sz = (int(math.ceil(s)) for s in size)
    return {
        'top': (u + sz, v, sx, sz),
        'bottom': (u + sz + sx, v, sx, sz),
        'east': (u, v + sz, sz, sy),
        'north': (u + sz, v + sz, sx, sy),
        'west': (u + sz + sx, v + sz, sz, sy),
        'south': (u + 2 * sz + sx, v + sz, sx, sy),
    }


def draw_textured(built, view, size, bounds, tex, tex_w, tex_h):
    """The painter's algorithm again, but each face subdivided to the texture's own pixels.

    Bilinear interpolation across the projected quad is exact here, not an approximation: the
    corners went through an affine transform and an orthographic projection, and both commute
    with the interpolation. `tex_w`/`tex_h` are the geometry's declared sheet size, which the PNG
    is allowed to be a multiple of — that multiple is where the authoring tool hides its extra
    resolution, and this samples at the PNG's grain so the preview shows all of it.
    """
    pixels = [[(24, 24, 28, 255)] * size for _ in range(size)]
    depth = [[1e9] * size for _ in range(size)]
    lo, hi = bounds
    span = max(hi[i] - lo[i] for i in range(3)) or 1
    scale = (size - 12) / span
    png_h = len(tex)
    png_w = len(tex[0])
    sx_png, sy_png = png_w / tex_w, png_h / tex_h

    def to_screen(c):
        px, py, pd = project(c, view)
        centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
        ox, oy, _ = project(centre, view)
        return (size / 2 + (px - ox) * scale, size / 2 + (py - oy) * scale, pd)

    quads = []
    for cube in built:
        if cube.get('uv') is None:
            continue
        pts = [to_screen(c) for c in cube['corners']]
        rects = uv_rects(cube['uv'], cube['size'])
        for idx, normal in FACES:
            rect_name = FACE_RECTS[normal]
            fx, fy, fw, fh = rects[rect_name]
            # The four projected corners keyed by their texture-space corner.
            keyed = {}
            for i in idx:
                dx, dy, dz = i >> 2 & 1, i >> 1 & 1, i & 1
                keyed[face_uv(rect_name, dx, dy, dz)] = pts[i]
            c00, c10 = keyed[(0, 0)], keyed[(1, 0)]
            c01, c11 = keyed[(0, 1)], keyed[(1, 1)]
            shade = 0.55 + 0.45 * abs(normal[1]) + 0.2 * abs(normal[0])
            quads.append((sum(p[2] for p in keyed.values()) / 4,
                          (c00, c10, c01, c11), shade,
                          (fx, fy, fw, fh)))
    quads.sort(key=lambda q: -q[0])

    def lerp2(c00, c10, c01, c11, u, v):
        return [c00[i] * (1 - u) * (1 - v) + c10[i] * u * (1 - v)
                + c01[i] * (1 - u) * v + c11[i] * u * v for i in range(3)]

    for centre, (c00, c10, c01, c11), shade, (fx, fy, fw, fh) in quads:
        pw, ph = max(1, round(fw * sx_png)), max(1, round(fh * sy_png))
        for j in range(ph):
            for i in range(pw):
                ty = int(fy * sy_png) + j
                txx = int(fx * sx_png) + i
                if not (0 <= ty < png_h and 0 <= txx < png_w):
                    continue
                texel = tex[ty][txx]
                if len(texel) > 3 and texel[3] < 8:
                    continue
                sub = [lerp2(c00, c10, c01, c11, u, v)
                       for u, v in ((i / pw, j / ph), ((i + 1) / pw, j / ph),
                                    (i / pw, (j + 1) / ph), ((i + 1) / pw, (j + 1) / ph))]
                quad = [sub[0], sub[1], sub[3], sub[2]]
                d = sum(p[2] for p in sub) / 4
                colour = tuple(min(255, int(v * min(shade, 1.35))) for v in texel[:3])
                xs = [p[0] for p in quad]
                ys = [p[1] for p in quad]
                x0, x1 = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
                y0, y1 = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
                for y in range(y0, y1 + 1):
                    for x in range(x0, x1 + 1):
                        if inside(quad, x + 0.5, y + 0.5) and d < depth[y][x]:
                            depth[y][x] = d
                            pixels[y][x] = colour + (255,)
    if view in ('front', 'side', '3/4'):
        _, gy, _ = to_screen([0, 0, 0])
        gy = int(gy)
        if 0 <= gy < size:
            for x in range(size):
                if pixels[gy][x][:3] == (24, 24, 28):
                    pixels[gy][x] = (70, 74, 86, 255)
    return pixels


# The texture each rig wears when `--textured` asks for everything: one representative variant.
DEFAULT_TEXTURES = {
    'dungeon_raider': 'raider_saqueador',
    'dungeon_guardian': 'guardian_husk',
    'dungeon_golem': 'golem_geoda',
    'dungeon_limo': 'limo_cueva',
    'dungeon_gran_limo': 'gran_limo',
    'dungeon_lepisma': 'lepisma_cueva',
    'dungeon_spider_cria': 'spider_cria',
    'dungeon_spider_tejedora': 'spider_tejedora',
    'dungeon_cazadora': 'spider_cazadora',
    'dungeon_reina': 'spider_reina',
    'dungeon_murcielago': 'murcielago_gruta',
    'dungeon_escarabajo': 'escarabajo_geoda',
    'dungeon_hongo': 'hongo_bombardero',
    'dungeon_musgo': 'musgo_agarrador',
    'dungeon_mastin': 'mastin_contrabandista',
}


# ---------------------------------------------------------------------------- raster

VIEWS = [('front', (0, 2)), ('side', (2, 1)), ('top', (0, 2)), ('3/4', None)]


def project(corner, view):
    x, y, z = corner
    if view == 'front':
        return x, -y, z
    if view == 'side':
        return -z, -y, x
    if view == 'top':
        return x, z, -y
    a = math.radians(35)
    b = math.radians(24)
    px = x * math.cos(a) + z * math.sin(a)
    pz = -x * math.sin(a) + z * math.cos(a)
    return px, -(y * math.cos(b) - pz * math.sin(b)), pz


def draw(built, view, size, bounds, highlight):
    """Painter's algorithm over cube faces, flat-shaded by normal. Good enough to see a rig."""
    pixels = [[(24, 24, 28, 255)] * size for _ in range(size)]
    depth = [[1e9] * size for _ in range(size)]
    lo, hi = bounds
    span = max(hi[i] - lo[i] for i in range(3)) or 1
    scale = (size - 12) / span

    def to_screen(c):
        px, py, pd = project(c, view)
        cx = (lo[0] + hi[0]) / 2
        cy = (lo[1] + hi[1]) / 2
        cz = (lo[2] + hi[2]) / 2
        ox, oy, _ = project([cx, cy, cz], view)
        return (size / 2 + (px - ox) * scale, size / 2 + (py - oy) * scale, pd)

    polys = []
    for cube in built:
        pts = [to_screen(c) for c in cube['corners']]
        for idx, normal in FACES:
            quad = [pts[i] for i in idx]
            world = [cube['corners'][i] for i in idx]
            centre = sum(p[2] for p in quad) / 4
            shade = 0.55 + 0.45 * abs(normal[1]) + 0.2 * abs(normal[0])
            polys.append((centre, quad, shade, cube['bone'], world))
    polys.sort(key=lambda p: -p[0])

    for centre, quad, shade, bone, _ in polys:
        base = (232, 96, 96) if bone in highlight else (150, 160, 190)
        colour = tuple(min(255, int(c * min(shade, 1.35))) for c in base)
        xs = [p[0] for p in quad]
        ys = [p[1] for p in quad]
        x0, x1 = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
        y0, y1 = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if inside(quad, x + 0.5, y + 0.5) and centre < depth[y][x]:
                    depth[y][x] = centre
                    pixels[y][x] = colour + (255,)
    # A floor line, so "this leg is above the body" is answerable at a glance.
    if view in ('front', 'side', '3/4'):
        _, gy, _ = to_screen([0, 0, 0])
        gy = int(gy)
        if 0 <= gy < size:
            for x in range(size):
                if pixels[gy][x][:3] == (24, 24, 28):
                    pixels[gy][x] = (70, 74, 86, 255)
    return pixels


def inside(quad, x, y):
    sign = None
    for i in range(4):
        ax, ay = quad[i][0], quad[i][1]
        bx, by = quad[(i + 1) % 4][0], quad[(i + 1) % 4][1]
        cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        if abs(cross) < 1e-9:
            continue
        s = cross > 0
        if sign is None:
            sign = s
        elif s != sign:
            return False
    return True


def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])
    raw = b''.join(b'\x00' + b''.join(bytes(p[:4]) for p in row) for row in rows)

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n'
                + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 6))
                + chunk(b'IEND', b''))


# ---------------------------------------------------------------------------- audit

def audit(name, geo, built, posed=False):
    """What a person would notice immediately, stated as numbers.

    Everything here was a real bug at least once: a leg segment pointing at the sky, a chain whose
    third cube started somewhere its second one did not end, geometry outside the bounds the entity
    declares. None of them fail a build today; all of them are obvious in one line here.
    """
    problems = []
    body = [c for c in built if c['bone'] in ('body', 'root', 'thorax')]
    body_top = max((max(p[1] for p in c['corners']) for c in body), default=0)
    body_bottom = min((min(p[1] for p in c['corners']) for c in body), default=0)

    # A leg holds the animal up, so its lowest point is below the body and its highest is not far
    # above it. Stated as physics rather than as numbers off this particular rig, because the check
    # has to survive an artist replacing the rig with a different-sized animal.
    # Grouped per leg, not per segment: a coxa is meant to stay up by the body and only the last
    # segment reaches the floor, so asking each bone individually to hold the animal up is a check
    # that fails on a correct rig.
    legs = {}
    for cube in built:
        if cube['bone'].startswith('leg') and cube['bone'] != 'legs':
            chain = cube['bone'].split('_femur')[0].split('_tibia')[0]
            legs.setdefault(chain, []).extend(cube['corners'])
    torso_top = max((max(p[1] for p in c['corners']) for c in built
                     if not c['bone'].startswith('leg')), default=0)
    # Only in the rest pose. A leg in the swing half of a stride is meant to be off the floor, a
    # climber's legs are on a wall, and a pounce is airborne by definition — asking a posed frame to
    # stand on the ground flags the clips that are working.
    for name, corners in ([] if posed else sorted(legs.items())):
        low = min(p[1] for p in corners)
        high = max(p[1] for p in corners)
        if low >= body_bottom:
            problems.append(f'{name}: never reaches below the body — lowest point y{low:.1f} '
                            f'against a body bottom of y{body_bottom:.1f}. It is holding nothing up')
        # A spider's knees ride above its back, so being above the body is not the fault. Being the
        # tallest thing on the animal is: that only happens when a segment has swung past vertical.
        if high > torso_top:
            problems.append(f'{name}: rises to y{high:.1f}, higher than anything on the body '
                            f'(y{torso_top:.1f}). A knee may ride above the back; a leg that is the '
                            f'tallest part of the animal is pointing at the sky')
        # Floating reads exactly as wrong as sinking, and neither shows up in a diff.
        if abs(low) > 1.5:
            problems.append(f'{name}: foot rests at y{low:.1f} rather than on the floor at y0 — '
                            f'the model will look {"sunk into" if low < 0 else "hovering over"} '
                            f'the ground')

    # Chain continuity: consecutive cubes of one bone should touch.
    by_bone = {}
    for cube in built:
        by_bone.setdefault(cube['bone'], []).append(cube)
    for bone, cubes in by_bone.items():
        if not bone.startswith('leg') or len(cubes) < 2:
            continue
        for i in range(len(cubes) - 1):
            a = [sum(p[j] for p in cubes[i]['corners']) / 8 for j in range(3)]
            b = [sum(p[j] for p in cubes[i + 1]['corners']) / 8 for j in range(3)]
            gap = math.dist(a, b)
            reach = max(math.dist(a, p) for p in cubes[i]['corners']) \
                + max(math.dist(b, p) for p in cubes[i + 1]['corners'])
            if gap > reach:
                problems.append(f'{bone}: segment {i} and {i + 1} do not touch '
                                f'(centres {gap:.1f} apart, reach {reach:.1f})')

    # Left against right, in the space GeckoLib draws in rather than the space the file is written
    # in. Every check above passes on a rig whose flanks disagree — each leg reaches the floor, each
    # chain holds together — and the animal still stands with one side fanned and the other folded
    # into a bundle of legs leaving a single point. The rest-pose splay is one Y rotation per leg
    # and the load-time mirror negates Y, so a value shared by both sides is right on one flank and
    # backwards on the other. A posed frame is exempt: a gait desynchronises the sides on purpose.
    def partner(bone):
        for a, b in (('_left', '_right'), ('_l', '_r')):
            if a in bone:
                return bone.replace(a, b, 1)
        return None

    per_bone = {}
    for cube in built:
        per_bone.setdefault(cube['bone'], []).extend(cube['corners'])
    for bone, corners in ([] if posed else sorted(per_bone.items())):
        other = partner(bone)
        if other not in per_bone:
            continue
        mine = sorted([-x, y, z] for x, y, z in corners)
        theirs = sorted(list(p) for p in per_bone[other])
        gap = (max(math.dist(a, b) for a, b in zip(mine, theirs))
               if len(mine) == len(theirs) else float('inf'))
        if gap > 0.01:
            problems.append(f'{bone} and {other} are not mirror images — {gap:.1f} apart at the '
                            f'worst corner. The two sides of the animal are in different poses')

    lo = [min(p[i] for c in built for p in c['corners']) for i in range(3)]
    hi = [max(p[i] for c in built for p in c['corners']) for i in range(3)]
    desc = geo['description']
    vb_w = desc.get('visible_bounds_width', 0) * 16
    vb_h = desc.get('visible_bounds_height', 0) * 16
    if max(hi[0] - lo[0], hi[2] - lo[2]) > vb_w + 1e-6:
        problems.append(f'visible_bounds_width {desc.get("visible_bounds_width")} is too small for '
                        f'{max(hi[0] - lo[0], hi[2] - lo[2]) / 16:.2f}; the model will be culled '
                        f'when its centre leaves the screen')
    if hi[1] - lo[1] > vb_h + 1e-6:
        problems.append(f'visible_bounds_height {desc.get("visible_bounds_height")} is too small '
                        f'for {(hi[1] - lo[1]) / 16:.2f}')
    return problems, (lo, hi)


# ---------------------------------------------------------------------------- main

def render(name, clip_name=None, time=0.0, texture=None):
    clip = None
    if clip_name:
        anim_path = os.path.join(ASSETS, 'animations', animation_for(name))
        with open(anim_path) as f:
            clip = json.load(f)['animations'][clip_name]
    geo, built = build(name, clip, time)
    problems, bounds = audit(name, geo, built, posed=clip is not None)

    size = 300
    if texture:
        tex, _, _ = decode_png(os.path.join(ASSETS, 'textures/entity/dungeon', texture + '.png'))
        desc = geo['description']
        panels = [draw_textured(built, v, size, bounds, tex,
                                desc['texture_width'], desc['texture_height'])
                  for v, _ in VIEWS]
    else:
        highlight = {c['bone'] for c in built
                     if any(p in ' '.join(problems) for p in [c['bone']])} if problems else set()
        panels = [draw(built, v, size, bounds, highlight) for v, _ in VIEWS]
    rows = []
    for y in range(size):
        row = []
        for i, panel in enumerate(panels):
            row += panel[y]
            if i < len(panels) - 1:
                row.append((90, 90, 100, 255))
        rows.append(row)

    label = name + ('' if not clip_name else f'-{clip_name}-{time}') \
        + ('' if not texture else f'-tex-{texture}')
    path = os.path.join(OUT, label + '.png')
    write_png(path, rows)
    print(f'{label}: {len(built)} cubes  bounds x{bounds[0][0]:.1f}..{bounds[1][0]:.1f} '
          f'y{bounds[0][1]:.1f}..{bounds[1][1]:.1f} z{bounds[0][2]:.1f}..{bounds[1][2]:.1f}')
    print(f'  -> {os.path.relpath(path, ROOT)}   [front | side | top | 3/4]')
    for p in problems:
        print(f'  ! {p}')
    return problems


def animation_for(rig):
    """Which clip file a rig plays from. The two arachnid builds share one; everything else is
    named after itself. Spelled out rather than derived by trimming the rig name, because
    `'dungeon_reina'.replace('geo', '')` quietly eats the middle of the word "dungeon"."""
    if rig.startswith('dungeon_spider'):
        return 'dungeon_spider.animation.json'
    return rig + '.animation.json'


def main():
    args = sys.argv[1:]
    texture = None
    if '--tex' in args:
        i = args.index('--tex')
        texture = args[i + 1]
        del args[i:i + 2]
    if '--textured' in args:
        for f in sorted(os.listdir(os.path.join(ASSETS, 'geo'))):
            if f.endswith('.geo.json'):
                rig = f[:-len('.geo.json')]
                render(rig, texture=DEFAULT_TEXTURES.get(rig))
                print()
        return
    if args:
        render(args[0], args[1] if len(args) > 1 else None,
               float(args[2]) if len(args) > 2 else 0.0, texture=texture)
        return
    bad = 0
    for f in sorted(os.listdir(os.path.join(ASSETS, 'geo'))):
        if f.endswith('.geo.json'):
            bad += len(render(f[:-len('.geo.json')]))
            print()
    print('no problems found' if not bad else f'{bad} problems')


if __name__ == '__main__':
    main()
