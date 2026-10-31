package com.workshopcraft.railworks.track;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** The client's copy of one node and the sections it draws. */
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
        public BitSet ties, rails;
        public boolean live, toeAtB;
        public long[] blocked;
        public double skipA, skipB;
        public int bladeEnd, bladeSide;
        public boolean closed;

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
        s.skipA = c.getDoubleOr("skipA", 0);
        s.skipB = c.getDoubleOr("skipB", 0);
        s.bladeEnd = c.getIntOr("bladeEnd", -1);
        s.bladeSide = c.getIntOr("bladeSide", 1);
        s.closed = c.getBooleanOr("closed", false);
        return s;
    }
}
