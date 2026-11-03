# engine.png: carriage.png with the chocolate, cream and moquette patches repainted green, oxide primer and coal
import random
from PIL import Image

SRC = 'src/main/resources/assets/railworks/textures/entity/carriage.png'
OUT = 'src/main/resources/assets/railworks/textures/entity/engine.png'
ZW, ZH = 336, 128

im = Image.open(SRC).convert('RGBA')


def recolour(i, rgb):
    x, y = (i % 3) * ZW, (i // 3) * ZH
    p = im.crop((x, y, x + ZW, y + ZH))
    px = p.load()
    lums = [sum(px[a, b][:3]) for a in range(ZW) for b in range(ZH)]
    avg = sum(lums) / len(lums)
    for a in range(ZW):
        for b in range(ZH):
            k = sum(px[a, b][:3]) / avg
            px[a, b] = tuple(max(0, min(255, int(c * k))) for c in rgb) + (255,)
    im.paste(p, (x, y))


recolour(0, (30, 78, 46))
recolour(1, (124, 54, 38))
rnd = random.Random(7)
x0, y0 = (15 % 3) * ZW, (15 // 3) * ZH
for a in range(ZW):
    for b in range(ZH):
        v = rnd.randint(18, 34)
        im.putpixel((x0 + a, y0 + b), (v, v, v + 2, 255))
for _ in range(900):
    a, b = rnd.randrange(ZW - 3), rnd.randrange(ZH - 3)
    v = rnd.randint(50, 80)
    for da in range(2):
        for db in range(2):
            im.putpixel((x0 + a + da, y0 + b + db), (v, v, v + 4, 255))
im.save(OUT)

# Item sprites and models for the M8a engine parts
import json
import math

RES = 'src/main/resources/assets/railworks/'
P = {
    'steel': {'a': (206, 212, 220), 'b': (138, 146, 158), 'c': (88, 94, 106), 'd': (40, 44, 52)},
    'cast': {'a': (132, 128, 124), 'b': (98, 94, 92), 'c': (70, 66, 64), 'd': (34, 32, 32)},
    'black': {'a': (92, 94, 100), 'b': (52, 54, 60), 'c': (34, 35, 40), 'd': (18, 18, 22)},
    'wood': {'a': (176, 132, 84), 'b': (128, 92, 56), 'c': (96, 66, 40), 'd': (58, 38, 22)},
    'green': {'a': (70, 140, 92), 'b': (40, 98, 60), 'c': (28, 72, 44), 'd': (14, 40, 24)},
    'oxide': {'a': (176, 92, 70), 'b': (124, 54, 38), 'c': (92, 38, 26), 'd': (54, 20, 14)},
    'brass': {'a': (240, 206, 120), 'b': (200, 156, 64), 'c': (150, 108, 40), 'd': (90, 62, 22)},
    'copper': {'a': (236, 160, 100), 'b': (184, 115, 51), 'c': (136, 80, 34), 'd': (80, 44, 18)},
    'coal': {'a': (80, 80, 86), 'b': (44, 44, 48), 'c': (28, 28, 30), 'd': (12, 12, 14)},
}


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
        self.rect(x0, y0, x1, y1, p['b'])
        self.rect(x0, y0, x1, y0, p['a'])
        self.rect(x0, y0, x0, y1, p['a'])
        self.rect(x0, y1, x1, y1, p['d'])
        self.rect(x1, y0, x1, y1, p['c'])

    def disc(self, cx, cy, r, p, hole=0):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if hole <= d <= r:
                    lit = (x - cx) + (y - cy)
                    self.px(x, y, p['d'] if d > r - 1 and lit > 0 else p['a'] if d > r - 1 else p['c'] if lit > 2 else p['b'])

    def bar(self, x0, y0, x1, y1, p, w=2):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x, y = round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)
            for k in range(w):
                self.px(x, y + k, p['a'] if k == 0 else p['c'])


def wheel(p, spokes=True, s=None):
    s = s or S()
    s.disc(8, 8, 7.5, p['tyre'])
    s.disc(8, 8, 6.2, p['body'])
    if spokes:
        for k in range(8):
            a = k * math.pi / 4
            for r in range(2, 6):
                s.px(int(8 + r * math.cos(a)), int(8 + r * math.sin(a)), p['body']['a'])
        s.disc(8, 8, 6.2, p['body'], hole=5.2)
    s.disc(8, 8, 2, p['tyre'])
    return s


def pattern(fn):
    s = S()
    s.block(1, 1, 14, 14, P['wood'])
    fn(s)
    return s


def groove(s, pts):
    for x, y in pts:
        s.px(x, y, P['wood']['d'])


def circle_pts(cx, cy, r):
    return {(int(cx + r * math.cos(t / 20)), int(cy + r * math.sin(t / 20))) for t in range(126)}


def cylinder(p, bored):
    s = S()
    s.block(2, 4, 13, 11, p)
    s.disc(4, 8, 3.5, p, hole=1.6 if bored else 0)
    if bored:
        s.disc(4, 8, 1.6, P['black'])
    s.rect(9, 3, 12, 3, p['c'])
    return s


def rolled():
    s = S()
    for y in range(3, 13):
        for x in range(2, 14):
            k = abs(y - 8)
            s.px(x, y, P['steel']['a'] if k == 4 else P['steel']['c'] if k == 0 else P['steel']['b'])
    s.rect(13, 3, 13, 12, P['steel']['d'])
    return s


def ring():
    s = S()
    s.disc(8, 8, 7.5, P['steel'], hole=5.5)
    for t in range(0, 360, 45):
        s.px(int(8 + 6.5 * math.cos(math.radians(t))), int(8 + 6.5 * math.sin(math.radians(t))), P['steel']['d'])
    return s


def tubes():
    s = S()
    for y in (3, 6, 9, 12):
        s.bar(1, y, 14, y - 2, P['copper'], 2)
    return s


def barrel():
    s = S()
    s.block(1, 4, 12, 11, P['oxide'])
    s.disc(12, 8, 4, P['oxide'], hole=2)
    for x in (4, 8):
        s.rect(x, 4, x, 11, P['oxide']['d'])
    return s


def firebox():
    s = S()
    s.block(2, 3, 13, 13, P['oxide'])
    s.block(4, 8, 8, 11, P['copper'])
    s.rect(5, 9, 7, 10, (255, 146, 48))
    s.disc(11, 3, 2, P['brass'])
    return s


def smokebox():
    s = S()
    s.disc(8, 8, 7, P['black'])
    s.disc(8, 8, 3, P['black'], hole=2)
    return s


def door():
    s = S()
    s.disc(8, 8, 6.5, P['black'])
    s.rect(4, 8, 12, 8, P['steel']['b'])
    s.disc(8, 8, 1.2, P['steel'])
    return s


def chimney():
    s = S()
    s.block(5, 4, 10, 14, P['black'])
    s.block(4, 1, 11, 4, P['copper'])
    return s


def tank():
    s = S()
    s.block(1, 4, 14, 12, P['oxide'])
    s.rect(2, 5, 13, 5, P['oxide']['a'])
    s.disc(12, 3, 1.8, P['brass'])
    return s


def cab():
    s = S()
    s.block(2, 2, 13, 14, P['oxide'])
    s.rect(1, 1, 14, 2, P['black']['b'])
    s.disc(6, 7, 2.5, (lambda g: {'a': g, 'b': g, 'c': g, 'd': (60, 80, 90)})((168, 196, 208)))
    s.rect(10, 8, 12, 14, P['oxide']['d'])
    return s


def bunker():
    s = S()
    s.block(2, 6, 13, 14, P['oxide'])
    for x in range(3, 13):
        for y in range(3 + (x % 3), 7):
            s.px(x, y, P['coal']['a' if (x + y) % 4 == 0 else 'b'])
    return s


def controls():
    s = S()
    s.block(1, 5, 9, 14, P['copper'])
    s.disc(5, 8, 2.5, {'a': (250, 250, 246), 'b': (236, 234, 226), 'c': (190, 188, 180), 'd': (60, 60, 60)})
    s.bar(9, 4, 14, 1, P['steel'], 2)
    s.bar(11, 14, 14, 6, P['black'], 2)
    return s


def wheelset():
    s = S()
    s.bar(3, 11, 12, 4, P['steel'], 2)
    s.disc(3.5, 11.5, 3.5, P['black'])
    s.disc(12.5, 4.5, 3.5, P['black'])
    s.disc(3.5, 11.5, 1, P['steel'])
    s.disc(12.5, 4.5, 1, P['steel'])
    return s


def motion():
    s = S()
    s.bar(1, 12, 14, 5, P['steel'], 2)
    s.bar(2, 6, 12, 12, P['steel'], 2)
    s.disc(13, 5, 1.6, P['brass'])
    return s


def cylinders():
    s = S()
    s.block(1, 4, 6, 11, P['black'])
    s.block(9, 4, 14, 11, P['black'])
    s.rect(6, 7, 9, 8, P['steel']['b'])
    return s


def rod():
    s = S()
    s.bar(1, 13, 14, 2, P['steel'], 2)
    s.disc(2, 13.5, 1.8, P['steel'])
    s.disc(14, 2.5, 1.8, P['steel'])
    return s


WP = {'tyre': P['steel'], 'body': P['black']}
RP = {'tyre': P['cast'], 'body': P['cast']}
ITEMS = {
    'engine_frames': (lambda s: (s.block(1, 6, 14, 9, P['black']), s.rect(1, 10, 2, 12, P['oxide']['b']), s.rect(13, 10, 14, 12, P['oxide']['b']), s)[-1])(S()),
    'engine_wheel_pattern': pattern(lambda s: groove(s, circle_pts(8, 8, 5) | circle_pts(8, 8, 2))),
    'cylinder_pattern': pattern(lambda s: groove(s, [(x, y) for x in range(3, 13) for y in (4, 11)] + [(x, y) for x in (3, 12) for y in range(4, 12)])),
    'chimney_pattern': pattern(lambda s: groove(s, [(x, y) for x in (5, 10) for y in range(5, 13)] + [(x, 4) for x in range(4, 12)])),
    'smokebox_door_pattern': pattern(lambda s: groove(s, circle_pts(8, 8, 5) | {(x, 8) for x in range(5, 12)})),
    'rough_engine_wheel': wheel(RP),
    'engine_wheel': wheel(WP),
    'rough_cylinder': cylinder(P['cast'], False),
    'bored_cylinder': cylinder(P['cast'], True),
    'coupling_rod': rod(),
    'rolled_plate': rolled(),
    'engine_wheelset': wheelset(),
    'engine_cylinders': cylinders(),
    'valve_motion': motion(),
    'boiler_ring': ring(),
    'boiler_tubes': tubes(),
    'boiler_barrel': barrel(),
    'engine_firebox': firebox(),
    'smokebox': smokebox(),
    'smokebox_door': door(),
    'engine_chimney': chimney(),
    'pannier_tank': tank(),
    'engine_cab': cab(),
    'coal_bunker': bunker(),
    'engine_controls': controls(),
}
for name, s in ITEMS.items():
    s.im.save(RES + 'textures/item/' + name + '.png')
    with open(RES + 'models/item/' + name + '.json', 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'railworks:item/' + name}}, f, indent=2)
        f.write('\n')
    with open(RES + 'items/' + name + '.json', 'w') as f:
        json.dump({'model': {'type': 'minecraft:model', 'model': 'railworks:item/' + name}}, f, indent=2)
        f.write('\n')
print(len(ITEMS), 'engine items')
