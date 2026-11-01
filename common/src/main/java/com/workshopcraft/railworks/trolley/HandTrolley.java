package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackActions;
import com.workshopcraft.railworks.track.TrackGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The platelayers' hand trolley. Two pumpers drive it, two more ride on the seat, and it can tow bogies. */
public class HandTrolley extends RailVehicle {
    public static final double LENGTH = 2.7, WIDTH = 2.1, HALF_WB = 0.8, WHEEL_R = 0.28, DECK = 0.64, SEAT = 0.98;
    static final double BRAKE = 7.5, STROKE = 3.5;
    static final double MASS = 1, RIDER_MASS = 0.4;
    static final ResourceKey<DamageType> RUN_DOWN = damage("trolley");
    public static final int FRAME = 0, PUMP = 3, DONE = 4;
    static final double[][] PLACES = {{0.95, DECK, 0}, {-0.95, DECK, 0}, {-0.2, SEAT, 0.72}, {0.3, SEAT, 0.72}};

    private static final EntityDataAccessor<Byte> DATA_PUMP = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_BOGIES = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_LOAD = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.FLOAT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Integer>[] DATA_PLACE = new EntityDataAccessor[4];

    static {
        for (int i = 0; i < 4; i++) DATA_PLACE[i] = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.INT);
    }

    /** Client hook, set by the client entry points. */
    public static Function<HandTrolley, @Nullable Input> clientInput = t -> null;

    private final ToolBox box = new ToolBox();

    // shared animation state
    public double beam, beamO;

    // server only
    private boolean squealing;
    private int hudHold, squealCooldown, brakeCooldown;
    private boolean wasRidden;
    private final Report report = new Report();

    public HandTrolley(EntityType<? extends HandTrolley> type, Level level) {
        super(type, level);
    }
    private final class ToolBox extends SimpleContainer {
        ToolBox() {
            super(9);
        }

        @Override
        public boolean stillValid(Player player) {
            return HandTrolley.this.isAlive() && player.distanceToSqr(HandTrolley.this) < 64;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_PUMP, (byte) 0).define(DATA_BOGIES, (byte) 0).define(DATA_LOAD, 1f);
        for (EntityDataAccessor<Integer> p : DATA_PLACE) b.define(p, 0);
    }

    @Override
    public int doneStage() {
        return DONE;
    }

    @Override
    public boolean isTrolley() {
        return true;
    }

    @Override
    protected double ownMass() {
        return MASS + RIDER_MASS * getPassengers().size();
    }

    @Override
    public double trackReach() {
        return LENGTH / 2;
    }

    @Override
    protected double wheelRadius() {
        return WHEEL_R;
    }

    @Override
    protected double halfWidth() {
        return 1.0;
    }

    @Override
    protected double outlineHeight() {
        return 1.75;
    }

    @Override
    public double eyeReach() {
        return 1.4;
    }

    @Override
    public double eyeHeight() {
        return 0.44;
    }

    @Override
    protected ResourceKey<DamageType> runDown() {
        return RUN_DOWN;
    }

    public int pumping() {
        return entityData.get(DATA_PUMP);
    }

    // ---------------------------------------------------------------- placing and taking off

    /** Puts a frame (or a whole trolley) on the nearest live track, facing the way the player looks. */
    public static boolean place(ServerLevel level, Player player, Vec3 at, ItemStack stack, boolean complete) {
        HandTrolley t = ModContent.HAND_TROLLEY_ENTITY.create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (t != null) {
            t.entityData.set(DATA_STAGE, complete ? DONE : FRAME);
            ItemContainerContents c = stack.get(DataComponents.CONTAINER);
            if (c != null) c.copyInto(t.box.getItems());
        }
        return put(level, player, at, t);
    }

    @Override
    public void takeOff(ServerLevel level, Player player) {
        int st = stage();
        ItemStack out = new ItemStack(st == DONE ? ModContent.HAND_TROLLEY : ModContent.TROLLEY_FRAME);
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack i : box.getItems()) items.add(i.copy());
        if (items.stream().anyMatch(i -> !i.isEmpty())) out.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        if (!player.getInventory().add(out)) spawnAtLocation(level, out);
        if (st < DONE) {
            for (ItemStack part : partsUpTo(st)) if (!player.getInventory().add(part)) spawnAtLocation(level, part);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.4f, 1.4f);
        box.clearContent();
        discard();
    }

    private static List<ItemStack> partsUpTo(int st) {
        List<ItemStack> l = new ArrayList<>();
        if (st >= 1) l.add(new ItemStack(ModContent.WHEELSET, Math.min(st, 2)));
        if (st >= PUMP) l.add(new ItemStack(ModContent.TROLLEY_PUMP));
        return l;
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        int st = stage();
        boolean riding = player.getVehicle() == this;
        if (held.is(ModContent.TOW_BAR) && !riding) return Coupling.click(this, player, location, held);
        if (player.isSecondaryUseActive() && held.is(ModContent.HAMMER)) {
            if (level() instanceof ServerLevel sl && hammerBar(sl, player, location)) return InteractionResult.SUCCESS;
            if (!getPassengers().isEmpty()) return InteractionResult.FAIL;
            if (level() instanceof ServerLevel sl) {
                uncoupleAll(sl, player);
                takeOff(sl, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (st < DONE && fits(held, st)) {
            if (level() instanceof ServerLevel sl) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                entityData.set(DATA_STAGE, st + 1);
                sl.playSound(null, getX(), getY(), getZ(), st < 2 ? SoundEvents.ANVIL_PLACE : SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.5f, 1.2f);
                say(player, Component.translatable("message.railworks.trolley.stage" + (st + 1)));
            }
            return InteractionResult.SUCCESS;
        }
        if (riding) return InteractionResult.PASS;
        if (player.isSecondaryUseActive() || onToolBox(location) || st < DONE) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (st < DONE && !player.isSecondaryUseActive() && !onToolBox(location)) {
                if (level() instanceof ServerLevel) say(player, Component.translatable("message.railworks.trolley.needs" + st));
                return InteractionResult.SUCCESS;
            }
            if (level() instanceof ServerLevel sl) {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, box, 1),
                        Component.translatable("container.railworks.tool_box")));
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BARREL_OPEN, SoundSource.NEUTRAL, 0.5f, 1.3f);
            }
            return InteractionResult.SUCCESS;
        }
        if (level() instanceof ServerLevel) {
            if (freePlace() < 0) {
                say(player, Component.translatable("message.railworks.trolley.full"));
                return InteractionResult.FAIL;
            }
            if (!player.startRiding(this)) return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean fits(ItemStack held, int st) {
        return st < 2 ? held.is(ModContent.WHEELSET) : st == 2 ? held.is(ModContent.TROLLEY_PUMP) : held.is(ModContent.TROLLEY_BRAKE);
    }

    private boolean onToolBox(Vec3 loc) {
        double hf = frontHeading();
        double a = loc.x * Math.cos(hf) + loc.z * Math.sin(hf);
        double w = -loc.x * Math.sin(hf) + loc.z * Math.cos(hf);
        return Math.abs(a - 0.05) < 0.6 && w > 0.4 && w < 1.1 && loc.y > DECK - 0.05;
    }

    // ---------------------------------------------------------------- places

    private int freePlace() {
        for (int i = 0; i < 4; i++) if (entityData.get(DATA_PLACE[i]) == 0) return i;
        return -1;
    }

    public int placeOf(Entity e) {
        for (int i = 0; i < 4; i++) if (entityData.get(DATA_PLACE[i]) == e.getId() + 1) return i;
        int i = getPassengers().indexOf(e);
        return Math.max(0, Math.min(3, i));
    }

    public boolean isPumper(Entity e) {
        return e.getVehicle() == this && placeOf(e) < 2;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return stage() == DONE && getPassengers().size() < 4 && freePlace() >= 0;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide()) {
            int p = freePlace();
            if (p >= 0) entityData.set(DATA_PLACE[p], passenger.getId() + 1);
            if (passenger instanceof ServerPlayer sp) {
                if (!wasRidden) report.reset(this);
                report.start(sp);
                sp.sendOverlayMessage(Component.translatable(p < 2 ? "message.railworks.trolley.pumper" : "message.railworks.trolley.seated"));
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide()) {
            for (EntityDataAccessor<Integer> p : DATA_PLACE) if (entityData.get(p) == passenger.getId() + 1) entityData.set(p, 0);
            if (passenger instanceof ServerPlayer sp) report.send(sp, this);
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        double[] p = PLACES[placeOf(passenger)];
        double hf = frontHeading();
        double y = p[1] + p[0] * Math.tan(Math.toRadians(-getXRot()));
        if (placeOf(passenger) < 2) y += passenger.getVehicleAttachmentPoint(this).y;
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

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double hf = frontHeading();
        double[] p = PLACES[placeOf(passenger)];
        for (int side : new int[]{p[2] > 0 ? 1 : -1, p[2] > 0 ? -1 : 1}) {
            double w = side * (WIDTH / 2 + 0.7);
            double x = getX() + p[0] * Math.cos(hf) - w * Math.sin(hf), z = getZ() + p[0] * Math.sin(hf) + w * Math.cos(hf);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos bp = BlockPos.containing(x, getY() + dy, z);
                double floor = level().getBlockFloorHeight(bp);
                if (!DismountHelper.isBlockFloorValid(floor)) continue;
                Vec3 v = new Vec3(x, bp.getY() + floor, z);
                if (DismountHelper.canDismountTo(level(), v, passenger, Pose.STANDING)) return v;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---------------------------------------------------------------- motion

    @Override
    protected void tell(Component c, boolean echo) {
        if (echo) hudHold = tickCount + 50;
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer sp) sp.sendOverlayMessage(c);
        if (echo && ECHO) LOG.info("[trolley] {}", c.getString());
    }

    @Override
    protected void pointsPassed(TrackGraph.Node n, Component how) {
        report.points(n, how);
    }

    @Override
    protected void entered(ServerLevel level, TrackGraph.Segment sg) {
        report.enter(level, sg);
    }

    /** Signed pump force along +s, from the pumpers' inputs. */
    private double thrust(List<Input> inputs, List<Vec3> looks, double us, byte[] pumpOut, int bogies, double load) {
        int ahead = 0, back = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Input in = inputs.get(i);
            if (in.forward() == in.backward()) continue;
            Vec3 look = looks.get(i);
            double h = frontHeading();
            int facing = look.x * Math.cos(h) + look.z * Math.sin(h) >= 0 ? 1 : -1;
            if ((in.forward() ? facing : -facing) > 0) ahead++;
            else back++;
        }
        int n = Math.abs(ahead - back), d = Integer.signum(ahead - back);
        pumpOut[0] = (byte) (d * n);
        if (n == 0) return 0;
        int ds = d * f();
        return ds * pumpForce(us * ds, n == 1, bogies, load);
    }

    // strong push from rest, a flat middle that sets the 1 in 20 climb, then a fall to the top speed;
    // towing, the curve slows to a lower top and the push is shared over the extra mass
    static double pumpForce(double v, boolean one, int bogies, double load) {
        double[] tops = one ? new double[]{6, 4.5, 3.5} : new double[]{7.5, 6, 5};
        double top = tops[Math.min(bogies, 2)], sc = top / tops[0];
        double f0 = (one ? 1.5 : 2.2) / load, flat = (one ? 0.61 : 0.67) / load, v1 = (one ? 3 : 4) * sc, v2 = (one ? 5.3 : 6.9) * sc;
        if (v < 0) return f0;
        if (v < v1) return f0 - (f0 - flat) * v / v1;
        if (v < v2) return flat;
        double end = 0.08 + 0.004 * top * top;
        return Math.max(0, flat - (v - v2) * (flat - end) / (top - v2));
    }

    @Override
    protected void animate(double ds) {
        super.animate(ds);
        beamO = beam;
        beam += ds * f() * Mth.TWO_PI / STROKE;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Rail r = rail(seg);
        if (r == null) {
            uncoupleAll(level, null);
            ItemStack out = new ItemStack(stage() == DONE ? ModContent.HAND_TROLLEY : ModContent.TROLLEY_FRAME);
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack i : box.getItems()) items.add(i.copy());
            out.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            ejectPassengers();
            spawnAtLocation(level, out);
            if (stage() < DONE) for (ItemStack part : partsUpTo(stage())) spawnAtLocation(level, part);
            discard();
            return;
        }
        List<Input> inputs = new ArrayList<>();
        List<Vec3> looks = new ArrayList<>();
        boolean jump = false;
        Entity driver = null;
        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp && placeOf(sp) < 2) {
                Input in = sp.getLastClientInput();
                inputs.add(in);
                looks.add(sp.getLookAngle());
                jump |= in.jump();
                driver = sp;
            }
        }
        boolean ridden = !getPassengers().isEmpty();
        boolean brake = !ridden || jump;
        if (stage() < DONE) {
            u = 0;
            placeOn(r);
            sync(false);
            return;
        }
        if (towTick(level)) return;
        List<Member> ms = consist();
        double m = mass(ms), load = m / ownMass();
        int bogies = 0;
        List<Entity> safe = new ArrayList<>();
        for (Member x : ms) {
            if (x.v() instanceof Bogie b) {
                bogies++;
                b.towedBrake(false);
            }
            if (x.v().pushed()) safe.add(x.v().pusher);
        }
        byte[] pump = new byte[1];
        double force = thrust(inputs, looks, u, pump, bogies, load) + pushForce(ms, m) + gradeForce(ms, m);
        double drag = 0.08 + 0.004 * u * u + (brake ? BRAKE / load : 0);
        boolean changed = entityData.get(DATA_PUMP) != pump[0] || entityData.get(DATA_BRAKE) != (brake || endBraking) || entityData.get(DATA_BOGIES) != bogies;
        entityData.set(DATA_BOGIES, (byte) bogies);
        entityData.set(DATA_LOAD, (float) load);
        double ds = drive(level, ms, force, drag, ridden, new Ride(driver, 1, safe), changed);
        Rail now = rail(seg);
        if (now == null) return;
        report.track(now, s, Math.abs(ds));
        sounds(level, now, ds, brake || endBraking, pump[0], ridden);
        entityData.set(DATA_PUMP, pump[0]);
        entityData.set(DATA_BRAKE, brake || endBraking);
        if (ridden && tickCount % 20 == 0) level.getChunkSource().addTicketWithRadius(ModContent.TROLLEY_TICKET, ChunkPos.containing(blockPosition()), 3);
        if (ridden && tickCount % 4 == 0 && tickCount > hudHold) hud(now);
        if (!ridden && wasRidden) report.reset(this);
        wasRidden = ridden;
    }

    private void sounds(ServerLevel level, Rail r, double ds, boolean brake, int pump, boolean ridden) {
        double speed = Math.abs(ds) / DT;
        if (pump != 0) {
            double a = Math.sin(beamO), b = Math.sin(beam);
            if (a < 0.95 && b >= 0.95) play(level, SoundEvents.WOODEN_TRAPDOOR_OPEN, 0.25f, 0.6f);
            if (a > -0.95 && b <= -0.95) play(level, SoundEvents.CHAIN_HIT, 0.4f, 0.8f);
        }
        if (brake && ridden && speed > 0.5) {
            if (!squealing) play(level, SoundEvents.GRINDSTONE_USE, 0.35f, 1.6f);
            else if (--brakeCooldown <= 0) play(level, SoundEvents.GRINDSTONE_USE, 0.15f, 1.4f);
            if (brakeCooldown <= 0 || !squealing) brakeCooldown = 10;
            squealing = true;
        } else squealing = false;
        double k = Math.abs(r.path().curvatureAt(Mth.clamp(s, 0, r.length())));
        if (k > 1 / 60.0 && speed > 2.5 && --squealCooldown <= 0) {
            play(level, SoundEvents.GRINDSTONE_USE, 0.12f, 1.9f);
            squealCooldown = 8;
        }
    }

    private void hud(Rail r) {
        double g = r.path().gradeAt(Mth.clamp(s, 0, r.length())) * Math.signum(u == 0 ? f() : u);
        MutableComponent grade = TrackActions.gradeText(g);
        if (Math.abs(g) >= 1e-4) grade.append(Component.translatable(g > 0 ? "railworks.trolley.up" : "railworks.trolley.down"));
        MutableComponent c = Component.translatable("railworks.trolley.hud", String.format(java.util.Locale.ROOT, "%.1f", Math.abs(u)), grade);
        int bogies = entityData.get(DATA_BOGIES);
        if (bogies > 0) c.append(Component.translatable("railworks.trolley.towing", bogies));
        if (braking()) c.append(Component.translatable("railworks.trolley.brake_on"));
        tell(c, false);
    }

    // ---------------------------------------------------------------- client

    @Override
    protected double predict(Rail r) {
        Input in = clientInput.apply(this);
        if (in == null || stage() != DONE) return Double.NaN;
        Entity me = null;
        for (Entity e : getPassengers()) if (isPumper(e)) me = e;
        List<Input> inputs = List.of(in);
        List<Vec3> looks = List.of(me != null ? me.getLookAngle() : Vec3.ZERO);
        byte[] pump = new byte[1];
        double load = entityData.get(DATA_LOAD);
        double force = thrust(inputs, looks, u, pump, entityData.get(DATA_BOGIES), load) - G * r.path().gradeAt(Mth.clamp(s, 0, r.length()));
        double drag = 0.08 + 0.004 * u * u + (in.jump() || braking() ? BRAKE / load : 0);
        double nu = step(u, force, drag);
        double ds = (u + nu) / 2 * DT;
        u = nu;
        if (snapU() == 0 && Math.abs(u) < 0.3 && nearSnap()) {
            ds = 0;
            u = 0;
        }
        return ds;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        NonNullList<ItemStack> items = box.getItems();
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        ContainerHelper.saveAllItems(out, box.getItems());
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) Containers.dropContents(level(), blockPosition(), box);
        super.remove(reason);
    }

    // ---------------------------------------------------------------- run report

    private static final class Report {
        double dist, steepest, tightest = Double.MAX_VALUE;
        final List<Component> points = new ArrayList<>();
        final Set<BlockPos> clearance = new LinkedHashSet<>();
        final Set<Long> seen = new java.util.HashSet<>();
        final java.util.Map<java.util.UUID, Integer> startPoints = new java.util.HashMap<>();
        final java.util.Map<java.util.UUID, Double> startDist = new java.util.HashMap<>();

        void reset(HandTrolley t) {
            dist = 0;
            steepest = 0;
            tightest = Double.MAX_VALUE;
            points.clear();
            clearance.clear();
            seen.clear();
            startPoints.clear();
            startDist.clear();
            if (t.level() instanceof ServerLevel sl) {
                TrackGraph.Segment sg = TrackGraph.get(sl).segment(t.seg);
                if (sg != null) enter(sl, sg);
            }
        }

        void start(ServerPlayer p) {
            startPoints.put(p.getUUID(), points.size());
            startDist.put(p.getUUID(), dist);
        }

        void enter(ServerLevel level, TrackGraph.Segment sg) {
            if (seen.add(sg.id)) clearance.addAll(TrackGraph.get(level).clearance(level, sg));
        }

        void points(TrackGraph.Node n, Component how) {
            points.add(Component.translatable("railworks.report.point", n.pos.getX(), n.pos.getY(), n.pos.getZ(), how));
        }

        void track(Rail r, double s, double ds) {
            if (ds <= 0) return;
            dist += ds;
            double ss = Mth.clamp(s, 0, r.length());
            steepest = Math.max(steepest, Math.abs(r.path().gradeAt(ss)));
            double k = Math.abs(r.path().curvatureAt(ss));
            if (k > 1e-5) tightest = Math.min(tightest, 1 / k);
        }

        void send(ServerPlayer p, HandTrolley t) {
            double d = dist - startDist.getOrDefault(p.getUUID(), 0.0);
            int from = Math.min(points.size(), startPoints.getOrDefault(p.getUUID(), 0));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("railworks.report.head", String.format(java.util.Locale.ROOT, "%.1f", d),
                    TrackActions.gradeText(steepest), TrackActions.radiusText(tightest)).withStyle(ChatFormatting.AQUA));
            if (from == points.size()) lines.add(Component.translatable("railworks.report.no_points").withStyle(ChatFormatting.GRAY));
            else for (Component c : points.subList(from, points.size())) lines.add(Component.translatable("railworks.report.points", c).withStyle(ChatFormatting.GRAY));
            if (clearance.isEmpty()) lines.add(Component.translatable("railworks.report.clear").withStyle(ChatFormatting.GREEN));
            else {
                BlockPos f = clearance.iterator().next();
                lines.add(Component.translatable("railworks.report.hits", clearance.size(), f.getX(), f.getY(), f.getZ()).withStyle(ChatFormatting.GOLD));
            }
            for (Component c : lines) {
                p.sendSystemMessage(c);
                if (ECHO) LOG.info("[report] {}", c.getString());
            }
            startPoints.remove(p.getUUID());
            startDist.remove(p.getUUID());
        }
    }
}
