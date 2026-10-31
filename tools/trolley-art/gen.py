"""Item sprites and the entity texture for the M4 hand trolley. Run from the repo root."""
from PIL import Image

OUT = 'common/src/main/resources/assets/railworks/textures/'
STEEL = {'a': (206, 212, 220), 'b': (138, 146, 158), 'c': (88, 94, 106), 'd': (40, 44, 52)}
WOOD = {'a': (176, 132, 84), 'b': (128, 92, 56), 'c': (96, 66, 40), 'd': (58, 38, 22)}
TREATED = {'a': (122, 94, 64), 'b': (92, 70, 48), 'c': (68, 50, 34), 'd': (36, 26, 18)}
RED = {'a': (226, 92, 70), 'b': (178, 42, 32), 'c': (120, 26, 20), 'd': (60, 14, 10)}


def sprite(rows, *pals):
    im = Image.new('RGBA', (16, 16))
    for y, r in enumerate(rows):
        assert len(r) == 16, r
        for x, ch in enumerate(r):
            if ch == '.':
                continue
            pal = pals[0]
            if ch.isupper():
                pal, ch = pals[1], ch.lower()
            elif ch in 'efgh':
                pal, ch = pals[2], 'abcd'['efgh'.index(ch)]
            im.putpixel((x, y), pal[ch] + (255,))
    return im


WHEEL = [
    "................",
    ".....dddddd.....",
    "...ddbbbbbbdd...",
    "..dbbaccccabbd..",
    "..dbacdbbdcabd..",
    ".dbac.dbbd.cabd.",
    ".dbcddbccbddcbd.",
    ".dbcbbcaacbbcbd.",
    ".dbcbbcaacbbcbd.",
    ".dbcddbccbddcbd.",
    ".dbac.dbbd.cabd.",
    "..dbacdbbdcabd..",
    "..dbbaccccabbd..",
    "...ddbbbbbbdd...",
    ".....dddddd.....",
    "................",
]
AXLE = [
    "................",
    "............dd..",
    "...........dabd.",
    "..........dabcd.",
    ".........dbbcd..",
    "........dbacd...",
    ".......dbacd....",
    "......dbacd.....",
    ".....dbacd......",
    "....dbacd.......",
    "...dbbcd........",
    "..dabcd.........",
    ".dabcd..........",
    ".dbcd...........",
    "..dd............",
    "................",
]
WHEELSET = [
    "................",
    "................",
    "..dd........dd..",
    ".dbad......dbad.",
    ".dbad......dbad.",
    ".dbadd....ddbad.",
    ".dbacddddddcbad.",
    ".dbaabbbbbbabad.",
    ".dbacccccccdbad.",
    ".dbadd....ddbad.",
    ".dbad......dbad.",
    ".dbad......dbad.",
    "..dd........dd..",
    "................",
    "................",
    "................",
]
FRAME = [
    "................",
    "................",
    "................",
    "................",
    "....ddddddddddd.",
    "...dabaabaabaad.",
    "..dabaabaabaacd.",
    ".dabaabaabaaccd.",
    "ddddddddddddccd.",
    "dbbbbbbbbbbdcd..",
    "dcccccccccccd...",
    "dddddddddddd....",
    ".cd.......cd....",
    ".dd.......dd....",
    "................",
    "................",
]
PUMP = [
    "................",
    "..dd............",
    ".daddd..........",
    "..ddabddd.......",
    "....ddbaaddd....",
    ".......dbbaadd..",
    ".......dcdddaad.",
    "......dBCBd..dd.",
    "......dBCBd.....",
    ".....dBd.dBd....",
    ".....dBd.dBd....",
    "....dBd...dBd...",
    "....dBd...dBd...",
    "...dBd.....dBd..",
    "...ddd.....ddd..",
    "................",
]
BRAKE = [
    "................",
    "..........ddd...",
    ".........dEFGd..",
    ".........dFFGd..",
    ".........dGGHd..",
    "........dbad....",
    ".......dbacd....",
    "......dbacd.....",
    ".....dbacd......",
    "....dbacd.......",
    "...dbacd........",
    "..ddbcd.........",
    ".dccbbcd........",
    ".dbbaacd........",
    ".ddddddd........",
    "................",
]
TROLLEY = [
    "................",
    ".dd.............",
    ".dadddd.........",
    "..ddabbaddd.....",
    "......dddbaadd..",
    ".......dBd.ddad.",
    ".......dBd...dd.",
    "....ddddddddddd.",
    "...dABAABAABAAd.",
    "..dABAABAABAACd.",
    ".dABAABAABAACCd.",
    "ddddddddddddCCd.",
    "dBBBBBBBBBBdCd..",
    "dCCCCCCCCCCCd...",
    "dddddddddddd....",
    ".dbd.....dbd....",
]



sprite(WHEEL, STEEL).save(OUT + 'item/wheel.png')
sprite(WHEEL, WOOD).save(OUT + 'item/wheel_pattern.png')
sprite(AXLE, STEEL).save(OUT + 'item/axle.png')
sprite(AXLE, WOOD).save(OUT + 'item/axle_pattern.png')
sprite(WHEELSET, STEEL).save(OUT + 'item/wheelset.png')
sprite(FRAME, TREATED).save(OUT + 'item/trolley_frame.png')
sprite(PUMP, STEEL, TREATED).save(OUT + 'item/trolley_pump.png')
sprite([r.replace('E', 'e').replace('F', 'f').replace('G', 'g').replace('H', 'h') for r in BRAKE], STEEL, None, RED).save(OUT + 'item/trolley_brake.png')
sprite(TROLLEY, STEEL, TREATED).save(OUT + 'item/hand_trolley.png')

# Entity sheet: plain zones tiled from the block textures.
sheet = Image.new('RGBA', (256, 256))


def tile(src, x0, y0, w, h, f=None):
    im = Image.open(OUT + 'block/' + src).convert('RGBA').crop((0, 0, 16, 16))
    if f:
        im = Image.eval(im, lambda v: v)
        px = im.load()
        for y in range(16):
            for x in range(16):
                px[x, y] = f(px[x, y])
    for y in range(y0, y0 + h, 16):
        for x in range(x0, x0 + w, 16):
            sheet.paste(im, (x, y))


tile('treated_planks.png', 0, 0, 128, 128)
tile('steel_block.png', 128, 0, 128, 128)
tile('cast_steel.png', 0, 128, 128, 128)
tile('set_steel.png', 128, 128, 128, 64, lambda p: (p[0] * 45 // 100, p[1] * 45 // 100, p[2] * 48 // 100, 255))
tile('steel_block.png', 128, 192, 64, 64, lambda p: (min(255, p[0] * 9 // 10 + 40), p[1] * 22 // 100, p[2] * 18 // 100, 255))
tile('steel_block.png', 192, 192, 64, 64)
import os
os.makedirs(OUT + "entity", exist_ok=True)
sheet.save(OUT + "entity/hand_trolley.png")
