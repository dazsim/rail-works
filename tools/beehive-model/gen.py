# Generates the formed beehive coke oven: per-part block models, blockstates, textures, CokeOvenShapes.java.
# Oven frame (facing north): x 0..48 px, y 0..64 px, z 0..48 px, front face at z=0, controller cell (1,1,0).
# Usage: python3 tools/beehive-model/gen.py (needs Pillow)
import json, math, os, sys
from PIL import Image

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = REPO + '/src/main/resources/assets/railworks'
JAVA = REPO + '/src/main/java/com/workshopcraft/railworks/block/CokeOvenShapes.java'
TEX = RES + '/textures/block'

AIR, BRICK, FRONT, IRON, FIRE, FLUE, DOME = range(7)
X0, X1, Y0, Y1, Z0, Z1 = 0, 48, 0, 64, -1, 48
vox = {}

def put(x, y, z, m): vox[(x, y, z)] = m
def get(x, y, z): return vox.get((x, y, z), AIR)

def radius(k):
    if k < 4: return 23.5
    t = (4 * k + 2 - 16) / 30.0
    return 23.5 * math.sqrt(1 - t * t)

# dome, solid
for k in range(11):
    r = radius(k)
    for x in range(48):
        for z in range(48):
            if (x + .5 - 24) ** 2 + (z + .5 - 24) ** 2 <= r * r:
                for y in range(4 * k, 4 * k + 4): put(x, y, z, DOME)

# flat front wall with a corbelled cap
for x in range(10, 38):
    for y in range(30):
        for z in range(0, 7): put(x, y, z, FRONT)
for x in range(9, 39):
    for y in range(28, 30): put(x, y, -1, FRONT)

AC = (24.0, 12.0)
def in_opening(x, y):
    if not 16 <= x < 32: return False
    if y < 12: return True
    return (x + .5 - AC[0]) ** 2 + (y + .5 - AC[1]) ** 2 <= 64
def arch_d(x, y): return math.hypot(x + .5 - AC[0], y + .5 - AC[1])
def arch_a(x, y): return math.degrees(math.atan2(y + .5 - AC[1], x + .5 - AC[0]))
def keystone(x, y): return y >= 12 and 8 <= arch_d(x, y) < 14 and 80 <= arch_a(x, y) <= 100

for x in range(9, 39):
    for y in range(12, 28):
        if keystone(x, y): put(x, y, -1, FRONT)

# door recess, iron door, straps, latch, firebox slot with grille
SLOT = (19, 29, 3, 9)
BARS = (20, 23, 24, 27)
for x in range(16, 32):
    for y in range(0, 21):
        if not in_opening(x, y): continue
        for z in range(0, 3): put(x, y, z, AIR)
        put(x, y, 3, IRON)
        if y in (1, 2, 10, 11): put(x, y, 2, IRON)
for y in range(5, 9): put(30, y, 2, IRON)
for x in range(SLOT[0], SLOT[1]):
    for y in range(SLOT[2], SLOT[3]):
        put(x, y, 3, IRON if x in BARS else AIR)
        put(x, y, 4, AIR)
        put(x, y, 5, FIRE)

# chimney with an open flue
for x in range(17, 31):
    for z in range(17, 31):
        for y in range(44, 58): put(x, y, z, BRICK)
for x in range(16, 32):
    for z in range(16, 32):
        for y in range(58, 64): put(x, y, z, BRICK)
for x in range(19, 29):
    for z in range(19, 29):
        put(x, 58, z, FLUE)
        for y in range(59, 64): put(x, y, z, AIR)
for x in range(18, 30):
    for z in range(18, 30):
        if 19 <= x < 29 and 19 <= z < 29: continue
        for y in range(59, 62): put(x, y, z, FLUE)

PARTS = [(x, y, z) for z in range(3) for y in range(3) for x in range(3) if not (y == 2 and x != 1 and z != 1)]
CTRL = (1, 1, 0)
def pidx(c): return c[0] + 3 * c[1] + 9 * c[2]

def cell(x, y, z): return (x // 16, y // 16, z // 16)
def owner(x, y, z, m):
    if m in (IRON, FIRE): return CTRL
    cx, cy, cz = cell(x, y, z)
    cz = max(cz, 0)
    if cy == 3: return (1, 2, 1)
    if cy == 2 and cx != 1 and cz != 1: return (1, 2, cz)
    return (cx, cy, cz)

DIRS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}

def texkey(m, d, x, y, z, lit):
    if m == FIRE: return 'fire_lit' if lit else 'fire'
    if m == FLUE: return 'flue'
    if d == 'north' and m in (FRONT, IRON):
        return 'facade_%d_%d' % (x // 16, y // 16)
    if m == IRON: return 'iron'
    return 'brick'

def faces(lit):
    out = {}
    for (x, y, z), m in vox.items():
        if m in (AIR, DOME): continue
        for d, (dx, dy, dz) in DIRS.items():
            if get(x + dx, y + dy, z + dz) != AIR: continue
            if d == 'down' and y == 0: continue
            o = owner(x, y, z, m)
            t = texkey(m, d, x, y, z, lit)
            if d in ('north', 'south'): plane, a, b = z + (dz > 0), x, y
            elif d in ('west', 'east'): plane, a, b = x + (dx > 0), z, y
            else: plane, a, b = y + (dy > 0), x, z
            out.setdefault((o, d, plane, t, a // 16, b // 16), set()).add((a, b))
    return out

def greedy(cells):
    rects, left = [], set(cells)
    for (a, b) in sorted(cells, key=lambda p: (p[1], p[0])):
        if (a, b) not in left: continue
        w = 1
        while (a + w, b) in left: w += 1
        h = 1
        while all((a + i, b + h) in left for i in range(w)): h += 1
        for i in range(w):
            for j in range(h): left.discard((a + i, b + j))
        rects.append((a, b, a + w, b + h))
    return rects

def uv(d, a0, b0, a1, b1, ca, cb):
    a0, a1, b0, b1 = a0 - 16 * ca, a1 - 16 * ca, b0 - 16 * cb, b1 - 16 * cb
    return {'north': [16 - a1, 16 - b1, 16 - a0, 16 - b0], 'south': [a0, 16 - b1, a1, 16 - b0],
            'west': [a0, 16 - b1, a1, 16 - b0], 'east': [16 - a1, 16 - b1, 16 - a0, 16 - b0],
            'up': [a0, b0, a1, b1], 'down': [a0, 16 - b1, a1, 16 - b0]}[d]

def build_models(lit):
    models = {}
    for (o, d, plane, t, ca, cb), cells in sorted(faces(lit).items()):
        ox, oy, oz = (c * 16 for c in o)
        for a0, b0, a1, b1 in greedy(cells):
            if d in ('north', 'south'): f, to = [a0 - ox, b0 - oy, plane - oz], [a1 - ox, b1 - oy, plane - oz]
            elif d in ('west', 'east'): f, to = [plane - ox, b0 - oy, a0 - oz], [plane - ox, b1 - oy, a1 - oz]
            else: f, to = [a0 - ox, plane - oy, b0 - oz], [a1 - ox, plane - oy, b1 - oz]
            el = {'from': f, 'to': to, 'faces': {d: {'uv': uv(d, a0, b0, a1, b1, ca, cb), 'texture': '#' + t}}}
            if t == 'fire_lit': el['light_emission'] = 15
            models.setdefault(o, []).append(el)
    return models

# dome drawn as octagonal courses: the union of four r x s slabs at 0, 45, 90 and 135 degrees
WALL_TOP = 30
def dome_elements(models):
    cuts = [0, 16, 20, 24, 28, 30, 32, 36, 40, 44]
    slabs = []
    for y0, y1 in zip(cuts, cuts[1:]):
        slabs.append((y0, y1, radius(y0 // 4)))
    for i, (y0, y1, r) in enumerate(slabs):
        ledge = i + 1 == len(slabs) or slabs[i + 1][2] != r
        s_ = r * math.tan(math.radians(22.5))
        ly = y0 // 16
        o = (1, ly, 1)
        rects = [('a', 0, r, s_, 24 - s_, 24 + s_), ('b', 0, s_, r, 24 - r, 24 + r),
                 ('a', 45, r, s_, 24 - s_, 24 + s_), ('a', -45, r, s_, 24 - s_, 24 + s_)]
        for n, (kind, ang, hx, hz, za, zb) in enumerate(rects):
            front = True
            if kind == 'b' and y1 <= WALL_TOP: za, front = 7, False
            xa, xb = 24 - hx, 24 + hx
            xs = sorted({xa, xb} | {v for v in (16, 32) if xa < v < xb})
            zs = sorted({za, zb} | {v for v in (16, 32) if za < v < zb})
            lift = 0.04 * n if ledge else 0
            for x0, x1 in zip(xs, xs[1:]):
                for z0, z1 in zip(zs, zs[1:]):
                    tx, tz, ty = int(x0 // 16), int(z0 // 16), ly
                    fc = {}
                    if ledge: fc['up'] = {'uv': uv('up', x0, z0, x1, z1, tx, tz), 'texture': '#brick'}
                    if kind == 'a':
                        if x0 == xa: fc['west'] = {'uv': uv('west', z0, y0, z1, y1, tz, ty), 'texture': '#brick'}
                        if x1 == xb: fc['east'] = {'uv': uv('east', z0, y0, z1, y1, tz, ty), 'texture': '#brick'}
                    else:
                        if z0 == za and front: fc['north'] = {'uv': uv('north', x0, y0, x1, y1, tx, ty), 'texture': '#brick'}
                        if z1 == zb: fc['south'] = {'uv': uv('south', x0, y0, x1, y1, tx, ty), 'texture': '#brick'}
                    if not fc: continue
                    r3 = lambda v: round(v, 4)
                    el = {'from': [r3(x0 - 16), y0 - 16 * ly, r3(z0 - 16)], 'to': [r3(x1 - 16), r3(y1 + lift - 16 * ly), r3(z1 - 16)], 'faces': fc}
                    if ang: el['rotation'] = {'origin': [8, 0, 8], 'axis': 'y', 'angle': ang}
                    for f in fc.values(): f['uv'] = [r3(v) for v in f['uv']]
                    models.setdefault(o, []).append(el)

def write_model(name, elements):
    used = sorted({e['faces'][d]['texture'][1:] for e in elements for d in e['faces']})
    tex = {'particle': 'railworks:block/coke_oven_bricks'}
    for t in used:
        tex[t] = 'railworks:block/' + {'brick': 'coke_oven_bricks', 'iron': 'coke_oven_iron', 'flue': 'coke_oven_flue',
                                       'fire': 'coke_oven_firebox', 'fire_lit': 'coke_oven_firebox_lit'}.get(t, 'coke_oven_' + t)
    path = RES + '/models/block/coke_oven/' + name + '.json'
    with open(path, 'w') as fh:
        fh.write('{"parent":"minecraft:block/block","textures":' + json.dumps(tex) + ',"elements":[\n')
        fh.write(',\n'.join(json.dumps(e, separators=(',', ':')) for e in elements))
        fh.write('\n]}\n')
    return len(elements)

os.makedirs(RES + '/models/block/coke_oven', exist_ok=True)
unlit, lit = build_models(False), build_models(True)
dome_elements(unlit)
total = 0
for p in PARTS:
    if p == CTRL: continue
    total += write_model('part_%d' % pidx(p), unlit.get(p, []))
total += write_model('door', unlit[CTRL])
write_model('door_lit', lit[CTRL])
print('elements', total)

ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
bs = {}
for f, r in ROT.items():
    for n in range(27):
        c = (n % 3, n // 3 % 3, n // 9)
        v = {'model': 'railworks:block/coke_oven/part_%d' % n} if c in PARTS and c != CTRL else {'model': 'railworks:block/coke_oven_bricks'}
        if r and c in PARTS and c != CTRL: v['y'] = r
        bs['facing=%s,part=%d' % (f, n)] = v
json.dump({'variants': bs}, open(RES + '/blockstates/coke_oven_part.json', 'w'), indent=2)
json.dump({'variants': {'': {'model': 'railworks:block/coke_oven_bricks'}}}, open(RES + '/blockstates/coke_oven_bricks.json', 'w'), indent=2)
bs = {}
for f, r in ROT.items():
    for l in ('false', 'true'):
        v = {'model': 'railworks:block/coke_oven/door' + ('_lit' if l == 'true' else '')}
        if r: v['y'] = r
        bs['facing=%s,lit=%s' % (f, l)] = v
json.dump({'variants': bs}, open(RES + '/blockstates/coke_oven.json', 'w'), indent=2)

# collision and selection follow the drawn dome: octagonal courses, front wall, chimney with its flue filled.
# Each part also carries the empty cells it draws for (top corners, the chimney above), so shapes may reach 1 block out.
def octagon(x, z, r):
    dx, dz = abs(x - 24), abs(z - 24)
    return dx <= r and dz <= r and dx + dz <= r * math.sqrt(2)

DOME_CUTS = [0, 16, 20, 24, 28, 30, 32, 36, 40, 44]
def solid(x, y, z):
    if 10 <= x < 38 and 0 <= y < 30 and 0 <= z < 7: return True
    if 17 <= x < 31 and 17 <= z < 31 and 44 <= y < 58: return True
    if 16 <= x < 32 and 16 <= z < 32 and 58 <= y < 64: return True
    for y0, y1 in zip(DOME_CUTS, DOME_CUTS[1:]):
        if y0 <= y < y1: return octagon(x, z, radius(y0 // 4))
    return False

def cells_of(c):
    out = [c]
    if c[1] == 2 and c[0] == 1 and c[2] != 1: out += [(0, 2, c[2]), (2, 2, c[2])]
    if c == (1, 2, 1): out.append((1, 3, 1))
    return out

G = 1
def boxes(c):
    sub = set()
    for cc in cells_of(c):
        for i in range(16 // G):
            for j in range(16 // G):
                for k in range(16 // G):
                    x, y, z = cc[0] * 16 + i * G, cc[1] * 16 + j * G, cc[2] * 16 + k * G
                    if solid(x + G / 2, y + G / 2, z + G / 2): sub.add(((x - c[0] * 16) // G, (y - c[1] * 16) // G, (z - c[2] * 16) // G))
    out = []
    while sub:
        j, i, k = min((j, i, k) for i, j, k in sub)
        w = 1
        while (i + w, j, k) in sub: w += 1
        d = 1
        while all((i + a, j, k + d) in sub for a in range(w)): d += 1
        h = 1
        while all((i + a, j + h, k + e) in sub for a in range(w) for e in range(d)): h += 1
        for a in range(w):
            for b in range(h):
                for e in range(d): sub.discard((i + a, j + b, k + e))
        out += [i * G, j * G, k * G, (i + w) * G, (j + h) * G, (k + d) * G]
    return out

rows = []
for n in range(27):
    c = (n % 3, n // 3 % 3, n // 9)
    b = boxes(c) if c in PARTS else []
    print('part', n, 'boxes', len(b) // 6)
    rows.append('            {' + ', '.join(map(str, b)) + '}')
with open(JAVA, 'w') as fh:
    fh.write('''package com.workshopcraft.railworks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generated by tools/beehive-model/gen.py: shapes for each part of the formed beehive oven, in pixels, facing north. */
final class CokeOvenShapes {
    private static final int[][] BOXES = {
''' + ',\n'.join(rows) + '''
    };
    private static final List<Map<Direction, VoxelShape>> SHAPES = new ArrayList<>();

    static {
        for (int[] b : BOXES) {
            VoxelShape s = Shapes.empty();
            for (int i = 0; i < b.length; i += 6) s = Shapes.or(s, Block.box(b[i], b[i + 1], b[i + 2], b[i + 3], b[i + 4], b[i + 5]));
            SHAPES.add(Shapes.rotateHorizontal(s.optimize()));
        }
    }

    private CokeOvenShapes() {}

    static VoxelShape get(int part, Direction facing) {
        return SHAPES.get(part).get(facing);
    }
}
''')

# textures
BR = Image.open(TEX + '/coke_oven_bricks.png').convert('RGBA')
def brick_px(x, y):
    return BR.getpixel((15 - x % 16, 15 - y % 16))
def h(*a):
    v = 2166136261
    for n in a: v = ((v ^ (n & 0xffff)) * 16777619) & 0xffffffff
    return v

IRONP = [(36, 36, 40), (48, 48, 53), (60, 60, 66), (82, 82, 89), (112, 112, 118)]
VOUS = [(110, 69, 53), (92, 58, 44), (101, 63, 48)]
MORTAR = (58, 37, 28)
SOOT = (40, 30, 26)

def facade(x, y):
    if in_opening(x, y):
        edge = not (in_opening(x - 1, y) and in_opening(x + 1, y) and in_opening(x, y + 1))
        if y in (1, 2, 10, 11):
            c = IRONP[3] if y in (2, 11) else IRONP[2]
            if x % 4 == 1 and y in (2, 11): c = IRONP[4]
            return c
        if x == 30 and 5 <= y < 9: return IRONP[4] if y == 8 else IRONP[3]
        if SLOT[0] - 1 <= x <= SLOT[1] and SLOT[2] - 1 <= y <= SLOT[3]:
            if x in BARS: return IRONP[3]
            return IRONP[3] if y == SLOT[3] else IRONP[2]
        if edge: return IRONP[4] if (x + y) % 3 == 0 else IRONP[2]
        return IRONP[0] if h(x, y) % 7 == 0 else IRONP[1]
    if y >= 12:
        d, a = arch_d(x, y), arch_a(x, y)
        if keystone(x, y):
            if d >= 13.2 or abs(a - 80) * d / 57.3 < .5 or abs(a - 100) * d / 57.3 < .5: return MORTAR
            return (122, 78, 60) if d > 12 or a > 96 else (110, 69, 53)
        if 8 <= d < 13.2:
            if d >= 12.4: return MORTAR
            n = int(a // 16)
            if min(abs(a - 16 * n), abs(a - 16 * (n + 1))) * d / 57.3 < .55: return MORTAR
            c = VOUS[n % 3]
            if d < 9: return SOOT
            if d > 11.6: c = tuple(max(0, v - 14) for v in c)
            return c
    if 16 <= x < 32 and y < 12 and False: return SOOT
    if (x in (15, 32)) and y < 12: return SOOT if y < 10 else brick_px(x, y)
    return brick_px(x, y)

def save(img, name):
    img.save(TEX + '/' + name + '.png')

for cx in range(3):
    for cy in range(2):
        im = Image.new('RGBA', (16, 16))
        for u in range(16):
            for v in range(16):
                im.putpixel((u, v), facade(cx * 16 + 15 - u, cy * 16 + 15 - v))
        save(im, 'coke_oven_facade_%d_%d' % (cx, cy))

im = Image.new('RGBA', (16, 16))
for u in range(16):
    for v in range(16):
        c = IRONP[1]
        if h(u, v, 3) % 9 == 0: c = IRONP[0]
        if h(u, v, 5) % 13 == 0: c = IRONP[2]
        im.putpixel((u, v), c)
save(im, 'coke_oven_iron')

im = Image.new('RGBA', (16, 16))
for u in range(16):
    for v in range(16):
        n = h(u, v, 11) % 10
        im.putpixel((u, v), (14, 12, 11) if n < 6 else (21, 18, 16) if n < 9 else (30, 25, 22))
save(im, 'coke_oven_flue')

# coal lumps for the firebox, drawn as a fixed pixel map
LUMPS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..aa..bbb..aa...",
    ".aaaa.bbbbaaaa..",
    "aaaaabbbbbaaaacc",
    "ccaaabbbbbbaaccc",
    "cccc.bbbb.ccccc.",
    "ddddd.bb.ddddddd",
    "ddddddddddddddd.",
    "dddddddddddddddd",
    "................",
]
def firebox(frame, lit):
    im = Image.new('RGBA', (16, 16))
    for v in range(16):
        for u in range(16):
            ch = LUMPS[v][u]
            if not lit:
                c = {'.': (24, 21, 19), 'a': (44, 40, 38), 'b': (52, 47, 44), 'c': (38, 34, 32), 'd': (60, 55, 50)}[ch]
                if ch != '.' and h(u, v, 7) % 11 == 0: c = (74, 34, 22)
            else:
                heat = (h(u, v, frame) % 5)
                if ch == '.':
                    c = [(150, 40, 10), (170, 50, 12), (190, 70, 14), (160, 45, 10), (200, 90, 20)][heat] if v > 4 else [(110, 25, 8), (130, 32, 9), (120, 28, 8), (140, 36, 10), (125, 30, 9)][heat]
                else:
                    edge = v + 1 < 16 and LUMPS[v + 1][u] == '.' or u + 1 < 16 and LUMPS[v][u + 1] == '.'
                    base = [(235, 140, 30), (250, 180, 50), (255, 210, 90), (245, 160, 40), (255, 230, 140)]
                    c = base[heat]
                    if edge: c = (200, 80, 15)
            im.putpixel((u, v), c)
    return im
save(firebox(0, False), 'coke_oven_firebox')
strip = Image.new('RGBA', (16, 64))
for f in range(4): strip.paste(firebox(f + 1, True), (0, 16 * f))
save(strip, 'coke_oven_firebox_lit')
json.dump({'animation': {'frametime': 5, 'interpolate': True}}, open(TEX + '/coke_oven_firebox_lit.png.mcmeta', 'w'), indent=2)
print('parts', len(PARTS))
