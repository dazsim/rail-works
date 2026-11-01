"""Coplanar faces of different boxes that overlap, the z-fighting check of railworks-mockup/zcheck for axis-aligned boxes."""
import sys

EPS = float(sys.argv[2]) if len(sys.argv) > 2 else 0.004
faces = []
for line in open(sys.argv[1]):
    s = line.split()
    if not s:
        continue
    n = list(map(float, s[1:4]))
    pts = [list(map(float, s[4 + 3 * i:7 + 3 * i])) for i in range(4)]
    ax = max(range(3), key=lambda i: abs(n[i]))
    u, v = [i for i in range(3) if i != ax]
    faces.append((s[0], ax, 1 if n[ax] > 0 else -1, pts[0][ax], min(p[u] for p in pts), max(p[u] for p in pts), min(p[v] for p in pts), max(p[v] for p in pts)))
same, opp = [], []
for ax in range(3):
    fs = sorted([f for f in faces if f[1] == ax], key=lambda f: f[3])
    for i, a in enumerate(fs):
        for b in fs[i + 1:]:
            if b[3] - a[3] > EPS:
                break
            if a[0] == b[0]:
                continue
            w = min(a[5], b[5]) - max(a[4], b[4])
            h = min(a[7], b[7]) - max(a[6], b[6])
            if w <= 0 or h <= 0 or w * h < 1e-5:
                continue
            (same if a[2] == b[2] else opp).append((w * h, b[3] - a[3], a[0], b[0]))
print(f'{sys.argv[1]}: same-facing {len(same)}, opposite {len(opp)}')
for ar, gap, x, y in sorted(same, reverse=True)[:int(sys.argv[3]) if len(sys.argv) > 3 else 40]:
    print(f'  {ar:.4f} gap {gap:.4f}  {x}  {y}')
