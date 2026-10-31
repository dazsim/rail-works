package com.workshopcraft.railworks.track;

import com.mojang.serialization.Codec;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.TrackNodeBlock;
import com.workshopcraft.railworks.block.TrackNodeBlockEntity;
import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every survey node and track section in one level. Nodes sit in TrackNodeBlocks; sections run between two nodes. */
public final class TrackGraph extends SavedData {
    public static final Codec<TrackGraph> CODEC = CompoundTag.CODEC.xmap(TrackGraph::load, TrackGraph::save);
    public static final SavedDataType<TrackGraph> TYPE = new SavedDataType<>(RailWorks.id("track"), TrackGraph::new, CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    public static final int MIN_LIFT = 0, MAX_LIFT = 15;

    public static final class Node {
        public final long id;
        public final BlockPos pos;
        public double heading;
        public int lift;
        public boolean stake = true, buffer;
        public int route;
        final List<Long> segs = new ArrayList<>();

        Node(long id, BlockPos pos, double heading) {
            this.id = id;
            this.pos = pos.immutable();
            this.heading = heading;
        }

        public double x() {
            return pos.getX() + 0.5;
        }

        public double z() {
            return pos.getZ() + 0.5;
        }

        public double y() {
            return pos.getY() + lift / 8.0;
        }

        public List<Long> segments() {
            return segs;
        }
    }

    public static final class Segment {
        public final long id;
        public long a, b;
        public int aPort, bPort;
        public TrackPath path;
        public double baseY;
        public int n;
        public BitSet ties = new BitSet(), rails = new BitSet();
        public boolean live;
        List<BlockPos> blocked = List.of();
        boolean checked;
        double skipA, skipB;
        int bladeEnd = -1, bladeSide;
        boolean bladeClosed;

        Segment(long id) {
            this.id = id;
        }

        void setPath(TrackPath p, double baseY) {
            path = p;
            this.baseY = baseY;
            n = Math.max(1, (int) Math.round(p.length));
            ties = new BitSet();
            rails = new BitSet();
            live = false;
        }

        public double tieS(int i) {
            return (i + 0.5) * path.length / n;
        }

        public boolean complete() {
            return ties.cardinality() >= n && rails.cardinality() >= n;
        }

        public boolean empty() {
            return ties.isEmpty() && rails.isEmpty();
        }

        public long other(long node) {
            return node == a ? b : a;
        }

        public boolean curved() {
            return path.minRadius() < 1e5;
        }

        public List<BlockPos> blocked() {
            return blocked;
        }
    }

    public record Spot(Segment seg, int index, double dist) {}

    final Map<Long, Node> nodes = new HashMap<>();
    final Map<BlockPos, Long> byPos = new HashMap<>();
    final Map<Long, Segment> segments = new LinkedHashMap<>();
    long nextId = 1;
    private boolean removing;

    public static TrackGraph get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Node node(long id) {
        return nodes.get(id);
    }

    public @Nullable Node nodeAt(BlockPos pos) {
        Long id = byPos.get(pos);
        return id == null ? null : nodes.get(id);
    }

    public @Nullable Segment segment(long id) {
        return segments.get(id);
    }

    public Iterable<Segment> segments() {
        return segments.values();
    }

    public Iterable<Node> nodes() {
        return nodes.values();
    }

    public Node addNode(BlockPos pos, double heading) {
        Node old = nodeAt(pos);
        if (old != null) return old;
        Node n = new Node(nextId++, pos, heading);
        nodes.put(n.id, n);
        byPos.put(n.pos, n.id);
        setDirty();
        return n;
    }

    /** The node at a track node block, created if the block was placed without one. */
    public Node nodeFor(ServerLevel level, BlockPos pos) {
        Node n = nodeAt(pos);
        if (n != null) return n;
        n = addNode(pos, 0);
        BlockState s = level.getBlockState(pos);
        n.stake = s.hasProperty(TrackNodeBlock.STAKE) && s.getValue(TrackNodeBlock.STAKE);
        return n;
    }

    static int port(Node n, double outward) {
        return Math.cos(outward - n.heading) >= 0 ? 0 : 1;
    }

    public static double outward(Segment s, long node) {
        return node == s.a ? s.path.t0 : s.path.endHeading() + Math.PI;
    }

    int count(Node n, int port, long except) {
        int c = 0;
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null && s.id != except && (s.a == n.id ? s.aPort : s.bPort) == port) c++;
        }
        return c;
    }

    private boolean portsOk(Node n) {
        int c0 = count(n, 0, -1), c1 = count(n, 1, -1);
        return c0 <= 2 && c1 <= 2 && !(c0 == 2 && c1 == 2);
    }

    /** The toe of a set of points: the port with two sections, or -1. */
    public int pointsPort(Node n) {
        if (count(n, 0, -1) == 2) return 0;
        if (count(n, 1, -1) == 2) return 1;
        return -1;
    }

    public List<Segment> legs(Node n) {
        int p = pointsPort(n);
        List<Segment> out = new ArrayList<>();
        if (p < 0) return out;
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null && (s.a == n.id ? s.aPort : s.bPort) == p) out.add(s);
        }
        out.sort((x, y) -> Long.compare(x.id, y.id));
        return out;
    }

    /** The section a train takes after arriving at a node along `from`, or null at a dead end. For M4. */
    public @Nullable Segment next(long nodeId, long from) {
        Node n = nodes.get(nodeId);
        Segment f = segments.get(from);
        if (n == null || f == null) return null;
        int inPort = f.a == n.id ? f.aPort : f.bPort;
        List<Segment> out = new ArrayList<>();
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null && s.id != from && (s.a == n.id ? s.aPort : s.bPort) != inPort) out.add(s);
        }
        if (out.isEmpty()) return null;
        if (out.size() == 1) return out.get(0);
        out.sort((x, y) -> Long.compare(x.id, y.id));
        return out.get(Math.min(n.route, out.size() - 1));
    }

    public enum LinkResult { OK, SAME, TOO_FAR, EXISTS, PORT_FULL, NO_PATH }

    public LinkResult link(ServerLevel level, Node A, Node B) {
        if (A == B) return LinkResult.SAME;
        if (A.segs.isEmpty() && !B.segs.isEmpty()) {
            Node t = A;
            A = B;
            B = t;
        }
        double dx = B.x() - A.x(), dz = B.z() - A.z();
        if (Math.hypot(dx, dz) > TrackPath.MAX_SPAN) return LinkResult.TOO_FAR;
        if (Math.hypot(dx, dz) < 2) return LinkResult.NO_PATH;
        for (long id : A.segs) {
            Segment s = segments.get(id);
            if (s != null && s.other(A.id) == B.id) return LinkResult.EXISTS;
        }
        double ta = Math.cos(A.heading) * dx + Math.sin(A.heading) * dz >= 0 ? A.heading : A.heading + Math.PI;
        TrackPath p;
        double rise = B.y() - A.y();
        if (B.segs.isEmpty()) p = TrackPath.free(A.x(), A.z(), ta, B.x(), B.z(), rise);
        else {
            double tb = Math.cos(B.heading) * dx + Math.sin(B.heading) * dz >= 0 ? B.heading : B.heading + Math.PI;
            p = TrackPath.fixed(A.x(), A.z(), ta, B.x(), B.z(), tb, rise);
        }
        if (p == null) return LinkResult.NO_PATH;
        Segment s = new Segment(nextId++);
        s.a = A.id;
        s.b = B.id;
        s.setPath(p, A.y());
        double oldB = B.heading;
        if (B.segs.isEmpty()) B.heading = p.endHeading();
        s.aPort = port(A, ta);
        s.bPort = port(B, p.endHeading() + Math.PI);
        segments.put(s.id, s);
        A.segs.add(s.id);
        B.segs.add(s.id);
        if (!portsOk(A) || !portsOk(B)) {
            detach(s);
            B.heading = oldB;
            return LinkResult.PORT_FULL;
        }
        refresh(level, s);
        changed(level, A);
        changed(level, B);
        return LinkResult.OK;
    }

    private void detach(Segment s) {
        segments.remove(s.id);
        Node a = nodes.get(s.a), b = nodes.get(s.b);
        if (a != null) a.segs.remove(s.id);
        if (b != null) b.segs.remove(s.id);
    }

    public boolean anyLaid(Node n) {
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null && !s.empty()) return true;
        }
        return false;
    }

    /** Raise or turn a node and re-solve its sections. False if a section no longer fits. */
    public boolean adjust(ServerLevel level, Node n, int dLift, double dHeading) {
        int lift = n.lift + dLift;
        if (lift < MIN_LIFT || lift > MAX_LIFT) return false;
        int oldLift = n.lift;
        double oldHeading = n.heading;
        n.lift = lift;
        n.heading += dHeading;
        Map<Long, Object[]> undo = new HashMap<>();
        boolean ok = true;
        for (long id : List.copyOf(n.segs)) {
            Segment s = segments.get(id);
            if (s == null) continue;
            Node o = nodes.get(s.other(n.id));
            if (o == null) continue;
            undo.put(id, new Object[]{s.a, s.b, s.path, s.baseY, s.aPort, s.bPort, o.heading});
            if (!resolve(s, n, o)) {
                ok = false;
                break;
            }
        }
        if (ok) for (long id : n.segs) {
            Segment s = segments.get(id);
            Node o = s == null ? null : nodes.get(s.other(n.id));
            if (o != null && !portsOk(o)) ok = false;
        }
        if (ok && !portsOk(n)) ok = false;
        if (!ok) {
            n.lift = oldLift;
            n.heading = oldHeading;
            for (var e : undo.entrySet()) {
                Segment s = segments.get(e.getKey());
                Object[] u = e.getValue();
                s.a = (long) u[0];
                s.b = (long) u[1];
                s.setPath((TrackPath) u[2], (double) u[3]);
                s.aPort = (int) u[4];
                s.bPort = (int) u[5];
                Node o = nodes.get(s.other(n.id));
                if (o != null) o.heading = (double) u[6];
            }
            return false;
        }
        changed(level, n);
        for (long id : n.segs) {
            Segment s = segments.get(id);
            refresh(level, s);
            Node o = nodes.get(s.other(n.id));
            if (o != null) changed(level, o);
        }
        return true;
    }

    private boolean resolve(Segment s, Node moved, Node other) {
        TrackPath p;
        if (other.segs.size() == 1) {
            double dx = other.x() - moved.x(), dz = other.z() - moved.z();
            double ta = Math.cos(moved.heading) * dx + Math.sin(moved.heading) * dz >= 0 ? moved.heading : moved.heading + Math.PI;
            p = TrackPath.free(moved.x(), moved.z(), ta, other.x(), other.z(), other.y() - moved.y());
            s.a = moved.id;
            s.b = other.id;
            other.heading = p.endHeading();
        } else {
            Node A = nodes.get(s.a), B = nodes.get(s.b);
            double dx = B.x() - A.x(), dz = B.z() - A.z();
            double ta = Math.cos(A.heading) * dx + Math.sin(A.heading) * dz >= 0 ? A.heading : A.heading + Math.PI;
            double tb = Math.cos(B.heading) * dx + Math.sin(B.heading) * dz >= 0 ? B.heading : B.heading + Math.PI;
            p = TrackPath.fixed(A.x(), A.z(), ta, B.x(), B.z(), tb, B.y() - A.y());
        }
        if (p == null) return false;
        Node A = nodes.get(s.a), B = nodes.get(s.b);
        s.setPath(p, A.y());
        s.aPort = port(A, p.t0);
        s.bPort = port(B, p.endHeading() + Math.PI);
        return true;
    }

    /** Remove sections with nothing laid on them. Returns how many went. */
    public int unlink(ServerLevel level, Node n) {
        int c = 0;
        for (long id : List.copyOf(n.segs)) {
            Segment s = segments.get(id);
            if (s != null && s.empty()) {
                removeSegment(level, s);
                c++;
            }
        }
        if (c > 0) changed(level, n);
        return c;
    }

    public void removeSegment(ServerLevel level, Segment s) {
        detach(s);
        setDirty();
        for (long id : new long[]{s.a, s.b}) {
            Node o = nodes.get(id);
            if (o == null) continue;
            if (o.segs.isEmpty() && !o.stake) removeNodeBlock(level, o);
            else {
                if (o.segs.size() != 1) o.buffer = false;
                changed(level, o);
            }
        }
    }

    private void removeNodeBlock(ServerLevel level, Node o) {
        if (o.buffer) Containers.dropItemStack(level, o.x(), o.y() + 0.5, o.z(), new ItemStack(ModContent.BUFFER_STOP));
        forget(o);
        if (level.getBlockState(o.pos).is(ModContent.TRACK_NODE)) level.removeBlock(o.pos, false);
    }

    private void forget(Node n) {
        nodes.remove(n.id);
        byPos.remove(n.pos);
        setDirty();
    }

    /** The node's block went away: drop everything laid on its sections and tidy up. */
    public void nodeRemoved(ServerLevel level, BlockPos pos) {
        if (removing) return;
        Node n = nodeAt(pos);
        if (n == null) return;
        removing = true;
        try {
            forget(n);
            double x = n.x(), y = n.y() + 0.5, z = n.z();
            if (n.stake) Containers.dropItemStack(level, x, y, z, new ItemStack(ModContent.SURVEY_STAKE));
            if (n.buffer) Containers.dropItemStack(level, x, y, z, new ItemStack(ModContent.BUFFER_STOP));
            for (long id : List.copyOf(n.segs)) {
                Segment s = segments.get(id);
                if (s == null) continue;
                drop(level, x, y, z, ModContent.TIE, s.ties.cardinality());
                drop(level, x, y, z, ModContent.RAIL_BAR, s.rails.cardinality());
                detach(s);
                Node o = nodes.get(s.other(n.id));
                if (o == null) continue;
                if (o.segs.isEmpty() && !o.stake) removeNodeBlock(level, o);
                else {
                    if (o.segs.size() != 1) o.buffer = false;
                    changed(level, o);
                }
            }
        } finally {
            removing = false;
        }
    }

    private static void drop(ServerLevel level, double x, double y, double z, net.minecraft.world.item.Item item, int count) {
        while (count > 0) {
            int c = Math.min(count, 64);
            Containers.dropItemStack(level, x, y, z, new ItemStack(item, c));
            count -= c;
        }
    }

    public boolean throwPoints(ServerLevel level, Node n) {
        if (pointsPort(n) < 0) return false;
        n.route ^= 1;
        changed(level, n);
        return true;
    }

    public void setRoute(ServerLevel level, Node n, int route) {
        if (pointsPort(n) < 0 || n.route == route) return;
        n.route = route;
        changed(level, n);
    }

    /** The nearest unlaid tie (or rail block when rails is set) to a point, among sections that are not failing. */
    public @Nullable Spot nearestFree(double x, double y, double z, double reach, boolean rails) {
        Spot best = null;
        for (Segment s : segments.values()) {
            if (s.path.fails() || !near(s, x, z, reach)) continue;
            for (int i = 0; i < s.n; i++) {
                if (rails ? (s.rails.get(i) || !s.ties.get(i)) : s.ties.get(i)) continue;
                double d = dist(s, s.tieS(i), x, y, z);
                if (d <= reach && (best == null || d < best.dist)) best = new Spot(s, i, d);
            }
        }
        return best;
    }

    /** The nearest laid rail block, else laid tie, within reach. */
    public @Nullable Spot nearestLaid(double x, double y, double z, double reach, boolean rails) {
        Spot best = null;
        for (Segment s : segments.values()) {
            if (!near(s, x, z, reach)) continue;
            for (int i = 0; i < s.n; i++) {
                if (!(rails ? s.rails.get(i) : s.ties.get(i) && !s.rails.get(i))) continue;
                double d = dist(s, s.tieS(i), x, y, z);
                if (d <= reach && (best == null || d < best.dist)) best = new Spot(s, i, d);
            }
        }
        return best;
    }

    /** The section passing closest to a point. */
    public @Nullable Spot nearestSegment(double x, double y, double z, double reach) {
        Spot best = null;
        for (Segment s : segments.values()) {
            if (!near(s, x, z, reach)) continue;
            for (double t = 0; t <= s.path.length; t += 0.5) {
                double d = dist(s, t, x, y, z);
                if (d <= reach && (best == null || d < best.dist)) best = new Spot(s, (int) Math.min(s.n - 1, t * s.n / Math.max(1e-6, s.path.length)), d);
            }
        }
        return best;
    }

    private static boolean near(Segment s, double x, double z, double reach) {
        double cx = s.path.x0, cz = s.path.z0;
        return Math.hypot(x - cx, z - cz) <= s.path.length + reach + 1;
    }

    private static double dist(Segment s, double t, double x, double y, double z) {
        double[] p = s.path.pose(t);
        double py = s.baseY + s.path.height(t);
        return Math.sqrt((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) + 0.5 * (py - y) * (py - y));
    }

    public void laid(ServerLevel level, Segment s) {
        boolean live = s.complete();
        if (live != s.live) s.live = live;
        setDirty();
        changed(level, nodes.get(s.a));
        changed(level, nodes.get(s.b));
    }

    /** Nodes whose stakes can come up now that every section at them is live. */
    public List<Node> stakesToReturn(Segment s) {
        List<Node> out = new ArrayList<>();
        for (long id : new long[]{s.a, s.b}) {
            Node n = nodes.get(id);
            if (n == null || !n.stake) continue;
            boolean all = true;
            for (long sid : n.segs) {
                Segment o = segments.get(sid);
                if (o != null && !o.live) all = false;
            }
            if (all) out.add(n);
        }
        return out;
    }

    /** Re-check what stands in the way of a section's clearance envelope. */
    public void refresh(ServerLevel level, Segment s) {
        List<BlockPos> out = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        double half = s.curved() ? 3.5 : 3.0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double t = 0.5; t < s.path.length; t += 1) {
            double[] p = s.path.pose(t);
            double y = s.baseY + s.path.height(t), nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
            for (double w = -half + 0.5; w < half; w += 1)
                for (double dy = 0.3; dy < 6.5; dy += 1) {
                    m.set(Math.floor(p[0] + nx * w), Math.floor(y + dy), Math.floor(p[1] + nz * w));
                    if (seen.contains(m) || !level.isLoaded(m)) continue;
                    BlockPos im = m.immutable();
                    seen.add(im);
                    BlockState st = level.getBlockState(im);
                    if (st.isAir() || st.is(ModContent.TRACK_NODE)) continue;
                    if (!st.getCollisionShape(level, im).isEmpty()) out.add(im);
                }
        }
        s.blocked = out;
        s.checked = true;
    }

    public void changed(ServerLevel level, @Nullable Node n) {
        setDirty();
        if (n == null || !level.isLoaded(n.pos)) return;
        if (level.getBlockEntity(n.pos) instanceof TrackNodeBlockEntity be) be.sync();
    }

    /** What the client needs to draw a node and the sections it owns. */
    public CompoundTag clientTag(ServerLevel level, BlockPos pos) {
        CompoundTag t = new CompoundTag();
        Node n = nodeAt(pos);
        if (n == null) return t;
        t.putDouble("heading", n.heading);
        t.putInt("lift", n.lift);
        t.putBoolean("buffer", n.buffer && n.segs.size() == 1);
        if (n.segs.size() == 1) {
            Segment s = segments.get(n.segs.get(0));
            if (s != null) t.putDouble("out", outward(s, n.id) + Math.PI);
        }
        List<Segment> legs = legs(n);
        if (legs.size() == 2) {
            ListTag lt = new ListTag();
            for (Segment s : legs) {
                annotate(s);
                lt.add(segTag(level, s, n.id));
            }
            t.put("legs", lt);
            t.putInt("route", n.route);
        }
        ListTag st = new ListTag();
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s == null || s.a != n.id) continue;
            annotate(s);
            st.add(segTag(level, s, n.id));
        }
        t.put("segs", st);
        return t;
    }

    private CompoundTag segTag(ServerLevel level, Segment s, long viewer) {
        CompoundTag t = new CompoundTag();
        if (!s.checked && !s.live) refresh(level, s);
        t.putLong("id", s.id);
        t.put("path", s.path.save());
        t.putDouble("y", s.baseY);
        t.putInt("n", s.n);
        t.putLongArray("ties", s.ties.toLongArray());
        t.putLongArray("rails", s.rails.toLongArray());
        t.putBoolean("live", s.live);
        t.putBoolean("toe", s.b == viewer && s.a != viewer);
        long[] bl = new long[s.blocked.size()];
        for (int i = 0; i < bl.length; i++) bl[i] = s.blocked.get(i).asLong();
        t.putLongArray("blocked", bl);
        t.putDouble("skipA", s.skipA);
        t.putDouble("skipB", s.skipB);
        t.putInt("bladeEnd", s.bladeEnd);
        t.putInt("bladeSide", s.bladeSide);
        t.putBoolean("closed", s.bladeClosed);
        return t;
    }

    private void annotate(Segment s) {
        s.skipA = s.skipB = 0;
        s.bladeEnd = -1;
        for (long end : new long[]{s.a, s.b}) {
            Node e = nodes.get(end);
            if (e == null) continue;
            List<Segment> l = legs(e);
            if (l.size() == 2 && l.contains(s)) computePoints(e, l);
        }
    }

    /** Long-timber zone and switch blades for the two legs at a toe. */
    private void computePoints(Node toe, List<Segment> legs) {
        double zone = 0;
        for (double d = 0.25; d < 24; d += 0.25) {
            double[] p0 = at(legs.get(0), toe.id, d), p1 = at(legs.get(1), toe.id, d);
            if (Math.hypot(p0[0] - p1[0], p0[1] - p1[1]) > 3.2) break;
            zone = d;
        }
        for (int i = 0; i < 2; i++) {
            Segment s = legs.get(i), o = legs.get(1 - i);
            boolean atA = s.a == toe.id;
            double sp = atA ? Math.min(4, s.path.length) : Math.max(0, s.path.length - 4);
            double[] p = s.path.pose(sp), q = at(o, toe.id, Math.min(4, o.path.length));
            double side = (q[0] - p[0]) * -Math.sin(p[2]) + (q[1] - p[1]) * Math.cos(p[2]);
            s.bladeEnd = atA ? 0 : 1;
            s.bladeSide = side >= 0 ? 1 : -1;
            s.bladeClosed = toe.route == i;
            if (atA) s.skipA = Math.min(zone, s.path.length);
            else s.skipB = Math.min(zone, s.path.length);
        }
    }

    private static double[] at(Segment s, long from, double d) {
        return s.path.pose(s.a == from ? d : s.path.length - d);
    }

    private CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("next", nextId);
        ListTag nl = new ListTag();
        for (Node n : nodes.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", n.id);
            c.putLong("pos", n.pos.asLong());
            c.putDouble("heading", n.heading);
            c.putInt("lift", n.lift);
            c.putBoolean("stake", n.stake);
            c.putBoolean("buffer", n.buffer);
            c.putInt("route", n.route);
            nl.add(c);
        }
        t.put("nodes", nl);
        ListTag sl = new ListTag();
        for (Segment s : segments.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("id", s.id);
            c.putLong("a", s.a);
            c.putLong("b", s.b);
            c.putInt("aPort", s.aPort);
            c.putInt("bPort", s.bPort);
            c.put("path", s.path.save());
            c.putDouble("y", s.baseY);
            c.putLongArray("ties", s.ties.toLongArray());
            c.putLongArray("rails", s.rails.toLongArray());
            sl.add(c);
        }
        t.put("segs", sl);
        return t;
    }

    private static TrackGraph load(CompoundTag t) {
        TrackGraph g = new TrackGraph();
        g.nextId = t.getLongOr("next", 1);
        for (Tag e : t.getListOrEmpty("nodes")) {
            if (!(e instanceof CompoundTag c)) continue;
            Node n = new Node(c.getLongOr("id", 0), BlockPos.of(c.getLongOr("pos", 0)), c.getDoubleOr("heading", 0));
            n.lift = c.getIntOr("lift", 0);
            n.stake = c.getBooleanOr("stake", true);
            n.buffer = c.getBooleanOr("buffer", false);
            n.route = c.getIntOr("route", 0);
            g.nodes.put(n.id, n);
            g.byPos.put(n.pos, n.id);
        }
        for (Tag e : t.getListOrEmpty("segs")) {
            if (!(e instanceof CompoundTag c)) continue;
            Segment s = new Segment(c.getLongOr("id", 0));
            s.a = c.getLongOr("a", 0);
            s.b = c.getLongOr("b", 0);
            Node a = g.nodes.get(s.a), b = g.nodes.get(s.b);
            if (a == null || b == null) continue;
            s.aPort = c.getIntOr("aPort", 0);
            s.bPort = c.getIntOr("bPort", 1);
            s.setPath(TrackPath.load(c.getCompoundOrEmpty("path")), c.getDoubleOr("y", 0));
            s.ties = BitSet.valueOf(c.getLongArray("ties").orElse(new long[0]));
            s.rails = BitSet.valueOf(c.getLongArray("rails").orElse(new long[0]));
            s.live = s.complete();
            g.segments.put(s.id, s);
            a.segs.add(s.id);
            b.segs.add(s.id);
        }
        return g;
    }
}
