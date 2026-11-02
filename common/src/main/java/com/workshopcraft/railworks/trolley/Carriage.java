package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackGraph;
import com.workshopcraft.railworks.track.TrackRunner;
import com.workshopcraft.railworks.trolley.CarriageLayout.Model;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A carriage body built a part at a time on two bogies, slot by slot as data/railworks/carriage lays out.
 * The bogies stay on the track as members of its train; the body sits on their pivots.
 */
public class Carriage extends RailVehicle {
    public static final double HALF_CENTRES = 7, REACH = 10.58;
    static final double MASS = 3.0, BRAKE = 1.45, DOOR_TOP = 1.0, STEP_TOP = 4.3;
    static final ResourceKey<DamageType> RUN_DOWN = damage("carriage");
    static final EntityDataAccessor<String> DATA_SLOTS = SynchedEntityData.defineId(Carriage.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> DATA_SEATS = SynchedEntityData.defineId(Carriage.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> DATA_DOORS = SynchedEntityData.defineId(Carriage.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_BOGIE_A = SynchedEntityData.defineId(Carriage.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_BOGIE_B = SynchedEntityData.defineId(Carriage.class, EntityDataSerializers.INT);

    private final List<Integer> order = new ArrayList<>();
    // A under the rear end, B under the front
    private @Nullable UUID bogieA, bogieB;
    private final Map<UUID, Integer> pending = new HashMap<>();
    private final Map<UUID, Integer> stepWait = new HashMap<>();
    private final Map<UUID, Integer> reseat = new HashMap<>();
    private int lost, rumble;
    // client: how far each door stands open, 0 to 1
    public final float[] door = new float[6], doorO = new float[6];

    public Carriage(EntityType<? extends Carriage> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_SLOTS, CarriageLayout.empty()).define(DATA_SEATS, "").define(DATA_DOORS, 0).define(DATA_BOGIE_A, 0).define(DATA_BOGIE_B, 0);
    }

    @Override
    public int doneStage() {
        return 1;
    }

    @Override
    protected double ownMass() {
        return MASS;
    }

    @Override
    public double trackReach() {
        return REACH;
    }

    @Override
    protected double wheelRadius() {
        return Bogie.WHEEL_R;
    }

    @Override
    protected double halfWidth() {
        return 2.5;
    }

    @Override
    protected double outlineHeight() {
        return 5.4;
    }

    @Override
    public double eyeReach() {
        return 10.22;
    }

    @Override
    public double eyeHeight() {
        return 0.98;
    }

    @Override
    protected ResourceKey<DamageType> runDown() {
        return RUN_DOWN;
    }

    @Override
    protected int towPoints() {
        return 3;
    }

    @Override
    protected boolean holdsChunks() {
        return true;
    }

    @Override
    protected double pushTop(int n) {
        return Math.min(PUSH_TOP, 1 + 0.5 * (n - 1));
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    public String slots() {
        return entityData.get(DATA_SLOTS);
    }

    public int doors() {
        return entityData.get(DATA_DOORS);
    }

    private void setSlot(int i, char c) {
        char[] a = slots().toCharArray();
        a[i] = c;
        entityData.set(DATA_SLOTS, new String(a));
    }

    /** The bogie under one end, +1 the front. */
    public @Nullable Bogie bogie(int end) {
        if (level() instanceof ServerLevel sl) {
            UUID id = end > 0 ? bogieB : bogieA;
            return id != null && sl.getEntity(id) instanceof Bogie b && b.isAlive() ? b : null;
        }
        int id = entityData.get(end > 0 ? DATA_BOGIE_B : DATA_BOGIE_A) - 1;
        return id >= 0 && level().getEntity(id) instanceof Bogie b ? b : null;
    }

    /** Sits the body on its bogie pivots: halfway between them, along the chord from one to the other. */
    @Override
    public void afterPlace() {
        Bogie a = bogie(-1), b = bogie(1);
        if (a == null || b == null) return;
        Vec3 pa = a.position(), pb = b.position();
        double dx = pb.x - pa.x, dy = pb.y - pa.y, dz = pb.z - pa.z, h = Math.hypot(dx, dz);
        if (h < 1) return;
        setYRot(Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90));
        setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
        setPos(pa.add(pb).scale(0.5));
    }

    @Override
    protected void clientTick() {
        super.clientTick();
        afterPlace();
        int d = doors();
        for (int i = 0; i < door.length; i++) {
            doorO[i] = door[i];
            float want = (d >> i & 1) != 0 ? 1 : 0;
            door[i] = want > door[i] ? Math.min(want, door[i] + 0.12f) : Math.max(want, door[i] - 0.25f);
        }
    }

    // ---------------------------------------------------------------- starting and taking apart

    /** A solebar offered to a finished bogie: find its partner, shunt the pair 14 apart, and lay the first solebar. */
    static InteractionResult start(Bogie a, Player player, ItemStack held) {
        if (!(a.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (a.towed() || !a.followers().isEmpty()) {
            say(player, Component.translatable("message.railworks.carriage.uncouple_first"));
            return InteractionResult.SUCCESS;
        }
        Bogie b = null;
        int dir = 0;
        double dist = 0;
        for (int d = -1; d <= 1; d += 2) {
            TrackRunner.Contact c = TrackRunner.ahead(level, a, d, 14);
            if (c == null || !(c.other() instanceof Bogie o) || !o.done() || o.towed() || !o.followers().isEmpty()) continue;
            double dd = c.gap() + a.trackReach() + o.trackReach();
            if (dd < 12 || dd > 16) continue;
            if (b == null || Math.abs(dd - 2 * HALF_CENTRES) < Math.abs(dist - 2 * HALF_CENTRES)) {
                b = o;
                dir = d;
                dist = dd;
            }
        }
        if (b == null) {
            say(player, Component.translatable("message.railworks.carriage.no_partner"));
            return InteractionResult.SUCCESS;
        }
        TrackGraph g = TrackGraph.get(level);
        double[] mid = Trains.walk(g, a.seg, a.s, dir * dist / 2);
        Carriage c = ModContent.CARRIAGE_ENTITY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        Rail r = mid == null || c == null ? null : c.rail((long) mid[0]);
        if (r == null) return InteractionResult.SUCCESS;
        int sign = (int) mid[2];
        double[] pa = Trains.walk(g, (long) mid[0], mid[1], -dir * sign * HALF_CENTRES), pb = Trains.walk(g, (long) mid[0], mid[1], dir * sign * HALF_CENTRES);
        if (pa == null || pb == null) return InteractionResult.SUCCESS;
        c.seg = (long) mid[0];
        c.s = mid[1];
        c.front = true;
        c.placeOn(r);
        Vec3 look = player.getLookAngle();
        if (look.x * Math.cos(c.frontHeading()) + look.z * Math.sin(c.frontHeading()) < 0) {
            c.front = false;
            c.placeOn(r);
        }
        BlockPos hit = c.outlineHit(level, c.seg, c.s, 0);
        if (hit != null) {
            say(player, Component.translatable("message.railworks.trolley.no_room", hit.getX(), hit.getY(), hit.getZ()));
            return InteractionResult.SUCCESS;
        }
        shunt(a, pa);
        shunt(b, pb);
        double hf = c.frontHeading();
        for (Bogie x : List.of(a, b)) {
            Vec3 d = x.position().subtract(c.position());
            int end = d.x * Math.cos(hf) + d.z * Math.sin(hf) >= 0 ? 1 : -1;
            int ts = Math.cos(x.frontHeading() - hf) > 0 ? 1 : -1;
            x.u = 0;
            x.setTow(c, ts, -end * ts, MOUNT);
            x.sync(true);
            if (end > 0) c.bogieB = x.getUUID();
            else c.bogieA = x.getUUID();
        }
        c.entityData.set(DATA_BOGIE_A, (end(c, -1)) + 1);
        c.entityData.set(DATA_BOGIE_B, (end(c, 1)) + 1);
        c.afterPlace();
        // the first solebar goes on the side the player stands
        Vec3 rel = player.position().subtract(c.position());
        int side = -rel.x * Math.sin(hf) + rel.z * Math.cos(hf) >= 0 ? 1 : -1;
        for (Slot s : CarriageLayout.SLOTS) {
            if (s.group().equals("solebar") && s.side() == side) {
                c.setSlot(s.i(), CarriageLayout.code(0, 0));
                c.order.add(s.i());
            }
        }
        c.sync(true);
        level.addFreshEntity(c);
        if (!player.getAbilities().instabuild) held.shrink(1);
        c.play(level, SoundEvents.ANVIL_PLACE, 0.6f, 0.8f);
        c.play(level, SoundEvents.CHAIN_PLACE, 0.6f, 0.7f);
        say(player, Component.translatable("message.railworks.carriage.started").withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static int end(Carriage c, int e) {
        Bogie b = c.bogie(e);
        return b == null ? -1 : b.getId();
    }

    /** Moves a bogie to a point on the line, keeping the way it faces. */
    private static void shunt(Bogie b, double[] p) {
        double h0 = b.frontHeading();
        Rail r = b.rail((long) p[0]);
        if (r == null) return;
        b.seg = (long) p[0];
        b.s = p[1];
        b.placeOn(r);
        if (Math.cos(b.frontHeading() - h0) < 0) {
            b.front = !b.front;
            b.placeOn(r);
        }
    }

    @Override
    public void takeOff(ServerLevel level, Player player) {
        takeLast(level, player);
    }

    /** Takes off the last part fitted; with the first solebar off the bogies are left loose. */
    private void takeLast(ServerLevel level, @Nullable Player player) {
        String st = slots();
        // parts set without a fitting order (commands, edited saves) come off before the ones on record
        List<Integer> loose = new ArrayList<>();
        for (int j = 0; j < CarriageLayout.SLOTS.size(); j++) if (CarriageLayout.fitted(st, j) && !order.contains(j)) loose.add(j);
        order.addAll(loose);
        if (order.isEmpty()) {
            dissolve(level);
            return;
        }
        int i = order.removeLast();
        Model m = CarriageLayout.modelAt(st, i);
        Slot s = CarriageLayout.SLOTS.get(i);
        setSlot(i, '.');
        if (s.door() >= 0) entityData.set(DATA_DOORS, doors() & ~(1 << s.door()));
        if (m != null && !m.seats().isEmpty()) for (Entity p : List.copyOf(getPassengers())) if (seatOf(p) / 4 == i) p.stopRiding();
        if (m != null) give(level, player, new ItemStack(m.part().asItem()), position());
        play(level, SoundEvents.ANVIL_PLACE, 0.4f, 1.3f);
        if (player != null) say(player, Component.translatable("message.railworks.carriage.removed", new ItemStack(m == null ? ModContent.SOLEBAR : m.part().asItem()).getHoverName()));
        if (order.isEmpty()) dissolve(level);
    }

    /** No body left: the bogies go back to being loose bogies. */
    private void dissolve(ServerLevel level) {
        for (int e = -1; e <= 1; e += 2) {
            Bogie b = bogie(e);
            if (b != null && b.mounted()) b.setTow(null, 1, 1);
        }
        bogieA = bogieB = null;
        discard();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy() && level() instanceof ServerLevel sl) {
            for (int e = -1; e <= 1; e += 2) {
                Bogie b = bogie(e);
                if (b != null && b.mounted()) b.setTow(null, 1, 1);
            }
            if (reason == RemovalReason.KILLED) for (int i : order) {
                Model m = CarriageLayout.modelAt(slots(), i);
                if (m != null) spawnAtLocation(sl, new ItemStack(m.part().asItem()));
            }
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- aiming at slots

    private double tanPitch() {
        return Math.tan(Math.toRadians(-getXRot()));
    }

    /** A world offset in the body's frame: x along, y up, z across to the right. */
    private Vec3 local(Vec3 d, boolean point) {
        if (point) d = d.subtract(position());
        double hf = frontHeading(), a = d.x * Math.cos(hf) + d.z * Math.sin(hf), w = -d.x * Math.sin(hf) + d.z * Math.cos(hf);
        return new Vec3(a, d.y - a * tanPitch(), w);
    }

    Vec3 world(double a, double y, double w) {
        double hf = frontHeading();
        return position().add(a * Math.cos(hf) - w * Math.sin(hf), y + a * tanPitch(), a * Math.sin(hf) + w * Math.cos(hf));
    }

    /** The slot the player's line of sight meets first among those ok accepts, or a near miss, or -1. */
    @Override
    protected double pickDistance(Vec3 eye, Vec3 look, double reach) {
        Vec3 o = local(eye, true), to = o.add(local(look, false).normalize().scale(reach));
        String st = slots();
        double best = Double.MAX_VALUE;
        for (Slot s : CarriageLayout.SLOTS) {
            if (done() && !CarriageLayout.fitted(st, s.i())) continue;
            AABB b = s.box().inflate(0.04);
            if (b.contains(o)) continue;
            var hit = b.clip(o, to);
            if (hit.isPresent()) best = Math.min(best, hit.get().distanceTo(o));
        }
        return best < Double.MAX_VALUE ? best : super.pickDistance(eye, look, reach);
    }

    int aimed(Player p, IntPredicate ok, boolean direct) {
        Vec3 o = local(p.getEyePosition(), true), d = local(p.getLookAngle(), false).normalize(), to = o.add(d.scale(6));
        int best = -1;
        double bs = Double.MAX_VALUE;
        for (Slot s : CarriageLayout.SLOTS) {
            if (!ok.test(s.i())) continue;
            AABB b = s.box().inflate(0.12);
            var hit = b.clip(o, to);
            double score;
            if (b.contains(o)) score = 0;
            else if (hit.isPresent()) score = hit.get().distanceTo(o);
            else if (direct) continue;
            else {
                double t = Mth.clamp(b.getCenter().subtract(o).dot(d), 0, 6), miss = Math.sqrt(b.distanceToSqr(o.add(d.scale(t))));
                if (miss > 1.2) continue;
                score = 10 + miss;
            }
            if (score < bs) {
                bs = score;
                best = s.i();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player hp && other(hp) instanceof Picked pk) return pk.v().hurtServer(level, source, damage);
        if (source.getEntity() instanceof Player p && p.getMainHandItem().is(ModContent.HAMMER) && !p.isSecondaryUseActive()) hammer(level, p);
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        Picked pk = other(player);
        if (pk != null) return pk.v().interact(player, hand, pk.loc());
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModContent.TOW_BAR)) return Coupling.click(this, player, location, held);
        if (held.is(ModContent.HAMMER)) {
            if (level() instanceof ServerLevel sl) {
                if (player.isSecondaryUseActive()) sneakHammer(sl, player, location);
                else hammer(sl, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (CarriageLayout.isPart(held.getItem())) {
            if (hand != partHand(player)) return InteractionResult.PASS;
            if (level() instanceof ServerLevel sl) fit(sl, player, held);
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty() || partHand(player) != null) return InteractionResult.PASS;
        if (level() instanceof ServerLevel sl) emptyHand(sl, player, location);
        return InteractionResult.SUCCESS;
    }

    /** The hand a click fits a part from, the same one the ghost shows: the main hand, else the off hand unless the main one holds a tool. */
    public static @Nullable InteractionHand partHand(Player p) {
        ItemStack main = p.getMainHandItem();
        if (CarriageLayout.isPart(main.getItem())) return InteractionHand.MAIN_HAND;
        return !main.is(ModContent.HAMMER) && !main.is(ModContent.TOW_BAR) && CarriageLayout.isPart(p.getOffhandItem().getItem()) ? InteractionHand.OFF_HAND : null;
    }

    private void fit(ServerLevel level, Player player, ItemStack held) {
        String st = slots();
        List<Slot> can = CarriageLayout.fitsFor(st, held.getItem());
        if (done()) can.removeIf(Slot::required);
        if (can.isEmpty()) {
            boolean anyFree = CarriageLayout.SLOTS.stream().anyMatch(s -> s.model(held.getItem()) != null && !CarriageLayout.fitted(st, s.i()));
            say(player, anyFree ? notYet(st, held.getItem()) : Component.translatable("message.railworks.carriage.all_on", held.getHoverName()));
            return;
        }
        int i = aimed(player, j -> can.stream().anyMatch(s -> s.i() == j), false);
        if (i < 0) {
            say(player, Component.translatable("message.railworks.carriage.aim", held.getHoverName()));
            return;
        }
        Slot s = CarriageLayout.SLOTS.get(i);
        Model m = s.model(held.getItem());
        if (m == null) return;
        Component name = held.getHoverName();
        setSlot(i, CarriageLayout.code(s.models().indexOf(m), 0));
        order.add(i);
        if (!player.getAbilities().instabuild) held.shrink(1);
        switch (m.part().fix()) {
            case CarriageLayout.RIVETS -> play(level, SoundEvents.ANVIL_PLACE, 0.45f, 1.0f);
            case CarriageLayout.BLOW -> play(level, SoundEvents.WOOD_PLACE, 0.8f, 0.9f);
            default -> play(level, m.part().id().equals("floor") ? SoundEvents.WOOD_PLACE : SoundEvents.CHAIN_PLACE, 0.8f, 0.9f);
        }
        String nst = slots();
        if (m.part().fix().equals(CarriageLayout.RIVETS)) say(player, Component.translatable("message.railworks.carriage.fitted_rivets", name, m.part().rivets()));
        else if (m.part().fix().equals(CarriageLayout.BLOW)) say(player, Component.translatable("message.railworks.carriage.fitted_blow", name));
        else say(player, readout(nst));
    }

    private boolean owes(String st, int i) {
        Model m = CarriageLayout.modelAt(st, i);
        return m != null && CarriageLayout.work(st, i) < m.part().work();
    }

    private void hammer(ServerLevel level, Player player) {
        String st = slots();
        if (done()) {
            int c = aimed(player, j -> CarriageLayout.SLOTS.get(j).group().equals("coupling") && CarriageLayout.fitted(st, j), true);
            if (c >= 0) {
                screw(level, player, CarriageLayout.SLOTS.get(c).end());
                return;
            }
            play(level, SoundEvents.ANVIL_LAND, 0.2f, 2.0f);
            say(player, Component.translatable("message.railworks.carriage.status", Component.translatable(braking() ? "railworks.bogie.brake_on" : "railworks.bogie.brake_off"),
                    String.format(java.util.Locale.ROOT, "%.1f", Math.abs(u)), seated()));
            return;
        }
        int i = aimed(player, j -> owes(st, j), false);
        if (i < 0) for (int j : order) if (owes(st, j)) {
            i = j;
            break;
        }
        if (i >= 0) {
            Model m = CarriageLayout.modelAt(st, i);
            int w = CarriageLayout.work(st, i);
            if (m == null) return;
            if (m.part().fix().equals(CarriageLayout.RIVETS)) {
                if (!takeRivet(player)) {
                    play(level, SoundEvents.ANVIL_LAND, 0.2f, 1.8f);
                    say(player, Component.translatable("message.railworks.carriage.need_rivets", new ItemStack(m.part().asItem()).getHoverName()).withStyle(ChatFormatting.YELLOW));
                    return;
                }
                setSlot(i, CarriageLayout.code(CarriageLayout.model(st, i), w + 1));
                play(level, SoundEvents.ANVIL_LAND, 0.3f, 1.7f);
                play(level, SoundEvents.CHAIN_HIT, 0.5f, 1.3f);
                say(player, Component.translatable("message.railworks.carriage.rivet", new ItemStack(m.part().asItem()).getHoverName(), w + 1, m.part().rivets(), CarriageLayout.rivetsOwed(slots())));
            } else {
                setSlot(i, CarriageLayout.code(CarriageLayout.model(st, i), 1));
                play(level, SoundEvents.WOOD_HIT, 0.9f, 0.8f);
                play(level, SoundEvents.ANVIL_LAND, 0.15f, 1.4f);
                say(player, Component.translatable("message.railworks.carriage.fixed", new ItemStack(m.part().asItem()).getHoverName()));
            }
            return;
        }
        if (CarriageLayout.finished(st)) {
            entityData.set(DATA_STAGE, 1);
            u = 0;
            play(level, SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
            play(level, SoundEvents.CHAIN_PLACE, 1.0f, 0.5f);
            play(level, SoundEvents.IRON_DOOR_CLOSE, 0.8f, 0.6f);
            say(player, Component.translatable("message.railworks.carriage.done").withStyle(ChatFormatting.GREEN));
            return;
        }
        play(level, SoundEvents.ANVIL_LAND, 0.25f, 1.8f);
        ItemStack off = player.getOffhandItem();
        say(player, CarriageLayout.isPart(off.getItem()) && !CarriageLayout.fitsFor(st, off.getItem()).isEmpty()
                ? Component.translatable("message.railworks.carriage.hammer_hand", off.getHoverName()) : readout(st));
    }

    private static boolean takeRivet(Player p) {
        if (p.getAbilities().instabuild) return true;
        Inventory inv = p.getInventory();
        for (int k = 0; k < inv.getContainerSize(); k++) {
            ItemStack x = inv.getItem(k);
            if (x.is(ModContent.RIVETS)) {
                x.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Why a part with free places cannot go on yet: the stage to finish first, then what that stage still wants. */
    private Component notYet(String st, net.minecraft.world.item.Item item) {
        for (Slot s : CarriageLayout.SLOTS) {
            if (s.model(item) == null || CarriageLayout.fitted(st, s.i())) continue;
            CarriageLayout.Phase p = CarriageLayout.phase(s.phase());
            List<String> needs = new ArrayList<>(p == null ? List.of() : p.needs());
            needs.addAll(s.needs());
            for (String n : needs) if (!CarriageLayout.complete(st, n))
                return Component.translatable("message.railworks.carriage.not_yet", new ItemStack(item).getHoverName(), Component.translatable("railworks.carriage.phase." + n), readout(st));
        }
        return Component.translatable("message.railworks.carriage.next_to", new ItemStack(item).getHoverName());
    }

    /** What the hammer still waits on: the parts that can go on now, then rivets and blows owed. */
    private Component readout(String st) {
        List<Component> bits = new ArrayList<>();
        Map<net.minecraft.world.item.Item, Integer> now = new java.util.LinkedHashMap<>();
        for (Slot s : CarriageLayout.SLOTS) {
            CarriageLayout.Phase p = CarriageLayout.phase(s.phase());
            if (s.required() && p != null && p.required() && CarriageLayout.canFit(st, s.i())) now.merge(s.models().getFirst().part().asItem(), 1, Integer::sum);
        }
        // a chain of floor or roof sections shows only its open ends, so count the whole run still missing
        for (Map.Entry<net.minecraft.world.item.Item, Integer> e : now.entrySet()) {
            int n = 0;
            for (Slot s : CarriageLayout.SLOTS) {
                CarriageLayout.Phase p = CarriageLayout.phase(s.phase());
                if (s.required() && p != null && p.required() && !CarriageLayout.fitted(st, s.i()) && s.models().getFirst().part().asItem() == e.getKey()
                        && (s.chain() || CarriageLayout.canFit(st, s.i()))) n++;
            }
            bits.add(Component.literal(n + " x ").append(new ItemStack(e.getKey()).getHoverName()));
        }
        int r = CarriageLayout.rivetsOwed(st), b = CarriageLayout.blowsOwed(st);
        if (r > 0) bits.add(Component.translatable("railworks.carriage.rivets", r));
        if (b > 0) bits.add(Component.translatable("railworks.carriage.blows", b));
        if (bits.isEmpty()) return Component.translatable("message.railworks.carriage.ready");
        MutableComponent list = Component.empty();
        for (int i = 0; i < bits.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(bits.get(i));
        }
        return Component.translatable("message.railworks.carriage.needs", list);
    }

    private void sneakHammer(ServerLevel level, Player player, Vec3 loc) {
        if (done()) {
            String st = slots();
            int c = aimed(player, j -> CarriageLayout.SLOTS.get(j).group().equals("coupling") && CarriageLayout.fitted(st, j), true);
            if (c < 0 && Math.abs(local(loc, false).x) > eyeReach() - 1.6) c = CarriageLayout.SLOTS.indexOf(coupling(endAt(loc)));
            if (c >= 0 && coupledAt(CarriageLayout.SLOTS.get(c).end())) {
                uncoupleEnd(level, player, CarriageLayout.SLOTS.get(c).end());
                return;
            }
            if (hammerBar(level, player, loc)) return;
            if (!getPassengers().isEmpty()) {
                say(player, Component.translatable("message.railworks.carriage.occupied"));
                return;
            }
            uncoupleAll(level, player);
            entityData.set(DATA_STAGE, 0);
            entityData.set(DATA_DOORS, 0);
            u = 0;
            for (int e = -1; e <= 1; e += 2) {
                Bogie b = bogie(e);
                if (b != null) b.u = 0;
            }
        }
        takeLast(level, player);
    }

    private static @Nullable Slot coupling(int end) {
        for (Slot s : CarriageLayout.SLOTS) if (s.group().equals("coupling") && s.end() == end) return s;
        return null;
    }

    private void uncoupleEnd(ServerLevel level, Player player, int end) {
        if (towed() && !mounted() && towEnd() == end) {
            uncouple(level, player);
            return;
        }
        for (RailVehicle f : followers()) if (!f.mounted() && f.leaderEnd() == end) f.uncouple(level, player);
    }

    /** The screw coupling at one end, hammered home on the carriage standing buffer to buffer there. */
    private void screw(ServerLevel level, Player player, int e) {
        if (coupledAt(e)) {
            say(player, Component.translatable("message.railworks.tow.taken"));
            return;
        }
        TrackRunner.Contact c = TrackRunner.ahead(level, this, e * f(), 0.6);
        if (c == null || !(c.other() instanceof Carriage o) || c.gap() > 0.5) {
            say(player, Component.translatable("message.railworks.carriage.no_neighbour"));
            return;
        }
        if (!o.done()) {
            say(player, Component.translatable("message.railworks.tow.unfinished"));
            return;
        }
        Vec3 my = eye(e);
        int eo = o.eye(1).distanceToSqr(my) < o.eye(-1).distanceToSqr(my) ? 1 : -1;
        if (o.coupledAt(eo)) {
            say(player, Component.translatable("message.railworks.tow.taken"));
            return;
        }
        String why = Coupling.check(level, this, e, o, eo, 0.6, MAX_VEHICLES * 3);
        if (why != null) {
            say(player, Component.translatable("message.railworks.tow." + why).withStyle(ChatFormatting.YELLOW));
            return;
        }
        boolean oLeads = o.consist().stream().anyMatch(m -> m.v().isTrolley());
        RailVehicle leader = oLeads ? o : this, follower = oLeads ? this : o;
        int le = oLeads ? eo : e, fe = oLeads ? e : eo;
        reroot(follower);
        follower.setTow(leader, -le * fe, fe, SCREW);
        Vec3 at = my.add(o.eye(eo)).scale(0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.2f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 0.7f);
        say(player, Component.translatable("message.railworks.carriage.coupled").withStyle(ChatFormatting.GREEN));
    }

    private void emptyHand(ServerLevel level, Player player, Vec3 loc) {
        String st = slots();
        if (!done()) {
            say(player, readout(st));
            return;
        }
        if (player.getVehicle() == this) return;
        // the end face is for pushing: looking along the body must not reach a bench or door inside
        if (Math.abs(local(player.position(), true).x) > eyeReach()) {
            pushAlong(player);
            return;
        }
        int i = aimed(player, j -> {
            Slot s = CarriageLayout.SLOTS.get(j);
            return (s.door() >= 0 || seats(st, j)) && CarriageLayout.secure(st, j);
        }, true);
        // an open doorway is looked through: a bench behind it wins, and the door shuts from above the benches
        if (i >= 0 && CarriageLayout.SLOTS.get(i).door() >= 0 && (doors() >> CarriageLayout.SLOTS.get(i).door() & 1) != 0) {
            int b = aimed(player, j -> seats(st, j) && CarriageLayout.secure(st, j), true);
            if (b >= 0) i = b;
        }
        Slot s = i < 0 ? null : CarriageLayout.SLOTS.get(i);
        if (s != null && s.door() >= 0) {
            boolean open = (doors() >> s.door() & 1) != 0;
            if (!open && Math.abs(u) >= DOOR_TOP) {
                say(player, Component.translatable("message.railworks.carriage.door_moving"));
                return;
            }
            entityData.set(DATA_DOORS, doors() ^ (1 << s.door()));
            Vec3 at = world(s.box().getCenter().x, 2, s.box().getCenter().z);
            level.playSound(null, at.x, at.y, at.z, open ? SoundEvents.WOODEN_DOOR_CLOSE : SoundEvents.WOODEN_DOOR_OPEN, net.minecraft.sounds.SoundSource.NEUTRAL, 0.9f, 0.8f);
            return;
        }
        if (s != null) {
            if (doors() == 0) {
                say(player, Component.translatable("message.railworks.carriage.doors_shut"));
                return;
            }
            if (Math.abs(u) >= DOOR_TOP) {
                say(player, Component.translatable("message.railworks.carriage.door_moving"));
                return;
            }
            int seat = freeSeat(i);
            if (seat < 0) {
                say(player, Component.translatable("message.railworks.carriage.bench_full"));
                return;
            }
            pending.put(player.getUUID(), seat);
            if (!player.startRiding(this)) pending.remove(player.getUUID());
            return;
        }
        if (Math.abs(local(loc, false).x) > eyeReach() - 1.6) {
            pushAlong(player);
            return;
        }
        say(player, Component.translatable("message.railworks.carriage.how_to_board"));
    }

    private void pushAlong(Player player) {
        Vec3 look = player.getLookAngle();
        double hf = frontHeading();
        push(player, look.x * Math.cos(hf) + look.z * Math.sin(hf) >= 0 ? 1 : -1);
    }

    // ---------------------------------------------------------------- seats

    private static boolean seats(String st, int i) {
        Model m = CarriageLayout.modelAt(st, i);
        return m != null && !m.seats().isEmpty();
    }

    /** Seats are numbered 4 to a seating slot: slot * 4 + the seat on that bench. */
    public static float @Nullable [] seat(String st, int id) {
        if (id < 0) return null;
        Model m = CarriageLayout.modelAt(st, id / 4);
        return m == null || id % 4 >= m.seats().size() ? null : m.seats().get(id % 4);
    }

    private Map<Integer, Integer> seatMap() {
        Map<Integer, Integer> m = new HashMap<>();
        String v = entityData.get(DATA_SEATS);
        if (v.isEmpty()) return m;
        for (String p : v.split(",")) {
            int k = p.indexOf(':');
            if (k > 0) m.put(Integer.parseInt(p.substring(0, k)), Integer.parseInt(p.substring(k + 1)));
        }
        return m;
    }

    private void setSeats(Map<Integer, Integer> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : m.entrySet()) {
            if (!b.isEmpty()) b.append(',');
            b.append(e.getKey()).append(':').append(e.getValue());
        }
        entityData.set(DATA_SEATS, b.toString());
    }

    private int leaving = -1;

    public int seatOf(Entity e) {
        Integer s = seatMap().get(e.getId());
        return s == null ? -1 : s;
    }

    private boolean taken(int seat) {
        return seatMap().containsValue(seat);
    }

    private int freeSeat(int bench) {
        Model m = CarriageLayout.modelAt(slots(), bench);
        if (m == null) return -1;
        for (int k = 0; k < m.seats().size(); k++) if (!taken(bench * 4 + k)) return bench * 4 + k;
        return -1;
    }

    private List<Integer> allSeats() {
        String st = slots();
        List<Integer> l = new ArrayList<>();
        for (Slot s : CarriageLayout.SLOTS) {
            if (!seats(st, s.i()) || !CarriageLayout.secure(st, s.i())) continue;
            Model m = CarriageLayout.modelAt(st, s.i());
            if (m != null) for (int k = 0; k < m.seats().size(); k++) l.add(s.i() * 4 + k);
        }
        return l;
    }

    private int seated() {
        return getPassengers().size();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return done() && getPassengers().size() < allSeats().size();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (level().isClientSide()) return;
        Integer want = pending.remove(passenger.getUUID());
        if (want == null) want = reseat.remove(passenger.getUUID());
        int seat = want != null && !taken(want) ? want : -1;
        if (seat < 0) for (int k : allSeats()) if (!taken(k)) {
            seat = k;
            break;
        }
        Map<Integer, Integer> m = seatMap();
        m.put(passenger.getId(), seat);
        setSeats(m);
        face(passenger, seat);
        if (passenger instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable("message.railworks.carriage.seated"));
    }

    private void face(Entity passenger, int seat) {
        float[] p = seat(slots(), seat);
        if (p == null) return;
        float yaw = getYRot() + (p[3] > 0 ? 0 : 180);
        passenger.forceSetRotation(yaw, false, 0, true);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        int seat = seatOf(passenger);
        leaving = seat;
        super.removePassenger(passenger);
        if (level().isClientSide()) return;
        Map<Integer, Integer> m = seatMap();
        m.remove(passenger.getId());
        setSeats(m);
        // nobody steps off faster than walking pace: back to the seat next tick
        if (passenger instanceof ServerPlayer sp && sp.isAlive() && !isRemoved() && Math.abs(u) > STEP_TOP && seat >= 0) {
            reseat.put(sp.getUUID(), seat);
            sp.sendOverlayMessage(Component.translatable("message.railworks.carriage.too_fast"));
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        float[] p = seat(slots(), seatOf(passenger));
        if (p == null) return new Vec3(0, 1.4, 0);
        double hf = frontHeading();
        double y = p[1] + p[0] * tanPitch();
        return new Vec3(p[0] * Math.cos(hf) - p[2] * Math.sin(hf), y, p[0] * Math.sin(hf) + p[2] * Math.cos(hf));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        super.positionRider(passenger, move);
        float d = Mth.wrapDegrees(getYRot() - yRotO);
        if (d != 0) {
            passenger.setYRot(passenger.getYRot() + d);
            passenger.yRotO += d;
            passenger.setYHeadRot(passenger.getYHeadRot() + d);
            if (passenger instanceof LivingEntity le) le.yBodyRot += d;
        }
    }

    /** Out through the nearest door, open ones first. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        String st = slots();
        // vanilla asks after the passenger is already off, so the seat it left is remembered
        int was = seatOf(passenger);
        float[] p = seat(st, was >= 0 ? was : leaving);
        double sx = p == null ? 0 : p[0], sz = p == null ? 0 : p[2];
        List<Slot> ds = new ArrayList<>();
        for (Slot s : CarriageLayout.SLOTS) if (s.door() >= 0 && s.at() != null && CarriageLayout.fitted(st, s.i())) ds.add(s);
        int open = doors();
        ds.sort((a, b) -> {
            int oa = (open >> a.door() & 1) != 0 ? 0 : 1, ob = (open >> b.door() & 1) != 0 ? 0 : 1;
            if (oa != ob) return oa - ob;
            return Double.compare(sq(a.at(), sx, sz), sq(b.at(), sx, sz));
        });
        for (Slot s : ds) {
            Vec3 at = world(s.at().x, s.at().y, s.at().z);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos bp = BlockPos.containing(at.x, at.y + dy, at.z);
                double floor = level().getBlockFloorHeight(bp);
                if (!DismountHelper.isBlockFloorValid(floor)) continue;
                Vec3 v = new Vec3(at.x, bp.getY() + floor, at.z);
                if (DismountHelper.canDismountTo(level(), v, passenger, Pose.STANDING)) return v;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    private static double sq(Vec3 a, double x, double z) {
        return (a.x - x) * (a.x - x) + (a.z - z) * (a.z - z);
    }

    /** Movement keys step a seated player along to the next free seat, or across the aisle. */
    private void seatKeys(ServerLevel level) {
        for (Entity e : getPassengers()) {
            if (!(e instanceof ServerPlayer sp)) continue;
            int wait = stepWait.getOrDefault(sp.getUUID(), 0);
            if (wait > 0) {
                stepWait.put(sp.getUUID(), wait - 1);
                continue;
            }
            Input in = sp.getLastClientInput();
            if (!in.forward() && !in.backward() && !in.left() && !in.right()) continue;
            int mine = seatOf(sp);
            float[] p = seat(slots(), mine);
            if (p == null) continue;
            Vec3 look = local(sp.getLookAngle(), false);
            double la = look.x, lw = look.z, n = Math.hypot(la, lw);
            if (n < 1e-3) continue;
            la /= n;
            lw /= n;
            // forward and back along the look, left and right across it; the body's z runs to the right
            double da = 0, dw = 0;
            if (in.forward()) {
                da += la;
                dw += lw;
            }
            if (in.backward()) {
                da -= la;
                dw -= lw;
            }
            if (in.right()) {
                da -= lw;
                dw += la;
            }
            if (in.left()) {
                da += lw;
                dw -= la;
            }
            boolean along = Math.abs(da) >= Math.abs(dw);
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int k : allSeats()) {
                if (k == mine || taken(k)) continue;
                float[] q = seat(slots(), k);
                if (q == null) continue;
                double ax = q[0] - p[0], az = q[2] - p[2];
                double d;
                if (along) {
                    if (ax * Math.signum(da) < 0.3) continue;
                    d = Math.abs(ax) + 0.3 * Math.abs(az);
                } else {
                    if (az * Math.signum(dw) < 0.3 || Math.abs(ax) > 1.6) continue;
                    d = Math.abs(az) + Math.abs(ax);
                }
                if (d < bd) {
                    bd = d;
                    best = k;
                }
            }
            stepWait.put(sp.getUUID(), 6);
            if (best < 0) continue;
            Map<Integer, Integer> m = seatMap();
            m.put(sp.getId(), best);
            setSeats(m);
            face(sp, best);
            positionRider(sp);
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WOOL_STEP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- motion

    @Override
    protected void serverTick(ServerLevel level) {
        Bogie a = bogie(-1), b = bogie(1);
        if (a == null || b == null) {
            if (entitiesLoadedAround(level, 24) && ++lost > 100) {
                for (int i : order) {
                    Model m = CarriageLayout.modelAt(slots(), i);
                    if (m != null) spawnAtLocation(level, new ItemStack(m.part().asItem()));
                }
                order.clear();
                dissolve(level);
            }
            return;
        }
        lost = 0;
        if (entityData.get(DATA_BOGIE_A) != a.getId() + 1) entityData.set(DATA_BOGIE_A, a.getId() + 1);
        if (entityData.get(DATA_BOGIE_B) != b.getId() + 1) entityData.set(DATA_BOGIE_B, b.getId() + 1);
        if (!a.mounted()) a.setTow(this, Math.cos(a.frontHeading() - frontHeading()) > 0 ? 1 : -1, 1, MOUNT);
        if (!b.mounted()) b.setTow(this, Math.cos(b.frontHeading() - frontHeading()) > 0 ? 1 : -1, -1, MOUNT);
        Rail r = rail(seg);
        if (r == null) return;
        if (!done()) {
            u = 0;
            afterPlace();
            sync(false);
            return;
        }
        for (Map.Entry<UUID, Integer> e : List.copyOf(reseat.entrySet())) {
            if (level.getEntity(e.getKey()) instanceof ServerPlayer sp && sp.isAlive() && sp.getVehicle() == null && sp.distanceTo(this) < 16) sp.startRiding(this, true, false);
            reseat.remove(e.getKey());
        }
        seatKeys(level);
        if (doors() != 0 && Math.abs(trainSpeed()) > DOOR_TOP) {
            entityData.set(DATA_DOORS, 0);
            play(level, SoundEvents.WOODEN_DOOR_CLOSE, 1.0f, 0.6f);
            play(level, SoundEvents.IRON_DOOR_CLOSE, 0.6f, 0.8f);
        }
        if (towTick(level)) return;
        Train t = train(level);
        if (t == null) return;
        List<Member> ms = t.members;
        double m = mass(ms);
        boolean pushed = anyPushed(ms);
        List<Entity> safe = new ArrayList<>();
        pushersOf(ms, safe);
        Entity driver = safe.isEmpty() ? null : safe.getFirst();
        for (Member x : ms) if (!x.v().isTrolley()) x.v().towedBrake(!pushed);
        double force = pushForce(ms, m) + gradeForce(ms, m);
        double drag = 0.08 + 0.004 * u * u + (pushed ? 0 : BRAKE);
        drive(level, t, force, drag, false, new Ride(driver, 0.5f, safe), false);
    }

    private double trainSpeed() {
        return root().u;
    }

    @Override
    protected void moved(ServerLevel level, Rail r, double ds, boolean changed, Ride ride) {
        super.moved(level, r, ds, changed, ride);
        double speed = Math.abs(ds) / DT;
        if (speed > 0.3 && --rumble <= 0) {
            play(level, SoundEvents.DEEPSLATE_BRICKS_STEP, 0.3f + (float) speed * 0.06f, 0.35f + (float) speed * 0.015f);
            play(level, SoundEvents.MINECART_INSIDE_UNDERWATER, 0.05f + (float) speed * 0.02f, 0.4f);
            rumble = Mth.clamp((int) (12 - speed * 1.5), 4, 12);
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String st = in.getStringOr("slots", CarriageLayout.empty());
        if (st.length() != CarriageLayout.SLOTS.size()) st = (st + CarriageLayout.empty()).substring(0, CarriageLayout.SLOTS.size());
        entityData.set(DATA_SLOTS, st);
        entityData.set(DATA_DOORS, in.getIntOr("doors", 0));
        order.clear();
        in.getIntArray("order").ifPresent(a -> {
            for (int i : a) if (i >= 0 && i < CarriageLayout.SLOTS.size()) order.add(i);
        });
        String a = in.getStringOr("bogie_a", ""), b = in.getStringOr("bogie_b", "");
        bogieA = a.isEmpty() ? null : UUID.fromString(a);
        bogieB = b.isEmpty() ? null : UUID.fromString(b);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("slots", slots());
        out.putInt("doors", doors());
        out.putIntArray("order", order.stream().mapToInt(Integer::intValue).toArray());
        if (bogieA != null) out.putString("bogie_a", bogieA.toString());
        if (bogieB != null) out.putString("bogie_b", bogieB.toString());
    }
}
