package com.workshopcraft.railworks.track;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Anything that runs on the track: a section, a distance along it and a speed along +s. Runners on the
 * same line find each other by walking the graph the way they are heading, so they meet across section
 * ends and through points, and trade momentum when they touch.
 */
public interface TrackRunner {
    /** How hard runners bounce off each other, 0 couples them, 1 is a perfect rebound. */
    double RESTITUTION = 0.3;

    long trackSegment();

    double trackS();

    /** Speed along +s of its section. */
    double trackSpeed();

    void bumpTrackSpeed(double u);

    /** Distance from its centre to its buffer face, the same at both ends. */
    double trackReach();

    double trackMass();

    /** False for a runner carried by another, like a bogie under a carriage, which the carrier stands in for. */
    default boolean trackContact() {
        return true;
    }

    /** The nearest runner ahead, gap is buffer face to buffer face, k turns its speed into ours. */
    record Contact(TrackRunner other, double gap, int k) {}

    static @Nullable Contact ahead(ServerLevel level, Entity self, int dir, double range) {
        return ahead(level, self, dir, range, r -> false);
    }

    /** As above, passing over runners skip accepts, such as the rest of its own train. */
    static @Nullable Contact ahead(ServerLevel level, Entity self, int dir, double range, java.util.function.Predicate<TrackRunner> skip) {
        if (!(self instanceof TrackRunner me) || dir == 0) return null;
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment sg = g.segment(me.trackSegment());
        if (sg == null) return null;
        double look = range + me.trackReach() + 4;
        java.util.List<TrackRunner> near = new java.util.ArrayList<>();
        for (Entity e : level.getEntities(self, self.getBoundingBox().inflate(look), e -> e instanceof TrackRunner r && e.isAlive() && r.trackContact() && !skip.test(r)))
            near.add((TrackRunner) e);
        if (near.isEmpty()) return null;
        TrackRunner best = null;
        double bestD = Double.MAX_VALUE;
        int bestK = 1;
        double s0 = me.trackS(), offset = dir > 0 ? sg.path.length - s0 : s0;
        boolean fwd = dir > 0;
        for (TrackRunner r : near) {
            if (r.trackSegment() != sg.id) continue;
            double d = (r.trackS() - s0) * dir;
            if (d > 1e-6 && d < bestD) {
                bestD = d;
                best = r;
                bestK = dir;
            }
        }
        for (int i = 0; i < 8 && offset < look && offset < bestD; i++) {
            long nodeId = fwd ? sg.b : sg.a;
            TrackGraph.Node n = g.node(nodeId);
            if (n == null || n.buffer) break;
            TrackGraph.Segment nx = g.next(nodeId, sg.id);
            if (nx == null || !nx.live) break;
            fwd = !(nx.b == nodeId && nx.a != nodeId);
            for (TrackRunner r : near) {
                if (r.trackSegment() != nx.id || r == me) continue;
                double d = offset + (fwd ? r.trackS() : nx.path.length - r.trackS());
                if (d < bestD) {
                    bestD = d;
                    best = r;
                    bestK = fwd ? 1 : -1;
                }
            }
            offset += nx.path.length;
            sg = nx;
        }
        if (best == null || bestD - me.trackReach() - best.trackReach() > range) return null;
        return new Contact(best, bestD - me.trackReach() - best.trackReach(), bestK);
    }

    /** Speeds after a bump along the line of travel, v1 ours towards them, v2 theirs the same way. */
    static double[] exchange(double m1, double v1, double m2, double v2) {
        double p = m1 * v1 + m2 * v2, m = m1 + m2, rel = v1 - v2;
        return new double[]{(p - m2 * RESTITUTION * rel) / m, (p + m1 * RESTITUTION * rel) / m};
    }
}
