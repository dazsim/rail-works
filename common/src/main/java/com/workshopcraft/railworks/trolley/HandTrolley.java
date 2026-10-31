package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackActions;
import com.workshopcraft.railworks.track.TrackGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The platelayers' hand trolley. It stores a section, a distance along its path and a speed along it,
 * so it always sits on the exact line the ties were laid to. The server owns the state; clients run the
 * same motion on their own copy of the track between updates.
 */
public class HandTrolley extends Entity {
    public static final double LENGTH = 2.7, WIDTH = 2.1, HALF_WB = 0.8, WHEEL_R = 0.28, DECK = 0.64, SEAT = 0.98;
    static final double DT = 0.05, G = 9.8, BRAKE = 4, CAP = 10, BUFFER_GAP = 0.59 + LENGTH / 2, END_GAP = 1.3;
    static final double STROKE = 3.5;
    public static final int FRAME = 0, PUMP = 3, DONE = 4;
    static final double[][] PLACES = {{0.95, DECK, 0}, {-0.95, DECK, 0}, {-0.2, SEAT, 0.72}, {0.3, SEAT, 0.72}};

    private static final EntityDataAccessor<Long> DATA_SEG = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_S = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_U = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_FRONT = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_PUMP = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_NEXT_A = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_NEXT_B = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.LONG);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Integer>[] DATA_PLACE = new EntityDataAccessor[4];

    static {
        for (int i = 0; i < 4; i++) DATA_PLACE[i] = SynchedEntityData.defineId(HandTrolley.class, EntityDataSerializers.INT);
    }

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("railworks");
    static final boolean ECHO = System.getenv("RAILWORKS_ECHO") != null;

    /** Client hooks, set by the client entry points. */
    public static LongFunction<@Nullable Rail> clientTrack = id -> null;
    public static Function<HandTrolley, @Nullable Input> clientInput = t -> null;

    private long seg;
    private double s, u;
    private boolean front = true;
    private final ToolBox box = new ToolBox();

    // shared animation state
    public double wheel, wheelO, beam, beamO;
    private double clack;

    // server only
    private int lastSync, hudHold, stopCooldown, squealCooldown, brakeCooldown;
    private boolean wasRidden, endBraking;
    private final Report report = new Report();

    // client only
    private boolean fresh = true, pending;
    private long snapSeg;
    private double snapS, snapU;
    private int snapAge, mismatch;
    private final InterpolationHandler ignore = new InterpolationHandler(this) {
        @Override
        public void interpolateTo(Vec3 position, float yRot, float xRot) {}
    };

    public HandTrolley(EntityType<? extends HandTrolley> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
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
        b.define(DATA_SEG, 0L).define(DATA_S, 0f).define(DATA_U, 0f).define(DATA_FRONT, true).define(DATA_STAGE, 0)
                .define(DATA_BRAKE, true).define(DATA_PUMP, (byte) 0).define(DATA_NEXT_A, -1L).define(DATA_NEXT_B, -1L);
        for (EntityDataAccessor<Integer> p : DATA_PLACE) b.define(p, 0);
    }

    public int stage() {
        return entityData.get(DATA_STAGE);
    }

    public boolean braking() {
        return entityData.get(DATA_BRAKE);
    }

    public int pumping() {
        return entityData.get(DATA_PUMP);
    }

    // ---------------------------------------------------------------- placing and taking off

    /** Puts a frame (or a whole trolley) on the nearest live track, facing the way the player looks. */
    public static boolean place(ServerLevel level, Player player, Vec3 at, ItemStack stack, boolean complete) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Spot spot = g.nearestSegment(at.x, at.y, at.z, 3);
        if (spot == null) {
            say(player, Component.translatable("message.railworks.trolley.no_track"));
            return false;
        }
        TrackGraph.Segment sg = spot.seg();
        if (!sg.live) {
            say(player, Component.translatable("message.railworks.trolley.not_live"));
            return false;
        }
        double best = 0, bd = Double.MAX_VALUE;
        for (double t = 0; t <= sg.path.length; t += 0.05) {
            double[] p = sg.path.pose(t);
            double d = (p[0] - at.x) * (p[0] - at.x) + (p[1] - at.z) * (p[1] - at.z);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        HandTrolley t = ModContent.HAND_TROLLEY_ENTITY.create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (t == null) return false;
        double h = sg.path.pose(best)[2];
        Vec3 look = player.getLookAngle();
        t.seg = sg.id;
        t.s = best;
        t.front = look.x * Math.cos(h) + look.z * Math.sin(h) >= 0;
        t.entityData.set(DATA_STAGE, complete ? DONE : FRAME);
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        if (c != null) c.copyInto(t.box.getItems());
        Rail r = rail(sg);
        t.placeOn(r);
        if (!level.getEntitiesOfClass(HandTrolley.class, t.getBoundingBox().inflate(0.5)).isEmpty()) {
            say(player, Component.translatable("message.railworks.trolley.occupied"));
            return false;
        }
        BlockPos hit = t.outlineHit(r, best, 0);
        if (hit != null) {
            say(player, Component.translatable("message.railworks.trolley.no_room", hit.getX(), hit.getY(), hit.getZ()));
            return false;
        }
        t.sync(true);
        level.addFreshEntity(t);
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1, 0.8f);
        return true;
    }

    private void takeOff(ServerLevel level, Player player) {
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
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        int st = stage();
        boolean riding = player.getVehicle() == this;
        if (player.isSecondaryUseActive() && held.is(ModContent.HAMMER)) {
            if (!getPassengers().isEmpty()) return InteractionResult.FAIL;
            if (level() instanceof ServerLevel sl) takeOff(sl, player);
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

    @Override
    public @Nullable InterpolationHandler getInterpolation() {
        return level().isClientSide() ? ignore : null;
    }

    // ---------------------------------------------------------------- track access

    static Rail rail(TrackGraph.Segment sg) {
        return new Rail(sg.id, sg.path, sg.baseY, sg.live);
    }

    private @Nullable Rail rail(long id) {
        if (level() instanceof ServerLevel sl) {
            TrackGraph.Segment sg = TrackGraph.get(sl).segment(id);
            return sg == null ? null : rail(sg);
        }
        return clientTrack.apply(id);
    }

    private record Hop(Rail rail, boolean enterAtB) {}

    private @Nullable Hop hop(Rail r, boolean atB) {
        if (level() instanceof ServerLevel sl) return serverHop(sl, r, atB);
        long code = entityData.get(atB ? DATA_NEXT_B : DATA_NEXT_A);
        if (code < 0 || entityData.get(DATA_SEG) != r.id()) return null;
        Rail n = clientTrack.apply(code >> 1);
        return n == null ? null : new Hop(n, (code & 1) != 0);
    }

    private @Nullable Hop serverHop(ServerLevel level, Rail r, boolean atB) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment sg = g.segment(r.id());
        if (sg == null) return null;
        long nodeId = atB ? sg.b : sg.a;
        TrackGraph.Node n = g.node(nodeId);
        if (n == null || n.buffer) return null;
        TrackGraph.Segment nx = g.next(nodeId, sg.id);
        if (nx == null || !nx.live) return null;
        int p = g.pointsPort(n);
        if (p >= 0) {
            int inPort = sg.a == nodeId ? sg.aPort : sg.bPort;
            List<TrackGraph.Segment> legs = g.legs(n);
            if (inPort == p) {
                int idx = legs.indexOf(sg);
                boolean thrown = idx >= 0 && idx != n.route;
                if (thrown) {
                    g.setRoute(level, n, idx);
                    TrackActions.updateFrames(level, n);
                    level.playSound(null, n.pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 1.3f);
                    level.playSound(null, n.pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.6f);
                    tellRiders(Component.translatable("message.railworks.trolley.thrown", n.pos.getX(), n.pos.getY(), n.pos.getZ()).withStyle(ChatFormatting.GOLD), true);
                }
                report.points(n, Component.translatable(thrown ? "railworks.report.trailing_thrown" : "railworks.report.trailing"));
            } else {
                report.points(n, Component.translatable("railworks.report.facing", side(g, n, legs, nx)));
            }
        }
        report.enter(nx);
        return new Hop(rail(nx), nx.b == nodeId && nx.a != nodeId);
    }

    private static Component side(TrackGraph g, TrackGraph.Node n, List<TrackGraph.Segment> legs, TrackGraph.Segment taken) {
        if (legs.size() != 2) return Component.literal("?");
        double[] c = new double[2];
        for (int i = 0; i < 2; i++) {
            TrackGraph.Segment l = legs.get(i);
            boolean fromA = l.a == n.id;
            double len = l.path.length, t = Math.min(len, 6);
            double[] p = l.path.pose(fromA ? t : len - t), q = l.path.pose(fromA ? 0 : len);
            double h = fromA ? q[2] : q[2] + Math.PI;
            c[i] = Math.cos(h) * (p[1] - q[1]) - Math.sin(h) * (p[0] - q[0]);
        }
        int i = legs.indexOf(taken);
        boolean right = c[i] > c[1 - i];
        return Component.translatable(right ? "railworks.report.right" : "railworks.report.left");
    }

    // ---------------------------------------------------------------- motion

    public double frontHeading() {
        return Math.toRadians(getYRot() + 90);
    }

    private void placeOn(Rail r) {
        double ss = Mth.clamp(s, 0, r.length());
        double[] p = r.path().pose(ss);
        double h = p[2] + (front ? 0 : Math.PI);
        double grade = r.path().gradeAt(ss) * (front ? 1 : -1);
        setPos(p[0], r.top(ss), p[1]);
        setYRot(Mth.wrapDegrees((float) Math.toDegrees(h) - 90));
        setXRot((float) -Math.toDegrees(Math.atan(grade)));
    }

    /** Signed pump force along +s, from the pumpers' inputs. */
    private double thrust(List<Input> inputs, List<Vec3> looks, double us, byte[] pumpOut) {
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
        double k = n == 1 ? 0.988 : 1.45, c = n == 1 ? 7.76 : 9.5;
        int ds = d * (front ? 1 : -1);
        return ds * Mth.clamp(k * (1 - us * ds / c), 0, k);
    }

    private static double step(double u, double force, double drag) {
        if (u == 0) return Math.abs(force) <= drag ? 0 : Math.signum(force) * (Math.abs(force) - drag) * DT;
        double v = u + force * DT - Math.signum(u) * drag * DT;
        if (Math.signum(v) != Math.signum(u)) v = 0;
        return Mth.clamp(v, -CAP, CAP);
    }

    private record Stop(double dist, boolean buffer) {}

    private static final Stop NONE = new Stop(Double.MAX_VALUE, false);

    private Stop stopAhead(ServerLevel level, int dir) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment sg = g.segment(seg);
        if (sg == null || !sg.live) return new Stop(0, false);
        double d = dir > 0 ? sg.path.length - s : s;
        boolean atB = dir > 0;
        for (int i = 0; i < 6 && d < 80; i++) {
            long nodeId = atB ? sg.b : sg.a;
            TrackGraph.Node n = g.node(nodeId);
            if (n != null && n.buffer) return new Stop(d - BUFFER_GAP, true);
            TrackGraph.Segment nx = n == null ? null : g.next(nodeId, sg.id);
            if (nx == null || !nx.live) return new Stop(d - END_GAP, false);
            atB = !(nx.b == nodeId && nx.a != nodeId);
            d += nx.path.length;
            sg = nx;
        }
        return NONE;
    }

    /** Moves along the line, hopping sections. Returns false if it ran out of track. */
    private boolean advance(double ds) {
        Rail r = rail(seg);
        if (r == null) return false;
        s += ds;
        for (int guard = 0; guard < 4 && (s > r.length() || s < 0); guard++) {
            boolean atB = s > r.length();
            double over = atB ? s - r.length() : -s;
            Hop h = hop(r, atB);
            if (h == null) {
                s = atB ? r.length() : 0;
                u = 0;
                return false;
            }
            if (atB == h.enterAtB()) {
                u = -u;
                front = !front;
            }
            r = h.rail();
            seg = r.id();
            s = h.enterAtB() ? r.length() - over : over;
        }
        return true;
    }

    private void animate(double ds) {
        wheelO = wheel;
        beamO = beam;
        double along = ds * (front ? 1 : -1);
        wheel += along / WHEEL_R;
        beam += along * Mth.TWO_PI / STROKE;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) serverTick(sl);
        else clientTick();
    }

    private void serverTick(ServerLevel level) {
        Rail r = rail(seg);
        if (r == null) {
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
        for (Entity e : getPassengers()) {
            if (e instanceof ServerPlayer sp && placeOf(sp) < 2) {
                Input in = sp.getLastClientInput();
                inputs.add(in);
                looks.add(sp.getLookAngle());
                jump |= in.jump();
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
        int dir = u > 0.001 ? 1 : u < -0.001 ? -1 : 0;
        byte[] pump = new byte[1];
        double force = thrust(inputs, looks, u, pump) - G * r.path().gradeAt(Mth.clamp(s, 0, r.length()));
        if (dir == 0) dir = (int) Math.signum(force);
        Stop stop = dir == 0 ? NONE : stopAhead(level, dir);
        double drag = 0.08 + 0.004 * u * u + (brake ? BRAKE : 0);
        boolean endBrake = false;
        if (!stop.buffer() && stop.dist() < 60 && u * u / (2 * BRAKE) >= stop.dist() - 0.1) {
            drag += BRAKE;
            endBrake = true;
            if (!endBraking && Math.abs(u) > 0.3) tellRiders(Component.translatable("message.railworks.trolley.end").withStyle(ChatFormatting.YELLOW), true);
        }
        endBraking = endBrake;
        double nu = step(u, force, drag);
        double ds = (u + nu) / 2 * DT;
        u = nu;
        boolean stopped = false;
        if (dir != 0 && Math.abs(ds) >= Math.max(0, stop.dist())) {
            ds = dir * Math.max(0, stop.dist());
            stopped = Math.abs(u) > 0.01 || ridden;
            if (Math.abs(u) > 0.3 && stopCooldown <= 0 && (stop.buffer() || !endBraking)) {
                if (stop.buffer()) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, Math.min(1, (float) Math.abs(u) / 4 + 0.2f), 0.7f);
                tellRiders(Component.translatable(stop.buffer() ? "message.railworks.trolley.buffer" : "message.railworks.trolley.end").withStyle(ChatFormatting.YELLOW), true);
                stopCooldown = 40;
            }
            u = 0;
        }
        if (ds != 0) {
            BlockPos hit = outlineHit(r, Mth.clamp(s + ds, 0, r.length()), (int) Math.signum(ds));
            if (hit != null) {
                if (Math.abs(u) > 0.3 || stopCooldown <= 0) {
                    BlockState bs = level.getBlockState(hit);
                    tellRiders(Component.translatable("message.railworks.trolley.blocked", bs.getBlock().getName(), hit.getX(), hit.getY(), hit.getZ()).withStyle(ChatFormatting.RED), true);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.8f, 0.7f);
                    stopCooldown = 40;
                }
                ds = 0;
                u = 0;
                stopped = true;
            }
        }
        stopCooldown--;
        long before = seg;
        if (ds != 0 && !advance(ds)) stopped = true;
        Rail now = rail(seg);
        if (now == null) return;
        placeOn(now);
        animate(ds);
        report.track(now, s, Math.abs(ds));
        sounds(level, now, ds, brake || endBrake, pump[0], ridden);
        nudge();
        boolean changed = before != seg || stopped || entityData.get(DATA_PUMP) != pump[0] || entityData.get(DATA_BRAKE) != (brake || endBrake);
        entityData.set(DATA_PUMP, pump[0]);
        entityData.set(DATA_BRAKE, brake || endBrake);
        sync(changed);
        if (ridden && tickCount % 20 == 0) level.getChunkSource().addTicketWithRadius(ModContent.TROLLEY_TICKET, ChunkPos.containing(blockPosition()), 3);
        if (ridden && tickCount % 4 == 0 && tickCount > hudHold) hud(now);
        if (!ridden && wasRidden) report.reset(this);
        wasRidden = ridden;
    }

    private void sync(boolean force) {
        if (level().isClientSide()) return;
        if (force || tickCount - lastSync >= 4) {
            lastSync = tickCount;
            entityData.set(DATA_SEG, seg);
            entityData.set(DATA_S, (float) s);
            entityData.set(DATA_U, (float) u);
            entityData.set(DATA_FRONT, front);
            if (level() instanceof ServerLevel sl) {
                TrackGraph g = TrackGraph.get(sl);
                TrackGraph.Segment sg = g.segment(seg);
                if (sg != null) {
                    entityData.set(DATA_NEXT_A, nextCode(g, sg, sg.a));
                    entityData.set(DATA_NEXT_B, nextCode(g, sg, sg.b));
                }
            }
        }
    }

    private static long nextCode(TrackGraph g, TrackGraph.Segment sg, long nodeId) {
        TrackGraph.Node n = g.node(nodeId);
        if (n == null || n.buffer) return -1;
        TrackGraph.Segment nx = g.next(nodeId, sg.id);
        if (nx == null || !nx.live) return -1;
        return nx.id * 2 + (nx.b == nodeId && nx.a != nodeId ? 1 : 0);
    }

    /** The first solid block inside the trolley's outline, checking only the leading end when moving. */
    private @Nullable BlockPos outlineHit(Rail r, double at, int dir) {
        double[] c = r.path().pose(at);
        double h = c[2];
        double[] as = dir == 0 ? new double[]{-1.3, -0.65, 0, 0.65, 1.3} : new double[]{dir * 1.35};
        for (double a : as) {
            double base = r.top(Mth.clamp(at + a, 0, r.length()));
            double x0 = c[0] + a * Math.cos(h), z0 = c[1] + a * Math.sin(h);
            for (double w = -1.0; w <= 1.001; w += 0.25) {
                double x = x0 - w * Math.sin(h), z = z0 + w * Math.cos(h);
                for (double y = base + 0.15; y <= base + 1.75; y += 0.4) {
                    BlockPos bp = BlockPos.containing(x, y, z);
                    BlockState st = level().getBlockState(bp);
                    if (st.isAir() || st.is(ModContent.TRACK_NODE)) continue;
                    VoxelShape shape = st.getCollisionShape(level(), bp);
                    if (shape.isEmpty()) continue;
                    double lx = x - bp.getX(), ly = y - bp.getY(), lz = z - bp.getZ();
                    for (AABB box : shape.toAabbs()) if (box.contains(lx, ly, lz)) return bp;
                }
            }
        }
        return null;
    }

    private void nudge() {
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(0.4, 0, 0.4), e -> e instanceof LivingEntity && e.getVehicle() != this && !e.isSpectator())) {
            double hf = frontHeading();
            double w = -(e.getX() - getX()) * Math.sin(hf) + (e.getZ() - getZ()) * Math.cos(hf);
            double side = w >= 0 ? 1 : -1;
            e.push(-Math.sin(hf) * side * 0.15, 0, Math.cos(hf) * side * 0.15);
            e.hurtMarked = true;
        }
    }

    private void sounds(ServerLevel level, Rail r, double ds, boolean brake, int pump, boolean ridden) {
        double speed = Math.abs(ds) / DT;
        clack += Math.abs(ds);
        if (clack >= 2) {
            clack -= 2;
            play(level, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.12f + (float) speed * 0.02f, 1.5f + (float) speed * 0.03f);
        }
        if (pump != 0) {
            double a = Math.sin(beamO), b = Math.sin(beam);
            if (a < 0.95 && b >= 0.95) play(level, SoundEvents.WOODEN_TRAPDOOR_OPEN, 0.25f, 0.6f);
            if (a > -0.95 && b <= -0.95) play(level, SoundEvents.CHAIN_HIT, 0.4f, 0.8f);
        }
        if (brake && ridden && speed > 0.5 && --brakeCooldown <= 0) {
            play(level, SoundEvents.GRINDSTONE_USE, 0.3f, 1.3f);
            brakeCooldown = 6;
        }
        double k = Math.abs(r.path().curvatureAt(Mth.clamp(s, 0, r.length())));
        if (k > 1 / 60.0 && speed > 2.5 && --squealCooldown <= 0) {
            play(level, SoundEvents.GRINDSTONE_USE, 0.12f, 1.9f);
            squealCooldown = 8;
        }
    }

    private void play(ServerLevel level, SoundEvent e, float vol, float pitch) {
        level.playSound(null, getX(), getY(), getZ(), e, SoundSource.NEUTRAL, vol, pitch);
    }

    private void hud(Rail r) {
        double g = r.path().gradeAt(Mth.clamp(s, 0, r.length())) * Math.signum(u == 0 ? (front ? 1 : -1) : u);
        MutableComponent grade = TrackActions.gradeText(g);
        if (Math.abs(g) >= 1e-4) grade.append(Component.translatable(g > 0 ? "railworks.trolley.up" : "railworks.trolley.down"));
        MutableComponent c = Component.translatable("railworks.trolley.hud", String.format(java.util.Locale.ROOT, "%.1f", Math.abs(u)), grade);
        if (braking()) c.append(Component.translatable("railworks.trolley.brake_on"));
        tellRiders(c, false);
    }

    private static void say(Player p, Component c) {
        if (ECHO) LOG.info("say: {}", c.getString());
        p.sendOverlayMessage(c);
    }

    private void tellRiders(Component c, boolean echo) {
        if (echo) hudHold = tickCount + 50;
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer sp) sp.sendOverlayMessage(c);
        if (echo && ECHO) LOG.info("[trolley] {}", c.getString());
    }

    // ---------------------------------------------------------------- client

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide() && (key == DATA_S || key == DATA_SEG || key == DATA_U)) pending = true;
    }

    private void clientTick() {
        if (pending) {
            pending = false;
            snapSeg = entityData.get(DATA_SEG);
            snapS = entityData.get(DATA_S);
            snapU = entityData.get(DATA_U);
            snapAge = 0;
            if (fresh) {
                fresh = false;
                seg = snapSeg;
                s = snapS;
                u = snapU;
                front = entityData.get(DATA_FRONT);
            }
        } else snapAge++;
        if (fresh) return;
        Rail r = rail(seg);
        if (r == null) {
            seg = snapSeg;
            s = snapS;
            front = entityData.get(DATA_FRONT);
            r = rail(seg);
            if (r == null) return;
        }
        Input in = clientInput.apply(this);
        double ds;
        if (in != null && stage() == DONE) {
            Entity me = null;
            for (Entity e : getPassengers()) if (isPumper(e) && clientInput.apply(this) != null) me = e;
            List<Input> inputs = List.of(in);
            List<Vec3> looks = List.of(me != null ? me.getLookAngle() : Vec3.ZERO);
            byte[] pump = new byte[1];
            double force = thrust(inputs, looks, u, pump) - G * r.path().gradeAt(Mth.clamp(s, 0, r.length()));
            double drag = 0.08 + 0.004 * u * u + (in.jump() || braking() ? BRAKE : 0);
            double nu = step(u, force, drag);
            ds = (u + nu) / 2 * DT;
            u = nu;
            if (snapU == 0 && Math.abs(u) < 0.3 && snapAge < 10 && Math.abs(snapS - s) < 0.5 && snapSeg == seg) {
                ds = 0;
                u = 0;
            }
        } else {
            u += (snapU - u) * 0.5;
            ds = u * DT;
        }
        double sBefore = s;
        long segBefore = seg;
        boolean frontBefore = front;
        if (ds != 0) advance(ds);
        if (seg == snapSeg) {
            double target = snapS + (snapU == 0 ? 0 : snapU * Math.min(snapAge, 6) * DT);
            double err = target - s;
            if (Math.abs(err) > 3) s = target;
            else s += err * (in != null ? 0.1 : 0.25);
            mismatch = 0;
        } else if (++mismatch > 8) {
            seg = snapSeg;
            s = snapS;
            u = snapU;
            front = entityData.get(DATA_FRONT);
            mismatch = 0;
        }
        Rail now = rail(seg);
        if (now == null) return;
        if (front != entityData.get(DATA_FRONT) && seg == snapSeg) front = entityData.get(DATA_FRONT);
        placeOn(now);
        double moved = seg == segBefore && front == frontBefore ? s - sBefore : ds;
        animate(moved);
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        seg = in.getLongOr("seg", 0);
        s = in.getDoubleOr("s", 0);
        u = in.getDoubleOr("u", 0);
        front = in.getBooleanOr("front", true);
        entityData.set(DATA_STAGE, in.getIntOr("stage", 0));
        NonNullList<ItemStack> items = box.getItems();
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(in, items);
        sync(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putLong("seg", seg);
        out.putDouble("s", s);
        out.putDouble("u", u);
        out.putBoolean("front", front);
        out.putInt("stage", stage());
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
                if (sg != null) enter(sg);
            }
        }

        void start(ServerPlayer p) {
            startPoints.put(p.getUUID(), points.size());
            startDist.put(p.getUUID(), dist);
        }

        void enter(TrackGraph.Segment sg) {
            if (seen.add(sg.id)) clearance.addAll(sg.blocked());
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
