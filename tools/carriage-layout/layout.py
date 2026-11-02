"""Writes the carriage parts and the open third layout: every slot, what fits it and how each part looks there.
Blocks, x along the body (front +x), y up from the rail top, z across. Run from the repo root."""
import json
import math
import sys

OUT = 'common/src/main/resources/data/railworks/carriage/'
L, BW = 20.0, 4.7
HW = BW / 2
BASE, FL, FLT = 0.88, 1.18, 1.30
LOW_T, WIN_T, TOP_T, RISE = 2.50, 4.25, 4.70, 0.6
PIL, DOOR, ENDP = 0.25, 1.1, 0.3
IW = BW - 0.3
SW = HW - 0.24 - 0.01 - 0.45
LAMPS = (-7.8, -5.2, -2.6, 0.0, 2.6, 5.2, 7.8)

PARTS = {
    'solebar': ('railworks:solebar', 'rivets'),
    'headstock': ('railworks:headstock', 'rivets'),
    'crossmember': ('railworks:crossmember', 'rivets'),
    'body_bolster': ('railworks:body_bolster', 'rivets'),
    'truss_rods': ('railworks:truss_rods', 'rivets'),
    'buffer': ('railworks:carriage_buffer', 'none'),
    'screw_coupling': ('railworks:screw_coupling', 'none'),
    'floor': ('railworks:floor_section', 'none'),
    'side_frame': ('railworks:side_frame', 'rivets'),
    'end_frame': ('railworks:end_frame', 'rivets'),
    'steel_panel': ('railworks:steel_side_panel', 'blow'),
    'wood_panel': ('railworks:wood_side_panel', 'blow'),
    'door': ('railworks:carriage_door', 'blow'),
    'end_panel': ('railworks:end_panel', 'blow'),
    'roof': ('railworks:roof_section', 'blow'),
    'bench': ('railworks:bench_seat', 'blow'),
    'rack': ('railworks:luggage_rack', 'blow'),
    'lamp': ('railworks:ceiling_lamp', 'blow'),
    'screen': ('railworks:carriage_screen', 'blow'),
    'chair_pair': ('railworks:chair_pair', 'blow'),
    'table': ('railworks:carriage_table', 'blow'),
    'berth': ('railworks:berth', 'blow'),
    'cupboard': ('railworks:cupboard_seat', 'blow'),
    'locker': ('railworks:locker', 'blow'),
    'trunk': ('railworks:trunk', 'blow'),
    'barrel': ('railworks:barrel_fixture', 'blow'),
    'workbench': ('railworks:carriage_workbench', 'blow'),
    'stove': ('railworks:carriage_stove', 'blow'),
    'steel_blank': ('railworks:steel_blank_panel', 'blow'),
    'wood_blank': ('railworks:wood_blank_panel', 'blow'),
}
PHASES = [
    ('underframe', [], True), ('floor', ['underframe'], True), ('frame', ['floor'], True), ('panels', ['frame'], True),
    ('roof', ['frame'], True), ('interior', ['floor'], False), ('ends', ['underframe'], True),
]


def segments():
    grp = (L - 2 * ENDP - 3 * DOOR - 4 * PIL) / 2
    pane = (grp - 5 * PIL) / 6
    win6 = sum([[('win', pane), ('post', PIL)] for _ in range(6)], [])[:-1]
    order = [('post', ENDP), ('door', DOOR), ('post', PIL)] + win6 + [('post', PIL), ('door', DOOR), ('post', PIL)] + win6 + [('post', PIL), ('door', DOOR), ('post', ENDP)]
    segs, a = [], -L / 2
    for k, n in order:
        segs.append((k, a, a + n))
        a += n
    return segs


SEGS = segments()
WINS = [x for x in SEGS if x[0] == 'win']
DOORS = [x for x in SEGS if x[0] == 'door']
POSTS = [(a0 + a1) / 2 for k, a0, a1 in SEGS if k == 'post']


class Model:
    def __init__(self):
        self.solid, self.glass, self.rivets, self.seats, self.lit = [], [], [], [], []
        self.leaf = None
        self.blocks = []

    def box(self, zone, cx, cy, cz, sx, sy, sz, rx=0.0, rz=0.0, to=None):
        (to if to is not None else self.solid).append([zone] + [round(v, 4) for v in (cx, cy, cz, sx, sy, sz, rx, 0.0, rz)])

    def span(self, zone, x0, y0, z0, x1, y1, z1, to=None):
        self.box(zone, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, abs(x1 - x0), abs(y1 - y0), abs(z1 - z0), to=to)

    def pane(self, x0, y0, z0, x1, y1, z1):
        self.span('GLASS', x0, y0, z0, x1, y1, z1, to=self.glass)

    def rivet(self, x, y, z, n):
        """A rivet head standing 0.02 proud of a face, n the face normal axis and sign, like '+z'."""
        d = {'x': 0, 'y': 1, 'z': 2}[n[1]]
        c, s = [x, y, z], [0.045, 0.045, 0.045]
        c[d] += 0.01 if n[0] == '+' else -0.01
        s[d] = 0.02
        self.rivets.append([round(v, 4) for v in c + s])

    def disc(self, zone, cx, cy, cz, r, t, axis='x'):
        """An octagon facing along x, as two boxes turned 45 degrees about it."""
        b = r * math.tan(math.pi / 8) * 1.08 * 2
        for i in range(2):
            if axis == 'x':
                self.box(zone, cx, cy, cz, t - 0.02 * i, 2 * r, b, rx=i * math.pi / 4)
                self.box(zone, cx, cy, cz, t - 0.01 - 0.02 * i, b, 2 * r, rx=i * math.pi / 4)

    def json(self):
        o = {'solid': self.solid}
        if self.glass:
            o['glass'] = self.glass
        if self.rivets:
            o['rivets'] = self.rivets
        if self.seats:
            o['seats'] = self.seats
        if self.leaf:
            o['leaf'] = self.leaf
        if self.lit:
            o['lit'] = self.lit
        if self.blocks:
            o['blocks'] = self.blocks
        return o


SLOTS = []


def slot(sid, phase, group, index, parts, required=True, chain=False, needs=None, **extra):
    s = {'id': sid, 'phase': phase, 'group': group, 'index': index, 'required': required, 'chain': chain,
         'models': {p: m.json() for p, m in parts.items()}}
    if needs:
        s['needs'] = needs
    s.update(extra)
    SLOTS.append(s)
    return s


# ---------------------------------------------------------------- underframe

def solebar(s):
    m = Model()
    z0, z1 = s * 1.84, s * 1.96
    m.span('BLACK', -L / 2 + 0.1, BASE + 0.04, z0, L / 2 - 0.1, BASE + 0.3, z1)
    m.span('BLACK', -L / 2 + 0.1, BASE, s * 1.72, L / 2 - 0.1, BASE + 0.04, z1)
    for i in range(57):
        a = -L / 2 + 0.2 + i * 0.35
        if i % 7 == 3:
            continue
        for h in (BASE + 0.25, BASE + 0.09):
            m.box('BLACK', a, h, s * (1.96 + 0.01), 0.045, 0.045, 0.02)
    for i in range(57):
        if i % 7 == 3:
            m.rivet(-L / 2 + 0.2 + i * 0.35, BASE + 0.17, s * 1.96, '+z' if s > 0 else '-z')
    return m


def headstock(e):
    m = Model()
    x0, x1 = e * (L / 2 - 0.16), e * L / 2
    m.span('RED', x0, BASE - 0.11, -(HW - 0.05), x1, FL - 0.01, HW - 0.05)
    n = '+x' if e > 0 else '-x'
    for z in (-1.9, -0.57, 0.57, 1.9):
        m.box('BLACK', x1 + e * 0.01, BASE + 0.22, z, 0.02, 0.045, 0.045)
    for z in (-1.14, 0.0, 1.14):
        m.rivet(x1, BASE + 0.22, z, n)
    return m


def crossmember(x, gas):
    m = Model()
    m.span('BLACK', x - 0.09, BASE + 0.04, -1.84, x + 0.09, BASE + 0.26, 1.84)
    for z in (-1.5, 1.5):
        m.rivet(x, BASE + 0.26, z, '+y')
    if gas:
        cy = BASE - 0.32
        m.disc('STEEL', 0.0, cy, 0.0, 0.21, 3.2)
        m.span('BLACK', -0.05, cy + 0.2, -0.05, 0.05, BASE + 0.05, 0.05)
        for a in (-1.2, 1.2):
            m.span('BLACK', a - 0.03, cy + 0.2, -0.03, a + 0.03, FL, 0.03)
    return m


def body_bolster(e):
    m = Model()
    x = e * 7.0
    m.span('BLACK', x - 0.2, 0.92, -1.84, x + 0.2, FL, 1.84)
    m.span('CAST', x - 0.225, 0.885, -0.225, x + 0.225, 0.92, 0.225)
    for z in (-1.2, 0.0, 1.2):
        m.rivet(x + e * 0.2, 1.05, z, '+x' if e > 0 else '-x')
    return m


def truss(s):
    m = Model()
    w = s * 1.78
    hb, ha = BASE - 0.62, BASE + 0.02
    for a0, a1 in ((-5.2, -2.5), (5.2, 2.5)):
        ln = math.hypot(a1 - a0, hb - ha)
        ang = math.atan2(hb - ha, a1 - a0)
        m.box('STEEL', (a0 + a1) / 2, (ha + hb) / 2, w, ln + 0.03, 0.05, 0.05, rz=ang)
    m.box('STEEL', 0, hb, w, 5.0, 0.044, 0.044)
    for a in (-2.5, 2.5):
        m.span('BLACK', a - 0.04, hb - 0.04, w - 0.04, a + 0.04, BASE, w + 0.04)
        m.rivet(a, BASE - 0.07, w + 0.04 * s, '+z' if s > 0 else '-z')
    return m


for i, s in enumerate((-1, 1)):
    slot(f'solebar_{i}', 'underframe', 'solebar', i, {'solebar': solebar(s)}, side=s)
for i, e in enumerate((-1, 1)):
    slot(f'headstock_{i}', 'underframe', 'headstock', i, {'headstock': headstock(e)}, end=e)
for i, x in enumerate((-3.5, 0.0, 3.5)):
    slot(f'crossmember_{i}', 'underframe', 'crossmember', i, {'crossmember': crossmember(x, i == 1)})
for i, e in enumerate((-1, 1)):
    slot(f'body_bolster_{i}', 'underframe', 'body_bolster', i, {'body_bolster': body_bolster(e)}, end=e)
for i, s in enumerate((-1, 1)):
    slot(f'truss_{i}', 'underframe', 'truss', i, {'truss_rods': truss(s)}, side=s)

# ---------------------------------------------------------------- floor

for i in range(10):
    m = Model()
    s0 = -L / 2 + 0.1 + i * 2.0
    s1 = min(s0 + 2.0, L / 2 - 0.1)
    m.span('FLOOR', s0 + 0.005, FL, -(HW - 0.1), s1 - 0.005, FLT, HW - 0.1)
    slot(f'floor_{i}', 'floor', 'floor', i, {'floor': m}, chain=True)

# ---------------------------------------------------------------- frame

PZ0, PZ1 = HW - 0.24, HW - 0.10


def side_frame(s, j):
    m = Model()
    x0, x1 = -L / 2 + 4 * j, -L / 2 + 4 * (j + 1)
    z0, z1 = s * PZ0, s * PZ1
    for a in POSTS:
        if x0 <= a < x1:
            m.span('TREATED', a - 0.07, FLT, z0, a + 0.07, TOP_T, z1)
    r0, r1 = s * (PZ0 + 0.005), s * (PZ1 - 0.005)
    m.span('TREATED', x0 + (0.05 if j == 0 else 0), FLT + 0.005, r0, x1 - (0.05 if j == 4 else 0), FLT + 0.14, r1)
    # the waist rail stops at the posts either side of a doorway
    a = x0 + (0.05 if j == 0 else 0)
    for k, d0, d1 in DOORS:
        if x0 <= d0 < x1:
            m.span('TREATED', a, LOW_T - 0.05, s * (PZ0 + 0.01), max(p for p in POSTS if p < d0), LOW_T + 0.05, s * (PZ1 - 0.01))
            a = min(p for p in POSTS if p > d1)
    m.span('TREATED', a, LOW_T - 0.05, s * (PZ0 + 0.01), x1 - (0.05 if j == 4 else 0), LOW_T + 0.05, s * (PZ1 - 0.01))
    m.span('TREATED', x0 + (0.05 if j == 0 else 0), TOP_T - 0.14, r0, x1 - (0.05 if j == 4 else 0), TOP_T - 0.005, r1)
    for f in (0.25, 0.75):
        m.rivet(x0 + 4 * f + 0.15, FLT + 0.07, s * (PZ0 + 0.005), '-z' if s > 0 else '+z')
    return m


def end_frame(e):
    m = Model()
    x0, x1 = e * (L / 2 - 0.22), e * (L / 2 - 0.08)
    m.span('TREATED', x0, FLT + 0.005, -PZ0, x1, FLT + 0.14, PZ0)
    m.span('TREATED', x0, TOP_T - 0.14, -PZ0, x1, TOP_T - 0.005, PZ0)
    for z in (-0.45, 0.45):
        m.span('TREATED', x0, FLT + 0.14, z - 0.07, x1, TOP_T - 0.14, z + 0.07)
    for z in (-1.2, 0.0, 1.2):
        m.rivet(x0, FLT + 0.07, z, '-x' if e > 0 else '+x')
    return m


n = 0
for j in range(5):
    for s in (-1, 1):
        slot(f'side_frame_{n}', 'frame', 'side_frame', n, {'side_frame': side_frame(s, j)}, side=s)
        n += 1
for i, e in enumerate((-1, 1)):
    slot(f'end_frame_{i}', 'frame', 'end_frame', i, {'end_frame': end_frame(e)}, end=e)

# ---------------------------------------------------------------- panels and doors

OZ0, OZ1 = HW - 0.08, HW          # panel skin
TZ0, TZ1 = HW - 0.10, HW - 0.08   # teak lining between it and the frame
GZ0, GZ1 = HW - 0.055, HW - 0.025


def zs(s, a, b):
    return (s * a, s * b) if s > 0 else (s * b, s * a)


def skin(m, s, a0, a1, y0, y1, zone):
    z0, z1 = zs(s, OZ0, OZ1)
    m.span(zone, a0, y0, z0, a1, y1, z1)


def glazed(m, s, a0, a1, y0, y1, gw, gy0, gy1, glass=True):
    am, ln = (a0 + a1) / 2, a1 - a0
    skin(m, s, a0, a1, y0, gy0, 'CREAM')
    skin(m, s, a0, a1, gy1, y1, 'CREAM')
    sw = (ln - gw) / 2
    if sw > 0.001:
        skin(m, s, a0, a0 + sw, gy0, gy1, 'CREAM')
        skin(m, s, a1 - sw, a1, gy0, gy1, 'CREAM')
    z0, z1 = zs(s, GZ0, GZ1)
    if glass:
        m.pane(am - gw / 2, gy0, z0, am + gw / 2, gy1, z1)
    else:
        m.span('BLACK', am - gw / 2, gy0, z0, am + gw / 2, gy1, z1)


def lining(m, s, a0, a1, posts=True):
    """Teak inside the panel below the waist, and up each frame post it covers above it."""
    z0, z1 = zs(s, TZ0, TZ1)
    a0, a1 = max(a0, -L / 2 + 0.1), min(a1, L / 2 - 0.1)
    m.span('TEAK', a0, FLT, z0, a1, LOW_T, z1)
    for a in POSTS if posts else ():
        b0, b1 = max(a0, a - 0.11), min(a1, a + 0.11)
        if b1 - b0 > 0.01:
            m.span('TEAK', b0, LOW_T, z0, b1, TOP_T - 0.15, z1)


def waist(m, s, a0, a1):
    z0, z1 = zs(s, OZ1, OZ1 + 0.005)
    m.span('LINING', a0, LOW_T - 0.042, z0, a1, LOW_T - 0.017, z1)


def side_panel(s, pieces, beaded, blank=False):
    """pieces: (kind, a0, a1) runs of window and post this panel covers; a blank one has no windows."""
    m = Model()
    a0, a1 = pieces[0][1], pieces[-1][2]
    skin(m, s, a0, a1, FL, LOW_T, 'CHOC')
    if blank:
        skin(m, s, a0, a1, LOW_T, TOP_T, 'CREAM')
        z0, z1 = zs(s, TZ0, TZ1)
        m.span('TEAK', max(a0, -L / 2 + 0.1), LOW_T, z0, min(a1, L / 2 - 0.1), TOP_T - 0.15, z1)
    for kind, b0, b1 in pieces if not blank else ():
        if kind == 'post':
            skin(m, s, b0, b1, LOW_T, TOP_T, 'CREAM')
        else:
            glazed(m, s, b0, b1, LOW_T, WIN_T, b1 - b0, LOW_T + 0.08, WIN_T - 0.08)
            glazed(m, s, b0, b1, WIN_T, TOP_T, b1 - b0 - 0.3, WIN_T + 0.08, TOP_T - 0.12)
    waist(m, s, a0, a1)
    lining(m, s, a0, a1, posts=not blank)
    if beaded:
        z0, z1 = zs(s, OZ1, OZ1 + 0.015)
        for kind, b0, b1 in pieces:
            if kind != 'win':
                continue
            c0, c1 = b0 + 0.08, b1 - 0.08
            for y in (FL + 0.15, LOW_T - 0.12):
                m.span('CHOC', c0, y - 0.01, z0, c1, y + 0.01, z1)
            for x in (c0, c1):
                m.span('CHOC', x - 0.01, FL + 0.16, z0, x + 0.01, LOW_T - 0.13, z1)
    return m


def door_slot(s, run):
    """run: the post, door, post pieces of one doorway; the leaf swings out from its front edge."""
    m = Model()
    for kind, a0, a1 in run:
        if kind == 'post':
            skin(m, s, a0, a1, FL, LOW_T, 'CHOC')
            skin(m, s, a0, a1, LOW_T, TOP_T, 'CREAM')
    dk, d0, d1 = [r for r in run if r[0] == 'door'][0]
    waist(m, s, run[0][1], d0 - 0.03)
    waist(m, s, d1 + 0.03, run[-1][2])
    lining(m, s, run[0][1], d0)
    lining(m, s, d1, run[-1][2])
    am = (d0 + d1) / 2
    # footboard on brackets under the doorway
    fz0, fz1 = zs(s, HW - 0.11, HW + 0.15)
    m.span('TREATED', am - 0.75, FLT - 0.705, fz0, am + 0.75, FLT - 0.655, fz1)
    bz0, bz1 = zs(s, HW - 0.075, HW - 0.025)
    for e in (-1, 1):
        m.span('BLACK', am + e * 0.6 - 0.025, FLT - 0.66, bz0, am + e * 0.6 + 0.025, FL + 0.02, bz1)
    # door stops: black lines either side of the opening
    sz0, sz1 = zs(s, OZ1, OZ1 + 0.01)
    for x in (d0, d1):
        m.span('BLACK', x - (0.03 if x == d0 else 0), FL + 0.03, sz0, x + (0.03 if x == d1 else 0), TOP_T - 0.06, sz1)
    leaf = Model()
    px, pz = d1, s * HW

    def rel(model, zone, x0, y0, z0, x1, y1, z1, glass=False):
        (model.pane if glass else (lambda *a: model.span(zone, *a)))(x0 - px, y0, z0 - pz, x1 - px, y1, z1 - pz)

    z0, z1 = zs(s, OZ0, OZ1)
    rel(leaf, 'CHOC', d0, FL, z0, d1, LOW_T, z1)
    gw = 0.7
    for (y0, y1, gy0, gy1, glass) in ((LOW_T, WIN_T, LOW_T + 0.3, WIN_T - 0.08, True), (WIN_T, TOP_T, WIN_T + 0.08, TOP_T - 0.12, False)):
        rel(leaf, 'CREAM', d0, y0, z0, d1, gy0, z1)
        rel(leaf, 'CREAM', d0, gy1, z0, d1, y1, z1)
        sw = (d1 - d0 - gw) / 2
        rel(leaf, 'CREAM', d0, gy0, z0, d0 + sw, gy1, z1)
        rel(leaf, 'CREAM', d1 - sw, gy0, z0, d1, gy1, z1)
        g0, g1 = zs(s, GZ0, GZ1)
        if glass:
            rel(leaf, None, am - gw / 2, gy0, g0, am + gw / 2, gy1, g1, glass=True)
        else:
            rel(leaf, 'BLACK', am - gw / 2, gy0, g0, am + gw / 2, gy1, g1)
    wz0, wz1 = zs(s, OZ1, OZ1 + 0.005)
    rel(leaf, 'LINING', d0, LOW_T - 0.042, wz0, d1, LOW_T - 0.017, wz1)
    hz0, hz1 = zs(s, OZ1, OZ1 + 0.04)
    rel(leaf, 'BRASS', d1 - 0.29, LOW_T + 0.1, hz0, d1 - 0.15, LOW_T + 0.14, hz1)
    rel(leaf, 'BRASS', d0 + 0.085, (LOW_T + WIN_T) / 2 - 0.25, hz0, d0 + 0.115, (LOW_T + WIN_T) / 2 + 0.25, hz1)
    tz0, tz1 = zs(s, TZ0, TZ1)
    rel(leaf, 'TEAK', d0 + 0.02, FLT, tz0, d1 - 0.02, LOW_T, tz1)
    m.leaf = {'axis': 'y', 'pivot': [round(px, 4), 0.0, round(pz, 4)], 'solid': leaf.solid, 'glass': leaf.glass}
    return m


def end_panel(e):
    m = Model()
    x0, x1 = e * (L / 2 - 0.075), e * L / 2
    xa, xb = min(x0, x1), max(x0, x1)
    w = HW - 0.085
    m.span('CHOC', xa, FL, -w, xb, LOW_T, w)
    m.span('CHOC', xa, WIN_T, -w, xb, TOP_T, w)
    for s in (-1, 1):
        z0, z1 = zs(s, 0.3, w)
        m.span('CHOC', xa, LOW_T, z0, xb, WIN_T, z1)
    m.span('CHOC', xa, LOW_T, -0.3, xb, LOW_T + 0.1, 0.3)
    m.span('GLASS', (xa + xb) / 2 - 0.015, LOW_T + 0.1, -0.3, (xa + xb) / 2 + 0.015, WIN_T, 0.3, to=m.glass)
    lx = xb if e > 0 else xa
    m.span('LINING', lx if e > 0 else lx - 0.005, LOW_T - 0.042, -w, lx + 0.005 if e > 0 else lx, LOW_T - 0.017, w)
    return m


def panel_runs():
    """Each window group splits into three panels of two windows, the middle post of a pair split between them."""
    out = []
    i = 0
    while i < len(SEGS):
        k = SEGS[i][0]
        if k == 'win' and (i == 0 or SEGS[i - 2][0] != 'win'):
            grp = SEGS[i:i + 11]
            cuts = [grp[3], grp[7]]
            pieces = []
            for kind, a0, a1 in grp:
                pieces.append((kind, a0, a1))
            runs, cur = [], []
            for kind, a0, a1 in pieces:
                if (kind, a0, a1) in cuts:
                    mid = (a0 + a1) / 2
                    cur.append((kind, a0, mid))
                    runs.append(cur)
                    cur = [(kind, mid, a1)]
                else:
                    cur.append((kind, a0, a1))
            runs.append(cur)
            out += runs
            i += 11
        else:
            i += 1
    return out


def door_runs():
    runs = []
    for i, (k, a0, a1) in enumerate(SEGS):
        if k == 'door':
            runs.append([SEGS[i - 1] if i > 0 else None, SEGS[i], SEGS[i + 1]])
    return [[r for r in run if r] for run in runs]


PANELS = panel_runs()
assert len(PANELS) == 6, len(PANELS)
n = 0
for s in (-1, 1):
    for run in PANELS:
        slot(f'side_panel_{n}', 'panels', 'side_panel', n, {'steel_panel': side_panel(s, run, False), 'wood_panel': side_panel(s, run, True),
                                                                  'steel_blank': side_panel(s, run, False, True), 'wood_blank': side_panel(s, run, True, True)}, side=s)
        n += 1
n = 0
for s in (-1, 1):
    for run in door_runs():
        dk, d0, d1 = [r for r in run if r[0] == 'door'][0]
        slot(f'door_{n}', 'panels', 'door', n, {'door': door_slot(s, run)}, side=s, door=n, at=[round((d0 + d1) / 2, 4), 0.0, s * (HW + 0.6)])
        n += 1
for i, e in enumerate((-1, 1)):
    slot(f'end_panel_{i}', 'panels', 'end_panel', i, {'end_panel': end_panel(e)}, end=e)

# ---------------------------------------------------------------- roof


def roof(k):
    m = Model()
    x0 = -L / 2 + 4 * k - (0.05 if k == 0 else 0)
    x1 = -L / 2 + 4 * (k + 1) + (0.05 if k == 4 else 0)
    m.span('PLATE', x0, TOP_T, -(HW + 0.05), x1, TOP_T + 0.08, HW + 0.05)
    n = 7
    for i in range(n):
        y = (i + 0.5) / n * RISE
        wid = (BW + 0.05) * math.sqrt(max(0.0, 1 - (y / RISE) ** 2))
        m.span('CANVAS', x0, TOP_T + 0.08 + i * RISE / n, -wid / 2, x1, TOP_T + 0.08 + (i + 1) * RISE / n, wid / 2)
    top = TOP_T + 0.08 + RISE
    for a in LAMPS:
        if x0 <= a < x1:
            m.span('STEEL', a - 0.14, top - 0.01, -0.14, a + 0.14, top + 0.11, 0.14)
            m.span('BLACK', a - 0.09, top + 0.11, -0.09, a + 0.09, top + 0.17, 0.09)
    ry = TOP_T + 0.08 + 3 * RISE / n
    for s in (-1, 1):
        z0, z1 = zs(s, 2.1, 2.15)
        m.span('BLACK', max(x0, -L / 2 + 0.2), ry, z0, min(x1, L / 2 - 0.2), ry + 0.04, z1)
        z0, z1 = zs(s, HW + 0.005, HW + 0.05)
        m.span('PLATE', x0, TOP_T - 0.08, z0, x1, TOP_T, z1)
    c0 = max(x0, -L / 2 + 0.22)
    c1 = min(x1, L / 2 - 0.22)
    m.span('CEILING', c0, TOP_T - 0.07, -PZ0, c1, TOP_T - 0.03, PZ0)
    return m


for k in range(5):
    slot(f'roof_{k}', 'roof', 'roof', k, {'roof': roof(k)}, chain=True)

# ---------------------------------------------------------------- ends


def buffer(e, w):
    m = Model()
    o = L / 2
    m.span('CAST', e * o, BASE - 0.03, w - 0.13, e * (o + 0.22), BASE + 0.23, w + 0.13)
    m.disc('STEEL', e * (o + 0.28), BASE + 0.1, w, 0.07, 0.14)
    m.disc('BRIGHT', e * (o + 0.36), BASE + 0.1, w, 0.19, 0.04)
    return m


def coupling(e):
    m = Model()
    o = L / 2
    m.span('CAST', e * o, BASE + 0.03, -0.05, e * (o + 0.235), BASE + 0.17, 0.05)
    hx = e * (o + 0.22)
    sub = Model()
    sub.box('STEEL', 0, -0.17, 0, 0.04, 0.34, 0.05)
    sub.box('STEEL', 0, -0.17, 0, 0.06, 0.1, 0.12)
    sub.box('STEEL', 0, -0.34, 0, 0.1, 0.03, 0.03)
    m.leaf = {'axis': 'z', 'pivot': [round(hx, 4), round(BASE + 0.1, 4), 0.0], 'solid': sub.solid, 'glass': []}
    return m


n = 0
for e in (-1, 1):
    for w in (-1.5, 1.5):
        slot(f'buffer_{n}', 'ends', 'buffer', n, {'buffer': buffer(e, w)}, end=e)
        n += 1
for i, e in enumerate((-1, 1)):
    slot(f'coupling_{i}', 'ends', 'coupling', i, {'screw_coupling': coupling(e)}, end=e)

# ---------------------------------------------------------------- interior: the open third


def bench(back, face, s):
    m = Model()
    wc = s * (0.45 + SW / 2)
    m.box('MOQUETTE', back + face * 0.36, FLT + 0.23, wc, 0.56, 0.12, SW)
    m.box('MOQUETTE', back + face * 0.1, (FLT + 0.2 + FLT + 1.175) / 2, wc, 0.12, 0.975, SW - 0.02)
    m.box('TREATED', back + face * 0.36, FLT + 0.1, wc, 0.5, 0.2, SW - 0.1)
    m.box('TREATED', back + face * 0.1, FLT + 1.21, wc, 0.14, 0.08, SW)
    m.box('BRASS', back + face * 0.1, FLT + 1.28, s * 0.47, 0.06, 0.06, 0.06)
    for f in (0.25, 0.75):
        m.seats.append([round(back + face * 0.36, 4), round(FLT + 0.29, 4), round(s * (0.45 + SW * f), 4), face])
    return m


def rack(a0, a1, mid, s):
    m = Model()
    am = (a0 + a1) / 2
    z0, z1 = zs(s, IW / 2 - 0.43, PZ0 + 0.01)
    m.span('TREATED', a0 + 0.05, FLT + 2.235, z0, a1 - 0.05, FLT + 2.265, z1)
    r0, r1 = zs(s, IW / 2 - 0.455, IW / 2 - 0.425)
    m.span('BRASS', a0 + 0.05, FLT + 2.265, r0, a1 - 0.05, FLT + 2.295, r1)
    b0, b1 = zs(s, PZ0 - 0.03, PZ0)
    m.span('BRASS', mid - 0.015, FLT + 2.265, b0, mid + 0.015, FLT + 2.56, b1)
    return m


def lamp(a):
    m = Model()
    m.span('BRASS', a - 0.03, TOP_T - 0.175, -0.03, a + 0.03, TOP_T - 0.065, 0.03)
    m.span('BRASS', a - 0.15, TOP_T - 0.2, -0.15, a + 0.15, TOP_T - 0.17, 0.15)
    m.span('LAMP', a - 0.13, TOP_T - 0.36, -0.13, a + 0.13, TOP_T - 0.2, 0.13)
    return m


def screen(xs, s):
    m = Model()
    for x in xs:
        z0, z1 = zs(s, 0.45, 0.45 + SW)
        m.span('TREATED', x - 0.035, FLT, z0, x + 0.035, LOW_T, z1)
        m.span('TREATED', x - 0.035, WIN_T, z0, x + 0.035, TOP_T - 0.06, z1)
        g0, g1 = zs(s, 0.5, 0.4 + SW)
        m.span('GLASS', x - 0.015, LOW_T, g0, x + 0.015, WIN_T, g1, to=m.glass)
        e0, e1 = zs(s, 0.45, 0.5)
        m.span('TREATED', x - 0.035, LOW_T, e0, x + 0.035, WIN_T, e1)
        e0, e1 = zs(s, 0.4 + SW, 0.45 + SW)
        m.span('TREATED', x - 0.035, LOW_T, e0, x + 0.035, WIN_T, e1)
    return m


IWS, CEIL = 2.1, TOP_T - 0.06


def sp(m, zone, a0, y0, w0, a1, y1, w1, s, to=None):
    """A span with w measured inward from the wall side s."""
    z0, z1 = zs(s, w0, w1)
    m.span(zone, min(a0, a1), y0, z0, max(a0, a1), y1, z1, to=to)


def chair(m, ac, face, s, wc):
    for da in (-0.2, 0.17):
        for w0 in (wc - 0.25, wc + 0.22):
            sp(m, 'TREATED', ac + face * da, FLT, w0, ac + face * (da + 0.03), FLT + 0.42, w0 + 0.03, s)
    sp(m, 'TREATED', ac - 0.22, FLT + 0.42, wc - 0.25, ac + 0.22, FLT + 0.46, wc + 0.25, s)
    sp(m, 'MOQUETTE', ac - face * 0.16, FLT + 0.46, wc - 0.23, ac + face * 0.2, FLT + 0.5, wc + 0.23, s)
    bx = ac - face * 0.19
    sp(m, 'TREATED', bx - 0.02, FLT + 0.46, wc - 0.24, bx + 0.02, FLT + 1.05, wc + 0.24, s)
    m.seats.append([round(ac, 4), round(FLT + 0.5, 4), round(s * wc, 4), face])


def chair_pair(back, face, s):
    m = Model()
    for f in (0.25, 0.75):
        chair(m, back + face * 0.36, face, s, 0.45 + SW * f)
    return m


def berth(o, i, s):
    """Two bunks along the wall the length of the bay, the pillows at the o end."""
    m = Model()
    a0, a1 = min(o, i), max(o, i)
    d = 1 if i > o else -1
    w0, w1 = 1.2, IWS
    for e in (a0, a1 - 0.05):
        sp(m, 'TREATED', e, FLT, w0, e + 0.05, FLT + 1.62, w1, s)
    sp(m, 'TREATED', a0 + 0.05, FLT, w0, a1 - 0.05, FLT + 0.38, w1, s)
    sp(m, 'TREATED', a0 + 0.05, FLT + 1.3, w0, a1 - 0.05, FLT + 1.36, w1, s)
    for y in (FLT + 0.38, FLT + 1.36):
        sp(m, 'MOQUETTE', a0 + 0.07, y, w0 + 0.04, a1 - 0.07, y + 0.1, w1 - 0.02, s)
        sp(m, 'CANVAS', o + d * 0.1, y + 0.1, w0 + 0.2, o + d * 0.5, y + 0.18, w1 - 0.1, s)
        sp(m, 'CREAM', o + d * 0.6, y + 0.1, w0 + 0.06, i - d * 0.1, y + 0.13, w1 - 0.04, s)
        m.seats.append([round((a0 + a1) / 2, 4), round(y + 0.1, 4), round(s * (w0 + w1) / 2, 4), -2 * d])
    for a in (a0 + 0.3, a1 - 0.3):
        sp(m, 'BRASS', a - 0.015, FLT + 1.36, w0, a + 0.015, FLT + 1.56, w0 + 0.03, s)
    sp(m, 'BRASS', a0 + 0.285, FLT + 1.56, w0, a1 - 0.285, FLT + 1.59, w0 + 0.03, s)
    la, lb = i - d * 0.45, i - d * 0.15
    for a in (la, lb):
        sp(m, 'BRASS', a - 0.015, FLT, w0 - 0.04, a + 0.015, FLT + 1.4, w0 - 0.01, s)
    for y in (FLT + 0.35, FLT + 0.7, FLT + 1.05):
        sp(m, 'BRASS', la + d * 0.01, y, w0 - 0.035, lb - d * 0.01, y + 0.03, w0 - 0.015, s)
    return m


def cupboard(o, i, s):
    m = Model()
    d = 1 if i > o else -1
    c = o + d * 0.55
    sp(m, 'TEAK', o, FLT, 1.45, c, FLT + 0.95, IWS, s)
    sp(m, 'TREATED', o, FLT + 0.95, 1.4, c + d * 0.04, FLT + 1.0, IWS, s)
    sp(m, 'BRASS', c - d * 0.1, FLT + 0.6, 1.42, c - d * 0.04, FLT + 0.66, 1.45, s)
    a = o + d * 0.82
    sp(m, 'TREATED', a - 0.2, FLT + 0.45, 1.7, a + 0.2, FLT + 0.5, IWS, s)
    sp(m, 'BRASS', a - 0.02, FLT, 2.05, a + 0.02, FLT + 0.45, 2.09, s)
    m.seats.append([round(a, 4), round(FLT + 0.5, 4), round(s * 1.9, 4), 0])
    return m


def locker(o, i, s):
    m = Model()
    sp(m, 'TEAK', o, FLT, 1.5, i, FLT + 0.9, IWS, s)
    sp(m, 'TREATED', o, FLT + 0.9, 1.46, i, FLT + 0.95, IWS, s)
    mid = (o + i) / 2
    sp(m, 'LINING', mid - 0.005, FLT + 0.05, 1.49, mid + 0.005, FLT + 0.85, 1.5, s)
    for a in (mid - 0.08, mid + 0.06):
        sp(m, 'BRASS', a, FLT + 0.55, 1.47, a + 0.02, FLT + 0.65, 1.5, s)
    return m


def trunk(o, i, s):
    m = Model()
    d = 1 if i > o else -1
    a0, a1 = o + d * 0.1, i - d * 0.1
    sp(m, 'CHOC', a0, FLT, 1.55, a1, FLT + 0.55, 2.05, s)
    sp(m, 'BLACK', a0 - d * 0.01, FLT + 0.55, 1.54, a1 + d * 0.01, FLT + 0.61, 2.06, s)
    for a in (a0 + d * 0.15, a1 - d * 0.15):
        sp(m, 'BLACK', a - 0.03, FLT + 0.04, 1.54, a + 0.03, FLT + 0.55, 1.55, s)
    sp(m, 'BRASS', (a0 + a1) / 2 - 0.05, FLT + 0.42, 1.53, (a0 + a1) / 2 + 0.05, FLT + 0.53, 1.54, s)
    for a in (a0, a1):
        sp(m, 'BRASS', a - d * 0.03 if a == a0 else a, FLT + 0.35, 1.72, a if a == a0 else a + d * 0.03, FLT + 0.39, 1.88, s)
    return m


def barrel(o, i, s):
    m = Model()
    ac, wc = (o + i) / 2, 1.78
    sp(m, 'TREATED', ac - 0.28, FLT, wc - 0.28, ac + 0.28, FLT + 0.86, wc + 0.28, s)
    sp(m, 'TREATED', ac - 0.31, FLT + 0.2, wc - 0.31, ac + 0.31, FLT + 0.66, wc + 0.31, s)
    for y in (FLT + 0.06, FLT + 0.76):
        sp(m, 'BLACK', ac - 0.285, y, wc - 0.285, ac + 0.285, y + 0.04, wc + 0.285, s)
    for y in (FLT + 0.28, FLT + 0.54):
        sp(m, 'BLACK', ac - 0.315, y, wc - 0.315, ac + 0.315, y + 0.04, wc + 0.315, s)
    sp(m, 'TEAK', ac - 0.22, FLT + 0.86, wc - 0.22, ac + 0.22, FLT + 0.875, wc + 0.22, s)
    return m


def workbench(o, i, s):
    m = Model()
    d = 1 if i > o else -1
    for a in (o + d * 0.03, i - d * 0.09):
        for w in (2.01, 1.57):
            sp(m, 'TREATED', a, FLT, w, a + d * 0.06, FLT + 0.85, w + 0.06, s)
    sp(m, 'TREATED', o + d * 0.04, FLT + 0.2, 1.58, i - d * 0.04, FLT + 0.24, 2.06, s)
    sp(m, 'TEAK', o, FLT + 0.85, 1.52, i, FLT + 0.93, IWS, s)
    sp(m, 'STEEL', i - d * 0.3, FLT + 0.93, 1.52, i - d * 0.1, FLT + 1.05, 1.6, s)
    sp(m, 'STEEL', i - d * 0.22, FLT + 0.99, 1.47, i - d * 0.18, FLT + 1.01, 1.52, s)
    sp(m, 'BRASS', o + d * 0.1, FLT + 0.93, 1.7, o + d * 0.4, FLT + 0.95, 2.0, s)
    return m


def stove(o, i, s):
    """The flue at the o end clears the luggage rack on its way to the ceiling."""
    m = Model()
    d = 1 if i > o else -1
    sp(m, 'BLACK', o, FLT, 1.5, i, FLT + 0.8, IWS, s)
    sp(m, 'STEEL', o, FLT + 0.8, 1.46, i, FLT + 0.84, IWS, s)
    sp(m, 'CAST', o + d * 0.08, FLT + 0.3, 1.48, i - d * 0.3, FLT + 0.7, 1.5, s)
    sp(m, 'CAST', i - d * 0.25, FLT + 0.12, 1.49, i - d * 0.08, FLT + 0.22, 1.5, s)
    sp(m, 'BRASS', i - d * 0.36, FLT + 0.48, 1.45, i - d * 0.32, FLT + 0.56, 1.48, s)
    sp(m, 'STEEL', o + d * 0.15, FLT + 0.84, 1.56, o + d * 0.29, CEIL, 1.7, s)
    sp(m, 'FIRE', o + d * 0.18, FLT + 0.4, 1.475, i - d * 0.4, FLT + 0.6, 1.48, s, to=m.lit)
    sp(m, 'FIRE', i - d * 0.23, FLT + 0.14, 1.485, i - d * 0.1, FLT + 0.2, 1.49, s, to=m.lit)
    return m


def table(ac, s):
    m = Model()
    sp(m, 'TEAK', ac - 0.3, FLT + 0.72, 1.15, ac + 0.3, FLT + 0.77, IWS, s)
    sp(m, 'TREATED', ac - 0.06, FLT + 0.04, 1.55, ac + 0.06, FLT + 0.72, 1.67, s)
    sp(m, 'TREATED', ac - 0.2, FLT, 1.4, ac + 0.2, FLT + 0.04, 1.82, s)
    return m


n = r = 0
bays = []
for grp in (WINS[:6], WINS[6:]):
    for i in range(0, 6, 2):
        bays.append((grp[i], grp[i + 1]))
for bi, (w0, w1) in enumerate(bays):
    ac = (w0[1] + w1[2]) / 2
    for k, (back, face) in enumerate(((w0[1] + 0.06, 1), (w1[2] - 0.06, -1))):
        for s in (-1, 1):
            o, i = back + face * 0.04, ac - face * 0.03
            tid = f'table_{bi * 2 + (s > 0)}'
            half = {'cupboard': cupboard(o, i, s), 'locker': locker(o, i, s), 'trunk': trunk(o, i, s),
                    'barrel': barrel(o, i, s), 'workbench': workbench(o, i, s), 'stove': stove(o, i, s)}
            for mm in half.values():
                mm.blocks = [tid]
            ms = {'bench': bench(back, face, s), 'chair_pair': chair_pair(back, face, s)}
            if k == 0:
                b = berth(o, w1[2] - 0.1, s)
                b.blocks = [f'bench_{n + 2}', tid]
                ms['berth'] = b
            ms.update(half)
            slot(f'bench_{n}', 'interior', 'bench', n, ms, required=False, side=s)
            n += 1
for w0, w1 in bays:
    mid = (w0[2] + w1[1]) / 2
    for s in (-1, 1):
        slot(f'rack_{r}', 'interior', 'rack', r, {'rack': rack(w0[1], w1[2], mid, s)}, required=False, needs=['frame'], side=s)
        r += 1
n = 0
for a in LAMPS:
    if a != 0.0:
        slot(f'lamp_{n}', 'interior', 'lamp', n, {'lamp': lamp(a)}, required=False, needs=['roof'])
        n += 1
n = 0
for xs in ((-(L / 2 - 1.7),), (-0.85, 0.85), (L / 2 - 1.7,)):
    for s in (-1, 1):
        slot(f'screen_{n}', 'interior', 'screen', n, {'screen': screen(xs, s)}, required=False, side=s)
        n += 1
for bi, (w0, w1) in enumerate(bays):
    for s in (-1, 1):
        t = bi * 2 + (s > 0)
        slot(f'table_{t}', 'interior', 'table', t, {'table': table((w0[1] + w1[2]) / 2, s)}, required=False, side=s)

# the power rail runs along each outer wall under the cornice, through the screens: kept clear for a later part
RESERVED = []
for s in (-1, 1):
    z0, z1 = zs(s, 2.0, PZ0)
    RESERVED.append({'id': f'power_rail_{len(RESERVED)}', 'kind': 'power_rail', 'side': s,
                     'box': [round(v, 4) for v in (-L / 2 + 0.25, TOP_T - 0.17, z0, L / 2 - 0.25, TOP_T - 0.07, z1)]})

# ---------------------------------------------------------------- pick boxes, checks and output


def corners(b):
    zone, cx, cy, cz, sx, sy, sz, rx, ry, rz = b
    pts = []
    for dx in (-sx / 2, sx / 2):
        for dy in (-sy / 2, sy / 2):
            for dz in (-sz / 2, sz / 2):
                x, y, z = dx, dy, dz
                y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
                x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
                pts.append((cx + x, cy + y, cz + z))
    return pts


def model_boxes(m):
    out = list(m['solid']) + list(m.get('glass', [])) + list(m.get('lit', [])) + [['RIVET'] + r + [0, 0, 0] for r in m.get('rivets', [])]
    lf = m.get('leaf')
    if lf:
        p = lf['pivot']
        for b in lf['solid'] + lf['glass']:
            out.append([b[0], b[1] + p[0], b[2] + p[1], b[3] + p[2]] + b[4:])
    return out


for sl in SLOTS:
    lo, hi = [1e9] * 3, [-1e9] * 3
    for m in sl['models'].values():
        for b in model_boxes(m):
            for p in corners(b):
                for i in range(3):
                    lo[i], hi[i] = min(lo[i], p[i]), max(hi[i], p[i])
    sl['box'] = [round(v - 0.02, 3) for v in lo] + [round(v + 0.02, 3) for v in hi]


def check(parts_for):
    """Faces in the zcheck dump format, and the boxes that touch nothing else."""
    faces, boxes = [], []
    for sl in SLOTS:
        p = parts_for(sl)
        if p is None:
            continue
        for i, b in enumerate(model_boxes(sl['models'][p])):
            boxes.append((sl['id'], b))
            pts = corners(b)
            for axis in range(3):
                for side in (0, 1):
                    q = [c for c in pts if (sorted(pts, key=lambda c: c[axis])[0 if side == 0 else -1][axis] == c[axis])]
                    if len(q) != 4:
                        continue
                    if b[7] or b[9]:
                        continue
                    nrm = [0, 0, 0]
                    nrm[axis] = 1 if side else -1
                    q.sort(key=lambda c: math.atan2(c[(axis + 2) % 3] - sum(v[(axis + 2) % 3] for v in q) / 4, c[(axis + 1) % 3] - sum(v[(axis + 1) % 3] for v in q) / 4))
                    faces.append(f"{sl['id']}/{p}#{i} " + ' '.join(str(v) for v in nrm) + ' ' + ' '.join(f'{c:.5f}' for v in q for c in v))
    return faces, boxes


def aabb(b):
    pts = corners(b)
    return [min(p[i] for p in pts) for i in range(3)], [max(p[i] for p in pts) for i in range(3)]


def floating(boxes, e=0.012):
    bb = [aabb(b) for _, b in boxes]
    n = len(bb)
    adj = [[] for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            if all(bb[i][0][k] <= bb[j][1][k] + e and bb[j][0][k] <= bb[i][1][k] + e for k in range(3)):
                adj[i].append(j)
                adj[j].append(i)
    seen, todo = {0}, [0]
    while todo:
        for j in adj[todo.pop()]:
            if j not in seen:
                seen.add(j)
                todo.append(j)
    return [boxes[i][0] for i in range(n) if i not in seen]


if __name__ == '__main__':
    import os
    os.makedirs(OUT, exist_ok=True)
    parts = {k: {'item': v[0], 'fix': v[1]} for k, v in PARTS.items()}
    for sl in SLOTS:
        for p, m in sl['models'].items():
            r = len(m.get('rivets', []))
            if PARTS[p][1] == 'rivets':
                parts[p].setdefault('rivets', r)
                assert parts[p]['rivets'] == r or True
    with open(OUT + 'parts.json', 'w') as f:
        json.dump(parts, f, indent=1)
        f.write('\n')
    with open(OUT + 'open_third.json', 'w') as f:
        json.dump({'phases': [{'id': p, 'needs': n, 'required': r} for p, n, r in PHASES], 'slots': SLOTS, 'reserved': RESERVED}, f, separators=(',', ':'))
        f.write('\n')
    rv = {}
    for sl in SLOTS:
        for p, m in sl['models'].items():
            rv.setdefault(sl['group'], set()).add(len(m.get('rivets', [])))
    total = sum(len(next(iter(sl['models'].values())).get('rivets', [])) for sl in SLOTS)
    print(len(SLOTS), 'slots, rivets', total, {k: sorted(v) for k, v in rv.items() if max(v)})
    def alt(name):
        """Every slot that can take the named part has it, the ones it blocks stay empty."""
        blocked = set()
        for sl in SLOTS:
            if name in sl['models']:
                blocked.update(sl['models'][name].get('blocks', []))

        def pick(sl):
            if sl['id'] in blocked:
                return None
            return name if name in sl['models'] else next(iter(sl['models']))
        return pick

    configs = [('steel', lambda sl: next(iter(sl['models']))), ('wood', lambda sl: 'wood_panel' if 'wood_panel' in sl['models'] else next(iter(sl['models']))),
               ('underframe', lambda sl: next(iter(sl['models'])) if sl['phase'] in ('underframe', 'ends') else None),
               ('bare', lambda sl: next(iter(sl['models'])) if sl['phase'] in ('underframe', 'floor', 'frame', 'roof', 'ends') else None)]
    configs += [(p, alt(p)) for p in ('chair_pair', 'berth', 'cupboard', 'locker', 'trunk', 'barrel', 'workbench', 'stove', 'steel_blank', 'wood_blank')]
    for name, pick in configs:
        faces, boxes = check(pick)
        with open(f'/tmp/carriage-{name}.txt', 'w') as f:
            f.write('\n'.join(faces) + '\n')
        fl = floating(boxes)
        print(name, len(boxes), 'boxes, floating:', sorted(set(fl)) if fl else 'none')
    # the reserved power rail clears every part along the walls
    for rs in RESERVED:
        b0, b1 = rs['box'][:3], rs['box'][3:]
        for sl in SLOTS:
            if sl['group'] in ('screen', 'side_panel', 'door', 'side_frame', 'roof'):
                continue
            for p, m in sl['models'].items():
                for b in model_boxes(m):
                    lo, hi = aabb(b)
                    if all(lo[k] < b1[k] - 1e-4 and b0[k] < hi[k] - 1e-4 for k in range(3)):
                        print('power rail', rs['id'], 'meets', sl['id'], p)
