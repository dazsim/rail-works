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
