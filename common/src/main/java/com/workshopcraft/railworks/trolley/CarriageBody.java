package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.trolley.CarriageLayout.Box;
import com.workshopcraft.railworks.trolley.CarriageLayout.Model;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A carriage body as solid boxes in its own frame (a along, y up, w across), and the sums for moving
 * entities against it or carrying them in it.
 */
public final class CarriageBody {
    // what counts as aboard: feet over the body, from the footboards up to the roof
    static final double ABOARD_A = 10.1, ABOARD_W = 2.62, ABOARD_Y0 = 0.45, ABOARD_Y1 = 6.4;
    static final float DOOR_SWING = (float) Math.toRadians(100);
    static final double PLAYER_STEP = 0.9;
    private static final double EPS = 1e-3;
    private static final int BUCKETS = 24;
    private static final Set<Carriage> CLIENT = new HashSet<>(), SERVER = new HashSet<>();

    /** Where the body stands: its origin, its heading along +a and the slope of its floor. */
    public record Pose(double x, double y, double z, double hf, double tan) {
        public Vec3 local(Vec3 p) {
            double dx = p.x - x, dz = p.z - z, c = Math.cos(hf), s = Math.sin(hf);
            double a = dx * c + dz * s;
            return new Vec3(a, p.y - y - a * tan, -dx * s + dz * c);
        }

        public Vec3 localVec(Vec3 d) {
            double c = Math.cos(hf), s = Math.sin(hf), a = d.x * c + d.z * s;
            return new Vec3(a, d.y - a * tan, -d.x * s + d.z * c);
        }

        public Vec3 world(Vec3 l) {
            double c = Math.cos(hf), s = Math.sin(hf);
            return new Vec3(x + l.x * c - l.z * s, y + l.y + l.x * tan, z + l.x * s + l.z * c);
        }

        public Vec3 worldVec(Vec3 l) {
            double c = Math.cos(hf), s = Math.sin(hf);
            return new Vec3(l.x * c - l.z * s, l.y + l.x * tan, l.x * s + l.z * c);
        }

        boolean same(Pose o) {
            return x == o.x && y == o.y && z == o.z && hf == o.hf && tan == o.tan;
        }
    }

    final String key;
    // min a, y, w then max a, y, w for each box
    final double[] box;
    final int[][] bucket;

    private CarriageBody(String key, double[] box, int[][] bucket) {
        this.key = key;
        this.box = box;
        this.bucket = bucket;
    }

    public static boolean aboard(Vec3 l) {
        return Math.abs(l.x) <= ABOARD_A && Math.abs(l.z) <= ABOARD_W && l.y >= ABOARD_Y0 && l.y <= ABOARD_Y1;
    }

    /** Feet at l, half as wide as hw, resting on a part of c's body. */
    public static boolean standsOn(Carriage c, Vec3 l, double hw) {
        if (!aboard(l)) return false;
        CarriageBody b = c.body();
        for (int i : b.candidates(l.x - hw, l.x + hw)) {
            int k = i * 6;
            double top = b.box[k + 4];
            if (top < l.y - 0.08 || top > l.y + 0.02) continue;
            if (b.box[k] >= l.x + hw || b.box[k + 3] <= l.x - hw || b.box[k + 2] >= l.z + hw || b.box[k + 5] <= l.z - hw) continue;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- live carriages

    static void live(Carriage c) {
        (c.level().isClientSide() ? CLIENT : SERVER).add(c);
    }

    public static List<Carriage> near(Level level, Vec3 p, double r) {
        Set<Carriage> set = level.isClientSide() ? CLIENT : SERVER;
        List<Carriage> out = new ArrayList<>(2);
        set.removeIf(Entity::isRemoved);
        for (Carriage c : set) if (c.level() == level && c.position().distanceToSqr(p) < r * r) out.add(c);
        return out;
    }

    static List<Carriage> all(Level level) {
        Set<Carriage> set = level.isClientSide() ? CLIENT : SERVER;
        set.removeIf(Entity::isRemoved);
        List<Carriage> out = new ArrayList<>();
        for (Carriage c : set) if (c.level() == level) out.add(c);
        return out;
    }

    /** Moves the client's own player with a body it stands in; set by the client. */
    public interface ClientCarry {
        void carry(Carriage c, Pose was, Pose now);
    }

    public static ClientCarry clientCarry = (c, was, now) -> {};

    // ---------------------------------------------------------------- the boxes

    static CarriageBody build(String st, int doors) {
        List<double[]> l = new ArrayList<>();
        for (Slot s : CarriageLayout.SLOTS) {
            Model m = CarriageLayout.modelAt(st, s.i());
            if (m == null) continue;
            for (Box b : m.solid()) add(l, b, 0, 0, 0, 0);
            for (Box b : m.glass()) add(l, b, 0, 0, 0, 0);
            if (m.leaf() != null && m.leaf().axis() == 'y') {
                boolean open = s.door() >= 0 && (doors >> s.door() & 1) != 0;
                double lx = m.leaf().solid().isEmpty() ? 0 : m.leaf().solid().getFirst().cx();
                float ang = open ? -Math.signum(s.side()) * Math.signum((float) lx) * DOOR_SWING : 0;
                Vec3 pv = m.leaf().pivot();
                for (Box b : m.leaf().solid()) add(l, b, pv.x, pv.y, pv.z, ang);
                for (Box b : m.leaf().glass()) add(l, b, pv.x, pv.y, pv.z, ang);
            }
        }
        double[] out = new double[l.size() * 6];
        List<List<Integer>> bk = new ArrayList<>();
        for (int k = 0; k < BUCKETS; k++) bk.add(new ArrayList<>());
        for (int i = 0; i < l.size(); i++) {
            System.arraycopy(l.get(i), 0, out, i * 6, 6);
            int k0 = bucketOf(out[i * 6]), k1 = bucketOf(out[i * 6 + 3]);
            for (int k = k0; k <= k1; k++) bk.get(k).add(i);
        }
        int[][] bucket = new int[BUCKETS][];
        for (int k = 0; k < BUCKETS; k++) bucket[k] = bk.get(k).stream().mapToInt(Integer::intValue).toArray();
        return new CarriageBody(st + doors, out, bucket);
    }

    private static int bucketOf(double a) {
        return Math.clamp((int) Math.floor(a) + BUCKETS / 2, 0, BUCKETS - 1);
    }

    /** A box turned about its centre, then about a y pivot, as the box round it. Rods and rivets are left out. */
    private static void add(List<double[]> out, Box b, double px, double py, double pz, float yaw) {
        float[] d = {b.sx(), b.sy(), b.sz()};
        java.util.Arrays.sort(d);
        if (d[1] < 0.06) return;
        double hx = b.sx() / 2, hy = b.sy() / 2, hz = b.sz() / 2;
        double cx = Math.cos(b.rx()), sx = Math.sin(b.rx()), cz = Math.cos(b.rz()), sz = Math.sin(b.rz());
        // rx about x, then rz about z: extents of the turned box
        double[][] r = {
                {cz, -sz * cx, sz * sx},
                {sz, cz * cx, -cz * sx},
                {0, sx, cx}};
        double ex = Math.abs(r[0][0]) * hx + Math.abs(r[0][1]) * hy + Math.abs(r[0][2]) * hz;
        double ey = Math.abs(r[1][0]) * hx + Math.abs(r[1][1]) * hy + Math.abs(r[1][2]) * hz;
        double ez = Math.abs(r[2][0]) * hx + Math.abs(r[2][1]) * hy + Math.abs(r[2][2]) * hz;
        double mx = b.cx(), my = b.cy(), mz = b.cz();
        if (yaw != 0) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            double nx = mx * c + mz * s, nz = -mx * s + mz * c;
            double fx = Math.abs(c) * ex + Math.abs(s) * ez, fz = Math.abs(s) * ex + Math.abs(c) * ez;
            mx = nx;
            mz = nz;
            ex = fx;
            ez = fz;
        }
        mx += px;
        my += py;
        mz += pz;
        out.add(new double[] {mx - ex, my - ey, mz - ez, mx + ex, my + ey, mz + ez});
    }

    // ---------------------------------------------------------------- moving against it

    /** Vanilla's move for e this tick, cut short by any carriage body in the way. */
    public static Vec3 collide(Entity e, Vec3 move) {
        if (e instanceof RailVehicle || e.noPhysics || e.isPassenger() || e.isSpectator()) return move;
        List<Carriage> cs = near(e.level(), e.position(), 16 + move.length());
        for (Carriage c : cs) {
            Pose p = c.pose();
            // mobs only go in when led
            boolean shut = e instanceof Mob m && !m.isLeashed() && !aboard(p.local(e.position()));
            move = (shut ? c.shutBody() : c.body()).clip(e, p, move);
        }
        return move;
    }

    Vec3 clip(Entity e, Pose pose, Vec3 move) {
        Vec3 p = pose.local(e.position());
        double hw = e.getBbWidth() / 2, h = e.getBbHeight();
        double[] b = {p.x - hw, p.y, p.z - hw, p.x + hw, p.y + h, p.z + hw};
        Vec3 m = pose.localVec(move);
        double step = e.maxUpStep() <= 0 ? 0 : e instanceof Player ? Math.max(e.maxUpStep(), PLAYER_STEP) : e.maxUpStep();
        double r = Math.abs(m.x) + Math.abs(m.y) + Math.abs(m.z) + step + 0.1;
        if (b[3] + r < -ABOARD_A - 0.5 || b[0] - r > ABOARD_A + 0.5 || b[5] + r < -3 || b[2] - r > 3 || b[4] + r < -0.5 || b[1] - r > 7) return move;
        int[] cand = candidates(b[0] - r, b[3] + r);
        if (cand.length == 0) return move;
        double dy = clipAxis(1, b, m.y, cand);
        double[] g = shift(b, 0, dy, 0);
        double da, dw;
        double[] after;
        if (Math.abs(m.x) >= Math.abs(m.z)) {
            da = clipAxis(0, g, m.x, cand);
            after = shift(g, da, 0, 0);
            dw = clipAxis(2, after, m.z, cand);
        } else {
            dw = clipAxis(2, g, m.z, cand);
            after = shift(g, 0, 0, dw);
            da = clipAxis(0, after, m.x, cand);
        }
        boolean cutA = da != m.x, cutW = dw != m.z, landed = dy != m.y && m.y < 0;
        if (step > 0 && (cutA || cutW) && (landed || e.onGround())) {
            double[] base = landed ? g : b;
            double up = clipAxis(1, base, step, cand);
            double[] s = shift(base, 0, up, 0);
            double sa, sw;
            if (Math.abs(m.x) >= Math.abs(m.z)) {
                sa = clipAxis(0, s, m.x, cand);
                s = shift(s, sa, 0, 0);
                sw = clipAxis(2, s, m.z, cand);
                s = shift(s, 0, 0, sw);
            } else {
                sw = clipAxis(2, s, m.z, cand);
                s = shift(s, 0, 0, sw);
                sa = clipAxis(0, s, m.x, cand);
                s = shift(s, sa, 0, 0);
            }
            double down = clipAxis(1, s, -up, cand);
            if (sa * sa + sw * sw > da * da + dw * dw + 1e-7) {
                da = sa;
                dw = sw;
                dy = (base[1] - b[1]) + up + down;
                cutA = cutW = false;
            }
        }
        if (dy == m.y && da == m.x && dw == m.z) return move;
        Vec3 w = pose.worldVec(new Vec3(da, dy, dw));
        // keep vanilla's exact figures on any axis the body left alone, so its own collision flags stay true
        double wx = !cutA && !cutW && Math.abs(w.x - move.x) < 1e-9 ? move.x : w.x;
        double wz = !cutA && !cutW && Math.abs(w.z - move.z) < 1e-9 ? move.z : w.z;
        double wy = dy == m.y && Math.abs(w.y - move.y) < 1e-9 ? move.y : w.y;
        return new Vec3(wx, wy, wz);
    }

    private int[] candidates(double a0, double a1) {
        int k0 = bucketOf(a0), k1 = bucketOf(a1);
        if (k0 == k1) return bucket[k0];
        Set<Integer> s = new HashSet<>();
        for (int k = k0; k <= k1; k++) for (int i : bucket[k]) s.add(i);
        return s.stream().mapToInt(Integer::intValue).toArray();
    }

    private static double[] shift(double[] b, double da, double dy, double dw) {
        return new double[] {b[0] + da, b[1] + dy, b[2] + dw, b[3] + da, b[4] + dy, b[5] + dw};
    }

    /** How far b may go along one axis before a box stops it. Boxes it already sits inside are let go. */
    private double clipAxis(int ax, double[] b, double d, int[] cand) {
        if (d == 0) return 0;
        int o1 = (ax + 1) % 3, o2 = (ax + 2) % 3;
        for (int i : cand) {
            int k = i * 6;
            if (box[k + o1] >= b[o1 + 3] - 1e-7 || box[k + o1 + 3] <= b[o1] + 1e-7) continue;
            if (box[k + o2] >= b[o2 + 3] - 1e-7 || box[k + o2 + 3] <= b[o2] + 1e-7) continue;
            if (d > 0) {
                if (box[k + ax] >= b[ax + 3] - EPS) d = Math.min(d, box[k + ax] - b[ax + 3]);
            } else {
                if (box[k + ax + 3] <= b[ax] + EPS) d = Math.max(d, box[k + ax + 3] - b[ax]);
            }
        }
        return d;
    }
}
