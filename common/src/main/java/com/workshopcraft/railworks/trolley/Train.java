package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.track.TrackGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A coupled set of vehicles moving as one rigid body along a path of sections. The path runs from the front of
 * the train (x = 0) to the rear; each member sits at a fixed offset behind the front one. Moving the train moves
 * one cursor, so every vehicle moves by exactly the same amount. The same object runs on the server, where it
 * grows its path from the track graph, and on the client, where the path comes in the sync packet.
 */
public final class Train {
    /** One section of the path. dir +1 when s grows towards the rear. */
    public record Step(long seg, int dir) {}

    /** Where a point on the path lies: the section, s along it and the step's dir. */
    public record Pos(Rail rail, double s, int dir) {}

    public final List<Step> steps = new ArrayList<>();
    /** The front member's x. */
    public double ref;
    public final List<UUID> ids = new ArrayList<>();
    public final List<RailVehicle.Member> members = new ArrayList<>();
    public double[] d = new double[0];
    public int[] flip = new int[0];

    double len(LongFunction<@Nullable Rail> track, int k) {
        Rail r = track.apply(steps.get(k).seg());
        return r == null ? 0 : r.length();
    }

    public double total(LongFunction<@Nullable Rail> track) {
        double t = 0;
        for (int k = 0; k < steps.size(); k++) t += len(track, k);
        return t;
    }

    public double span() {
        double m = 0;
        for (double v : d) m = Math.max(m, v);
        return m;
    }

    public @Nullable Pos at(LongFunction<@Nullable Rail> track, double x) {
        double X = 0;
        for (int k = 0; k < steps.size(); k++) {
            Step st = steps.get(k);
            Rail r = track.apply(st.seg());
            if (r == null) return null;
            double l = r.length();
            if (x <= X + l || k == steps.size() - 1) {
                double t = Mth.clamp(x - X, 0, l);
                return new Pos(r, st.dir() > 0 ? t : l - t, st.dir());
            }
            X += l;
        }
        return null;
    }

    /** The x of a point on a section of the path, or NaN if the section is not on it. */
    public double xOf(LongFunction<@Nullable Rail> track, long seg, double s) {
        double X = 0;
        for (int k = 0; k < steps.size(); k++) {
            Step st = steps.get(k);
            double l = len(track, k);
            if (st.seg() == seg) return X + (st.dir() > 0 ? s : l - s);
            X += l;
        }
        return Double.NaN;
    }

    public void addStepFront(Step st, double l) {
        steps.addFirst(st);
        ref += l;
    }

    /** Drops sections no centre stands on. */
    public void trim(LongFunction<@Nullable Rail> track) {
        while (steps.size() > 1) {
            double l = len(track, 0);
            if (ref < l) break;
            steps.removeFirst();
            ref -= l;
        }
        double rear = ref + span(), X = 0;
        int keep = steps.size();
        for (int k = 0; k < steps.size(); k++) {
            if (X > rear + 1e-9 && k > 0) {
                keep = k;
                break;
            }
            X += len(track, k);
        }
        while (steps.size() > keep) steps.removeLast();
    }

    public int indexOf(RailVehicle v) {
        for (int i = 0; i < members.size(); i++) if (members.get(i).v() == v) return i;
        return -1;
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        long[] segs = new long[steps.size()];
        int[] dirs = new int[steps.size()];
        for (int k = 0; k < steps.size(); k++) {
            segs[k] = steps.get(k).seg();
            dirs[k] = steps.get(k).dir();
        }
        t.putLongArray("segs", segs);
        t.putIntArray("dirs", dirs);
        t.putDouble("ref", ref);
        ListTag ms = new ListTag();
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag m = new CompoundTag();
            m.putString("id", ids.get(i).toString());
            m.putDouble("d", d[i]);
            m.putInt("flip", flip[i]);
            ms.add(m);
        }
        t.put("members", ms);
        return t;
    }

    public static @Nullable Train load(CompoundTag t) {
        long[] segs = t.getLongArray("segs").orElse(new long[0]);
        int[] dirs = t.getIntArray("dirs").orElse(new int[0]);
        if (segs.length == 0 || segs.length != dirs.length) return null;
        Train tr = new Train();
        for (int k = 0; k < segs.length; k++) tr.steps.add(new Step(segs[k], dirs[k]));
        tr.ref = t.getDoubleOr("ref", 0);
        ListTag ms = t.getListOrEmpty("members");
        tr.d = new double[ms.size()];
        tr.flip = new int[ms.size()];
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompoundOrEmpty(i);
            try {
                tr.ids.add(UUID.fromString(m.getStringOr("id", "")));
            } catch (IllegalArgumentException e) {
                return null;
            }
            tr.d[i] = m.getDoubleOr("d", 0);
            tr.flip[i] = m.getIntOr("flip", 1);
        }
        return tr;
    }

    static int stepDir(TrackGraph.Segment nx, long node, boolean towardsRear) {
        boolean fromA = !(nx.b == node && nx.a != node);
        return fromA == towardsRear ? 1 : -1;
    }
}
