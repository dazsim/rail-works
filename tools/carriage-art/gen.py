"""Item sprites, item models and the entity sheets for the M6a carriage. Run from the repo root."""
import json
import os
import random
from PIL import Image

RES = 'common/src/main/resources/assets/railworks/'
OUT = RES + 'textures/'
STEEL = {'a': (206, 212, 220), 'b': (138, 146, 158), 'c': (88, 94, 106), 'd': (40, 44, 52)}
CAST = {'a': (132, 128, 124), 'b': (98, 94, 92), 'c': (70, 66, 64), 'd': (34, 32, 32)}
BLACK = {'a': (92, 94, 100), 'b': (52, 54, 60), 'c': (34, 35, 40), 'd': (18, 18, 22)}
WOOD = {'a': (176, 132, 84), 'b': (128, 92, 56), 'c': (96, 66, 40), 'd': (58, 38, 22)}
TREATED = {'a': (122, 94, 64), 'b': (92, 70, 48), 'c': (68, 50, 34), 'd': (36, 26, 18)}
RED = {'a': (226, 92, 70), 'b': (178, 42, 32), 'c': (120, 26, 20), 'd': (60, 14, 10)}
CHOC = {'a': (132, 84, 60), 'b': (98, 58, 40), 'c': (70, 40, 28), 'd': (40, 22, 16)}
CREAM = {'a': (246, 232, 196), 'b': (226, 208, 166), 'c': (184, 164, 124), 'd': (120, 104, 76)}
BRASS = {'a': (240, 206, 120), 'b': (200, 156, 64), 'c': (150, 108, 40), 'd': (90, 62, 22)}
GLASS = {'a': (220, 236, 240), 'b': (168, 196, 208), 'c': (120, 150, 166), 'd': (70, 90, 104)}
CANVAS = {'a': (236, 234, 226), 'b': (204, 202, 194), 'c': (160, 158, 150), 'd': (96, 94, 90)}
MOQ = {'a': (196, 70, 62), 'b': (150, 40, 40), 'c': (104, 26, 28), 'd': (56, 14, 16)}


class S:
    def __init__(self):
        self.im = Image.new('RGBA', (16, 16))

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.im.putpixel((x, y), c + (255,))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def block(self, x0, y0, x1, y1, p):
        """A lit box: light top and left edge, dark bottom and right."""
        self.rect(x0, y0, x1, y1, p['b'])
        self.rect(x0, y0, x1, y0, p['a'])
        self.rect(x0, y0, x0, y1, p['a'])
        self.rect(x0, y1, x1, y1, p['d'])
        self.rect(x1, y0, x1, y1, p['c'])

    def diag(self, x0, y0, n, w, p):
        """A bar going up to the right, w thick."""
        for i in range(n):
            for k in range(w):
                c = p['a'] if k == 0 else p['d'] if k == w - 1 else p['b'] if k < w / 2 + 0.5 else p['c']
                self.px(x0 + i, y0 - i + k, c)

    def save(self, name):
        self.im.save(OUT + 'item/' + name + '.png')
        return name


def rivets():
    s = S()
    for x, y in [(3, 9), (7, 7), (11, 9), (5, 12), (9, 12), (13, 12), (7, 10)]:
        s.rect(x, y, x + 2, y + 1, STEEL['b'])
        s.rect(x, y, x + 1, y, STEEL['a'])
        s.px(x + 1, y + 2, STEEL['c'])
        s.px(x + 1, y + 3, STEEL['d'])
    return s


def ibeam(p, s=None, lo=2):
    s = s or S()
    for i in range(12):
        x, y = lo + i, 13 - i
        s.px(x, y - 2, p['a']); s.px(x + 1, y - 2, p['a'])
        s.px(x, y - 1, p['c'])
        s.px(x, y, p['b']); s.px(x + 1, y, p['c'])
        s.px(x, y + 1, p['d'])
    return s


def pattern(shape):
    s = S()
    s.block(1, 3, 14, 12, WOOD)
    shape(s)
    return s


def rivet_holes(s):
    for x in (4, 8, 12):
        for y in (6, 10):
            s.rect(x - 1, y - 1, x, y, WOOD['d'])


def beam_slot(s):
    s.rect(3, 6, 12, 6, WOOD['d']); s.rect(7, 7, 8, 8, WOOD['d']); s.rect(3, 9, 12, 9, WOOD['d'])


def buffer_slot(s):
    s.rect(3, 6, 4, 9, WOOD['d']); s.rect(5, 7, 10, 8, WOOD['d']); s.rect(11, 5, 12, 10, WOOD['d'])


def channel(p, studs=None):
    s = S()
    for i in range(14):
        x, y = 1 + i, 14 - i
        s.px(x, y - 3, p['a']); s.px(x + 1, y - 3, p['a'])
        s.px(x, y - 2, p['b']); s.px(x, y - 1, p['b'])
        s.px(x, y, p['c']); s.px(x + 1, y, p['d'])
        if studs and i % 3 == 1:
            s.px(x, y - 2, studs['a'])
    return s


def headstock():
    s = S()
    s.block(1, 6, 14, 10, RED)
    for x in (3, 11):
        s.rect(x, 7, x + 1, 9, BLACK['c'])
    s.rect(7, 7, 8, 9, BLACK['b'])
    return s


def crossmember():
    s = S()
    s.block(1, 7, 14, 9, BLACK)
    s.rect(1, 6, 14, 6, BLACK['a'])
    s.rect(1, 10, 14, 10, BLACK['d'])
    for x in (2, 13):
        s.px(x, 8, STEEL['b'])
    return s


def body_bolster():
    s = S()
    s.block(2, 9, 13, 12, BLACK)
    s.block(4, 6, 11, 8, BLACK)
    s.rect(6, 12, 9, 13, STEEL['b']); s.rect(6, 13, 9, 13, STEEL['c'])
    for x in (3, 7, 12):
        s.px(x, 10, STEEL['b'])
    return s


def truss():
    s = S()
    for i in range(14):
        y = 5 + (min(i, 13 - i) * 2 // 3 if True else 0)
        s.px(1 + i, min(y, 9), BLACK['b'])
        s.px(1 + i, min(y, 9) + 1, BLACK['d'])
    for x in (5, 10):
        s.rect(x, 4, x, 8, BLACK['a'])
    s.rect(1, 4, 14, 4, BLACK['c'])
    return s


def stock(head):
    s = S()
    s.block(1, 4, 4, 11, CAST)
    s.block(5, 6, 10, 9, CAST)
    if head:
        s.rect(11, 7, 11, 8, STEEL['c'])
        s.block(12, 2, 14, 13, STEEL)
    return s


def screw():
    s = S()
    for i in range(3):
        x = 2 + i * 4
        s.rect(x, 7, x + 3, 7, STEEL['b']); s.rect(x, 9, x + 3, 9, STEEL['c'])
        s.px(x, 8, STEEL['b']); s.px(x + 3, 8, STEEL['d'])
    s.rect(6, 4, 9, 5, STEEL['a']); s.rect(6, 11, 9, 12, STEEL['c'])
    for x in (6, 8):
        s.rect(x, 6, x, 10, STEEL['d'])
    s.rect(13, 6, 14, 11, BLACK['b']); s.px(14, 5, BLACK['a']); s.px(12, 11, BLACK['d'])
    s.rect(1, 6, 1, 10, BLACK['b'])
    return s


def floor():
    s = S()
    for i, y in enumerate(range(3, 13, 3)):
        s.block(1 + (i % 2), y, 14 - (i % 2 == 0), y + 2, TREATED)
    return s


def side_frame():
    s = S()
    for x in (1, 6, 10, 14):
        s.rect(x, 2, x + 1, 14, TREATED['b']); s.rect(x, 2, x, 14, TREATED['a'])
    for y in (2, 9, 13):
        s.rect(1, y, 15, y + 1, TREATED['b']); s.rect(1, y, 15, y, TREATED['a'])
    return s


def end_frame():
    s = S()
    for x in (2, 13):
        s.rect(x, 2, x + 1, 14, TREATED['b']); s.rect(x, 2, x, 14, TREATED['a'])
    s.rect(2, 2, 14, 3, TREATED['b']); s.rect(2, 13, 14, 14, TREATED['c'])
    s.rect(7, 2, 8, 14, TREATED['c'])
    return s


def panel(beaded):
    s = S()
    s.rect(1, 1, 14, 14, CHOC['b'])
    s.rect(1, 1, 14, 8, CREAM['b'])
    s.rect(1, 9, 14, 9, BRASS['b'])
    for x0 in (2, 9):
        s.rect(x0, 2, x0 + 4, 7, GLASS['b']); s.px(x0, 2, GLASS['a']); s.px(x0 + 1, 3, GLASS['a'])
    if beaded:
        s.rect(2, 11, 13, 11, CHOC['c']); s.rect(2, 13, 13, 13, CHOC['c'])
        s.rect(7, 1, 8, 8, CREAM['c'])
    else:
        s.rect(1, 14, 14, 14, STEEL['c'])
    s.rect(1, 1, 1, 14, CHOC['d']); s.rect(14, 1, 14, 14, CHOC['d'])
    return s


def door():
    s = S()
    s.rect(3, 1, 12, 14, CHOC['b'])
    s.rect(3, 1, 12, 7, CREAM['b'])
    s.rect(5, 2, 10, 6, GLASS['b']); s.px(5, 2, GLASS['a'])
    s.rect(3, 8, 12, 8, BRASS['b'])
    s.rect(10, 9, 11, 10, BRASS['a'])
    s.rect(4, 11, 11, 11, CHOC['c'])
    s.rect(12, 1, 12, 14, CHOC['d']); s.rect(3, 14, 12, 14, CHOC['d'])
    return s


def end_panel():
    s = S()
    s.rect(1, 3, 14, 14, CHOC['b'])
    s.rect(2, 1, 13, 2, CHOC['b'])
    s.rect(6, 4, 9, 8, GLASS['b'])
    s.rect(1, 10, 14, 10, BRASS['b'])
    s.rect(1, 3, 1, 14, CHOC['d']); s.rect(14, 3, 14, 14, CHOC['d'])
    return s


def roof():
    s = S()
    for x in range(1, 15):
        top = 6 - int(round(3 * (1 - ((x - 7.5) / 7) ** 2)))
        s.rect(x, top, x, top + 3, CANVAS['b'])
        s.px(x, top, CANVAS['a'])
        s.px(x, top + 4, TREATED['c'])
    s.rect(6, 1, 9, 2, BLACK['b']); s.rect(7, 0, 8, 0, BLACK['a'])
    for y in (8, 11):
        s.rect(2, y, 13, y, CANVAS['c'])
    return s


def bench():
    s = S()
    s.block(2, 2, 4, 13, TREATED)
    s.block(4, 3, 6, 10, MOQ)
    s.block(4, 9, 13, 11, MOQ)
    s.rect(5, 12, 5, 14, TREATED['c']); s.rect(12, 12, 12, 14, TREATED['c'])
    return s


def rack():
    s = S()
    s.rect(1, 4, 14, 4, BRASS['a']); s.rect(1, 10, 14, 10, BRASS['b'])
    for x in range(2, 14, 2):
        for y in range(5, 10):
            if (x + y) % 2 == 0:
                s.px(x, y, (196, 180, 140))
    for x in (2, 13):
        s.rect(x, 2, x, 12, BRASS['c'])
    return s


def lamp():
    s = S()
    s.rect(7, 1, 8, 4, BRASS['c'])
    s.block(4, 5, 11, 7, BRASS)
    for y, w in [(8, 3), (9, 3), (10, 2), (11, 1)]:
        s.rect(7 - w, y, 8 + w, y, GLASS['a'] if y == 8 else GLASS['b'])
    s.px(7, 9, (255, 240, 180)); s.px(8, 9, (255, 240, 180))
    return s


def screen():
    s = S()
    s.block(3, 1, 12, 14, TREATED)
    s.rect(5, 3, 10, 8, GLASS['b']); s.px(5, 3, GLASS['a'])
    s.rect(5, 10, 10, 12, TREATED['c'])
    return s


def chairs():
    s = S()
    for x0 in (1, 9):
        s.block(x0, 2, x0 + 1, 12, TREATED)
        s.block(x0 + 2, 8, x0 + 5, 10, MOQ)
        s.rect(x0 + 2, 11, x0 + 2, 14, TREATED['c']); s.rect(x0 + 5, 11, x0 + 5, 14, TREATED['c'])
    return s


def table():
    s = S()
    s.block(1, 4, 14, 6, WOOD)
    s.rect(7, 7, 8, 12, TREATED['b']); s.rect(8, 7, 8, 12, TREATED['c'])
    s.block(4, 13, 11, 14, TREATED)
    return s


def berth():
    s = S()
    s.block(1, 1, 2, 14, TREATED); s.block(13, 1, 14, 14, TREATED)
    for y in (3, 9):
        s.block(3, y, 12, y + 2, MOQ)
        s.rect(3, y, 5, y, CANVAS['a'])
        s.rect(3, y + 3, 12, y + 3, TREATED['c'])
    for y in (5, 8, 11):
        s.rect(10, y, 12, y, BRASS['b'])
    return s


def cupboard():
    s = S()
    s.block(1, 3, 8, 14, WOOD)
    s.rect(1, 2, 9, 2, TREATED['a'])
    s.px(7, 8, BRASS['a'])
    s.rect(10, 9, 14, 10, TREATED['b']); s.rect(10, 9, 14, 9, TREATED['a'])
    s.rect(14, 11, 14, 14, BRASS['c'])
    return s


def locker():
    s = S()
    s.block(2, 3, 13, 14, WOOD)
    s.rect(2, 2, 13, 2, TREATED['a'])
    s.rect(7, 4, 8, 13, BRASS['c'])
    s.px(6, 8, BRASS['a']); s.px(9, 8, BRASS['a'])
    return s


def trunk():
    s = S()
    s.block(1, 6, 14, 14, CHOC)
    s.block(1, 4, 14, 6, BLACK)
    for x in (4, 11):
        s.rect(x, 7, x, 14, BLACK['c'])
    s.rect(7, 7, 8, 8, BRASS['a'])
    return s


def barrel():
    s = S()
    for y in range(2, 15):
        w = 5 if y in (2, 14) else 6
        s.rect(8 - w, y, 7 + w, y, WOOD['b'])
        s.px(8 - w, y, WOOD['a']); s.px(7 + w, y, WOOD['c'])
    for y in (4, 12):
        s.rect(2, y, 13, y, BLACK['b'])
    for x in (5, 9):
        s.rect(x, 3, x, 13, WOOD['c'])
    return s


def workbench():
    s = S()
    s.block(1, 5, 14, 7, WOOD)
    for x in (2, 12):
        s.rect(x, 8, x + 1, 14, TREATED['b'])
    s.rect(3, 11, 12, 11, TREATED['c'])
    s.block(10, 2, 13, 4, STEEL)
    s.rect(3, 4, 7, 4, BRASS['b'])
    return s


def stove():
    s = S()
    s.rect(6, 0, 8, 6, STEEL['b']); s.rect(6, 0, 6, 6, STEEL['a'])
    s.block(2, 7, 13, 14, BLACK)
    s.rect(1, 7, 14, 7, STEEL['b'])
    s.rect(4, 9, 8, 12, CAST['b'])
    s.rect(5, 10, 7, 11, (255, 150, 50))
    s.rect(10, 12, 12, 13, (255, 120, 40))
    return s


def blank(beaded):
    s = S()
    s.rect(1, 1, 14, 14, CHOC['b'])
    s.rect(1, 1, 14, 8, CREAM['b'])
    s.rect(1, 9, 14, 9, BRASS['b'])
    if beaded:
        s.rect(2, 11, 13, 11, CHOC['c']); s.rect(2, 13, 13, 13, CHOC['c'])
    else:
        s.rect(1, 14, 14, 14, STEEL['c'])
    s.rect(1, 1, 1, 14, CHOC['d']); s.rect(14, 1, 14, 14, CHOC['d'])
    return s



def end_door(wood):
    s = S()
    s.rect(1, 1, 14, 14, CHOC['b'])
    s.rect(5, 2, 10, 14, CHOC['c'] if not wood else TREATED['b'])
    s.rect(6, 3, 9, 6, GLASS['b']); s.px(6, 3, GLASS['a'])
    if wood:
        for y in (8, 10, 12):
            s.rect(6, y, 9, y, TREATED['c'])
    else:
        for y in (8, 13):
            s.px(6, y, STEEL['a']); s.px(9, y, STEEL['a'])
    s.rect(9, 9, 9, 9, BRASS['a'])
    s.rect(1, 1, 1, 14, CHOC['d']); s.rect(14, 1, 14, 14, CHOC['d'])
    return s


def gangway_frame():
    s = S()
    s.rect(3, 1, 12, 2, BLACK['b'])
    s.rect(3, 1, 4, 14, BLACK['b']); s.rect(11, 1, 12, 14, BLACK['b'])
    s.rect(3, 1, 12, 1, BLACK['a'])
    for y in (4, 12):
        s.px(3, y, STEEL['a']); s.px(12, y, STEEL['a'])
    return s


def gangway_bellows():
    s = S()
    for x in range(2, 14):
        c = BLACK['b'] if x % 2 == 0 else BLACK['c']
        s.rect(x, 2, x, 13, c)
    s.rect(2, 1, 13, 1, BLACK['a'])
    s.rect(13, 1, 14, 14, STEEL['b']); s.rect(14, 1, 14, 14, STEEL['c'])
    return s


def gangway_footplate():
    s = S()
    for i in range(6):
        s.rect(2 + i, 9 - i, 9 + i, 9 - i, STEEL['b'])
    s.rect(1, 10, 14, 11, STEEL['c']); s.rect(1, 10, 14, 10, STEEL['a'])
    s.px(5, 7, STEEL['d']); s.px(10, 7, STEEL['d'])
    return s


def partition():
    s = S()
    s.rect(2, 1, 13, 14, TREATED['b'])
    s.rect(4, 2, 11, 6, GLASS['b']); s.px(4, 2, GLASS['a'])
    s.rect(2, 8, 13, 8, TREATED['c'])
    s.rect(2, 1, 2, 14, TREATED['a']); s.rect(13, 1, 13, 14, TREATED['d'])
    return s


def corridor_wall():
    s = S()
    s.rect(1, 1, 14, 14, TREATED['b'])
    s.rect(2, 2, 6, 7, GLASS['b']); s.rect(9, 2, 13, 7, GLASS['b'])
    s.rect(8, 1, 8, 14, TREATED['d'])
    s.px(10, 10, BRASS['a'])
    s.rect(1, 14, 14, 14, TREATED['d'])
    return s


ITEMS = {
    'rivets': rivets(),
    'rivet_pattern': pattern(rivet_holes),
    'steel_beam': ibeam(STEEL),
    'beam_pattern': pattern(beam_slot),
    'rolled_solebar': channel(STEEL),
    'solebar': channel(BLACK, STEEL),
    'headstock': headstock(),
    'crossmember': crossmember(),
    'body_bolster': body_bolster(),
    'truss_rods': truss(),
    'buffer_pattern': pattern(buffer_slot),
    'buffer_stock': stock(False),
    'carriage_buffer': stock(True),
    'screw_coupling': screw(),
    'floor_section': floor(),
    'side_frame': side_frame(),
    'end_frame': end_frame(),
    'steel_side_panel': panel(False),
    'wood_side_panel': panel(True),
    'carriage_door': door(),
    'end_panel': end_panel(),
    'roof_section': roof(),
    'bench_seat': bench(),
    'luggage_rack': rack(),
    'ceiling_lamp': lamp(),
    'carriage_screen': screen(),
    'chair_pair': chairs(),
    'carriage_table': table(),
    'berth': berth(),
    'cupboard_seat': cupboard(),
    'locker': locker(),
    'trunk': trunk(),
    'barrel_fixture': barrel(),
    'carriage_workbench': workbench(),
    'carriage_stove': stove(),
    'steel_blank_panel': blank(False),
    'wood_blank_panel': blank(True),
    'steel_end_door': end_door(False),
    'wood_end_door': end_door(True),
    'gangway_frame': gangway_frame(),
    'gangway_bellows': gangway_bellows(),
    'gangway_footplate': gangway_footplate(),
    'compartment_partition': partition(),
    'corridor_wall': corridor_wall(),
}
for name, s in ITEMS.items():
    s.save(name)
    with open(RES + 'models/item/' + name + '.json', 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'railworks:item/' + name}}, f, indent=2)
        f.write('\n')
    with open(RES + 'items/' + name + '.json', 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'railworks:item/' + name}}, f, indent=2)
        f.write('\n')

# Entity sheet: 3 by 8 zones of 336 by 128, in CarriageModel.Zone order, recoloured from the machine sheet
# so paint wears to steel the same way.
MACHINE = Image.open(OUT + 'entity/machine.png').convert('RGBA')
SRC = {'wood': (0, 0, 128, 128), 'steel': (128, 0, 256, 64), 'fine': (128, 64, 256, 96), 'plate': (128, 96, 256, 128),
       'paint': (0, 128, 128, 256), 'dark': (128, 128, 256, 176), 'brass': (224, 176, 256, 192), 'red': (128, 192, 192, 256),
       'rough': (192, 192, 256, 256)}
MEAN = {'wood': (57, 41, 27), 'steel': (185, 191, 195), 'fine': (122, 129, 141), 'plate': (93, 89, 87), 'paint': (112, 145, 94),
        'dark': (37, 38, 42), 'brass': (183, 142, 59), 'red': (172, 42, 34), 'rough': (80, 74, 70)}
ZW, ZH = 336, 128
ZONES = [
    ('CHOC', 'paint', (98, 58, 40)), ('CREAM', 'paint', (224, 206, 164)), ('BLACK', 'dark', None),
    ('RED', 'red', None), ('STEEL', 'fine', None), ('BRIGHT', 'steel', None),
    ('PLATE', 'plate', None), ('CAST', 'rough', None), ('BRASS', 'brass', None),
    ('LINING', 'brass', (214, 172, 70)), ('TREATED', 'wood', None), ('TEAK', 'wood', (150, 92, 50)),
    ('FLOOR', 'wood', (84, 62, 42)), ('CANVAS', 'paint', (214, 212, 202)), ('CEILING', 'paint', (236, 234, 226)),
    ('MOQUETTE', 'red', (130, 36, 38)), ('GLASS', None, (150, 186, 200)), ('LAMP', None, (255, 236, 170)),
    ('RIVET', 'steel', (214, 214, 206)), ('FIRE', None, (255, 146, 48)),
]


def tinted(key, target):
    im = MACHINE.crop(SRC[key])
    if target is None:
        return im
    m = MEAN[key]
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            k = (r / m[0] + g / m[1] + b / m[2]) / 3
            px[x, y] = tuple(min(255, int(t * k)) for t in target) + (a,)
    return im


sheet = Image.new('RGBA', (1024, 1024))
ghost = Image.new('RGBA', (1024, 1024))
rnd = random.Random(6)
for i, (name, key, target) in enumerate(ZONES):
    u, v = (i % 3) * ZW, (i // 3) * ZH
    if key is None:
        alpha = 96 if name == 'GLASS' else 255
        for y in range(ZH):
            for x in range(ZW):
                d = rnd.randint(-4, 4) + (10 if (x + y) % 37 < 3 and name == 'GLASS' else 0)
                sheet.putpixel((u + x, v + y), tuple(max(0, min(255, c + d)) for c in target) + (alpha,))
        continue
    src = tinted(key, target)
    for y in range(0, ZH, src.size[1]):
        for x in range(0, ZW, src.size[0]):
            sheet.paste(src, (u + x, v + y))
for y in range(1024):
    for x in range(1024):
        n = rnd.randint(-6, 6)
        ghost.putpixel((x, y), (170 + n, 210 + n, 255, 110))
sheet.save(OUT + 'entity/carriage.png')
ghost.save(OUT + 'entity/carriage_ghost.png')
print(len(ITEMS), 'items,', len(ZONES), 'zones')
