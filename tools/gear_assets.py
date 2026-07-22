"""Grey placeholder art for dungeon gear.

These are what a piece looks like when no Armourer's Workshop skin is configured. AW is a soft
dependency on purpose, so "grey" is a real state the mod supports rather than a stand-in nobody
sees — but it is deliberately plain: legible silhouettes in an inventory, nothing more. Drawn here
rather than recoloured from vanilla art so the mod ships nothing derived from Mojang's textures.
"""
import os, struct, zlib

OUT_TEX = 'src/main/resources/assets/teras/textures/item'
OUT_ARMOR = 'src/main/resources/assets/teras/textures/models/armor'
OUT_MODEL = 'src/main/resources/assets/teras/models/item'

# A three-tone grey so a shape reads at 16x16 without looking like a solid block.
DARK, MID, LIGHT = (60, 60, 66, 255), (110, 110, 118, 255), (160, 160, 170, 255)
NONE = (0, 0, 0, 0)


def png(path, w, h, pixels):
    raw = b''.join(b'\x00' + b''.join(bytes(pixels[y][x]) for x in range(w)) for y in range(h))
    def chunk(tag, data):
        c = tag + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    body = (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(body)


def blank(w=16, h=16):
    return [[NONE] * w for _ in range(h)]


def rect(px, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < len(px[0]) and 0 <= y < len(px):
                px[y][x] = colour


def sword():
    px = blank()
    for i in range(9):                      # blade, running corner to corner
        rect(px, 4 + i, 11 - i, 5 + i, 12 - i, LIGHT if i % 2 else MID)
    rect(px, 3, 12, 5, 14, DARK)            # guard
    rect(px, 2, 13, 4, 15, MID)             # grip
    return px


def axe():
    px = blank()
    rect(px, 4, 13, 5, 15, MID)             # haft
    for i in range(8):
        rect(px, 5 + i, 12 - i, 6 + i, 13 - i, MID)
    rect(px, 8, 2, 13, 7, LIGHT)            # head
    rect(px, 8, 2, 9, 7, DARK)
    return px


def shield():
    px = blank()
    rect(px, 4, 2, 11, 11, MID)
    rect(px, 4, 2, 11, 3, LIGHT)
    rect(px, 5, 12, 10, 13, MID)
    rect(px, 6, 14, 9, 14, DARK)
    rect(px, 7, 5, 8, 9, DARK)              # boss
    return px


def helmet():
    px = blank()
    rect(px, 3, 4, 12, 10, MID)
    rect(px, 3, 4, 12, 5, LIGHT)
    rect(px, 5, 7, 10, 9, DARK)             # visor slit
    rect(px, 3, 11, 4, 12, MID)
    rect(px, 11, 11, 12, 12, MID)
    return px


def chest():
    px = blank()
    rect(px, 4, 3, 11, 12, MID)
    rect(px, 4, 3, 11, 4, LIGHT)
    rect(px, 2, 4, 3, 9, MID)               # pauldrons
    rect(px, 12, 4, 13, 9, MID)
    rect(px, 7, 6, 8, 12, DARK)             # centre seam
    return px


def legs():
    px = blank()
    rect(px, 4, 3, 11, 6, MID)
    rect(px, 4, 3, 11, 3, LIGHT)
    rect(px, 4, 7, 6, 13, MID)
    rect(px, 9, 7, 11, 13, MID)
    rect(px, 7, 7, 8, 13, DARK)
    return px


def boots():
    px = blank()
    rect(px, 3, 6, 6, 11, MID)
    rect(px, 9, 6, 12, 11, MID)
    rect(px, 2, 12, 7, 13, DARK)            # soles
    rect(px, 8, 12, 13, 13, DARK)
    rect(px, 3, 6, 6, 6, LIGHT)
    rect(px, 9, 6, 12, 6, LIGHT)
    return px


def charm():
    px = blank()
    for y in range(6, 13):                  # a hanging stone
        for x in range(5, 11):
            if abs(x - 7.5) + abs(y - 9.5) <= 4:
                px[y][x] = MID
    rect(px, 7, 4, 8, 5, LIGHT)             # loop
    rect(px, 7, 8, 8, 10, DARK)
    return px


def armor_layer(inner):
    """One grey layer for all four pieces. layer_1 is head/chest/boots, layer_2 is legs."""
    px = blank(64, 32)
    if inner:
        rect(px, 0, 0, 63, 31, NONE)
        rect(px, 0, 16, 55, 31, MID)        # the legs layer's leg columns
        rect(px, 0, 16, 55, 17, LIGHT)
    else:
        rect(px, 0, 0, 63, 15, MID)         # helmet band
        rect(px, 0, 0, 63, 1, LIGHT)
        rect(px, 0, 16, 63, 31, MID)        # body + arms
        rect(px, 0, 16, 63, 17, LIGHT)
    return px


SPRITES = {
    'gear_espada': sword, 'gear_hacha': axe, 'gear_escudo': shield,
    'gear_yelmo': helmet, 'gear_coraza': chest, 'gear_grebas': legs,
    'gear_botas': boots, 'gear_amuleto': charm,
}

MODEL = '{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "teras:item/%s"\n  }\n}\n'

# The shield's two models are hand-authored: they carry the display transforms and the `blocking`
# predicate override that give it a raised pose, which a generated sprite model has no way to
# express. Regenerating them from here would silently flatten the shield back to a floating icon.
HAND_AUTHORED_MODELS = {'gear_escudo'}


def main():
    for name, draw in SPRITES.items():
        png(os.path.join(OUT_TEX, name + '.png'), 16, 16, draw())
        os.makedirs(OUT_MODEL, exist_ok=True)
        if name not in HAND_AUTHORED_MODELS:
            with open(os.path.join(OUT_MODEL, name + '.json'), 'w') as f:
                f.write(MODEL % name)
        print('wrote', name)
    png(os.path.join(OUT_ARMOR, 'dungeon_layer_1.png'), 64, 32, armor_layer(False))
    png(os.path.join(OUT_ARMOR, 'dungeon_layer_2.png'), 64, 32, armor_layer(True))
    print('wrote armour layers')


if __name__ == '__main__':
    main()
