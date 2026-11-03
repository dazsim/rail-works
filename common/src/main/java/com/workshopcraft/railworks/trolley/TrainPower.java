package com.workshopcraft.railworks.trolley;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of carriages joined by gangways as one power network, in train order from the leading end. Each gangway
 * link carries at most {@link #LINK_CAP} a tick. Topology only until the LV rail: {@link #state()} stays NONE.
 */
public final class TrainPower {
    public static final long LINK_CAP = 1000;

    public enum State { NONE, LOW, OK }

    /** What one carriage's own fixtures store. */
    public interface Node {
        long stored();

        long space();

        long put(long amount, boolean simulate);

        long take(long amount, boolean simulate);
    }

    private final List<Node> nodes;
    private final long[] used;

    public TrainPower(List<? extends Node> nodes) {
        this.nodes = List.copyOf(nodes);
        this.used = new long[Math.max(0, this.nodes.size() - 1)];
    }

    public int size() {
        return nodes.size();
    }

    public Node node(int i) {
        return nodes.get(i);
    }

    public State state() {
        return State.NONE;
    }

    public long stored() {
        long t = 0;
        for (Node n : nodes) t += n.stored();
        return t;
    }

    public long space() {
        long t = 0;
        for (Node n : nodes) t += n.space();
        return t;
    }

    public void tick() {
        java.util.Arrays.fill(used, 0);
    }

    private long room(int from, int to) {
        long r = Long.MAX_VALUE;
        for (int l = Math.min(from, to); l < Math.max(from, to); l++) r = Math.min(r, LINK_CAP - used[l]);
        return Math.max(0, r);
    }

    private void carry(int from, int to, long amount) {
        for (int l = Math.min(from, to); l < Math.max(from, to); l++) used[l] += amount;
    }

    /** Draws for carriage at, from its own store first and then along the train in order; all or nothing. */
    public long draw(int at, long amount, boolean simulate) {
        return move(at, amount, simulate, false);
    }

    /** Offers from carriage at into its own store first and then along the train in order; takes what fits. */
    public long offer(int at, long amount, boolean simulate) {
        return move(at, amount, simulate, true);
    }

    private long move(int at, long amount, boolean simulate, boolean in) {
        if (amount <= 0 || at < 0 || at >= nodes.size()) return 0;
        List<Integer> order = new ArrayList<>();
        order.add(at);
        for (int i = 0; i < nodes.size(); i++) if (i != at) order.add(i);
        long left = amount;
        long[] plan = new long[nodes.size()];
        for (int i : order) {
            if (left == 0) break;
            long r = Math.min(left, room(i, at));
            long got = in ? nodes.get(i).put(r, true) : nodes.get(i).take(r, true);
            plan[i] = got;
            left -= got;
        }
        if (!in && left > 0) return 0;
        long done = amount - left;
        if (simulate) return done;
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] == 0) continue;
            if (in) nodes.get(i).put(plan[i], false);
            else nodes.get(i).take(plan[i], false);
            carry(i, at, plan[i]);
        }
        return done;
    }

    /** Where to split when the gangway between train positions i and i + 1 parts: each side keeps its own stores. */
    public TrainPower[] part(int i) {
        return new TrainPower[] {new TrainPower(nodes.subList(0, i + 1)), new TrainPower(nodes.subList(i + 1, nodes.size()))};
    }
}
