"""Item sprites for the M5a castings, patterns and machines. Run from the repo root."""
from PIL import Image

OUT = 'src/main/resources/assets/railworks/textures/item/'
STEEL = {'a': (206, 212, 220), 'b': (138, 146, 158), 'c': (88, 94, 106), 'd': (40, 44, 52)}
CAST = {'a': (132, 128, 124), 'b': (98, 94, 92), 'c': (70, 66, 64), 'd': (34, 32, 32)}
WOOD = {'a': (176, 132, 84), 'b': (128, 92, 56), 'c': (96, 66, 40), 'd': (58, 38, 22)}
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


CWHEEL = [
    "....dddddddd....",
    "..ddccccccccdd..",
    ".dcbbbbbbbbbbcd.",
    ".dbaaaaaaaaaabd.",
    "dcbaccccccccabcd",
    "dcbacbbbbbbcabcd",
    "dcbacbddddbcabcd",
    "dcbacbdccdbcabcd",
    "dcbacbdccdbcabcd",
    "dcbacbddddbcabcd",
    "dcbacbbbbbbcabcd",
    "dcbaccccccccabcd",
    ".dbaaaaaaaaaabd.",
    ".dcbbbbbbbbbbcd.",
    "..ddccccccccdd..",
    "....dddddddd....",
]
CAXLE = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..dd........dd..",
    ".dbad......dbad.",
    "ddbaddddddddbadd",
    "cabaabbbbbbaabac",
    "ccbccccccccccbcc",
    ".dbad......dbad.",
    "..dd........dd..",
    "................",
    "................",
    "................",
    "................",
]
PLATE = [
    "................",
    "................",
    "..dddddddddddd..",
    "..dabbbbbbbbbd..",
    "..dbaabbbbbbbd..",
    "..dbabbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "..dbbbbbbbbccd..",
    "..dbccccccccdd..",
    "..dddddddddddd..",
    "................",
    "................",
]
HOLES = [(4, 4), (10, 4), (4, 10), (10, 10)]
AXLEBOX = [
    "................",
    "................",
    "...dddddddddd...",
    "..dbaaaaaaaabd..",
    "..dabbbbbbbbcd..",
    "..dab......bcd..",
    "..dab......bcd..",
    "..dab......bcd..",
    "..dabbbbbbbbcd..",
    "..dbbbbbbbbbcd..",
    "ddddbbbbbbbbdddd",
    "dbbdbbbbbbbbdbbd",
    "dccdccccccccdccd",
    "dddddddddddddddd",
    "................",
    "................",
]
SPRING = [
    "................",
    "...dddddddddd...",
    "...dabbbbbbcd...",
    "....ddddddddd...",
    "...dabbbbbbcd...",
    "....ddddddddd...",
    "...dabbbbbbcd...",
    "....ddddddddd...",
    "...dabbbbbbcd...",
    "....ddddddddd...",
    "...dabbbbbbcd...",
    "....ddddddddd...",
    "...dabbbbbbcd...",
    "...dddddddddd...",
    "................",
    "................",
]
LATHE = [
    "................",
    "................",
    "................",
    "..dddd..........",
    ".dbbbbd.........",
    ".dbbbbdd.....dd.",
    "edbbbbdaaaaadbd.",
    "edbbbbdccccdbbd.",
    "eddddddddddddbd.",
    "dbbbbbbbbbbbbbbd",
    "dcccccccccccccdd",
    "..dbd......dbd..",
    "..dbd......dbd..",
    "..dbd......dbd..",
    ".ddddd....ddddd.",
    "................",
]
DRILL = [
    "................",
    "......eeee......",
    "....dddddddd....",
    "....dbbbbbbd....",
    "...edbbbbbbd....",
    "..e.dddddddd....",
    "...e..dd..da....",
    "......dd..da....",
    "......dd..dd....",
    "......dd...d....",
    "......dddddddd..",
    "......dd.dcccd..",
    "......dd........",
    "......dd........",
    "...dddddddddd...",
    "...dccccccccd...",
]


def holes(im):
    for x, y in HOLES:
        for dx in (0, 1):
            for dy in (0, 1):
                im.putpixel((x + dx, y + dy), (24, 26, 30, 255))
    return im


sprite(CWHEEL, CAST).save(OUT + 'rough_carriage_wheel.png')
sprite(CWHEEL, STEEL).save(OUT + 'carriage_wheel.png')
sprite(CWHEEL, WOOD).save(OUT + 'carriage_wheel_pattern.png')
sprite(CAXLE, CAST).save(OUT + 'rough_carriage_axle.png')
sprite(CAXLE, STEEL).save(OUT + 'carriage_axle.png')
sprite(CAXLE, WOOD).save(OUT + 'carriage_axle_pattern.png')
sprite(PLATE, CAST).save(OUT + 'steel_plate.png')
holes(sprite(PLATE, STEEL)).save(OUT + 'drilled_plate.png')
sprite(PLATE, WOOD).save(OUT + 'plate_pattern.png')
sprite(AXLEBOX, CAST).save(OUT + 'rough_axle_box.png')
sprite(AXLEBOX, STEEL).save(OUT + 'axle_box.png')
sprite(AXLEBOX, WOOD).save(OUT + 'axle_box_pattern.png')
sprite(SPRING, STEEL).save(OUT + 'bogie_spring.png')
sprite(SPRING, WOOD).save(OUT + 'spring_pattern.png')
sprite(LATHE, CAST, None, RED).save(OUT + 'lathe.png')
sprite(DRILL, CAST, None, RED).save(OUT + 'pillar_drill.png')
