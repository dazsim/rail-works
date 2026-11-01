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

        public int sectionCount() {
            return segs.size();
        }

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
        /** Held as a straight between its two stakes, for station platforms. */
        public boolean locked;
        public BitSet extra = new BitSet();
        List<BlockPos> blocked = List.of();
        BitSet unsupported = new BitSet();
        boolean checked;
        /** Bumped whenever anything the client draws for this section may have changed. */
        public int rev;
        private double @Nullable [] box;
        // per end (0 at a, 1 at b) when that end is the toe of points
        final double[] skip = new double[2], frog = {-1, -1};
        final int[] side = new int[2];
        final boolean[] blade = new boolean[2], closed = new boolean[2];

        Segment(long id) {
            this.id = id;
        }

        void setPath(TrackPath p, double baseY) {
            path = p;
            this.baseY = baseY;
            n = Math.max(1, (int) Math.round(p.length));
            ties = new BitSet();
            rails = new BitSet();
            extra = new BitSet();
            unsupported = new BitSet();
            checked = false;
            live = false;
            box = null;
            rev++;
        }

        /** Horizontal distance from a point to this section's bounding box. */
        public double reachFrom(double x, double z) {
            if (box == null) {
                double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
                for (double t = 0; ; t += 2) {
                    double[] p = path.pose(Math.min(t, path.length));
                    b[0] = Math.min(b[0], p[0]);
                    b[1] = Math.min(b[1], p[1]);
                    b[2] = Math.max(b[2], p[0]);
                    b[3] = Math.max(b[3], p[1]);
                    if (t >= path.length) break;
                }
                box = b;
            }
            double dx = Math.max(0, Math.max(box[0] - x, x - box[2])), dz = Math.max(0, Math.max(box[1] - z, z - box[3]));
            return Math.hypot(dx, dz);
        }

        public double tieS(int i) {
            return (i + 0.5) * path.length / n;
        }

        /** Nothing laid on it at all. */
        public boolean empty() {
            return ties.isEmpty() && rails.isEmpty();
        }

        public boolean complete() {
            return ties.cardinality() >= n && rails.cardinality() >= n;
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

        public int unsupportedFree() {
            BitSet b = (BitSet) unsupported.clone();
            b.andNot(ties);
            return b.cardinality();
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

    /** How far down the legs from the toe they stay closer than width: the end of the points' locked zone. */
    public double clearance(Node toe, double width) {
        List<Segment> legs = legs(toe);
        if (legs.size() != 2) return 0;
        double max = Math.min(legs.get(0).path.length, legs.get(1).path.length);
        for (double d = 0.25; d < max; d += 0.25) {
            double[] p0 = at(legs.get(0), toe.id, d), p1 = at(legs.get(1), toe.id, d);
            if (Math.hypot(p0[0] - p1[0], p0[1] - p1[1]) >= width) return d;
        }
        return max;
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
        changedAround(level, A);
        changedAround(level, B);
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

    public enum LockResult { LOCKED, UNLOCKED, LAID, TURN_LOCKED, NO_FIT }

    /** True if any section at the node is locked straight. */
    public boolean lockedAt(Node n) {
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null && s.locked) return true;
        }
        return false;
    }

    /**
     * Lock a section as a straight between its two stakes, or unlock it. Both ends turn to face along the line and
     * every section at them re-plans to meet it. Refused if that would move anything laid, or turn a stake that
     * another locked section holds.
     */
    public LockResult toggleLock(ServerLevel level, Segment s) {
        if (s.locked) {
            s.locked = false;
            s.rev++;
            changedAround(level, nodes.get(s.a));
            return LockResult.UNLOCKED;
        }
        Node A = nodes.get(s.a), B = nodes.get(s.b);
        if (A == null || B == null) return LockResult.NO_FIT;
        double h = Math.atan2(B.z() - A.z(), B.x() - A.x());
        double dA = turnTo(A.heading, h), dB = turnTo(B.heading, h);
        boolean moveA = Math.abs(dA) > 1e-4, moveB = Math.abs(dB) > 1e-4;
        boolean bent = s.curved();
        if (moveA || moveB || bent) {
            for (Node n : new Node[]{A, B}) {
                if (anyLaid(n)) return LockResult.LAID;
                for (long id : n.segs) {
                    Segment o = segments.get(id);
                    if (o != null && o != s && o.locked && ((n == A && moveA) || (n == B && moveB))) return LockResult.TURN_LOCKED;
                }
            }
            if (moveA && !adjust(level, A, 0, dA)) return LockResult.NO_FIT;
            if (moveB && !adjust(level, B, 0, turnTo(B.heading, h))) {
                if (moveA) adjust(level, A, 0, -dA);
                return LockResult.NO_FIT;
            }
            if (s.curved() && !adjust(level, A, 0, 0)) return LockResult.NO_FIT;
        }
        s.locked = true;
        s.rev++;
        changedAround(level, A);
        changedAround(level, B);
        return LockResult.LOCKED;
    }

    /** The smallest turn from a heading to the line h, either way along it. */
    private static double turnTo(double heading, double h) {
        double d = Math.IEEEremainder(h - heading, Math.PI);
        return d;
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
        for (long id : n.segs) refresh(level, segments.get(id));
        changedAround(level, n);
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
        if (c > 0) changedAround(level, n);
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
                changedAround(level, o);
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

    /** Blocks of nodes that were broken while track was laid on them, put back on the next tick. */
    private final java.util.Set<BlockPos> restore = new java.util.HashSet<>();

    /**
     * The node's block went away. Laid ties and rails are never removed this way: if any section at the node has
     * anything laid, the node stays in the graph exactly as it was and its block comes back on the next tick.
     * Otherwise the stake and buffer drop and the node's empty sections are unlinked.
     */
    public void nodeRemoved(ServerLevel level, BlockPos pos) {
        if (removing) return;
        Node n = nodeAt(pos);
        if (n == null) return;
        if (anyLaid(n)) {
            restore.add(n.pos);
            return;
        }
        removing = true;
        try {
            forget(n);
            double x = n.x(), y = n.y() + 0.5, z = n.z();
            if (n.stake) Containers.dropItemStack(level, x, y, z, new ItemStack(ModContent.SURVEY_STAKE));
            if (n.buffer) Containers.dropItemStack(level, x, y, z, new ItemStack(ModContent.BUFFER_STOP));
            for (long id : List.copyOf(n.segs)) {
                Segment s = segments.get(id);
                if (s == null) continue;
                detach(s);
                Node o = nodes.get(s.other(n.id));
                if (o == null) continue;
                if (o.segs.isEmpty() && !o.stake) removeNodeBlock(level, o);
                else {
                    if (o.segs.size() != 1) o.buffer = false;
                    changedAround(level, o);
                }
            }
        } finally {
            removing = false;
        }
    }

    /** Put back the blocks of nodes broken while track was laid on them. */
    public void restoreBroken(ServerLevel level) {
        if (restore.isEmpty()) return;
        for (BlockPos pos : List.copyOf(restore)) {
            restore.remove(pos);
            Node n = nodeAt(pos);
            if (n == null || !level.isLoaded(pos)) continue;
            BlockState st = level.getBlockState(pos);
            if (!st.is(ModContent.TRACK_NODE) && (st.isAir() || st.canBeReplaced()))
                level.setBlock(pos, ModContent.TRACK_NODE.defaultBlockState().setValue(com.workshopcraft.railworks.block.TrackNodeBlock.STAKE, n.stake), 3);
            changedAround(level, n);
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

    /**
     * Nodes whose stakes can come up now that a section at them went live: every other section at the stake is
     * live too, or has nothing laid on it at all. The empty ones are stray links and get dropped by {@link #dropEmpty}.
     */
    public List<Node> stakesToReturn(Segment s) {
        List<Node> out = new ArrayList<>();
        for (long id : new long[]{s.a, s.b}) {
            Node n = nodes.get(id);
            if (n == null || !n.stake) continue;
            boolean ok = true;
            for (long sid : n.segs) {
                Segment o = segments.get(sid);
                if (o != null && !o.live && !o.empty()) ok = false;
            }
            if (ok) out.add(n);
        }
        return out;
    }

    /** Drop the empty sections at a stake that is coming up; returns how many went. */
    public int dropEmpty(ServerLevel level, Node n) {
        int c = 0;
        for (long sid : List.copyOf(n.segs)) {
            Segment o = segments.get(sid);
            if (o != null && !o.live && o.empty()) {
                removeSegment(level, o);
                c++;
            }
        }
        return c;
    }

    /** Unlaid sections whose clearance or support may have changed, re-checked a few at a time. */
    private final java.util.Set<Long> recheck = new java.util.LinkedHashSet<>();

    /** A block changed somewhere in the level: queue any unfinished section whose envelope might hold it. */
    public void blockChanged(BlockPos pos) {
        if (segments.isEmpty()) return;
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        for (Segment s : segments.values()) {
            if (pos.getY() < s.baseY - 24 || pos.getY() > s.baseY + 24 || s.reachFrom(x, z) > 5) continue;
            if (s.live) s.checked = false;
            else recheck.add(s.id);
        }
    }

    /** Re-check queued sections, a handful every half second, and resync those whose readout changed. */
    public void recheckChanged(ServerLevel level) {
        if (recheck.isEmpty() || level.getGameTime() % 10 != 0) return;
        java.util.Iterator<Long> it = recheck.iterator();
        for (int budget = 8; budget > 0 && it.hasNext(); budget--) {
            Segment s = segments.get(it.next());
            it.remove();
            if (s == null || s.live) continue;
            List<BlockPos> was = s.blocked;
            BitSet un = s.unsupported;
            refresh(level, s);
            if (!was.equals(s.blocked) || !un.equals(s.unsupported)) {
                changed(level, nodes.get(s.a));
                changed(level, nodes.get(s.b));
            }
        }
    }

    /** What stands in the way of a section, re-checked for finished track only when something nearby changed. */
    public List<BlockPos> clearance(ServerLevel level, Segment s) {
        if (s.live && !s.checked) {
            s.blocked = scan(level, s);
            s.checked = true;
        }
        return s.blocked;
    }

    /** Re-check what stands in the way of a section's clearance envelope. */
    public void refresh(ServerLevel level, Segment s) {
        s.blocked = scan(level, s);
        BitSet un = new BitSet();
        for (int i = 0; i < s.n; i++) if (!supported(level, s, i)) un.set(i);
        s.unsupported = un;
        s.checked = true;
        s.rev++;
    }

    private List<BlockPos> scan(ServerLevel level, Segment s) {
        List<BlockPos> out = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        double half = s.curved() ? 3.5 : 3.0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double t = 0.5; t < s.path.length; t += 1) {
            double[] p = s.path.pose(t);
            double y = s.baseY + s.path.height(t), nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
            for (double w = -half + 0.5; w < half; w += 1)
                for (double dy : ENVELOPE) {
                    m.set(Math.floor(p[0] + nx * w), Math.floor(y + dy), Math.floor(p[1] + nz * w));
                    if (seen.contains(m) || !level.isLoaded(m)) continue;
                    BlockPos im = m.immutable();
                    if (above(level, im, y + 0.03)) {
                        seen.add(im);
                        out.add(im);
                    }
                }
        }
        return out;
    }

    // from just above the tie bottom, so ground standing above the bed counts as in the way
    private static final double[] ENVELOPE = {0.03, 0.3, 1.3, 2.3, 3.3, 4.3, 5.3, 6.3};

    /** True if the block at pos is solid somewhere above height y. */
    private static boolean above(ServerLevel level, BlockPos pos, double y) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir() || st.is(ModContent.TRACK_NODE) || st.is(ModContent.GROUND_FRAME)) return false;
        var shape = st.getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) + pos.getY() > y;
    }

    /** The first block a tie would sit inside, or null. */
    public @Nullable BlockPos buried(ServerLevel level, Segment s, int i) {
        double t = s.tieS(i);
        double[] p = s.path.pose(t);
        double y = s.baseY + s.path.height(t), nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
        for (double w : new double[]{0, -0.75, 0.75, -1.4, 1.4})
            for (double dy : new double[]{0.03, 0.3, 0.6}) {
                BlockPos b = BlockPos.containing(p[0] + nx * w, y + dy, p[1] + nz * w);
                if (level.isLoaded(b) && above(level, b, y + 0.03)) return b;
            }
        return null;
    }

    /** A tie needs something solid under its middle, or under both rails. */
    public boolean supported(ServerLevel level, Segment s, int i) {
        double t = s.tieS(i);
        double[] p = s.path.pose(t);
        double y = s.baseY + s.path.height(t), nx = -Math.sin(p[2]), nz = Math.cos(p[2]);
        if (solidUnder(level, p[0], y, p[1])) return true;
        double h = TIE_GAUGE / 2;
        return solidUnder(level, p[0] + nx * h, y, p[1] + nz * h) && solidUnder(level, p[0] - nx * h, y, p[1] - nz * h);
    }

    private static final double TIE_GAUGE = 1.5;

    private static boolean solidUnder(ServerLevel level, double x, double y, double z) {
        // ground up to a block below is packed with ballast
        for (double dy : new double[]{0.05, 0.3, 0.55, 0.8, 1.0}) {
            BlockPos p = BlockPos.containing(x, y - dy, z);
            if (!level.isLoaded(p)) return true;
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.is(ModContent.TRACK_NODE)) continue;
            var shape = st.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            double ly = y - dy - p.getY();
            if (shape.max(net.minecraft.core.Direction.Axis.Y) >= ly - 1e-3 && shape.min(net.minecraft.core.Direction.Axis.Y) <= ly + 1e-3) return true;
        }
        return false;
    }

    /** Extra rail bars a rail block costs: the crossing and its check rail sit on one block of each leg. */
    public int extraBars(Segment s, int i) {
        annotate(s);
        int c = 0;
        for (int e = 0; e < 2; e++) {
            if (!s.blade[e] || s.frog[e] < 0) continue;
            double at = e == 0 ? s.frog[e] : s.path.length - s.frog[e];
            int fi = (int) Math.min(s.n - 1, Math.max(0, Math.floor(at * s.n / s.path.length)));
            if (fi == i) c++;
        }
        return Math.min(1, c);
    }

    /** Resync a node, its neighbours, and the far ends of any points next to it, whose drawing may have changed. */
    public void changedAround(ServerLevel level, @Nullable Node n) {
        if (n == null) return;
        java.util.Set<Node> out = new java.util.LinkedHashSet<>();
        out.add(n);
        for (long id : n.segs) {
            Segment s = segments.get(id);
            Node o = s == null ? null : nodes.get(s.other(n.id));
            if (o == null || !out.add(o) || pointsPort(o) < 0) continue;
            for (long id2 : o.segs) {
                Segment s2 = segments.get(id2);
                Node o2 = s2 == null ? null : nodes.get(s2.other(o.id));
                if (o2 != null) out.add(o2);
            }
        }
        for (Node m : out) changed(level, m);
    }

    public void changed(ServerLevel level, @Nullable Node n) {
        setDirty();
        if (n == null) return;
        for (long id : n.segs) {
            Segment s = segments.get(id);
            if (s != null) s.rev++;
        }
        if (!level.isLoaded(n.pos)) return;
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
            if (s == null) continue;
            annotate(s);
            st.add(segTag(level, s, n.id));
        }
        t.put("segs", st);
        return t;
    }

    /** A section on its own, for clients that draw it wherever it runs. */
    public CompoundTag syncTag(ServerLevel level, Segment s) {
        annotate(s);
        return segTag(level, s, 0);
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
        if (s.locked) t.putBoolean("locked", true);
        t.putBoolean("toe", s.b == viewer && s.a != viewer);
        t.putBoolean("own", s.a == viewer);
        Node na = nodes.get(s.a), nb = nodes.get(s.b);
        if (na != null) t.putLong("apos", na.pos.asLong());
        if (nb != null) t.putLong("bpos", nb.pos.asLong());
        t.putLongArray("unsupported", s.unsupported.toLongArray());
        long[] bl = new long[s.blocked.size()];
        for (int i = 0; i < bl.length; i++) bl[i] = s.blocked.get(i).asLong();
        t.putLongArray("blocked", bl);
        for (int e = 0; e < 2; e++) {
            String k = e == 0 ? "A" : "B";
            t.putDouble("skip" + k, s.skip[e]);
            t.putDouble("frog" + k, s.frog[e]);
            t.putInt("side" + k, s.side[e]);
            t.putBoolean("blade" + k, s.blade[e]);
            t.putBoolean("closed" + k, s.closed[e]);
        }
        return t;
    }

    private void annotate(Segment s) {
        for (int e = 0; e < 2; e++) {
            s.skip[e] = 0;
            s.frog[e] = -1;
            s.side[e] = 0;
            s.blade[e] = s.closed[e] = false;
        }
        for (long end : new long[]{s.a, s.b}) {
            Node e = nodes.get(end);
            if (e == null) continue;
            List<Segment> l = legs(e);
            if (l.size() == 2 && l.contains(s)) computePoints(e, l);
        }
    }

    /** Long-timber zone and switch blades for the two legs at a toe. */
    private void computePoints(Node toe, List<Segment> legs) {
        double zone = 0, prevSep = 0;
        double frog = -1;
        for (double d = 0.25; d < 24; d += 0.25) {
            double[] p0 = at(legs.get(0), toe.id, d), p1 = at(legs.get(1), toe.id, d);
            double sep = Math.hypot(p0[0] - p1[0], p0[1] - p1[1]);
            if (frog < 0 && sep >= TIE_GAUGE) frog = d - 0.25 + 0.25 * Math.min(1, Math.max(0, (TIE_GAUGE - prevSep) / Math.max(1e-6, sep - prevSep)));
            prevSep = sep;
            if (sep > 3.2) break;
            zone = d;
        }
        for (int i = 0; i < 2; i++) {
            Segment s = legs.get(i), o = legs.get(1 - i);
            boolean atA = s.a == toe.id;
            double sp = atA ? Math.min(4, s.path.length) : Math.max(0, s.path.length - 4);
            double[] p = s.path.pose(sp), q = at(o, toe.id, Math.min(4, o.path.length));
            double side = (q[0] - p[0]) * -Math.sin(p[2]) + (q[1] - p[1]) * Math.cos(p[2]);
            int e = atA ? 0 : 1;
            s.blade[e] = true;
            s.side[e] = side >= 0 ? 1 : -1;
            s.closed[e] = toe.route == i;
            s.skip[e] = Math.min(zone, s.path.length);
            s.frog[e] = frog > 0 && frog < s.path.length - 1 ? frog : -1;
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
            if (!s.extra.isEmpty()) c.putLongArray("extra", s.extra.toLongArray());
            if (s.locked) c.putBoolean("locked", true);
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
            s.extra = BitSet.valueOf(c.getLongArray("extra").orElse(new long[0]));
            s.locked = c.getBooleanOr("locked", false);
            s.live = s.complete();
            g.segments.put(s.id, s);
            a.segs.add(s.id);
            b.segs.add(s.id);
        }
        return g;
    }
}
