package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.track.TrackGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Server side train work: finding the path a consist stands on, and growing it as the train runs. */
final class Trains {
    private Trains() {}

    static final int MAX_DEPTH = 16;

    private record Found(RailVehicle v, double dist, int dir, boolean rear) {}

    private record Branch(List<Train.Step> steps, List<Found> found) {}

    /** The train a root's consist makes, putting back or uncoupling any vehicle that has strayed onto another leg. */
    static Train build(ServerLevel level, RailVehicle root) {
        for (int guard = 0; ; guard++) {
            List<RailVehicle.Member> ms = root.consist();
            Train t = tryBuild(level, root, ms, guard >= 8);
            if (t != null) return t;
        }
    }

    private static @Nullable Train tryBuild(ServerLevel level, RailVehicle root, List<RailVehicle.Member> ms, boolean last) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment rs = g.segment(root.seg);
        Train t = new Train();
        if (rs == null || ms.size() <= 1) return single(root);
        List<RailVehicle> want = new ArrayList<>();
        double reach = 0;
        for (RailVehicle.Member m : ms) {
            if (m.v() != root) want.add(m.v());
            reach += 2 * m.v().trackReach() + RailVehicle.BAR_REACH + 1;
        }
        Map<RailVehicle, Found> seen = new HashMap<>();
        int f = root.f();
        Branch front = search(g, rs, root.s, f, false, 0, want, seen, reach, 0, true);
        Branch rear = search(g, rs, root.s, -f, true, 0, want, seen, reach, 0, true);
        // path: the front branch reversed, then the root's section, then the rear branch
        for (int k = front.steps.size() - 1; k >= 1; k--) t.steps.add(front.steps.get(k));
        int rootK = t.steps.size();
        t.steps.add(new Train.Step(rs.id, -f));
        for (int k = 1; k < rear.steps.size(); k++) t.steps.add(rear.steps.get(k));
        double X = 0;
        for (int k = 0; k < rootK; k++) X += len(g, t.steps.get(k).seg());
        double xr = X + (-f > 0 ? root.s : rs.path.length - root.s);
        List<RailVehicle> vs = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Integer> flips = new ArrayList<>();
        vs.add(root);
        xs.add(xr);
        flips.add(1);
        for (Branch b : List.of(front, rear))
            for (Found fd : b.found) {
                vs.add(fd.v);
                xs.add(fd.rear ? xr + fd.dist : xr - fd.dist);
                flips.add(fd.v.f() == -fd.dir ? 1 : -1);
            }
        double total = 0;
        for (Train.Step st : t.steps) total += len(g, st.seg());
        boolean split = false;
        for (RailVehicle v : want) {
            if (vs.contains(v)) continue;
            Found fd = seen.get(v);
            boolean ok = false;
            if (fd != null && !last) {
                double x = fd.rear ? xr + fd.dist : xr - fd.dist;
                Train.Pos p = x >= 0 && x <= total ? t.at(id -> rail(g, id), x) : null;
                if (p != null && v.outlineHit(level, p.rail().id(), p.s(), 0) == null && clearOf(level, v, p.rail().id(), p.s(), ms)) {
                    int flip = v.f() == -fd.dir ? 1 : -1;
                    v.seg = p.rail().id();
                    v.s = p.s();
                    v.front = -p.dir() * flip > 0;
                    v.placeOn(p.rail());
                    vs.add(v);
                    xs.add(x);
                    flips.add(flip);
                    ok = true;
                    RailVehicle.notifyNear(level, v, Component.translatable("message.railworks.tow.rejoined").withStyle(ChatFormatting.GOLD));
                    if (RailVehicle.ECHO) RailVehicle.LOG.info("[train] put {} back on {} at {}", v.getType().toShortString(), p.rail().id(), p.s());
                }
            }
            if (!ok) {
                v.uncouple(level, null);
                RailVehicle.notifyNear(level, v, Component.translatable("message.railworks.tow.split").withStyle(ChatFormatting.YELLOW));
                if (RailVehicle.ECHO) RailVehicle.LOG.info("[train] {} off the train's path, uncoupled", v.getType().toShortString());
                split = true;
            }
        }
        if (split) return null;
        double min = Double.MAX_VALUE;
        for (double x : xs) min = Math.min(min, x);
        Integer[] order = new Integer[vs.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(xs.get(a), xs.get(b)));
        t.ref = min;
        t.d = new double[order.length];
        t.flip = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            int o = order[i];
            t.members.add(new RailVehicle.Member(vs.get(o), flips.get(o)));
            t.ids.add(vs.get(o).getUUID());
            t.d[i] = xs.get(o) - min;
            t.flip[i] = flips.get(o);
        }
        t.trim(id -> rail(g, id));
        if (RailVehicle.ECHO) RailVehicle.LOG.info("[train] built {} vehicles over {} sections, span {}", order.length, t.steps.size(), String.format(java.util.Locale.ROOT, "%.2f", t.span()));
        return t;
    }

    static Train single(RailVehicle v) {
        Train t = new Train();
        t.steps.add(new Train.Step(v.seg, -v.f()));
        Rail r = v.rail(v.seg);
        double l = r == null ? 0 : r.length();
        t.ref = -v.f() > 0 ? v.s : l - v.s;
        t.members.add(new RailVehicle.Member(v, 1));
        t.ids.add(v.getUUID());
        t.d = new double[]{0};
        t.flip = new int[]{1};
        return t;
    }

    private static boolean clearOf(ServerLevel level, RailVehicle b, long seg, double at, List<RailVehicle.Member> ms) {
        for (RailVehicle o : level.getEntitiesOfClass(RailVehicle.class, b.getBoundingBox().inflate(60), o -> o != b && o.seg == seg)) {
            boolean ours = false;
            for (RailVehicle.Member m : ms) ours |= m.v() == o;
            if (!ours && Math.abs(o.s - at) < o.trackReach() + b.trackReach()) return false;
        }
        return true;
    }

    /**
     * Walks from s0 on sg in s direction sigma, collecting wanted vehicles and their distance from the root.
     * At facing points it tries both legs and keeps the one holding most of the train, the route on a tie.
     */
    private static Branch search(TrackGraph g, TrackGraph.Segment sg, double s0, int sigma, boolean rear, double dist0, List<RailVehicle> want,
                                 Map<RailVehicle, Found> seen, double max, int depth, boolean first) {
        int dir = rear ? sigma : -sigma;
        List<Found> own = new ArrayList<>();
        for (RailVehicle v : want) {
            if (v.seg != sg.id) continue;
            double t = (v.s - s0) * sigma;
            if (first ? t > 1e-6 : t >= -1e-6) {
                Found fd = new Found(v, dist0 + Math.max(0, t), dir, rear);
                own.add(fd);
                seen.putIfAbsent(v, fd);
            }
        }
        List<Train.Step> steps = new ArrayList<>();
        steps.add(new Train.Step(sg.id, dir));
        double rem = sigma > 0 ? sg.path.length - s0 : s0;
        long node = sigma > 0 ? sg.b : sg.a;
        TrackGraph.Node n = g.node(node);
        if (depth >= MAX_DEPTH || dist0 + rem > max || n == null || n.buffer || sg.a == sg.b) return new Branch(steps, own);
        int inPort = sg.a == node ? sg.aPort : sg.bPort;
        TrackGraph.Segment route = g.next(node, sg.id);
        Branch best = null;
        boolean bestRoute = false;
        for (long id : n.segments()) {
            TrackGraph.Segment nx = g.segment(id);
            if (nx == null || nx.id == sg.id || !nx.live || (nx.a == node ? nx.aPort : nx.bPort) == inPort) continue;
            boolean fromA = !(nx.b == node && nx.a != node);
            Branch b = search(g, nx, fromA ? 0 : nx.path.length, fromA ? 1 : -1, rear, dist0 + rem, want, seen, max, depth + 1, false);
            boolean isRoute = nx == route;
            if (best == null || b.found.size() > best.found.size() || (b.found.size() == best.found.size() && isRoute && !bestRoute)) {
                best = b;
                bestRoute = isRoute;
            }
        }
        if (best != null) {
            steps.addAll(best.steps);
            own.addAll(best.found);
        }
        return new Branch(steps, own);
    }

    static double len(TrackGraph g, long id) {
        TrackGraph.Segment sg = g.segment(id);
        return sg == null ? 0 : sg.path.length;
    }

    static @Nullable Rail rail(TrackGraph g, long id) {
        TrackGraph.Segment sg = g.segment(id);
        return sg == null ? null : RailVehicle.rail(sg);
    }

    /** The node at one end of the path, front or rear. */
    static long endNode(TrackGraph.Segment sg, Train.Step st, boolean front) {
        return front == (st.dir() > 0) ? sg.a : sg.b;
    }

    /** Adds the next section at one end, by the route; live crossings throw and report points. */
    static boolean grow(ServerLevel level, @Nullable RailVehicle root, Train t, boolean front) {
        TrackGraph g = TrackGraph.get(level);
        Train.Step st = front ? t.steps.getFirst() : t.steps.getLast();
        TrackGraph.Segment sg = g.segment(st.seg());
        if (sg == null) return false;
        long node = endNode(sg, st, front);
        TrackGraph.Segment nx;
        if (root != null) nx = root.cross(level, g, sg, node);
        else {
            TrackGraph.Node n = g.node(node);
            nx = n == null || n.buffer ? null : g.next(node, sg.id);
            if (nx != null && !nx.live) nx = null;
        }
        if (nx == null) return false;
        Train.Step ns = new Train.Step(nx.id, Train.stepDir(nx, node, !front));
        if (front) t.addStepFront(ns, nx.path.length);
        else t.steps.add(ns);
        return true;
    }

    /** Moves the train dx along the path, + towards the rear. False if it ran out of track. */
    static boolean move(ServerLevel level, RailVehicle root, Train t, double dx) {
        TrackGraph g = TrackGraph.get(level);
        t.ref += dx;
        boolean ok = true;
        for (int guard = 0; guard < 8 && t.ref < 0; guard++) {
            if (!grow(level, root, t, true)) {
                t.ref = 0;
                ok = false;
                break;
            }
        }
        double span = t.span();
        for (int guard = 0; guard < 8 && t.ref + span > t.total(id -> rail(g, id)); guard++) {
            if (!grow(level, root, t, false)) {
                t.ref = t.total(id -> rail(g, id)) - span;
                ok = false;
                break;
            }
        }
        if (t.ref < 0) t.ref = 0;
        t.trim(id -> rail(g, id));
        return ok;
    }

    /** A copy of the path with extra track either end by the route, for the clients to run onto. */
    static Train lookahead(ServerLevel level, Train t, double extra) {
        TrackGraph g = TrackGraph.get(level);
        Train c = new Train();
        c.steps.addAll(t.steps);
        c.ref = t.ref;
        c.d = t.d;
        for (int guard = 0; guard < 6 && c.ref < extra; guard++) if (!grow(level, null, c, true)) break;
        double span = t.span();
        for (int guard = 0; guard < 6 && c.total(id -> rail(g, id)) - c.ref - span < extra; guard++) if (!grow(level, null, c, false)) break;
        return c;
    }

    /**
     * A point delta along s from (seg, s), across nodes by the route: {seg, s, sign}, sign -1 when the way
     * of travel runs against s there. Stops at the end of the track.
     */
    static double @Nullable [] walk(TrackGraph g, long seg, double s, double delta) {
        TrackGraph.Segment sg = g.segment(seg);
        if (sg == null) return null;
        double t = s + delta;
        int sign = 1;
        for (int guard = 0; guard < 8; guard++) {
            double l = sg.path.length;
            if (t >= 0 && t <= l) return new double[]{sg.id, t, sign};
            boolean atB = t > l;
            double over = atB ? t - l : -t;
            long node = atB ? sg.b : sg.a;
            TrackGraph.Node n = g.node(node);
            TrackGraph.Segment nx = n == null || n.buffer ? null : g.next(node, sg.id);
            if (nx == null || !nx.live) return new double[]{sg.id, atB ? l : 0, sign};
            boolean enterAtB = nx.b == node && nx.a != node;
            if (atB == enterAtB) sign = -sign;
            t = enterAtB ? nx.path.length - over : over;
            sg = nx;
        }
        return new double[]{sg.id, Math.max(0, Math.min(sg.path.length, t)), sign};
    }
}
