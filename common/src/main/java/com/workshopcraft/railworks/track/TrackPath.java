package com.workshopcraft.railworks.track;

import net.minecraft.nbt.CompoundTag;

/**
 * A track centre line: straights and arcs from a start pose, with an even grade eased at both ends.
 * Heading t points along (cos t, sin t) in x and z; positive curvature turns from +x towards +z.
 * Heights are relative to the start.
 */
public final class TrackPath {
    public static final double MIN_RADIUS = 30, MAX_GRADE = 1 / 20.0, STEEP_GRADE = 1 / 40.0, MAX_SPAN = 96;
    private static final double MAX_ARC = Math.toRadians(150);

    public final double x0, z0, t0, rise;
    public final double[] len, curv;
    public final double length;

    public TrackPath(double x0, double z0, double t0, double rise, double[] len, double[] curv) {
        this.x0 = x0;
        this.z0 = z0;
        this.t0 = t0;
        this.rise = rise;
        this.len = len;
        this.curv = curv;
        double l = 0;
        for (double v : len) l += v;
        this.length = l;
    }

    /** x, z and heading at distance s along the path. */
    public double[] pose(double s) {
        double x = x0, z = z0, t = t0;
        s = Math.max(0, Math.min(length, s));
        for (int i = 0; i < len.length && s > 0; i++) {
            double l = Math.min(s, len[i]), k = curv[i];
            if (Math.abs(k) < 1e-9) {
                x += l * Math.cos(t);
                z += l * Math.sin(t);
            } else {
                x += (Math.sin(t + k * l) - Math.sin(t)) / k;
                z -= (Math.cos(t + k * l) - Math.cos(t)) / k;
                t += k * l;
            }
            s -= len[i];
        }
        return new double[]{x, z, t};
    }

    public double curvatureAt(double s) {
        for (int i = 0; i < len.length; i++) {
            if (s <= len[i] || i == len.length - 1) return curv[i];
            s -= len[i];
        }
        return 0;
    }

    private double ease() {
        return Math.min(5, length / 3);
    }

    /** The steepest grade, as rise over run. */
    public double grade() {
        double e = ease();
        return length - e <= 1e-6 ? 0 : rise / (length - e);
    }

    /** Height above the start at s. */
    public double height(double s) {
        double e = ease(), g = grade();
        s = Math.max(0, Math.min(length, s));
        if (e <= 1e-6) return length <= 0 ? 0 : rise * s / length;
        if (s < e) return g * s * s / (2 * e);
        if (s > length - e) return rise - g * (length - s) * (length - s) / (2 * e);
        return g * e / 2 + g * (s - e);
    }

    public double gradeAt(double s) {
        double e = ease(), g = grade();
        if (e <= 1e-6) return length <= 0 ? 0 : rise / length;
        if (s < e) return g * s / e;
        if (s > length - e) return g * (length - s) / e;
        return g;
    }

    public double minRadius() {
        double r = Double.POSITIVE_INFINITY;
        for (int i = 0; i < len.length; i++)
            if (len[i] > 0.05 && Math.abs(curv[i]) > 1e-9) r = Math.min(r, 1 / Math.abs(curv[i]));
        return r;
    }

    public double endHeading() {
        return pose(length)[2];
    }

    public boolean tooTight() {
        return minRadius() < MIN_RADIUS - 1e-3;
    }

    public boolean tooSteep() {
        return Math.abs(grade()) > MAX_GRADE + 1e-6;
    }

    public boolean steep() {
        return Math.abs(grade()) > STEEP_GRADE + 1e-6;
    }

    public boolean fails() {
        return tooTight() || tooSteep();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("x", x0);
        t.putDouble("z", z0);
        t.putDouble("t", t0);
        t.putDouble("rise", rise);
        long[] l = new long[len.length], k = new long[len.length];
        for (int i = 0; i < len.length; i++) {
            l[i] = Double.doubleToLongBits(len[i]);
            k[i] = Double.doubleToLongBits(curv[i]);
        }
        t.putLongArray("len", l);
        t.putLongArray("curv", k);
        return t;
    }

    public static TrackPath load(CompoundTag t) {
        long[] l = t.getLongArray("len").orElse(new long[0]), k = t.getLongArray("curv").orElse(new long[0]);
        int n = Math.min(l.length, k.length);
        double[] len = new double[n], curv = new double[n];
        for (int i = 0; i < n; i++) {
            len[i] = Double.longBitsToDouble(l[i]);
            curv[i] = Double.longBitsToDouble(k[i]);
        }
        return new TrackPath(t.getDoubleOr("x", 0), t.getDoubleOr("z", 0), t.getDoubleOr("t", 0), t.getDoubleOr("rise", 0), len, curv);
    }

    static double wrapPi(double a) {
        a = (a + Math.PI) % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        return a - Math.PI;
    }

    private static double wrap2Pi(double a) {
        a %= 2 * Math.PI;
        if (a < 0) a += 2 * Math.PI;
        return a > 2 * Math.PI - 1e-7 ? 0 : a;
    }

    /** One arc from the start pose through the end point; the end heading follows. */
    public static TrackPath free(double x0, double z0, double t0, double x1, double z1, double rise) {
        double dx = x1 - x0, dz = z1 - z0, d = Math.hypot(dx, dz);
        double a = wrapPi(Math.atan2(dz, dx) - t0);
        if (Math.abs(a) < 1e-5 || d < 1e-6) return new TrackPath(x0, z0, t0, rise, new double[]{d}, new double[]{0});
        double k = 2 * Math.sin(a) / d;
        return new TrackPath(x0, z0, t0, rise, new double[]{2 * a / k}, new double[]{k});
    }

    /** Arc, straight, arc between two fixed poses, with the widest radius that fits. Null if none does. */
    public static TrackPath fixed(double x0, double z0, double t0, double x1, double z1, double t1, double rise) {
        double chord = Math.hypot(x1 - x0, z1 - z0);
        for (double r = 3000; r >= 2; r *= 0.97) {
            TrackPath p = csc(x0, z0, t0, x1, z1, t1, rise, r, chord);
            if (p == null) continue;
            double lo = r, hi = r / 0.97;
            for (int i = 0; i < 14 && r < 3000; i++) {
                double mid = (lo + hi) / 2;
                TrackPath q = csc(x0, z0, t0, x1, z1, t1, rise, mid, chord);
                if (q != null) {
                    p = q;
                    lo = mid;
                } else hi = mid;
            }
            return p;
        }
        return null;
    }

    private static TrackPath csc(double x0, double z0, double t0, double x1, double z1, double t1, double rise, double r, double chord) {
        TrackPath best = null;
        for (int s1 = -1; s1 <= 1; s1 += 2)
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                double c1x = x0 - s1 * r * Math.sin(t0), c1z = z0 + s1 * r * Math.cos(t0);
                double c2x = x1 - s2 * r * Math.sin(t1), c2z = z1 + s2 * r * Math.cos(t1);
                double dx = c2x - c1x, dz = c2z - c1z, d = Math.hypot(dx, dz), al = Math.atan2(dz, dx);
                double[] phis;
                double straight;
                if (s1 == s2) {
                    straight = d;
                    phis = d < 1e-7 ? new double[]{t0} : new double[]{al};
                } else {
                    if (d < 2 * r) continue;
                    straight = Math.sqrt(d * d - 4 * r * r);
                    double b = Math.asin(2 * r / d);
                    phis = new double[]{al + b, al - b};
                }
                for (double phi : phis) {
                    double a1 = wrap2Pi(s1 * (phi - t0)), a2 = wrap2Pi(s2 * (t1 - phi));
                    if (a1 > MAX_ARC || a2 > MAX_ARC) continue;
                    TrackPath p = new TrackPath(x0, z0, t0, rise, new double[]{r * a1, straight, r * a2}, new double[]{s1 / r, 0, s2 / r});
                    if (p.length > chord * 2.5 + 1) continue;
                    double[] e = p.pose(p.length);
                    if (Math.hypot(e[0] - x1, e[1] - z1) > 0.02 || Math.abs(wrapPi(e[2] - t1)) > 0.01) continue;
                    if (best == null || p.length < best.length) best = p;
                }
            }
        return best == null ? null : best.trimmed();
    }

    private TrackPath trimmed() {
        int n = 0;
        for (double l : len) if (l > 1e-6) n++;
        if (n == len.length) return this;
        double[] l2 = new double[Math.max(1, n)], k2 = new double[Math.max(1, n)];
        int j = 0;
        for (int i = 0; i < len.length; i++)
            if (len[i] > 1e-6) {
                l2[j] = len[i];
                k2[j++] = curv[i];
            }
        return new TrackPath(x0, z0, t0, rise, l2, k2);
    }
}
