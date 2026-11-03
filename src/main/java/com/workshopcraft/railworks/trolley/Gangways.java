package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.trolley.CarriageBody.Pose;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

/** The gangway at each end of a carriage: built or not, joined to the next carriage's or at rest, and its boxes. */
public final class Gangways {
    public static final double FLT = 1.30, GF = 10, GR = 10.06, GO = 10.5, GIN = 0.47, GOUT = 0.62, HEAD = FLT + 2.4, CROWN = FLT + 2.6;
    // the gangway's own space, before the middle plane cuts it
    static final double IN_A = 9.9, IN_Y0 = FLT - 0.6, IN_Y1 = CROWN;

    private Gangways() {}

    /**
     * Joined at end e to o's end oe, the middle plane through (ma, mw) in our frame with its normal (na, nw) pointing
     * out of us toward o.
     */
    public record Joint(Carriage o, int e, int oe, double ma, double mw, double na, double nw) {
        /** How far l lies past the plane, on o's side. */
        public double past(Vec3 l) {
            return (l.x - ma) * na + (l.z - mw) * nw;
        }

        /** The plane's farthest a out from our end over |w| up to hw. */
        public double reach(double hw) {
            double a0 = ma - (-hw - mw) * nw / na, a1 = ma - (hw - mw) * nw / na;
            return e > 0 ? Math.max(a0, a1) : Math.min(a0, a1);
        }
    }

    static int k(int e) {
        return e < 0 ? 0 : 1;
    }

    private static int[] frame, bellows, foot;
    private static int size = -1;

    private static void ids() {
        if (size == CarriageLayout.SLOTS.size()) return;
        frame = new int[2];
        bellows = new int[2];
        foot = new int[2];
        for (int i = 0; i < 2; i++) {
            frame[i] = CarriageLayout.index("gangway_frame_" + i);
            bellows[i] = CarriageLayout.index("gangway_bellows_" + i);
            foot[i] = CarriageLayout.index("gangway_footplate_" + i);
        }
        size = CarriageLayout.SLOTS.size();
    }

    private static boolean secure(String st, int i) {
        return i >= 0 && i < st.length() && CarriageLayout.secure(st, i);
    }

    /** Frame, bellows and footplate all on and fixed at end e. */
    public static boolean finished(String st, int e) {
        ids();
        return secure(st, foot[k(e)]) && secure(st, bellows[k(e)]) && secure(st, frame[k(e)]);
    }

    static boolean walled(String st, int e) {
        ids();
        return secure(st, bellows[k(e)]);
    }

    /** The parts that stretch when joined, drawn and collided apart from the fixed body. */
    public static boolean moving(Slot s) {
        return s.group().equals("gangway_bellows") || s.group().equals("gangway_footplate");
    }

    public static boolean any(String st) {
        ids();
        for (int i = 0; i < 2; i++) if (secure(st, frame[i]) || foot[i] >= 0 && CarriageLayout.fitted(st, foot[i])) return true;
        return false;
    }

    /** The carriages joined to c by gangways, in train order from the leading end; the power network's topology. */
    public static List<Carriage> train(Carriage c) {
        List<Carriage> out = new ArrayList<>();
        walk(c, 1, out);
        java.util.Collections.reverse(out);
        out.add(c);
        walk(c, -1, out);
        if (out.getFirst().towed() && !out.getLast().towed()) java.util.Collections.reverse(out);
        return out;
    }

    private static void walk(Carriage c, int e, List<Carriage> out) {
        for (Joint j = c.joint(e); j != null && j.o() != c && !out.contains(j.o()); j = j.o().joint(-j.oe())) out.add(j.o());
    }

    /** A carriage screw coupled at one of our ends, and its end there. */
    record Partner(Carriage o, int oe) {}

    /** The finished carriage screw coupled to c at end e, or null. */
    static @Nullable Partner partner(Carriage c, int e) {
        if (!c.done()) return null;
        Carriage o = null;
        int oe = 0;
        if (c.towed() && !c.mounted() && c.towKind() == RailVehicle.SCREW && c.towEnd() == e && c.leader() instanceof Carriage l) {
            o = l;
            oe = c.leaderEnd();
        } else {
            for (Carriage n : CarriageBody.near(c.level(), c.position(), 32)) {
                if (n != c && n.towed() && n.towKind() == RailVehicle.SCREW && n.leaderEnd() == e && n.leader() == c) {
                    o = n;
                    oe = n.towEnd();
                    break;
                }
            }
        }
        return o == null || !o.done() ? null : new Partner(o, oe);
    }

    /** The carriage screw coupled to c at end e and its end there, both with finished gangways that meet, or null. */
    static @Nullable Joint find(Carriage c, int e) {
        if (!finished(c.slots(), e)) return null;
        Partner pt = partner(c, e);
        if (pt == null || !finished(pt.o().slots(), pt.oe())) return null;
        Carriage o = pt.o();
        int oe = pt.oe();
        Pose p = c.pose(), q = o.pose();
        Vec3 pa = p.world(new Vec3(e * GO, FLT, 0)), pb = q.world(new Vec3(oe * GO, FLT, 0));
        if (pa.distanceToSqr(pb) > 4) return null;
        Vec3 m = p.local(pa.add(pb).scale(0.5));
        Vec3 n = p.localVec(p.worldVec(new Vec3(e, 0, 0)).subtract(q.worldVec(new Vec3(oe, 0, 0))));
        double len = Math.hypot(n.x, n.z);
        if (len < 1e-6 || Math.abs(n.x) < 0.3 * len) return null;
        return new Joint(o, e, oe, m.x, m.z, n.x / len, n.z / len);
    }

    /** In the gangway's space at either end, cut at the middle plane when joined; tol lets a point that far past it count. */
    public static boolean holds(Carriage c, Vec3 l, double tol) {
        if (Math.abs(l.z) > GOUT || l.y < IN_Y0 || l.y > IN_Y1) return false;
        int e = l.x > 0 ? 1 : -1;
        if (e * l.x < IN_A) return false;
        if (!finished(c.slots(), e)) return false;
        Joint j = c.joint(e);
        if (j == null) return e * l.x <= GO;
        return j.past(l) <= tol;
    }

    /** In the gangway's space at end e on our side of where the plane was, for putting people back on parting. */
    static boolean inHalf(Vec3 l, int e, @Nullable Joint was) {
        if (Math.abs(l.z) > GOUT + 0.2 || l.y < IN_Y0 || l.y > IN_Y1 + 0.5 || e * l.x < IN_A + 0.02) return false;
        if (was != null) return was.past(l) <= 0.05;
        return e * l.x <= GO + 0.7;
    }

    private static void span(List<double[]> out, int e, double a0, double y0, double w0, double a1, double y1, double w1) {
        double x0 = e * a0, x1 = e * a1;
        out.add(new double[] {Math.min(x0, x1), y0, Math.min(w0, w1), Math.max(x0, x1), y1, Math.max(w0, w1)});
    }

    /** Floor, walls, roof and the rest bar of each gangway, out to the middle plane when joined. */
    static CarriageBody body(Carriage c) {
        String st = c.slots();
        List<double[]> l = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        for (int e = -1; e <= 1; e += 2) {
            ids();
            if (!secure(st, bellows[k(e)])) continue;
            Joint j = c.joint(e);
            double far = j == null ? GO : e * j.reach(GOUT) + 0.05;
            if (secure(st, foot[k(e)])) span(l, e, GF, FLT - 0.04, -GIN, j == null ? GO : e * j.reach(GIN) + 0.12, FLT, GIN);
            for (int s = -1; s <= 1; s += 2) span(l, e, GR, FLT - 0.04, s * GIN, far, HEAD, s * GOUT);
            span(l, e, GR, HEAD, -GOUT, far, CROWN, GOUT);
            if (j == null) span(l, e, GO - 0.04, FLT, -GIN, GO, HEAD, GIN);
            key.append(e).append(j == null ? "r" : String.format(java.util.Locale.ROOT, "%.3f", far));
        }
        return CarriageBody.of(key.toString(), l, GO + 1.2);
    }

    /**
     * The gangway at c's end e has parted from was: everyone and everything in c's half goes just inside the end door of
     * to at its end toEnd, facing in.
     */
    static void part(ServerLevel level, Carriage c, int e, Joint was, Carriage to, int toEnd) {
        Pose p = c.pose(), q = to.pose();
        Vec3 mid = p.world(new Vec3(e * 10.3, FLT + 1, 0));
        Vec3 at = q.world(new Vec3(toEnd * 9.4, FLT, 0));
        Vec3 in = q.worldVec(new Vec3(-toEnd, 0, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-in.x, in.z));
        for (Entity o : level.getEntities(c, new AABB(mid, mid).inflate(3), x -> !(x instanceof RailVehicle) && !x.isPassenger() && !x.isSpectator())) {
            if (!inHalf(p.local(o.position()), e, was)) continue;
            if (o instanceof ServerPlayer sp) Aboard.putBack(sp, to, q.local(at), yaw);
            else {
                o.setPos(at);
                o.setDeltaMovement(Vec3.ZERO);
                o.setYRot(yaw);
                if (o instanceof LivingEntity le) {
                    le.setYHeadRot(yaw);
                    le.yBodyRot = yaw;
                }
            }
        }
    }
}
