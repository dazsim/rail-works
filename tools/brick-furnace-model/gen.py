# Generates the formed Brick Furnace: per-part block models, blockstates, textures, BrickFurnaceShapes.java.
# Furnace frame (facing north): x 0..48 px, y 0..80 px, z 0..48 px, door face at z=0, door cell (1,0,0),
# tap on the west face at cell (0,1,1), the hollow shaft is the centre column (1,y,1).
# Usage: python3 tools/brick-furnace-model/gen.py (needs Pillow)
import json, math, os, sys
from PIL import Image

REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = REPO + '/common/src/main/resources/assets/railworks'
JAVA = REPO + '/common/src/main/java/com/workshopcraft/railworks/block/BrickFurnaceShapes.java'
TEX = RES + '/textures/block'

AIR, BRICK, FRONT, IRON, HOT, HIDDEN, SPY, MELT, COALS = range(9)
C = 10
vox = {}
def put(x, y, z, m): vox[(x, y, z)] = m
def get(x, y, z): return vox.get((x, y, z), AIR)

def chamfered(x, z):
    for cx, cz in ((0, 0), (47, 0), (0, 47), (47, 47)):
        if abs(x - cx) + abs(z - cz) < C: return True
    return False

for x in range(48):
    for z in range(48):
        if 16 <= x < 32 and 16 <= z < 32: continue
        for y in range(80):
            put(x, y, z, HIDDEN if chamfered(x, z) else (IRON if y >= 78 else BRICK))

# front: arched door recess with an iron door
AC = (24.0, 17.0)
def in_opening(x, y):
    if not 19 <= x < 29: return False
    if y < 17: return True
    return (x + .5 - AC[0]) ** 2 + (y + .5 - AC[1]) ** 2 <= 25
def arch_d(x, y): return math.hypot(x + .5 - AC[0], y + .5 - AC[1])
def arch_a(x, y): return math.degrees(math.atan2(y + .5 - AC[1], x + .5 - AC[0]))
STRAPS = (3, 9, 15)
for x in range(19, 29):
    for y in range(23):
        if not in_opening(x, y): continue
        put(x, y, 0, IRON if y in STRAPS or (x == 27 and 6 <= y <= 11) else AIR)
        put(x, y, 1, IRON)
for x in (23, 24):
    for y in (18, 19):
        put(x, y, 1, AIR)
        put(x, y, 2, SPY)

# west face: tap frame, sloped channel and stream
TZ = (21, 27)
HOLE = [(-1, y, z) for z in (23, 24) for y in (30, 31)]
for z in range(19, 29):
    for y in range(26, 37): put(-1, y, z, IRON)
for p in HOLE: put(*p, SPY)
def chan_floor(x): return 29 if x >= -5 else 28
for x in range(-10, -1):
    f = chan_floor(x)
    for z in range(TZ[0], TZ[1]):
        put(x, f, z, IRON)
        if z in (TZ[0], TZ[1] - 1):
            put(x, f + 1, z, IRON)
            put(x, f + 2, z, IRON)
TAP_HOT = HOLE + [(x, chan_floor(x) + 1, z) for x in range(-10, -1) for z in range(TZ[0] + 1, TZ[1] - 1)]
def stream(bottom): return [(x, y, z) for x in (-10, -9) for z in (23, 24) for y in range(bottom, 28)]
STREAM = {2: stream(26), 3: stream(9)}

PARTS = [(x, y, z) for z in range(3) for y in range(5) for x in range(3) if not (x == 1 and z == 1)]
CTRL = (1, 0, 0)
TAP = (0, 1, 1)
def pidx(c): return c[0] + 3 * c[1] + 15 * c[2]

def owner(x, y, z, m):
    if x < 0: return TAP
    if m in (IRON, SPY) and z <= 2 and 16 <= x < 32 and y < 30: return CTRL
    return (x // 16, y // 16, z // 16)

DIRS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}

def texkey(m, d, x, y, z, lit):
    if m == SPY: return 'hot' if lit else 'dark'
    if m == HOT: return 'hot'
    if d == 'north' and m in (FRONT, IRON, BRICK) and 16 <= x < 32 and y < 32 and z <= 2:
        return 'facade_%d' % (y // 16)
    if m == IRON: return 'iron'
    return 'brick'

def solid_for_cull(x, y, z, extra):
    if (x, y, z) in extra: return True
    return get(x, y, z) != AIR

def faces(lit, extra):
    out = {}
    allv = dict(vox)
    for p in extra: allv[p] = HOT
    for (x, y, z), m in allv.items():
        if m in (AIR, HIDDEN): continue
        for d, (dx, dy, dz) in DIRS.items():
            n = (x + dx, y + dy, z + dz)
            nm = allv.get(n, AIR)
            if nm != AIR and not (nm == HOT and m != HOT): continue
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

def build_models(lit, extra=()):
    models = {}
    for (o, d, plane, t, ca, cb), cells in sorted(faces(lit, set(extra)).items()):
        ox, oy, oz = (c * 16 for c in o)
        for a0, b0, a1, b1 in greedy(cells):
            if d in ('north', 'south'): f, to = [a0 - ox, b0 - oy, plane - oz], [a1 - ox, b1 - oy, plane - oz]
            elif d in ('west', 'east'): f, to = [plane - ox, b0 - oy, a0 - oz], [plane - ox, b1 - oy, a1 - oz]
            else: f, to = [a0 - ox, plane - oy, b0 - oz], [a1 - ox, plane - oy, b1 - oz]
            el = {'from': f, 'to': to, 'faces': {d: {'uv': uv(d, a0, b0, a1, b1, ca, cb), 'texture': '#' + t}}}
            if t == 'hot': el['light_emission'] = 15
            models.setdefault(o, []).append(el)
    return models

# 45 degree chamfer faces over the stepped corners
L = C * math.sqrt(2)
def chamfers(models):
    corners = {(0, 0): ('north', 45), (2, 0): ('north', -45), (0, 2): ('south', -45), (2, 2): ('south', 45)}
    for (cx, cz), (face, ang) in corners.items():
        mx = 16 * cx + (C / 2 if cx == 0 else 16 - C / 2)
        mz = (C / 2 if cz == 0 else 16 - C / 2)
        lx, lz = mx - 16 * cx, mz
        for cy in range(5):
            spans = [(0, 16, 'brick')] if cy < 4 else [(0, 14, 'brick'), (14, 16, 'iron')]
            for y0, y1, t in spans:
                if face == 'north': z0, z1 = lz, lz + 1
                else: z0, z1 = lz - 1, lz
                fc = {face: {'uv': [0, 16 - y1, round(L, 4), 16 - y0], 'texture': '#' + t}}
                if cy == 4 and y1 == 16: fc['up'] = {'uv': [0, 0, round(L, 4), 1], 'texture': '#iron'}
                el = {'from': [round(lx - L / 2, 4), y0, round(z0, 4)], 'to': [round(lx + L / 2, 4), y1, round(z1, 4)],
                      'rotation': {'origin': [round(lx, 4), 0, round(lz, 4)], 'axis': 'y', 'angle': ang}, 'faces': fc}
                models.setdefault((cx, cy, cz), []).append(el)

# what is seen down the shaft: glowing coke while lit, then the melt rising with the tank level
def shaft(level, lit):
    if level == 0 and not lit: return []
    y = 2 if level == 0 else 2 + 6 * level
    t = 'coals' if level == 0 else 'hot'
    return [{'from': [0, y, 16], 'to': [16, y, 32], 'faces': {'up': {'uv': [0, 0, 16, 16], 'texture': '#' + t}}, 'light_emission': 15}]

TEXNAMES = {'brick': 'fire_bricks', 'iron': 'coke_oven_iron', 'hot': 'molten_steel_still', 'coals': 'coke_oven_firebox_lit',
            'dark': 'coke_oven_flue', 'facade_0': 'brick_furnace_facade_0', 'facade_1': 'brick_furnace_facade_1'}
def write_model(name, elements):
    used = sorted({e['faces'][d]['texture'][1:] for e in elements for d in e['faces']})
    tex = {'particle': 'railworks:block/fire_bricks'}
    for t in used: tex[t] = 'railworks:block/' + TEXNAMES[t]
    path = RES + '/models/block/brick_furnace/' + name + '.json'
    with open(path, 'w') as fh:
        fh.write('{"parent":"minecraft:block/block","textures":' + json.dumps(tex) + ',"elements":[\n')
        fh.write(',\n'.join(json.dumps(e, separators=(',', ':')) for e in elements))
        fh.write('\n]}\n')
    return len(elements)

os.makedirs(RES + '/models/block/brick_furnace', exist_ok=True)
base = build_models(False)
chamfers(base)
lit = build_models(True)
total = 0
for p in PARTS:
    if p in (CTRL, TAP): continue
    total += write_model('part_%d' % pidx(p), base.get(p, []))
for tap in range(4):
    extra = TAP_HOT + STREAM.get(tap, []) if tap else []
    m = build_models(False, extra)
    total += write_model('tap_%d' % tap, m.get(TAP, []))
for l in (False, True):
    src = lit if l else base
    for level in range(5):
        write_model('door_%s_%d' % ('lit' if l else 'unlit', level), src[CTRL] + shaft(level, l))
print('elements', total)

ROT = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
bs = {}
for f, r in ROT.items():
    for n in range(45):
        c = (n % 3, n // 3 % 5, n // 15)
        for tap in range(4):
            if c in PARTS and c != CTRL:
                v = {'model': 'railworks:block/brick_furnace/' + ('tap_%d' % tap if c == TAP else 'part_%d' % n)}
                if r: v['y'] = r
            else:
                v = {'model': 'railworks:block/fire_bricks'}
            bs['facing=%s,part=%d,tap=%d' % (f, n, tap)] = v
json.dump({'variants': bs}, open(RES + '/blockstates/brick_furnace_part.json', 'w'), indent=1)
bs = {}
for f, r in ROT.items():
    for l in ('false', 'true'):
        for level in range(5):
            v = {'model': 'railworks:block/brick_furnace/door_%s_%d' % ('lit' if l == 'true' else 'unlit', level)}
            if r: v['y'] = r
            bs['facing=%s,level=%d,lit=%s' % (f, level, l)] = v
json.dump({'variants': bs}, open(RES + '/blockstates/brick_furnace.json', 'w'), indent=1)

# collision follows the drawn tower; the tap channel stays outside the shapes
def solid(x, y, z):
    m = get(x, y, z)
    if m == HIDDEN:
        for cx, cz in ((0, 0), (47, 0), (0, 47), (47, 47)):
            if abs(x - cx) + abs(z - cz) < C - 1: return False
        return True
    return m not in (AIR, HOT)

def boxes(c):
    sub = set()
    for i in range(16):
        for j in range(16):
            for k in range(16):
                if solid(c[0] * 16 + i, c[1] * 16 + j, c[2] * 16 + k): sub.add((i, j, k))
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
        out += [i, j, k, i + w, j + h, k + d]
    return out

rows = []
for n in range(45):
    c = (n % 3, n // 3 % 5, n // 15)
    b = boxes(c) if c in PARTS else []
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

/** Generated by tools/brick-furnace-model/gen.py: shapes for each part of the formed brick furnace, in pixels, facing north. */
final class BrickFurnaceShapes {
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

    private BrickFurnaceShapes() {}

    static VoxelShape get(int part, Direction facing) {
        return SHAPES.get(part).get(facing);
    }
}
''')

# textures: buff fire bricks recoloured from the coke oven brick pattern
SRC = Image.open(TEX + '/coke_oven_bricks.png').convert('RGB')
PAL = {(0x6e, 0x45, 0x35): (222, 196, 140), (0x5c, 0x3a, 0x2c): (206, 178, 124), (0x4c, 0x30, 0x24): (188, 158, 106),
       (0x3a, 0x25, 0x1c): (164, 134, 90), (0x2e, 0x27, 0x24): (128, 116, 98), (0x3b, 0x33, 0x30): (146, 134, 114)}
def h(*a):
    v = 2166136261
    for n in a: v = ((v ^ (n & 0xffff)) * 16777619) & 0xffffffff
    return v
FB = Image.new('RGBA', (16, 16))
for u in range(16):
    for v in range(16):
        c = PAL.get(SRC.getpixel((u, v)), (190, 160, 110))
        if c[0] > 150 and h(u, v, 2) % 11 == 0: c = tuple(max(0, x - 12) for x in c)
        if c[0] > 150 and h(u, v, 9) % 17 == 0: c = (min(255, c[0] + 6), c[1] - 4, c[2] - 10)
        FB.putpixel((u, v), c + (255,))
FB.save(TEX + '/fire_bricks.png')
def brick_px(x, y): return FB.getpixel((15 - x % 16, 15 - y % 16))

IRONP = [(36, 36, 40), (48, 48, 53), (60, 60, 66), (82, 82, 89), (112, 112, 118)]
VOUS = [(196, 162, 108), (178, 146, 96), (188, 154, 102)]
MORTAR = (128, 116, 98)
SOOT = (70, 60, 50)
def facade(x, y):
    if in_opening(x, y):
        if y in STRAPS: return IRONP[4] if x % 3 == 1 else IRONP[3]
        if x == 27 and 6 <= y <= 11: return IRONP[4] if y == 11 else IRONP[3]
        if x in (23, 24) and y in (18, 19): return IRONP[0]
        edge = not (in_opening(x - 1, y) and in_opening(x + 1, y) and in_opening(x, y + 1))
        if edge: return IRONP[3] if (x + y) % 3 == 0 else IRONP[2]
        return IRONP[0] if h(x, y) % 7 == 0 else IRONP[1]
    if y >= 17:
        d, a = arch_d(x, y), arch_a(x, y)
        if 5 <= d < 8.4:
            if d >= 7.8: return MORTAR
            n = int(a // 20)
            if min(abs(a - 20 * n), abs(a - 20 * (n + 1))) * d / 57.3 < .5: return MORTAR
            c = VOUS[n % 3]
            if d < 5.6: return SOOT
            return c
    if x in (18, 29) and y < 17: return SOOT if y < 15 else brick_px(x, y)
    return brick_px(x, y)[:3]

for cy in range(2):
    im = Image.new('RGBA', (16, 16))
    for u in range(16):
        for v in range(16):
            c = facade(16 + 15 - u, cy * 16 + 15 - v)
            im.putpixel((u, v), tuple(c[:3]) + (255,))
    im.save(TEX + '/brick_furnace_facade_%d.png' % cy)
print('parts', len(PARTS))
