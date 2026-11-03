package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.api.CarriagePowerNetwork;
import com.workshopcraft.railworks.api.FixturePower;
import com.workshopcraft.railworks.api.FixtureType;
import com.workshopcraft.railworks.api.PowerTier;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** One carriage's LV store and the train network it is on this tick. Server side. */
final class Power implements TrainPower.Node {
    static final long BASE = 10000;
    static final int VERSION = 1;
    private static int[] rails, jumpers;
    private static int size = -1;

    private final Carriage c;
    long stored;
    private long refusedAt = Long.MIN_VALUE / 2;
    // bumped when a part goes on or off, so linked carriages rebuild their network
    int epoch;
    private @Nullable Net net;
    long lastIn, lastOut;
    private @Nullable String capFor;
    private long cap;

    /** The carriages linked this tick in train order, the epochs they had, and the shared rules. */
    record Net(@Nullable TrainPower tp, List<Carriage> cars, int[] epochs, long time, int topo) {
        int indexOf(Carriage c) {
            for (int i = 0; i < cars.size(); i++) if (cars.get(i) == c) return i;
            return -1;
        }
    }

    Power(Carriage c) {
        this.c = c;
    }

    private static void ids() {
        if (size == CarriageLayout.SLOTS.size()) return;
        rails = new int[] {CarriageLayout.index("lv_rail_0"), CarriageLayout.index("lv_rail_1")};
        jumpers = new int[] {CarriageLayout.index("jumper_0"), CarriageLayout.index("jumper_1")};
        size = CarriageLayout.SLOTS.size();
    }

    private static boolean secure(String st, int i) {
        return i >= 0 && i < st.length() && CarriageLayout.secure(st, i);
    }

    static int[] railSlots() {
        ids();
        return rails;
    }

    /** One finished LV rail makes the network. */
    static boolean hasRail(String st) {
        ids();
        return secure(st, rails[0]) || secure(st, rails[1]);
    }

    static boolean jumper(String st, int e) {
        ids();
        return secure(st, jumpers[Gangways.k(e)]);
    }

    boolean hasRail() {
        return hasRail(c.slots());
    }

    /** 10,000 FE and the declared buffers of the fixtures fitted, or 0 with no rail. */
    long capacity() {
        String st = c.slots();
        if (st.equals(capFor)) return cap;
        long n = 0;
        if (hasRail(st)) {
            n = BASE;
            for (int i = 0; i < st.length(); i++) {
                if (!CarriageLayout.secure(st, i)) continue;
                FixtureType t = Fittings.typeAt(st, i);
                if (t != null && t.power() != null) n += t.power().buffer();
            }
        }
        capFor = st;
        cap = n;
        return n;
    }

    @Override
    public long stored() {
        return Math.min(stored, capacity());
    }

    @Override
    public long space() {
        return Math.max(0, capacity() - stored());
    }

    @Override
    public long put(long amount, boolean simulate) {
        long n = Math.max(0, Math.min(amount, space()));
        if (!simulate) stored = stored() + n;
        return n;
    }

    @Override
    public long take(long amount, boolean simulate) {
        long n = Math.max(0, Math.min(amount, stored()));
        if (!simulate) stored = stored() - n;
        return n;
    }

    @Override
    public boolean refused() {
        return c.level().getGameTime() - refusedAt <= 1;
    }

    @Override
    public void refuse() {
        refusedAt = c.level().getGameTime();
    }

    /** Start of the carriage's tick: lose what no longer fits. */
    void tick() {
        stored = stored();
    }

    /** The carriage screw coupled at end e when power crosses to it: both have a rail and a joined gangway or two jumpers. */
    static Gangways.@Nullable Partner linked(Carriage c, int e) {
        Gangways.Partner p = Gangways.partner(c, e);
        if (p == null || !hasRail(c.slots()) || !hasRail(p.o().slots())) return null;
        Gangways.Joint j = c.joint(e);
        if (j != null && j.o() == p.o()) return p;
        return jumper(c.slots(), e) && jumper(p.o().slots(), p.oe()) ? p : null;
    }

    private static void walk(Carriage c, int e, List<Carriage> out) {
        for (Gangways.Partner p = linked(c, e); p != null && p.o() != c && !out.contains(p.o()); p = linked(p.o(), -p.oe())) out.add(p.o());
    }

    /** Linked carriages from the end that is not towed. */
    static List<Carriage> chain(Carriage c) {
        List<Carriage> out = new ArrayList<>();
        walk(c, 1, out);
        java.util.Collections.reverse(out);
        out.add(c);
        walk(c, -1, out);
        if (out.getFirst().towed() && !out.getLast().towed()) java.util.Collections.reverse(out);
        return out;
    }

    private boolean fresh(Net n, long t) {
        if (n.time() != t || n.topo() != RailVehicle.topology) return false;
        for (int i = 0; i < n.cars().size(); i++) {
            Carriage o = n.cars().get(i);
            if (!o.isAlive() || o.power.epoch != n.epochs()[i] || o.power.net != n) return false;
        }
        return true;
    }

    /** The network this carriage is on this tick, built once a tick for every carriage on it. */
    Net net() {
        long t = c.level().getGameTime();
        if (net != null && fresh(net, t)) return net;
        List<Carriage> cars = hasRail() ? chain(c) : List.of(c);
        int[] ep = new int[cars.size()];
        List<Power> nodes = new ArrayList<>(cars.size());
        for (int i = 0; i < cars.size(); i++) {
            Power p = cars.get(i).power;
            ep[i] = p.epoch;
            nodes.add(p);
            Net old = p.net;
            if (old != null && old.time() != t) {
                int k = old.indexOf(cars.get(i));
                p.lastIn = old.tp() == null || k < 0 ? 0 : old.tp().in(k);
                p.lastOut = old.tp() == null || k < 0 ? 0 : old.tp().out(k);
            } else if (old == null) {
                p.lastIn = p.lastOut = 0;
            }
        }
        Net n = new Net(hasRail() ? new TrainPower(nodes) : null, List.copyOf(cars), ep, t, RailVehicle.topology);
        for (Carriage o : cars) o.power.net = n;
        return n;
    }

    CarriagePowerNetwork.State state() {
        Net n = net();
        if (n.tp() == null) return CarriagePowerNetwork.State.NONE;
        return switch (n.tp().state()) {
            case NONE -> CarriagePowerNetwork.State.NONE;
            case LOW -> CarriagePowerNetwork.State.LOW;
            case OK -> CarriagePowerNetwork.State.OK;
        };
    }

    /** What a fixture sees: the train network, each way at most its declared rate a tick. */
    CarriagePowerNetwork view(@Nullable FixturePower fp, long[] moved) {
        if (!hasRail()) return CarriagePowerNetwork.NONE;
        return new CarriagePowerNetwork() {
            private long[] spent() {
                long t = c.level().getGameTime();
                if (moved[2] != t) {
                    moved[0] = moved[1] = 0;
                    moved[2] = t;
                }
                return moved;
            }

            @Override
            public boolean connected() {
                return true;
            }

            @Override
            public PowerTier tier() {
                return PowerTier.LV;
            }

            @Override
            public long offer(long amount, boolean simulate) {
                if (fp == null || fp.role() != FixturePower.Role.PRODUCER || amount <= 0) return 0;
                long[] m = spent();
                long a = Math.min(amount, fp.rate() - m[0]);
                if (a <= 0) return 0;
                Net n = net();
                long got = n.tp().offer(n.indexOf(c), a, simulate);
                if (!simulate) m[0] += got;
                return got;
            }

            @Override
            public long draw(long amount, boolean simulate) {
                if (fp == null || fp.role() != FixturePower.Role.CONSUMER || amount <= 0) return 0;
                long[] m = spent();
                Net n = net();
                if (amount > fp.rate() - m[1]) {
                    if (!simulate) refuse();
                    return 0;
                }
                long got = n.tp().draw(n.indexOf(c), amount, simulate);
                if (!simulate) m[1] += got;
                return got;
            }

            @Override
            public long stored() {
                return net().tp().stored();
            }

            @Override
            public long space() {
                return net().tp().space();
            }

            @Override
            public long capacity() {
                TrainPower tp = net().tp();
                return tp.stored() + tp.space();
            }

            @Override
            public State state() {
                return Power.this.state();
            }
        };
    }
}
