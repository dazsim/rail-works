package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.api.CarriageFixtures;
import com.workshopcraft.railworks.api.CarriagePowerNetwork;
import com.workshopcraft.railworks.api.Fixture;
import com.workshopcraft.railworks.api.FixtureContext;
import com.workshopcraft.railworks.api.FixtureType;
import com.workshopcraft.railworks.trolley.CarriageLayout.Model;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The working fixtures fitted in one carriage: their items, state, menus and ticks. Server side. */
final class Fittings {
    static final double REACH = 4;
    private final Carriage c;
    private final Map<Integer, Fitted> fitted = new HashMap<>();

    Fittings(Carriage c) {
        this.c = c;
    }

    static @Nullable FixtureType typeAt(String st, int i) {
        Model m = CarriageLayout.modelAt(st, i);
        return m == null ? null : CarriageFixtures.forPart(m.part().id());
    }

    /** The fixture in slot i, made on first use, or null when the part there is not one. */
    @Nullable Fitted get(int i) {
        if (!(c.level() instanceof ServerLevel)) return null;
        FixtureType t = typeAt(c.slots(), i);
        Fitted f = fitted.get(i);
        if (f != null && f.type == t) return f;
        if (f != null) drop(i);
        if (t == null) return null;
        f = new Fitted(t, i);
        fitted.put(i, f);
        return f;
    }

    void tick() {
        String st = c.slots();
        for (int i = 0; i < st.length(); i++) {
            if (!CarriageLayout.secure(st, i) || typeAt(st, i) == null) continue;
            Fitted f = get(i);
            if (f != null) f.impl.tick(f);
        }
    }

    boolean use(int i, Player p) {
        Fitted f = CarriageLayout.secure(c.slots(), i) ? get(i) : null;
        return f != null && f.impl.use(f, p);
    }

    /** The part in slot i is coming off: shut its menus, let it know, and drop what it held. */
    void drop(int i) {
        Fitted f = fitted.remove(i);
        if (f == null) return;
        f.gone = true;
        for (Map.Entry<Player, AbstractContainerMenu> e : f.viewers.entrySet()) if (e.getKey().containerMenu == e.getValue() && e.getKey() instanceof net.minecraft.server.level.ServerPlayer sp) sp.closeContainer();
        f.impl.removed(f);
        Vec3 at = f.position();
        for (ItemStack s : f.items.removeAllItems()) Containers.dropItemStack(c.level(), at.x, at.y, at.z, s);
        setLit(i, false);
    }

    void dropAll() {
        for (int i : List.copyOf(fitted.keySet())) drop(i);
    }

    // ---------------------------------------------------------------- saving

    void save(ValueOutput out) {
        ValueOutput.ValueOutputList l = out.childrenList("fixtures");
        for (Fitted f : fitted.values()) {
            ValueOutput o = l.addChild();
            o.putInt("slot", f.slot);
            o.putString("type", f.type.id().toString());
            ContainerHelper.saveAllItems(o, f.items.getItems());
            f.impl.save(o.child("data"));
        }
    }

    void load(ValueInput in) {
        fitted.clear();
        for (ValueInput o : in.childrenListOrEmpty("fixtures")) {
            int i = o.getIntOr("slot", -1);
            if (i < 0 || i >= CarriageLayout.SLOTS.size()) continue;
            FixtureType t = typeAt(c.slots(), i);
            if (t == null || !t.id().toString().equals(o.getStringOr("type", ""))) continue;
            Fitted f = new Fitted(t, i);
            ContainerHelper.loadAllItems(o, f.items.getItems());
            f.impl.load(o.childOrEmpty("data"));
            fitted.put(i, f);
        }
    }

    // ---------------------------------------------------------------- lit flags, synced as a string of 0 and 1, 2 for a junction lamp's amber

    private void setLit(int i, boolean on) {
        setLook(i, on ? '1' : '0');
    }

    void setLook(int i, char ch) {
        String v = c.look();
        if (v.length() != CarriageLayout.SLOTS.size()) v = "0".repeat(CarriageLayout.SLOTS.size());
        if (v.charAt(i) == ch) return;
        char[] a = v.toCharArray();
        a[i] = ch;
        c.setLook(new String(a));
    }

    static boolean lit(String look, int i) {
        return i < look.length() && look.charAt(i) == '1';
    }

    /** Where a point in half bay pixels is in the body's frame. */
    static Vec3 bayPoint(Slot s, double x, double y, double z) {
        float[] hb = s.halfBay();
        double mid = hb == null ? s.box().getCenter().x : (hb[0] + hb[1]) / 2;
        int k = s.side() == 0 ? 1 : s.side();
        return new Vec3(mid + k * (x - CarriageLayout.BAY_X / 2) * CarriageLayout.PX, CarriageLayout.FLOOR + y * CarriageLayout.PX,
                k * (CarriageLayout.WALL - (CarriageLayout.BAY_Z - z) * CarriageLayout.PX));
    }

    final class Fitted implements FixtureContext {
        final FixtureType type;
        final int slot;
        final Fixture impl;
        final SimpleContainer items;
        final Map<Player, AbstractContainerMenu> viewers = new HashMap<>();
        // FE offered and drawn this tick, and the tick
        private final long[] moved = {0, 0, -1};
        boolean gone;

        Fitted(FixtureType type, int slot) {
            this.type = type;
            this.slot = slot;
            this.items = new SimpleContainer(type.inventorySize()) {
                @Override
                public boolean stillValid(Player p) {
                    return Fitted.this.stillValid(p);
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    Fitted.this.setChanged();
                }
            };
            this.impl = type.factory().apply(this);
        }

        @Override
        public FixtureType type() {
            return type;
        }

        @Override
        public Entity carriage() {
            return c;
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public ServerLevel level() {
            return (ServerLevel) c.level();
        }

        @Override
        public Vec3 position() {
            return toWorld(CarriageLayout.BAY_X / 2, CarriageLayout.BAY_Y / 2, CarriageLayout.BAY_Z / 2);
        }

        @Override
        public Vec3 toWorld(double x, double y, double z) {
            Vec3 p = bayPoint(CarriageLayout.SLOTS.get(slot), x, y, z);
            return c.world(p.x, p.y, p.z);
        }

        @Override
        public double speed() {
            return c.root().u;
        }

        @Override
        public Container items() {
            return items;
        }

        @Override
        public boolean stillValid(Player p) {
            if (gone || !c.isAlive() || p.level() != c.level()) return false;
            return p.getEyePosition().distanceToSqr(position()) <= (REACH + 0.5) * (REACH + 0.5);
        }

        @Override
        public void setLit(boolean on) {
            Fittings.this.setLit(slot, on);
        }

        @Override
        public boolean lit() {
            return Fittings.lit(c.look(), slot);
        }

        @Override
        public void setChanged() {
            // entities save whatever their state when the chunk saves; nothing to flag
        }

        @Override
        public CarriagePowerNetwork power() {
            return c.power.view(type.power(), moved);
        }

        /** A consumer's menu says when its power is short. */
        private Component title() {
            Component t = impl.title(this);
            if (type.power() == null || type.power().role() != com.workshopcraft.railworks.api.FixturePower.Role.CONSUMER) return t;
            CarriagePowerNetwork.State s = c.power.state();
            if (s == CarriagePowerNetwork.State.OK) return t;
            return Component.empty().append(t).append(Component.translatable(s == CarriagePowerNetwork.State.LOW ? "container.railworks.low_power" : "container.railworks.no_power"));
        }

        @Override
        public RandomSource random() {
            return c.getRandom();
        }

        @Override
        public Component partName() {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(type.item())).getHoverName();
        }

        @Override
        public boolean openMenu(Player p) {
            List<AbstractContainerMenu> made = new ArrayList<>(1);
            OptionalInt id = p.openMenu(new SimpleMenuProvider((cid, inv, pl) -> {
                AbstractContainerMenu m = impl.menu(this, cid, inv, pl);
                if (m != null) made.add(m);
                return m;
            }, title()));
            if (id.isEmpty() || made.isEmpty()) return false;
            viewers.entrySet().removeIf(e -> e.getKey().containerMenu != e.getValue());
            viewers.put(p, made.getFirst());
            return true;
        }
    }
}
