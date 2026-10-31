package com.workshopcraft.railworks.block;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The outline of a stake turned to its heading, built from thin slices so it hugs the rotated post. One per degree, cached. */
public final class StakeShapes {
    private StakeShapes() {}

    /** Half width of the post and cap, and the height of the cap top, matching TrackRenderer.stake. */
    public static final double HALF = 0.095, TOP = 0.875;
    private static final int SLICES = 5;
    private static final VoxelShape[] CACHE = new VoxelShape[90];

    public static VoxelShape forHeading(double heading) {
        double deg = Math.toDegrees(heading) % 90;
        if (deg < 0) deg += 90;
        int i = (int) Math.round(deg) % 90;
        VoxelShape s = CACHE[i];
        if (s == null) CACHE[i] = s = build(Math.toRadians(i));
        return s;
    }

    private static VoxelShape build(double th) {
        double c = Math.cos(th), sn = Math.sin(th);
        double[][] v = new double[4][];
        int[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int k = 0; k < 4; k++) {
            double a = sg[k][0] * HALF, b = sg[k][1] * HALF;
            v[k] = new double[]{a * c - b * sn, a * sn + b * c};
        }
        double w = HALF * (Math.abs(c) + Math.abs(sn));
        VoxelShape out = Shapes.empty();
        for (int j = 0; j < SLICES; j++) {
            double x0 = -w + 2 * w * j / SLICES, x1 = -w + 2 * w * (j + 1) / SLICES;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int q = 0; q <= 8; q++) {
                double x = x0 + (x1 - x0) * q / 8;
                for (int k = 0; k < 4; k++) {
                    double[] p = v[k], r = v[(k + 1) % 4];
                    double dx = r[0] - p[0];
                    if (Math.abs(dx) < 1e-9) {
                        if (Math.abs(x - p[0]) < 1e-6) {
                            lo = Math.min(lo, Math.min(p[1], r[1]));
                            hi = Math.max(hi, Math.max(p[1], r[1]));
                        }
                        continue;
                    }
                    double t = (x - p[0]) / dx;
                    if (t < -1e-6 || t > 1 + 1e-6) continue;
                    double z = p[1] + t * (r[1] - p[1]);
                    lo = Math.min(lo, z);
                    hi = Math.max(hi, z);
                }
            }
            if (lo > hi) continue;
            out = Shapes.or(out, Shapes.box(0.5 + x0, 0, 0.5 + lo, 0.5 + x1, TOP, 0.5 + hi));
        }
        return out.optimize();
    }
}
