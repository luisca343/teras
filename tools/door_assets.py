#!/usr/bin/env python3
"""Generates the three custom door textures.

    python3 tools/door_assets.py

Deterministic and idempotent, like the other authoring tools. Writes:

  assets/teras/textures/block/reja.png          the combat portcullis
  assets/teras/textures/block/marca_pacto.png   the devil gate's mark
  assets/teras/textures/block/marca_orden.png   the Orden gate's mark

Placeholders in the same sense the shipped rooms are: correct in structure, plain in execution.
Repaint them and nothing in code has to change.

The reja is drawn to tile: bars every 8 pixels and a crossbar at mid-height, so nine of them in a
3x3 doorway read as one lattice instead of nine squares with seams. The two marks are opposites on
purpose — a broken ring falling into a chevron against a closed ring rising into rays — because
they are the run's two counter-poles and a player sees them one floor apart at most.
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'src/main/resources/assets/teras/textures/block')

SIZE = 16

# Bars are 3px wide on an 8px period in BOTH axes, so the lattice is square and survives tiling.
# The period has to divide 16 or the seam between two blocks gets a gap the others do not have,
# which is the one thing that would give away that a gate is nine blocks rather than one.
BAR_PERIOD = 8
BAR_WIDTH = 3
CROSSBAR_Y = (3, 4, 5, 11, 12, 13)

IRON = (0x44, 0x48, 0x4E, 0xFF)
IRON_LIT = (0x6A, 0x70, 0x78, 0xFF)
IRON_DARK = (0x25, 0x28, 0x2C, 0xFF)
RIVET = (0x8A, 0x90, 0x98, 0xFF)
CLEAR = (0, 0, 0, 0)


def reja():
    """Vertical bars, a crossbar, and a rivet at every junction."""
    px = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            in_bar = (x % BAR_PERIOD) < BAR_WIDTH
            in_cross = y in CROSSBAR_Y
            if not in_bar and not in_cross:
                continue
            if in_bar and in_cross:
                px[y][x] = RIVET
            elif in_bar:
                # Left column lit, right shadowed: the bars read as round rather than flat at 16px.
                px[y][x] = (IRON_LIT if (x % BAR_PERIOD) == 0
                            else IRON_DARK if (x % BAR_PERIOD) == BAR_WIDTH - 1 else IRON)
            else:
                px[y][x] = (IRON_LIT if (y % BAR_PERIOD) == CROSSBAR_Y[0] % BAR_PERIOD
                            else IRON_DARK if (y % BAR_PERIOD) == (CROSSBAR_Y[0] + 2) % BAR_PERIOD
                            else IRON)
    return px


# '.' is the base stone, ',' its speckle, '#' the mark, '*' the mark's glow, ' ' unused.
PACTO_ART = [
    '.,...........,..',
    '......****......',
    '...*.*####*.*...',
    '..*.*##..##*.*..',
    '.,..*#*..*#*..,.',
    '....*#....#*....',
    '....*#....#*....',
    '.,..*#*..*#*...,',
    '.....*#**#*.....',
    '......*##*......',
    '.,.....##.....,.',
    '.......##.......',
    '....*..##..*....',
    '.,...*.##.*...,.',
    '......*##*......',
    '.,.....**.....,.',
]

ORDEN_ART = [
    '.,.....**.....,.',
    '......*##*......',
    '....*..##..*....',
    '.......##.......',
    '.,.....##.....,.',
    '......*##*......',
    '.....*#**#*.....',
    '.,..*#*..*#*...,',
    '....*#....#*....',
    '....*#....#*....',
    '.,..*#*..*#*..,.',
    '..*.*##..##*.*..',
    '...*.*####*.*...',
    '..*..*####*..*..',
    '.....****.......',
    '.,...........,..',
]

PACTO_PALETTE = {
    '.': (0x1C, 0x18, 0x20, 0xFF),
    ',': (0x2A, 0x24, 0x30, 0xFF),
    '#': (0xC1, 0x12, 0x1F, 0xFF),
    '*': (0x74, 0x0E, 0x18, 0xFF),
}

ORDEN_PALETTE = {
    '.': (0xE2, 0xDE, 0xD4, 0xFF),
    ',': (0xCB, 0xC5, 0xB8, 0xFF),
    '#': (0xF2, 0xC1, 0x4E, 0xFF),
    '*': (0xB4, 0x8A, 0x2C, 0xFF),
}


def from_art(art, palette):
    return [[palette[char] for char in line] for line in art]


def write_png(path, px):
    raw = b''
    for row in px:
        raw += b'\x00'
        for pixel in row:
            raw += bytes(pixel)

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)


def main():
    write_png(os.path.join(ROOT, 'reja.png'), reja())
    write_png(os.path.join(ROOT, 'marca_pacto.png'), from_art(PACTO_ART, PACTO_PALETTE))
    write_png(os.path.join(ROOT, 'marca_orden.png'), from_art(ORDEN_ART, ORDEN_PALETTE))
    print('wrote reja.png, marca_pacto.png, marca_orden.png')


if __name__ == '__main__':
    main()
