package com.workshopcraft.railworks.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** The client's copy of one node and the sections that meet at it. */
public final class TrackView {
    public double heading, out;
    public int lift, route;
    public boolean buffer, hasOut;
    public final List<Seg> segs = new ArrayList<>(), legs = new ArrayList<>();

    public static final class Seg {
        public long id;
        public TrackPath path;
        public double y;
        public int n;
        public BitSet ties, rails, unsupported;
        public boolean live, toeAtB, own;
        public long aPos, bPos;
        public long[] blocked;
        public final double[] skip = new double[2], frog = new double[2];
        public final int[] side = new int[2];
        public final boolean[] blade = new boolean[2], closed = new boolean[2];

        public double tieS(int i) {
            return (i + 0.5) * path.length / n;
        }

        public double height(double s) {
            return y + path.height(s);
        }
    }

    public static TrackView parse(CompoundTag t) {
        TrackView v = new TrackView();
        v.heading = t.getDoubleOr("heading", 0);
        v.lift = t.getIntOr("lift", 0);
        v.buffer = t.getBooleanOr("buffer", false);
        v.hasOut = t.contains("out");
        v.out = t.getDoubleOr("out", 0);
        v.route = t.getIntOr("route", 0);
        for (Tag e : t.getListOrEmpty("segs")) if (e instanceof CompoundTag c) v.segs.add(seg(c));
        for (Tag e : t.getListOrEmpty("legs")) if (e instanceof CompoundTag c) v.legs.add(seg(c));
        return v;
    }

    private static Seg seg(CompoundTag c) {
        Seg s = new Seg();
        s.id = c.getLongOr("id", 0);
        s.path = TrackPath.load(c.getCompoundOrEmpty("path"));
        s.y = c.getDoubleOr("y", 0);
        s.n = Math.max(1, c.getIntOr("n", 1));
        s.ties = BitSet.valueOf(c.getLongArray("ties").orElse(new long[0]));
        s.rails = BitSet.valueOf(c.getLongArray("rails").orElse(new long[0]));
        s.live = c.getBooleanOr("live", false);
        s.toeAtB = c.getBooleanOr("toe", false);
        s.blocked = c.getLongArray("blocked").orElse(new long[0]);
        s.unsupported = BitSet.valueOf(c.getLongArray("unsupported").orElse(new long[0]));
        s.own = c.getBooleanOr("own", true);
        s.aPos = c.getLongOr("apos", 0);
        s.bPos = c.getLongOr("bpos", 0);
        for (int e = 0; e < 2; e++) {
            String k = e == 0 ? "A" : "B";
            s.skip[e] = c.getDoubleOr("skip" + k, 0);
            s.frog[e] = c.getDoubleOr("frog" + k, -1);
            s.side[e] = c.getIntOr("side" + k, 1);
            s.blade[e] = c.getBooleanOr("blade" + k, false);
            s.closed[e] = c.getBooleanOr("closed" + k, false);
        }
        return s;
    }
}
