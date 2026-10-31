"""Textures for M2 steel content. Run from the repo root."""
import json, random, shutil
from PIL import Image

VAN = '/workspace/mods/mc261src/assets/minecraft/textures/'
OUT = 'common/src/main/resources/assets/railworks/textures/'
rnd = random.Random(26)

def lum(p): return (0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]) / 255

def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if t <= b:
            k = (t - a) / (b - a) if b > a else 0
            return tuple(round(ca[i] + (cb[i] - ca[i]) * k) for i in range(3))
    return stops[-1][1]

def remap(src, stops, mask=None, stretch=True):
    im = Image.open(src).convert('RGBA')
    px = im.load()
    ls = [lum(px[x, y]) for y in range(im.height) for x in range(im.width) if px[x, y][3] and (mask is None or mask(px[x, y]))]
    lo, hi = (min(ls), max(ls)) if stretch else (0, 1)
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if not p[3] or (mask and not mask(p)): continue
            px[x, y] = ramp(stops, (lum(p) - lo) / max(1e-6, hi - lo)) + (p[3],)
    return im

STEEL_HOT = [(0, (96, 22, 6)), (0.35, (168, 52, 12)), (0.7, (226, 104, 28)), (1, (255, 168, 72))]
for kind in ('still', 'flow'):
    remap(VAN + 'block/lava_%s.png' % kind, STEEL_HOT).save(OUT + 'block/molten_steel_%s.png' % kind)
    shutil.copy(VAN + 'block/lava_%s.png.mcmeta' % kind, OUT + 'block/molten_steel_%s.png.mcmeta' % kind)

cool = remap(VAN + 'block/lava_still.png', [(0, (52, 14, 8)), (0.6, (120, 30, 12)), (1, (176, 58, 22))])
cool.crop((0, 0, 16, 16)).save(OUT + 'block/cooling_steel.png')

remap(VAN + 'block/iron_block.png', [(0, (58, 62, 70)), (0.5, (104, 110, 120)), (1, (168, 174, 184))]).save(OUT + 'block/steel_block.png')
remap(VAN + 'block/iron_block.png', [(0, (120, 124, 130)), (0.6, (176, 180, 186)), (1, (226, 230, 236))]).save(OUT + 'block/cast_steel.png')

im = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16):
        wave = ((x * 3 + y * 5 + (x * y) % 7) % 11) / 10
        n = rnd.random()
        v = 64 + int(wave * 22) + int(n * 18)
        c = (v, v + 2, v + 6)
        if n > 0.93: c = (v + 26, v + 20, v + 14)
        if (x + 2 * y) % 9 == 0 and n > 0.4: c = (v - 18, v - 17, v - 14)
        im.putpixel((x, y), c + (255,))
im.save(OUT + 'block/set_steel.png')

remap(VAN + 'item/iron_ingot.png', [(0, (40, 44, 52)), (0.45, (92, 98, 110)), (0.8, (150, 158, 170)), (1, (214, 220, 228))]).save(OUT + 'item/steel_ingot.png')
remap(VAN + 'item/clay_ball.png', [(0, (112, 92, 66)), (0.5, (184, 160, 122)), (1, (232, 216, 184))]).save(OUT + 'item/fire_clay.png')
remap(VAN + 'item/brick.png', [(0, (88, 64, 40)), (0.45, (172, 138, 92)), (0.8, (214, 186, 136)), (1, (240, 222, 180))]).save(OUT + 'item/fire_brick.png')

def is_lava(p): return p[0] > 150 and p[0] > p[2] * 1.8 and p[1] > 40
remap(VAN + 'item/lava_bucket.png', STEEL_HOT, mask=is_lava).save(OUT + 'item/molten_steel_bucket.png')

def draw(pixels, size=16):
    im = Image.new('RGBA', (size, size))
    for (x, y), c in pixels.items():
        if 0 <= x < size and 0 <= y < size: im.putpixel((x, y), c + (255,))
    return im

def bar(col_hi, col_mid, col_lo, col_edge, x0=1, x1=14):
    px = {}
    for i in range(x0, x1 + 1):
        x, y = i, 15 - i
        px[(x, y - 1)] = col_hi
        px[(x, y)] = col_mid
        px[(x + 1, y)] = col_lo
        px[(x, y + 1)] = col_edge
        px[(x + 1, y - 1)] = col_hi if i < x1 else col_edge
    return px

draw(bar((206, 212, 220), (138, 146, 158), (88, 94, 106), (40, 44, 52))).save(OUT + 'item/rail_bar.png')
wood = ((176, 132, 84), (128, 92, 56), (96, 66, 40), (58, 38, 22))
draw(bar(*wood, x0=2, x1=13)).save(OUT + 'item/rail_bar_pattern.png')
px = {}
for bx, by in ((2, 4), (8, 9)):
    for x in range(bx, bx + 6):
        for y in range(by, by + 4):
            top = y == by
            edge = x in (bx, bx + 5) or y == by + 3
            px[(x, y)] = wood[0] if top else wood[3] if edge else wood[1]
draw(px).save(OUT + 'item/ingot_pattern.png')

def ladle(full):
    px = {}
    dark, mid, hi = (44, 46, 52), (92, 96, 104), (150, 154, 162)
    for i in range(7, 15):
        px[(i, 15 - i)] = mid
        px[(i + 1, 15 - i)] = dark
    for x in range(1, 9):
        for y in range(7, 13):
            dx, dy = x - 4.5, (y - 9.5) * 1.3
            if dx * dx + dy * dy <= 13:
                rim = dx * dx + dy * dy > 8 or y >= 11
                px[(x, y)] = (hi if y <= 8 else mid) if rim else ((255, 150, 50) if full else (30, 30, 34))
    if full:
        for (x, y) in [(3, 8), (5, 9), (4, 9)]: px[(x, y)] = (255, 214, 120)
    return draw(px)
ladle(False).save(OUT + 'item/hand_ladle.png')
ladle(True).save(OUT + 'item/hand_ladle_full.png')

gui = Image.open(OUT + 'gui/coke_oven.png').convert('RGBA')
new = gui.copy()
bg = gui.getpixel((20, 20))
for y in range(14, 72):
    for x in range(7, 169): new.putpixel((x, y), bg)
slot = gui.crop((43, 34, 61, 52))
new.paste(slot, (43, 16)); new.paste(slot, (43, 52))
new.paste(gui.crop((45, 54, 59, 68)), (45, 37))
new.paste(gui.crop((66, 34, 90, 51)), (68, 35))
X, Y, W, H = 101, 18, 24, 50
for y in range(Y - 1, Y + H + 1):
    for x in range(X - 1, X + W + 1):
        c = (28, 28, 28)
        if x == X - 1 or y == Y - 1: c = (55, 55, 55)
        if x == X + W or y == Y + H: c = (255, 255, 255)
        new.putpixel((x, y), c + (255,))
new.save(OUT + 'gui/brick_furnace.png')
print('ok')
