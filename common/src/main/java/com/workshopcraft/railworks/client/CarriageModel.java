package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.workshopcraft.railworks.trolley.CarriageBody.Pose;
import com.workshopcraft.railworks.trolley.CarriageLayout;
import com.workshopcraft.railworks.trolley.Gangways;
import net.minecraft.world.phys.Vec3;
import com.workshopcraft.railworks.trolley.CarriageLayout.Box;
import com.workshopcraft.railworks.trolley.CarriageLayout.Model;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Carriage geometry from the layout boxes: each zone is a 336 by 128 patch of the sheet at 16 pixels a block. */
public final class CarriageModel {
    public static final List<String> ZONES = List.of("CHOC", "CREAM", "BLACK", "RED", "STEEL", "BRIGHT", "PLATE", "CAST", "BRASS",
            "LINING", "TREATED", "TEAK", "FLOOR", "CANVAS", "CEILING", "MOQUETTE", "GLASS", "LAMP", "RIVET", "FIRE");
    static final int ZW = 336, ZH = 128, SHEET = 1024;

    private CarriageModel() {}

    /** Vertices as x y z u v nx ny nz, four to a quad. */
    public static final class Mesh {
        float[] d = new float[8 * 4 * 6 * 64];
        int verts;

        void put(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            if ((verts + 1) * 8 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            int o = verts++ * 8;
            d[o] = x;
            d[o + 1] = y;
            d[o + 2] = z;
            d[o + 3] = u;
            d[o + 4] = v;
            d[o + 5] = nx;
            d[o + 6] = ny;
            d[o + 7] = nz;
        }

        public boolean empty() {
            return verts == 0;
        }

        void emit(PoseStack.Pose p, VertexConsumer vc, int light, int color) {
            Matrix4f m = p.pose();
            Vector3f pos = new Vector3f(), nn = new Vector3f();
            for (int i = 0; i < verts; i++) {
                int o = i * 8;
                m.transformPosition(d[o], d[o + 1], d[o + 2], pos);
                p.transformNormal(d[o + 5], d[o + 6], d[o + 7], nn);
                vc.addVertex(pos.x(), pos.y(), pos.z(), color, d[o + 3], d[o + 4], OverlayTexture.NO_OVERLAY, light, nn.x(), nn.y(), nn.z());
            }
        }
    }

    /** The solid and glass meshes for one build state, leaves left out. */
    public record Built(Mesh solid, Mesh glass) {}

    /** joined has bit 0 set when the rear gangway is joined, bit 1 the front: their rest bellows and footplate are left out. */
    public static Built build(String st, int joined) {
        Mesh solid = new Mesh(), glass = new Mesh();
        for (Slot s : CarriageLayout.SLOTS) {
            Model m = CarriageLayout.modelAt(st, s.i());
            if (m == null) continue;
            if (Gangways.moving(s) && (joined >> (s.end() > 0 ? 1 : 0) & 1) != 0) continue;
            // a block model is drawn by the renderer itself; its box only stands in for collision and aim
            for (Box b : m.solid()) if (!b.zone().equals(CarriageLayout.BLOCK)) box(solid, b, 0, 0, 0);
            for (Box b : m.glass()) box(glass, b, 0, 0, 0);
            int set = CarriageLayout.work(st, s.i());
            for (int k = 0; k < m.rivets().size(); k++) {
                float[] r = m.rivets().get(k);
                box(solid, new Box(k < set ? "BLACK" : "RIVET", r[0], r[1], r[2], r[3], r[4], r[5], 0, 0), 0, 0, 0);
            }
        }
        return new Built(solid, glass);
    }

    /** The fire boxes of every fixture burning now, to be drawn at full light. */
    public static Mesh lit(String st, String look) {
        Mesh out = new Mesh();
        for (Slot s : CarriageLayout.SLOTS) {
            Model m = CarriageLayout.modelAt(st, s.i());
            if (m == null || !com.workshopcraft.railworks.trolley.Carriage.lit(look, s.i())) continue;
            for (Box b : m.lit()) box(out, b, 0, 0, 0);
        }
        return out;
    }

    /** What could go on next for the item held: the first model in each slot it fits. */
    public static Built ghost(List<Slot> slots, net.minecraft.world.item.Item item) {
        Mesh m = new Mesh();
        for (Slot s : slots) {
            Model md = item == null ? s.models().getFirst() : s.model(item);
            if (md == null) continue;
            for (Box b : md.solid()) box(m, b, 0, 0, 0);
            for (Box b : md.lit()) box(m, b, 0, 0, 0);
            if (md.leaf() != null) for (Box b : md.leaf().solid()) box(m, b, (float) md.leaf().pivot().x, (float) md.leaf().pivot().y, (float) md.leaf().pivot().z);
        }
        return new Built(m, new Mesh());
    }

    /** A box, turned about its centre by rx then rz, its centre moved by (ox, oy, oz). */
    static void box(Mesh out, Box b, float ox, float oy, float oz) {
        int zi = Math.max(0, ZONES.indexOf(b.zone()));
        float u0 = (zi % 3) * ZW, v0 = (zi / 3) * ZH;
        float hx = b.sx() / 2, hy = b.sy() / 2, hz = b.sz() / 2;
        float cx = b.cx() + ox, cy = b.cy() + oy, cz = b.cz() + oz;
        float cxr = (float) Math.cos(b.rx()), sxr = (float) Math.sin(b.rx()), czr = (float) Math.cos(b.rz()), szr = (float) Math.sin(b.rz());
        // six faces: normal axis, sign; the face's u runs along ua, v down along va
        for (int f = 0; f < 6; f++) {
            int ax = f / 2, sg = f % 2 == 0 ? 1 : -1;
            int ua = ax == 0 ? 2 : 0, va = ax == 1 ? 2 : 1;
            float[] h = {hx, hy, hz}, c = {cx, cy, cz};
            float lu = 2 * h[ua], lv = 2 * h[va];
            float su = Math.min(lu * 16, ZW - 64), sv = Math.min(lv * 16, ZH - 32);
            float ou = (float) Math.floorMod((int) Math.floor((c[ua] - h[ua]) * 16), 64), ov = (float) Math.floorMod((int) Math.floor((c[va] - h[va]) * 16), 32);
            float[][] q = new float[4][3];
            float[] uu = new float[4], vv = new float[4];
            for (int k = 0; k < 4; k++) {
                // corners in order so the quad faces out: walk round the face counterclockwise seen from outside
                int a = k == 1 || k == 2 ? 1 : -1, bb = k >= 2 ? 1 : -1;
                float[] p = new float[3];
                p[ax] = sg * h[ax];
                p[ua] = a * h[ua];
                p[va] = bb * h[va];
                if (sg * (ax == 1 ? -1 : 1) < 0) p[ua] = -p[ua];
                q[k] = p;
                uu[k] = (u0 + ou + (p[ua] + h[ua]) / (2 * h[ua] == 0 ? 1 : 2 * h[ua]) * su) / SHEET;
                vv[k] = (v0 + ov + (h[va] - p[va]) / (2 * h[va] == 0 ? 1 : 2 * h[va]) * sv) / SHEET;
            }
            float[] n = new float[3];
            n[ax] = sg;
            float[] nr = turn(n, cxr, sxr, czr, szr);
            float[] q0 = turn(q[0], cxr, sxr, czr, szr), q1 = turn(q[1], cxr, sxr, czr, szr), q2 = turn(q[2], cxr, sxr, czr, szr);
            // keep the winding outward whatever the axes did
            float[] e1 = {q1[0] - q0[0], q1[1] - q0[1], q1[2] - q0[2]}, e2 = {q2[0] - q0[0], q2[1] - q0[1], q2[2] - q0[2]};
            float dot = (e1[1] * e2[2] - e1[2] * e2[1]) * nr[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * nr[1] + (e1[0] * e2[1] - e1[1] * e2[0]) * nr[2];
            for (int i = 0; i < 4; i++) {
                int k = dot >= 0 ? i : 3 - i;
                float[] p = turn(q[k], cxr, sxr, czr, szr);
                out.put(p[0] + cx, p[1] + cy, p[2] + cz, uu[k], vv[k], nr[0], nr[1], nr[2]);
            }
        }
    }

    private static float[] turn(float[] p, float cxr, float sxr, float czr, float szr) {
        float y = p[1] * cxr - p[2] * sxr, z = p[1] * sxr + p[2] * cxr;
        float x = p[0] * czr - y * szr, y2 = p[0] * szr + y * czr;
        return new float[]{x, y2, z};
    }

    /** A door or coupling leaf, turned about its pivot, or for a sliding leaf moved along by slide; the bolt shown when bolted. */
    static void leaf(Mesh solid, Mesh glass, CarriageLayout.Leaf l, float angle, float slide, boolean bolted) {
        float px = (float) l.pivot().x + slide, py = (float) l.pivot().y, pz = (float) l.pivot().z;
        for (int g = 0; g < 3; g++) {
            if (g == 2 && !bolted) break;
            Mesh out = g == 1 ? glass : solid;
            for (Box b : g == 0 ? l.solid() : g == 1 ? l.glass() : l.bolt()) {
                Mesh tmp = new Mesh();
                box(tmp, b, 0, 0, 0);
                float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
                for (int i = 0; i < tmp.verts; i++) {
                    int o = i * 8;
                    float x = tmp.d[o], y = tmp.d[o + 1], z = tmp.d[o + 2], nx = tmp.d[o + 5], ny = tmp.d[o + 6], nz = tmp.d[o + 7];
                    if (l.axis() == 'y') {
                        out.put(x * c + z * s + px, y + py, -x * s + z * c + pz, tmp.d[o + 3], tmp.d[o + 4], nx * c + nz * s, ny, -nx * s + nz * c);
                    } else {
                        out.put(x * c - y * s + px, x * s + y * c + py, z + pz, tmp.d[o + 3], tmp.d[o + 4], nx * c - ny * s, nx * s + ny * c, nz);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- joined gangways

    static final int FOLDS = 8;

    /** A six sided cell from eight corners, c[t * 4 + y * 2 + w], each face facing away from the cell's middle. */
    static void cell(Mesh out, String zone, Vec3[] c) {
        int zi = Math.max(0, ZONES.indexOf(zone));
        float u0 = (zi % 3) * ZW / (float) SHEET, v0 = (zi / 3) * ZH / (float) SHEET, du = 48f / SHEET, dv = 48f / SHEET;
        Vec3 mid = Vec3.ZERO;
        for (Vec3 v : c) mid = mid.add(v);
        mid = mid.scale(1 / 8.0);
        int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] f : faces) {
            Vec3 a = c[f[0]], b = c[f[1]], d = c[f[2]], e = c[f[3]];
            Vec3 n = d.subtract(a).cross(e.subtract(b));
            if (n.lengthSqr() < 1e-12) continue;
            n = n.normalize();
            Vec3 fc = a.add(b).add(d).add(e).scale(0.25);
            boolean flip = n.dot(fc.subtract(mid)) < 0;
            if (flip) n = n.scale(-1);
            Vec3[] q = flip ? new Vec3[] {e, d, b, a} : new Vec3[] {a, b, d, e};
            float[][] uv = {{u0, v0}, {u0 + du, v0}, {u0 + du, v0 + dv}, {u0, v0 + dv}};
            // the winding of a, b, d, e has its normal along (d - a) x (e - b); put() takes them counterclockwise from outside
            for (int i = 0; i < 4; i++) out.put((float) q[i].x, (float) q[i].y, (float) q[i].z, uv[i][0], uv[i][1], (float) n.x, (float) n.y, (float) n.z);
        }
    }

    /** The bellows of a joined gangway as a ruled surface from our end face at e to the other's at oe, face plates meeting halfway. */
    public static void bellows(Mesh out, Pose me, int e, Pose other, int oe) {
        double ws = Math.signum(me.worldVec(new Vec3(0, 0, 1)).dot(other.worldVec(new Vec3(0, 0, 1))));
        if (ws == 0) ws = 1;
        double fws = ws;
        java.util.function.BiFunction<double[], Double, Vec3> at = (yw, t) -> {
            Vec3 a = new Vec3(e * Gangways.GR, yw[0], yw[1]);
            Vec3 b = me.local(other.world(new Vec3(oe * Gangways.GR, yw[0], yw[1] * fws)));
            return a.lerp(b, t);
        };
        double len = at.apply(new double[] {Gangways.FLT, 0}, 0.0).distanceTo(at.apply(new double[] {Gangways.FLT, 0}, 1.0));
        double plate = Math.min(0.2, 0.04 / Math.max(len, 0.1));
        double y0 = Gangways.FLT - 0.04, y1 = Gangways.HEAD, gin = Gangways.GIN + 0.005;
        for (int k = 0; k < 2 * FOLDS; k++) {
            double t0 = k < FOLDS ? (0.5 - plate) * k / FOLDS : 0.5 + plate + (0.5 - plate) * (k - FOLDS) / FOLDS;
            double t1 = k < FOLDS ? (0.5 - plate) * (k + 1) / FOLDS : 0.5 + plate + (0.5 - plate) * (k - FOLDS + 1) / FOLDS;
            double ow = k % 2 == 0 ? 0.58 : 0.55;
            String zone = k % 2 == 0 ? "BLACK" : "CAST";
            for (int sd = -1; sd <= 1; sd += 2) ring(out, zone, at, t0, t1, y0, y1, sd * gin, sd * ow);
            ring(out, zone, at, t0, t1, y1, y1 + ow - Gangways.GIN, -ow, ow);
        }
        double t0 = 0.5 - plate, t1 = 0.5 + plate;
        for (int sd = -1; sd <= 1; sd += 2) ring(out, "STEEL", at, t0, t1, y0, y1, sd * gin, sd * Gangways.GOUT);
        ring(out, "STEEL", at, t0, t1, y1, Gangways.CROWN, -Gangways.GOUT, Gangways.GOUT);
    }

    private static void ring(Mesh out, String zone, java.util.function.BiFunction<double[], Double, Vec3> at, double t0, double t1, double y0, double y1, double w0, double w1) {
        Vec3[] c = new Vec3[8];
        for (int t = 0; t < 2; t++)
            for (int y = 0; y < 2; y++)
                for (int w = 0; w < 2; w++) c[t * 4 + y * 2 + w] = at.apply(new double[] {y == 0 ? y0 : y1, w == 0 ? w0 : w1}, t == 0 ? t0 : t1);
        cell(out, zone, c);
    }

    /** Our footplate run out from the end at e to past the middle plane at far, the second carriage's a little higher and narrower. */
    public static void footplate(Mesh out, int e, double far, boolean upper) {
        float lift = upper ? 0.03f : 0, hw = (float) Gangways.GIN - (upper ? 0.01f : 0);
        float a0 = (float) (e * Gangways.GF), a1 = (float) (e * far);
        float cx = (a0 + a1) / 2, sx = Math.abs(a1 - a0);
        box(out, new Box("STEEL", cx, (float) Gangways.FLT - 0.02f + lift, 0, sx, 0.04f, 2 * hw, 0, 0), 0, 0, 0);
    }
}
