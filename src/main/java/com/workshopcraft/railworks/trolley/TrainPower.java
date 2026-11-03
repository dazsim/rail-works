package com.workshopcraft.railworks.trolley;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of carriages linked by gangways or jumpers as one power network, in train order from the leading end.
 * Each link carries at most {@link #LINK_CAP} a tick, and each carriage's fixtures draw and offer at most
 * {@link #NET_CAP} a tick each way.
 */
public final class TrainPower {
    public static final long LINK_CAP = 1000, NET_CAP = 1000;

    public enum State { NONE, LOW, OK }

    /** What one carriage's own fixtures store. */
    public interface Node {
        long stored();

        long space();

        long put(long amount, boolean simulate);

        long take(long amount, boolean simulate);

        /** A draw was refused here lately, kept by the node across rebuilt networks. */
        default boolean refused() {
            return false;
        }

        default void refuse() {}
    }

    private final List<Node> nodes;
    private final long[] used, in, out;
    private boolean refused;

    public TrainPower(List<? extends Node> nodes) {
        this.nodes = List.copyOf(nodes);
        this.used = new long[Math.max(0, this.nodes.size() - 1)];
        this.in = new long[this.nodes.size()];
        this.out = new long[this.nodes.size()];
    }

    public int size() {
        return nodes.size();
    }

    public Node node(int i) {
        return nodes.get(i);
    }

    /** NONE with no carriages, LOW under 10% or after a refused draw, else OK. */
    public State state() {
        if (nodes.isEmpty()) return State.NONE;
        boolean r = refused;
        for (Node n : nodes) r |= n.refused();
        long s = stored(), cap = s + space();
        return r || s * 10 < cap ? State.LOW : State.OK;
    }

    /** What carriage i's fixtures offered in and drew out this tick. */
    public long in(int i) {
        return in[i];
    }

    public long out(int i) {
        return out[i];
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
        java.util.Arrays.fill(in, 0);
        java.util.Arrays.fill(out, 0);
        refused = false;
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
        long cap = NET_CAP - (in ? this.in[at] : out[at]);
        if (!in && amount > cap) return refuse(at, simulate);
        long want = Math.min(amount, Math.max(0, cap));
        List<Integer> order = new ArrayList<>();
        order.add(at);
        for (int i = 0; i < nodes.size(); i++) if (i != at) order.add(i);
        long left = want;
        long[] plan = new long[nodes.size()];
        for (int i : order) {
            if (left == 0) break;
            long r = Math.min(left, room(i, at));
            long got = in ? nodes.get(i).put(r, true) : nodes.get(i).take(r, true);
            plan[i] = got;
            left -= got;
        }
        if (!in && left > 0) return refuse(at, simulate);
        long done = want - left;
        if (simulate) return done;
        if (in) this.in[at] += done;
        else out[at] += done;
        for (int i = 0; i < plan.length; i++) {
            if (plan[i] == 0) continue;
            if (in) nodes.get(i).put(plan[i], false);
            else nodes.get(i).take(plan[i], false);
            carry(i, at, plan[i]);
        }
        return done;
    }

    private long refuse(int at, boolean simulate) {
        if (!simulate) {
            refused = true;
            nodes.get(at).refuse();
        }
        return 0;
    }

    /** Where to split when the gangway between train positions i and i + 1 parts: each side keeps its own stores. */
    public TrainPower[] part(int i) {
        return new TrainPower[] {new TrainPower(nodes.subList(0, i + 1)), new TrainPower(nodes.subList(i + 1, nodes.size()))};
    }
}
