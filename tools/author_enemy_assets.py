#!/usr/bin/env python3
"""Generates the placeholder rigs and textures the first-party bestiary needs.

    python3 tools/author_enemy_assets.py

Deterministic, like tools/author_cuevas_rooms.py: same run, same bytes. These are placeholders in
the sense DUNGEONS_PISOS.md §19 means — correct in structure, crude in execution, and meant to be
replaced by an artist without touching code. Every hook the entity drives is present, so replacing
a `.geo.json`, an `.animation.json` or a `.png` is the whole job.

What it writes:

  geo/dungeon_limo.geo.json          a blob rig — one squashable body bone, so a hop reads
  animations/dungeon_limo.animation.json   idle / walk / attack / jump, all squash-and-stretch
  textures/entity/dungeon/limo_cueva.png   green blob
  textures/entity/dungeon/limo_mayor.png   darker, heavier blob
  textures/entity/dungeon/spider_lepisma.png  a pale cave crawler on the spider rig

The animation names are the ones DungeonGeoEnemy's controller can request; BestiaryAudit holds
every variant to them, so a missing clip fails the build rather than playing nothing in game.
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'src/main/resources/assets/teras')

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


def crawler_texture(path, size=64):
    """The cave crawler: pale chitin with darker plates, distinct from every spider sheet."""
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            n = noise(x * 40503 + y * 86969)
            plate = (x // 4 + y // 4) % 2 == 0
            base = (168, 156, 132) if plate else (140, 128, 108)
            if n < 0.12:
                base = (96, 88, 74)
            elif n > 0.93:
                base = (198, 188, 166)
            jitter = int((n - 0.5) * 14)
            row.append((max(0, min(255, base[0] + jitter)),
                        max(0, min(255, base[1] + jitter)),
                        max(0, min(255, base[2] + jitter)),
                        255))
        rows.append(row)
    write_png(path, rows, size, size)

# ---------------------------------------------------------------------------- geo

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
    geo = os.path.join(ROOT, 'geo/dungeon_limo.geo.json')
    anim = os.path.join(ROOT, 'animations/dungeon_limo.animation.json')
    write_json(geo, limo_geo())
    write_json(anim, limo_animation())
    tex = os.path.join(ROOT, 'textures/entity/dungeon')
    blob_texture(os.path.join(tex, 'limo_cueva.png'), (86, 158, 92), (58, 112, 64), (128, 196, 130))
    blob_texture(os.path.join(tex, 'limo_mayor.png'), (62, 108, 78), (38, 70, 52), (96, 150, 108))
    crawler_texture(os.path.join(tex, 'spider_lepisma.png'))
    for path in (geo, anim,
                 os.path.join(tex, 'limo_cueva.png'),
                 os.path.join(tex, 'limo_mayor.png'),
                 os.path.join(tex, 'spider_lepisma.png')):
        print('wrote', os.path.relpath(path, os.path.join(ROOT, '..', '..', '..', '..')))
    print('\nclips in dungeon_limo:', ', '.join(limo_animation()['animations']))


if __name__ == '__main__':
    main()
