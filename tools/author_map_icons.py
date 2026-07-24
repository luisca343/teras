#!/usr/bin/env python3
"""Generates the minimap's room icons: an 8×8 bitmap font plus its font provider.

    python3 tools/author_map_icons.py

Deterministic and idempotent, like the other authoring tools. Writes:

  assets/teras/textures/gui/dungeon_icons.png   the glyph sheet, 6 across
  assets/teras/font/dungeon_icons.json          the bitmap provider

Letters could not do this job. A glyph has to say "boss" at nine pixels square and be tintable —
a red skull and a grey one are the same icon in two colours — and Minecraft's default font has no
skull in it at all (its symbol range is CP437, which stops at card suits). A bitmap font is the
supported way to add one: the glyphs are drawn white so `drawString`'s colour tints them, and the
overlay picks the tint per room type.

Codepoints are the private-use area from U+E000 in RoomType order, so a new room type appends a
glyph here and a colour in DungeonMapOverlay and nothing else moves.
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                    'src/main/resources/assets/teras')

SIZE = 8
PER_ROW = 6

# '#' opaque white, '.' transparent. Drawn to read at 8px, not to be pretty at 64.
ICONS = [
    # start — a downward stair/entrance arch
    ('start', [
        '........',
        '.######.',
        '.#....#.',
        '.#....#.',
        '.#.##.#.',
        '.#.##.#.',
        '.#.##.#.',
        '........',
    ]),
    # boss / mini_boss — a skull. One glyph, tinted red for the boss and grey for the mini.
    ('skull', [
        '..####..',
        '.######.',
        '.##..##.',
        '.##..##.',
        '.######.',
        '..####..',
        '.#.##.#.',
        '..#..#..',
    ]),
    # shop — a coin with a slot
    ('shop', [
        '..####..',
        '.#....#.',
        '#..##..#',
        '#.#..#.#',
        '#.#..#.#',
        '#..##..#',
        '.#....#.',
        '..####..',
    ]),
    # treasure — a chest with a clasp
    ('treasure', [
        '........',
        '.######.',
        '.#....#.',
        '.######.',
        '.#.##.#.',
        '.#.##.#.',
        '.######.',
        '........',
    ]),
    # secret — a question mark
    ('secret', [
        '..####..',
        '.##..##.',
        '.....##.',
        '...###..',
        '..##....',
        '..##....',
        '........',
        '..##....',
    ]),
    # super_secret — a star, deliberately unlike everything else on the map
    ('super_secret', [
        '...##...',
        '...##...',
        '#.####.#',
        '.######.',
        '..####..',
        '.##..##.',
        '.#....#.',
        '........',
    ]),
    # challenge — crossed blades
    ('challenge', [
        '#......#',
        '.#....#.',
        '..#..#..',
        '...##...',
        '...##...',
        '..#..#..',
        '.#....#.',
        '#......#',
    ]),
    # curse — a closed eye ringed, the shrine's stare
    ('curse', [
        '........',
        '..####..',
        '.#....#.',
        '#..##..#',
        '#..##..#',
        '.#....#.',
        '..####..',
        '........',
    ]),
    # sacrifice — a dagger, point down
    ('sacrifice', [
        '...##...',
        '...##...',
        '...##...',
        '.######.',
        '...##...',
        '...##...',
        '...##...',
        '....#...',
    ]),
    # arcade — a machine with a screen
    ('arcade', [
        '.######.',
        '.#....#.',
        '.#.##.#.',
        '.#.##.#.',
        '.#....#.',
        '.######.',
        '..#..#..',
        '.###.##.',
    ]),
    # devil_deal — horns
    ('devil_deal', [
        '##....##',
        '.##..##.',
        '..####..',
        '.######.',
        '.##..##.',
        '.######.',
        '..#..#..',
        '........',
    ]),
    # sello — the seal ring around its pin: la sala del sello, the way down
    ('sello', [
        '..####..',
        '.#....#.',
        '#..##..#',
        '#..##..#',
        '.#....#.',
        '..####..',
        '........',
        '........',
    ]),
    # orden — the font she stands at, which is the one fixture her chapel has. A vessel rather than
    # a symbol on purpose: the devil's horns opposite it are a *what*, and hers should be a *where*,
    # so a party reading the two flanks off the map is choosing between two rooms and not two creeds.
    ('orden', [
        '.######.',
        '.######.',
        '..####..',
        '...##...',
        '...##...',
        '..####..',
        '.######.',
        '........',
    ]),
]


def write_png(path, rows, width, height):
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            raw += bytes(rows[y][x])

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)


def main():
    cols = PER_ROW
    rows_of_icons = (len(ICONS) + cols - 1) // cols
    width = cols * SIZE
    height = rows_of_icons * SIZE
    pixels = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]

    for index, (_, art) in enumerate(ICONS):
        ox = (index % cols) * SIZE
        oy = (index // cols) * SIZE
        for py, line in enumerate(art):
            for px, ch in enumerate(line):
                if ch == '#':
                    pixels[oy + py][ox + px] = (255, 255, 255, 255)

    png = os.path.join(ROOT, 'textures/gui/dungeon_icons.png')
    write_png(png, pixels, width, height)

    # Each string is one row of the sheet, padded with the null char for unused slots — the
    # provider requires every row to be the same length as the sheet is wide in glyphs.
    chars = []
    for row in range(rows_of_icons):
        line = ''
        for col in range(cols):
            index = row * cols + col
            line += chr(0xE000 + index) if index < len(ICONS) else chr(0)
        chars.append(line)

    font = os.path.join(ROOT, 'font/dungeon_icons.json')
    os.makedirs(os.path.dirname(font), exist_ok=True)
    with open(font, 'w') as f:
        f.write('{\n  "providers": [\n    {\n')
        f.write('      "type": "bitmap",\n')
        f.write('      "file": "teras:gui/dungeon_icons.png",\n')
        f.write('      "ascent": 7,\n      "height": 8,\n')
        f.write('      "chars": [\n')
        # Always \uXXXX: JSON has no \xNN escape, and the padding slot is U+0000.
        f.write(',\n'.join('        "%s"' % ''.join('\\u%04x' % ord(ch) for ch in c)
                           for c in chars))
        f.write('\n      ]\n    }\n  ]\n}\n')

    for index, (name, _) in enumerate(ICONS):
        print('U+%04X  %s' % (0xE000 + index, name))
    print('\nwrote', os.path.relpath(png), 'and', os.path.relpath(font))


if __name__ == '__main__':
    main()
