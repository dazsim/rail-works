package com.workshopcraft.railworks.trolley;

import java.util.ArrayList;
import java.util.List;

/**
 * The pannier tank engine as boxes in its own frame (x along, y up from rail top, z right), stage by stage.
 * Zone P is the livery colour, PB and PR the black and red parts: all three show as primer until painted.
 */
public final class EngineShape {
    public static final double[] AXLES = {-2.3, 0, 2.3};
    public static final double WR = 0.7, WY = 0.7, CRANK = 0.32;

    /** A box of size s about centre c, turned by the row major matrix m (null for none). */
    public record Part(String zone, float cx, float cy, float cz, float sx, float sy, float sz, float[] m) {}

    /** One item's worth of a stage: what it adds and where its rivets go. */
    public record Unit(String item, List<Part> parts, List<float[]> rivets) {}

    /** Rivets: one blow per rivet set, BLOW: one blow per unit, NONE: done once fitted. */
    public enum Fix { RIVETS, BLOW, NONE }

    public record Stage(String id, Fix fix, List<Unit> units) {
        public int work() {
            return switch (fix) {
                case RIVETS -> units.stream().mapToInt(u -> u.rivets().size()).sum();
                case BLOW -> units.size();
                case NONE -> 0;
            };
        }
    }

    public static final List<Stage> STAGES = stages();

    private EngineShape() {}

    static float[] rot(char ax, double a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return switch (ax) {
            case 'x' -> new float[] {1, 0, 0, 0, c, -s, 0, s, c};
            case 'y' -> new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
            default -> new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
        };
    }

    static float[] apply(float[] m, double x, double y, double z) {
        return new float[] {(float) (m[0] * x + m[1] * y + m[2] * z), (float) (m[3] * x + m[4] * y + m[5] * z), (float) (m[6] * x + m[7] * y + m[8] * z)};
    }

    static Part b(String z, double x0, double x1, double y0, double y1, double z0, double z1) {
        return new Part(z, (float) (x0 + x1) / 2, (float) (y0 + y1) / 2, (float) (z0 + z1) / 2,
                (float) Math.abs(x1 - x0), (float) Math.abs(y1 - y0), (float) Math.abs(z1 - z0), null);
    }

    static Part c(String z, double cx, double cy, double cz, double sx, double sy, double sz, float[] m) {
        return new Part(z, (float) cx, (float) cy, (float) cz, (float) sx, (float) sy, (float) sz, m);
    }

    static double[] side(double a, double b) {
        return a < b ? new double[] {a, b} : new double[] {b, a};
    }

    static Part bs(String z, double x0, double x1, double y0, double y1, double za, double zb) {
        double[] s = side(za, zb);
        return b(z, x0, x1, y0, y1, s[0], s[1]);
    }

    /** A polygon tube of radius r along an axis through c, plates t thick; cap fills both ends. */
    static List<Part> prism(String z, char axis, double cx, double cy, double cz, double r, double l, int n, double t, boolean cap) {
        List<Part> out = new ArrayList<>();
        double ch = 2 * r * Math.tan(Math.PI / n) * 1.02;
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            float[] m = rot(axis, a);
            float[] off;
            if (axis == 'x') {
                off = apply(m, 0, r - t / 2, 0);
                out.add(c(z, cx + off[0], cy + off[1], cz + off[2], l, t, ch, m));
            } else if (axis == 'y') {
                off = apply(m, r - t / 2, 0, 0);
                out.add(c(z, cx + off[0], cy + off[1], cz + off[2], t, l, ch, m));
            } else {
                off = apply(m, 0, r - t / 2, 0);
                out.add(c(z, cx + off[0], cy + off[1], cz + off[2], ch, t, l, m));
            }
        }
        if (cap) {
            int k = Math.max(2, n / 4);
            double w = 2 * r * Math.cos(Math.PI / n) * 0.98, d = w * Math.tan(Math.PI / (2 * k)) * 1.05;
            for (int i = 0; i < k; i++) {
                float[] m = rot(axis, Math.PI * i / k);
                if (axis == 'x') out.add(c(z, cx, cy, cz, l * 0.999, w, d, m));
                else if (axis == 'y') out.add(c(z, cx, cy, cz, w, l * 0.999, d, m));
                else out.add(c(z, cx, cy, cz, w, d, l * 0.999, m));
            }
        }
        return out;
    }

    static List<Part> prism(String z, char axis, double cx, double cy, double cz, double r, double l, int n) {
        return prism(z, axis, cx, cy, cz, r, l, n, 0.06, true);
    }

    /** One wheel about its own centre, crank at angle 0: drawn turned by the renderer. */
    public static List<Part> wheel(int s) {
        List<Part> o = new ArrayList<>();
        double zc = s * 0.72;
        o.addAll(prism("STEEL", 'z', 0, 0, zc, WR, 0.12, 24, 0.09, false));
        o.addAll(prism("BLACK", 'z', 0, 0, zc - s * 0.08, WR + 0.04, 0.03, 24, 0.05, false));
        for (int i = 0; i < 12; i++) {
            float[] m = rot('z', 2 * Math.PI * i / 12);
            float[] p = apply(m, 0, 0.36, 0);
            o.add(c("P", p[0], p[1], zc, 0.07, 0.6, 0.06, m));
        }
        o.addAll(prism("P", 'z', 0, 0, zc, 0.16, 0.1, 10));
        o.add(c("P", -0.3, 0, zc, 0.36, 0.26, 0.09, null));
        o.addAll(prism("STEEL", 'z', CRANK, 0, s * 0.83, 0.07, 0.12, 8));
        return o;
    }

    /** The coupling rod on side s with that side's crank at angle a. */
    public static Part rod(int s, double a) {
        double py = WY + CRANK * Math.sin(a), px = CRANK * Math.cos(a);
        double[] z = side(s * 0.88, s * 0.95);
        return b("STEEL", px - 2.42, px + 2.42, py - 0.06, py + 0.06, z[0], z[1]);
    }

    /** Points along a line for rivet heads, facing out along the given axis. */
    static List<float[]> row(int n, double x0, double y0, double z0, double x1, double y1, double z1, char face) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 0.5 : (double) i / (n - 1);
            float x = (float) (x0 + (x1 - x0) * t), y = (float) (y0 + (y1 - y0) * t), z = (float) (z0 + (z1 - z0) * t);
            float sx = face == 'x' ? 0.03f : 0.06f, sy = face == 'y' ? 0.03f : 0.06f, sz = face == 'z' ? 0.03f : 0.06f;
            out.add(new float[] {x, y, z, sx, sy, sz});
        }
        return out;
    }

    static Unit unit(String item, List<Part> parts, List<float[]> rivets) {
        return new Unit(item, List.copyOf(parts), List.copyOf(rivets));
    }

    static List<float[]> cat(List<float[]> a, List<float[]> b) {
        List<float[]> o = new ArrayList<>(a);
        o.addAll(b);
        return o;
    }

    private static List<Stage> stages() {
        List<Stage> st = new ArrayList<>();
        // 1 frames, running plate, buffer beams and steps
        List<Part> f = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            f.add(bs("PB", -4.75, 4.75, 0.42, 1.55, s * 0.52, s * 0.6));
            for (double x : AXLES) f.add(bs("PB", x - 0.22, x + 0.22, 0.45, 0.95, s * 0.6, s * 0.66));
            f.add(bs("PB", -4.9, 4.9, 1.38, 1.55, s * 2.14, s * 2.2));
            f.add(bs("PB", -3.05, -2.45, 1.0, 1.06, s * 1.98, s * 2.2));
            f.add(bs("PB", -3.05, -2.45, 0.55, 0.61, s * 1.98, s * 2.2));
            f.add(bs("PB", -3.02, -2.98, 0.55, 1.38, s * 2.14, s * 2.2));
            f.add(bs("PB", -2.52, -2.48, 0.55, 1.38, s * 2.14, s * 2.2));
        }
        for (int e = -1; e <= 1; e += 2) {
            double[] x = side(e * 4.78, e * 4.92);
            f.add(b("PR", x[0], x[1], 0.7, 1.55, -2.2, 2.2));
        }
        f.add(b("PB", -4.9, 4.9, 1.55, 1.65, -2.2, 2.2));
        st.add(new Stage("frames", Fix.RIVETS, List.of(unit("engine_frames", f,
                cat(row(12, -4.4, 1.48, -0.61, 4.4, 1.48, -0.61, 'z'), row(12, -4.4, 1.48, 0.61, 4.4, 1.48, 0.61, 'z'))))));
        // 2 wheelsets: the wheels themselves are drawn turning; this is the axle between them
        List<Unit> ws = new ArrayList<>();
        for (double x : AXLES) ws.add(unit("engine_wheelset", List.of(c("STEEL", x, WY, 0, 0.14, 0.14, 1.3, null)), List.of()));
        st.add(new Stage("wheelsets", Fix.BLOW, ws));
        // 3 cylinders and motion
        List<Part> cy = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            cy.addAll(prism("CAST", 'x', 3.7, 1.1, s * 0.27, 0.3, 1.3, 12));
            cy.add(b("STEEL", 0.1, 3.05, 0.95, 1.05, s * 0.27 - 0.04, s * 0.27 + 0.04));
        }
        List<Part> mo = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            mo.add(bs("STEEL", 2.6, 3.05, 1.2, 1.28, s * 0.4, s * 0.48));
            mo.add(bs("STEEL", 2.6, 3.05, 0.86, 0.94, s * 0.4, s * 0.48));
        }
        st.add(new Stage("motion", Fix.RIVETS, List.of(
                unit("engine_cylinders", cy, cat(row(4, 3.2, 1.4, -0.27, 4.2, 1.4, -0.27, 'y'), row(4, 3.2, 1.4, 0.27, 4.2, 1.4, 0.27, 'y'))),
                unit("valve_motion", mo, List.of()))));
        // 4 boiler barrel and dome
        List<Part> bb = new ArrayList<>(prism("P", 'x', 0.9, 2.6, 0, 0.85, 4.4, 24));
        bb.addAll(prism("P", 'y', 1.2, 3.58, 0, 0.38, 0.55, 14));
        for (int i = 0; i < 3; i++) bb.add(b("STEEL", -0.5 + i * 1.6, -0.44 + i * 1.6, 1.73, 3.47, -0.02, 0.02));
        st.add(new Stage("boiler", Fix.RIVETS, List.of(unit("boiler_barrel", bb,
                cat(row(8, -1.2, 3.47, 0.05, 3.0, 3.47, 0.05, 'y'), row(8, -1.2, 2.6, 0.87, 3.0, 2.6, 0.87, 'z'))))));
        // 5 firebox: casing outside, copper backhead in the cab, brass safety valve bonnet
        List<Part> fb = new ArrayList<>();
        fb.add(b("P", -2.3, -1.3, 1.65, 3.55, -0.95, 0.95));
        fb.add(b("COPPER", -2.33, -2.3, 1.7, 3.5, -0.9, 0.9));
        fb.addAll(prism("BRASS", 'y', -0.6, 3.62, 0, 0.24, 0.55, 12));
        fb.addAll(prism("BRASS", 'y', -1.0, 3.75, 0.3, 0.07, 0.35, 8));
        st.add(new Stage("firebox", Fix.RIVETS, List.of(unit("engine_firebox", fb,
                cat(row(6, -2.2, 3.57, -0.6, -1.4, 3.57, -0.6, 'y'), row(6, -2.2, 3.57, 0.6, -1.4, 3.57, 0.6, 'y'))))));
        // 6 smokebox and saddle
        List<Part> sb = new ArrayList<>(prism("PB", 'x', 3.72, 2.6, 0, 0.92, 1.25, 24));
        sb.add(b("PB", 3.1, 4.34, 1.65, 2.0, -0.72, 0.72));
        st.add(new Stage("smokebox", Fix.RIVETS, List.of(unit("smokebox", sb, row(8, 3.15, 3.53, 0, 4.3, 3.53, 0, 'y')))));
        // 7 door and chimney
        List<Part> dr = new ArrayList<>(prism("PB", 'x', 4.385, 2.6, 0, 0.8, 0.07, 20));
        dr.add(b("STEEL", 4.42, 4.48, 2.55, 2.65, -0.25, 0.25));
        List<Part> ch = new ArrayList<>(prism("PB", 'y', 3.72, 4.15, 0, 0.26, 1.5, 14));
        ch.addAll(prism("COPPER", 'y', 3.72, 5.07, 0, 0.34, 0.35, 14));
        st.add(new Stage("chimney", Fix.BLOW, List.of(unit("smokebox_door", dr, List.of()), unit("engine_chimney", ch, List.of()))));
        // 8 pannier tanks
        List<Unit> tk = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            List<Part> t = new ArrayList<>();
            t.add(bs("P", -1.3, 2.7, 2.0, 3.75, s * 0.9, s * 1.95));
            t.add(bs("PB", 2.0, 2.4, 3.75, 3.82, s * 1.25, s * 1.6));
            t.add(bs("STEEL", -1.2, 2.6, 3.0, 3.04, s * 1.95, s * 1.99));
            tk.add(unit("pannier_tank", t, row(6, -1.2, 3.72, s * 1.96, 2.6, 3.72, s * 1.96, 'z')));
        }
        st.add(new Stage("tanks", Fix.RIVETS, tk));
        // 9 cab and bunker
        List<Part> cab = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            cab.add(bs("P", -2.3, -1.3, 1.65, 4.9, s * 2.07, s * 2.15));
            cab.add(bs("P", -4.3, -3.1, 1.65, 4.9, s * 2.07, s * 2.15));
            cab.add(bs("P", -3.1, -2.3, 3.6, 4.9, s * 2.07, s * 2.15));
            cab.add(bs("BRASS", -4.0, -3.35, 2.9, 3.25, s * 2.15, s * 2.17));
            cab.add(bs("P", -1.38, -1.3, 3.9, 4.6, s * 1.55, s * 2.15));
            cab.add(bs("P", -1.38, -1.3, 3.9, 4.6, s * 0.25, s * 0.95));
            cab.add(bs("GLASS", -1.37, -1.31, 3.9, 4.6, s * 0.95, s * 1.55));
            cab.add(bs("P", -4.38, -4.3, 3.6, 4.9, s * 1.4, s * 2.15));
            cab.add(bs("P", -4.38, -4.3, 3.6, 4.9, 0, s * 0.6));
            cab.add(bs("GLASS", -4.37, -4.31, 3.75, 4.5, s * 0.6, s * 1.4));
            cab.add(bs("P", -4.38, -4.3, 4.5, 4.9, s * 0.6, s * 1.4));
            cab.add(bs("P", -4.38, -4.3, 3.6, 3.75, s * 0.6, s * 1.4));
            cab.add(bs("TEAK", -3.9, -3.4, 2.55, 2.62, s * 1.6, s * 2.06));
        }
        cab.add(b("P", -1.38, -1.3, 1.65, 3.9, -2.15, 2.15));
        cab.add(b("P", -1.38, -1.3, 4.6, 4.9, -2.15, 2.15));
        cab.add(b("P", -1.38, -1.3, 3.9, 4.6, -0.25, 0.25));
        for (int i = 0; i < 8; i++) {
            double a0 = -0.42 + 0.84 * i / 8, a1 = -0.42 + 0.84 * (i + 1) / 8, am = (a0 + a1) / 2, r = 6.0;
            double y = 4.9 - r * Math.cos(0.42) + r * Math.cos(am);
            cab.add(c("BLACK", -2.85, y + 0.03, r * Math.sin(am), 3.25, 0.06, r * (a1 - a0) * 1.03, rot('x', am)));
        }
        cab.add(b("TREATED", -4.3, -2.3, 1.65, 1.7, -2.07, 2.07));
        cab.add(b("TREATED", -2.3, -1.38, 1.65, 1.7, -2.07, -0.95));
        cab.add(b("TREATED", -2.3, -1.38, 1.65, 1.7, 0.95, 2.07));
        List<Part> bk = List.of(b("P", -4.9, -4.38, 1.65, 3.6, -2.0, 2.0));
        st.add(new Stage("cab", Fix.RIVETS, List.of(
                unit("engine_cab", cab, cat(row(4, -2.25, 4.85, 2.16, -1.35, 4.85, 2.16, 'z'), row(4, -2.25, 4.85, -2.16, -1.35, 4.85, -2.16, 'z'))),
                unit("coal_bunker", bk, row(2, -4.91, 3.4, -1.5, -4.91, 3.4, 1.5, 'x')))));
        // 10 controls
        List<Part> ct = new ArrayList<>();
        ct.add(b("BLACK", -2.36, -2.33, 2.05, 2.6, -0.28, 0.28));
        ct.addAll(prism("BRASS", 'x', -2.33, 3.25, 0.4, 0.15, 0.04, 12));
        ct.addAll(prism("DIAL", 'x', -2.355, 3.25, 0.4, 0.12, 0.012, 12));
        for (double z : new double[] {-0.55, 0.85}) {
            ct.add(b("BRASS", -2.36, -2.3, 2.6, 2.66, z - 0.05, z + 0.05));
            ct.add(b("BRASS", -2.36, -2.3, 3.04, 3.1, z - 0.05, z + 0.05));
            ct.add(b("GLASS", -2.35, -2.31, 2.66, 3.04, z - 0.03, z + 0.03));
        }
        ct.add(b("BRASS", -2.45, -2.3, 2.95, 3.03, -0.05, 0.05));
        ct.add(b("BRASS", -2.4, -2.3, 2.75, 2.85, 1.05, 1.25));
        ct.add(b("BLACK", -2.85, -2.55, 1.7, 1.95, 1.55, 1.85));
        ct.add(b("STEEL", -2.95, -2.5, 1.95, 2.05, 1.85, 1.9));
        ct.add(b("STEEL", -2.42, -2.36, 4.1, 4.5, 0.9, 0.93));
        ct.add(b("BRASS", -2.45, -2.33, 4.08, 4.12, 0.85, 0.98));
        ct.add(b("BRASS", -2.42, -2.36, 4.5, 4.62, 0.86, 0.97));
        st.add(new Stage("controls", Fix.BLOW, List.of(unit("engine_controls", ct, List.of()))));
        // 11 buffers and screw couplings
        List<Unit> bf = new ArrayList<>();
        for (int e = -1; e <= 1; e += 2)
            for (int s = -1; s <= 1; s += 2) {
                List<Part> p = new ArrayList<>(prism("STEEL", 'x', e * 5.03, 0.98, s * 1.5, 0.08, 0.22, 8));
                p.addAll(prism("BLACK", 'x', e * 5.27, 0.98, s * 1.5, 0.19, 0.22, 12));
                p.add(b("BLACK", Math.min(e * 4.92, e * 4.98), Math.max(e * 4.92, e * 4.98), 0.75, 1.21, s * 1.5 - 0.23, s * 1.5 + 0.23));
                bf.add(unit("carriage_buffer", p, List.of()));
            }
        for (int e = -1; e <= 1; e += 2) {
            double[] x = side(e * 4.92, e * 5.15);
            bf.add(unit("screw_coupling", List.of(b("STEEL", x[0], x[1], 0.91, 1.05, -0.05, 0.05), c("STEEL", e * 5.1, 0.78, 0, 0.05, 0.4, 0.05, null)), List.of()));
        }
        st.add(new Stage("buffers", Fix.NONE, bf));
        return List.copyOf(st);
    }

    /** The fixed controls' moving handles, at their settings: regulator 0..10, reverser -5..5, brake 0..1. */
    public static List<Part> handles(int reg, int rev, double brake) {
        List<Part> o = new ArrayList<>();
        o.add(c("STEEL", -2.55, 2.99, 0.38, 0.06, 0.06, 0.8, rot('y', 0.35 - 0.09 * reg)));
        o.add(c("BLACK", -2.55, 2.8, 1.18, 0.4, 0.06, 0.06, rot('y', -0.3 - 1.2 * brake)));
        float[] m = rot('z', -0.05 * rev);
        float[] top = apply(m, 0, 0.65, 0);
        o.add(c("STEEL", -2.7 + top[0], 1.8 + top[1], 1.7, 0.07, 1.3, 0.07, m));
        return o;
    }

    /** The coal heap in the bunker, by how full it is. */
    public static Part coal(double full) {
        return b("COAL", -4.85, -4.4, 3.6, 3.62 + 0.3 * full, -1.8, 1.8);
    }

    /** The fire seen through the firehole. */
    public static Part fire() {
        return b("FIRE", -2.37, -2.36, 2.15, 2.5, -0.18, 0.18);
    }
}
