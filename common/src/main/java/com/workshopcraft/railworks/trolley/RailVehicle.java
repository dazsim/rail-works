package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackActions;
import com.workshopcraft.railworks.track.TrackGraph;
import com.workshopcraft.railworks.track.TrackRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Anything that rolls on the track by itself: the hand trolley and the bogie. It stores a section, a distance
 * along its path and a speed along it, so it always sits on the exact line the ties were laid to. Vehicles
 * joined by tow bars form a consist that the root vehicle moves as one. The server owns the state; clients
 * run the same motion on their own copy of the track between updates.
 */
public abstract class RailVehicle extends Entity implements TrackRunner {
    static final double DT = 0.05, G = 9.8, END_BRAKE = 4, CAP = 10, BAR_REACH = 4;
    // running people down: no harm below HIT_MIN, HIT_TOP damage at CAP, linear between
    static final double HIT_MIN = 1.5, HIT_TOP = 12, HIT_KNOCK = 0.45, HIT_LIFT = 0.3, HIT_SLOW = 0.9;
    // pushing by hand: force per pusher, the speed it fades out at
    static final double PUSH_F = 1.6, PUSH_TOP = 2.5;
    static final int MAX_BOGIES = 2;

    static final EntityDataAccessor<Long> DATA_SEG = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Float> DATA_S = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> DATA_U = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> DATA_FRONT = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> DATA_BRAKE = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Integer> DATA_TOW = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Byte> DATA_TOW_END = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.BYTE);

    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("railworks");
    static final boolean ECHO = System.getenv("RAILWORKS_ECHO") != null;

    /** Client hook, set by the client entry points. */
    public static LongFunction<@Nullable Rail> clientTrack = id -> null;

    protected long seg;
    protected double s, u;
    protected boolean front = true;

    // shared animation state
    public double wheel, wheelO;
    private double clack;

    // server only
    protected int stopCooldown;
    protected boolean endBraking;
    private int lastSync, towLost;
    private @Nullable UUID towId;
    private int towSign = 1, towEnd = 1;
    /** Everyone pushing by hand just now, each with the way they push, +1 towards our front. */
    protected final List<Push> pushers = new ArrayList<>();

    protected record Push(Player player, int dir, int until) {}

    // client only
    private final InterpolationHandler ignore = new InterpolationHandler(this) {
        @Override
        public void interpolateTo(Vec3 position, float yRot, float xRot) {}
    };

    protected RailVehicle(EntityType<? extends RailVehicle> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_SEG, 0L).define(DATA_S, 0f).define(DATA_U, 0f).define(DATA_FRONT, true).define(DATA_STAGE, 0)
                .define(DATA_BRAKE, true).define(DATA_TOW, 0).define(DATA_TOW_END, (byte) 3);
    }

    public int stage() {
        return entityData.get(DATA_STAGE);
    }

    public boolean braking() {
        return entityData.get(DATA_BRAKE);
    }

    public abstract int doneStage();

    public boolean done() {
        return stage() >= doneStage();
    }

    /** Own mass, riders included, the trolley empty is 1. */
    protected abstract double ownMass();

    protected abstract double wheelRadius();

    protected abstract double halfWidth();

    protected abstract double outlineHeight();

    /** How far along from the centre the tow eyes sit, and how high. */
    public abstract double eyeReach();

    public abstract double eyeHeight();

    protected abstract ResourceKey<DamageType> runDown();

    public abstract void takeOff(ServerLevel level, Player player);

    public boolean isTrolley() {
        return false;
    }

    /** Messages for whoever rides it, if anyone does. */
    protected void tell(Component c, boolean echo) {
        if (echo && ECHO) LOG.info("[{}] {}", getType().toShortString(), c.getString());
    }

    protected void pointsPassed(TrackGraph.Node n, Component how) {}

    protected void entered(ServerLevel level, TrackGraph.Segment sg) {}

    int f() {
        return front ? 1 : -1;
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !isRemoved();
    }

    @Override
    public @Nullable InterpolationHandler getInterpolation() {
        return level().isClientSide() ? ignore : null;
    }

    /** The box round the body turned to its heading, long enough to reach the tow eyes so they can be clicked. */
    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        double hf = Math.toRadians(getYRot() + 90), c = Math.abs(Math.cos(hf)), sn = Math.abs(Math.sin(hf)), l = eyeReach() + 0.15, w = halfWidth();
        double ex = l * c + w * sn, ez = l * sn + w * c;
        return new AABB(pos.x - ex, pos.y, pos.z - ez, pos.x + ex, pos.y + getBbHeight(), pos.z + ez);
    }

    /** Which end a click lands on, +1 the front. */
    public int endAt(Vec3 loc) {
        double hf = frontHeading();
        return loc.x * Math.cos(hf) + loc.z * Math.sin(hf) >= 0 ? 1 : -1;
    }

    static void say(Player p, Component c) {
        if (ECHO) LOG.info("say: {}", c.getString());
        p.sendOverlayMessage(c);
    }

    static void give(ServerLevel level, @Nullable Player player, ItemStack stack, Vec3 at) {
        if (player != null && player.getInventory().add(stack)) return;
        ItemEntity e = new ItemEntity(level, at.x, at.y + 0.3, at.z, stack);
        e.setDefaultPickUpDelay();
        level.addFreshEntity(e);
    }

    /** A sneaking hammer blow near a vehicle takes off a tow bar it touches, or else the vehicle itself. */
    public static boolean hammerNear(ServerLevel level, Player player, Vec3 at) {
        AABB box = new AABB(at, at).inflate(1.8, 1.2, 1.8);
        for (RailVehicle v : level.getEntitiesOfClass(RailVehicle.class, box.inflate(4), RailVehicle::towed)) {
            RailVehicle l = v.leader();
            if (l == null) continue;
            Vec3 a = v.eye(v.towEnd()), b = l.eye(v.leaderEnd());
            if (distToSegment(at, a, b) < 0.7 && player.distanceToSqr(at) < 36) {
                v.uncouple(level, player);
                return true;
            }
        }
        RailVehicle best = null;
        for (RailVehicle t : level.getEntitiesOfClass(RailVehicle.class, box, e -> e.getPassengers().isEmpty()))
            if (best == null || t.distanceToSqr(at) < best.distanceToSqr(at)) best = t;
        if (best == null || best.distanceToSqr(player) > 36) return false;
        best.uncoupleAll(level, player);
        best.takeOff(level, player);
        return true;
    }

    static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1e-6 ? 0 : Mth.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** Sneak and hammer right by a coupled end takes the bar off there. */
    protected boolean hammerBar(ServerLevel level, Player player, Vec3 loc) {
        int end = endAt(loc);
        double hf = frontHeading(), a = Math.abs(loc.x * Math.cos(hf) + loc.z * Math.sin(hf));
        if (a < eyeReach() - 0.7) return false;
        if (towed() && towEnd() == end) {
            uncouple(level, player);
            return true;
        }
        for (RailVehicle f : followers()) {
            if (f.leaderEnd() == end) {
                f.uncouple(level, player);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tow bars

    public record Member(RailVehicle v, int sign) {}

    public boolean towed() {
        return level().isClientSide() ? entityData.get(DATA_TOW) > 0 : towId != null;
    }

    public @Nullable RailVehicle leader() {
        if (level() instanceof ServerLevel sl) {
            if (towId == null) return null;
            return sl.getEntity(towId) instanceof RailVehicle v && v.isAlive() ? v : null;
        }
        int id = entityData.get(DATA_TOW) - 1;
        return id >= 0 && level().getEntity(id) instanceof RailVehicle v ? v : null;
    }

    /** +1 when it faces the same way as its leader. */
    public int towSign() {
        return level().isClientSide() ? ((entityData.get(DATA_TOW_END) & 2) != 0 ? 1 : -1) : towSign;
    }

    /** Its own end the bar is on, +1 the front. */
    public int towEnd() {
        return level().isClientSide() ? ((entityData.get(DATA_TOW_END) & 1) != 0 ? 1 : -1) : towEnd;
    }

    /** The leader's end the bar is on. */
    public int leaderEnd() {
        return -towSign() * towEnd();
    }

    List<RailVehicle> followers() {
        if (!(level() instanceof ServerLevel)) return List.of();
        UUID me = getUUID();
        return level().getEntitiesOfClass(RailVehicle.class, getBoundingBox().inflate(12), v -> v != this && me.equals(v.towId));
    }

    public RailVehicle root() {
        RailVehicle v = this;
        for (int i = 0; i < 8; i++) {
            RailVehicle l = v.leader();
            if (l == null || l == this) break;
            v = l;
        }
        return v;
    }

    /** Every vehicle joined to this one, the root first, each with its facing against the root. */
    public List<Member> consist() {
        List<Member> out = new ArrayList<>();
        out.add(new Member(root(), 1));
        for (int i = 0; i < out.size() && out.size() < 8; i++) {
            Member m = out.get(i);
            for (RailVehicle f : m.v.followers()) if (out.stream().noneMatch(o -> o.v == f)) out.add(new Member(f, m.sign * f.towSign));
        }
        return out;
    }

    static double mass(List<Member> ms) {
        double m = 0;
        for (Member x : ms) m += x.v.ownMass();
        return m;
    }

    /** Turns a speed along our +s into one along the root's +s. */
    private int rootK() {
        int sign = 1;
        RailVehicle v = this;
        for (int i = 0; i < 8; i++) {
            RailVehicle l = v.leader();
            if (l == null) break;
            sign *= v.towSign();
            v = l;
        }
        return sign * f() * v.f();
    }

    public boolean coupledAt(int end) {
        if (towed() && towEnd() == end) return true;
        for (RailVehicle f : followers()) if (f.leaderEnd() == end) return true;
        return false;
    }

    void setTow(@Nullable RailVehicle leader, int sign, int end) {
        towId = leader == null ? null : leader.getUUID();
        towSign = sign;
        towEnd = end;
        towLost = 0;
        topology++;
        if (leader != null) train = null;
        syncTow(leader);
    }

    private void syncTow(@Nullable RailVehicle leader) {
        entityData.set(DATA_TOW, leader == null ? 0 : leader.getId() + 1);
        entityData.set(DATA_TOW_END, (byte) ((towEnd > 0 ? 1 : 0) | (towSign > 0 ? 2 : 0)));
    }

    /** Makes this vehicle the root, turning the bars on the way up round. */
    static void reroot(RailVehicle v) {
        List<RailVehicle> path = new ArrayList<>();
        path.add(v);
        for (int i = 0; i < 8; i++) {
            RailVehicle l = path.getLast().leader();
            if (l == null || path.contains(l)) break;
            path.add(l);
        }
        int n = path.size();
        int[] sign = new int[n], end = new int[n];
        for (int i = 0; i < n - 1; i++) {
            sign[i] = path.get(i).towSign;
            end[i] = path.get(i).leaderEnd();
        }
        for (int i = 0; i < n - 1; i++) path.get(i + 1).setTow(path.get(i), sign[i], end[i]);
        v.setTow(null, 1, 1);
    }

    /** Takes the bar off between this vehicle and its leader, into the player's hands or onto the ground. */
    public void uncouple(ServerLevel level, @Nullable Player player) {
        if (towId == null) return;
        RailVehicle l = leader();
        Vec3 at = l == null ? position() : eye(towEnd()).add(l.eye(leaderEnd())).scale(0.5);
        setTow(null, 1, 1);
        give(level, player, new ItemStack(ModContent.TOW_BAR), at);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 0.7f, 1.1f);
        if (player != null) say(player, Component.translatable("message.railworks.tow.uncoupled"));
        if (ECHO) LOG.info("[tow] uncoupled {}", getType().toShortString());
    }

    public void uncoupleAll(ServerLevel level, @Nullable Player player) {
        uncouple(level, player);
        for (RailVehicle f : followers()) f.uncouple(level, player);
    }

    public Vec3 eye(int end) {
        return eye(end, position(), getYRot(), getXRot());
    }

    /** The tow eye on one end, for a body at pos turned to yRot and xRot. */
    public Vec3 eye(int end, Vec3 pos, float yRot, float xRot) {
        double hf = Math.toRadians(yRot + 90), p = Math.toRadians(-xRot), a = end * eyeReach();
        return new Vec3(pos.x + a * Math.cos(p) * Math.cos(hf), pos.y + eyeHeight() + a * Math.sin(p), pos.z + a * Math.cos(p) * Math.sin(hf));
    }

    /** Keeps a towed vehicle with its leader. True while it is towed, so it leaves the moving to the root. */
    protected boolean towTick(ServerLevel level) {
        if (towId == null) return false;
        RailVehicle l = leader();
        if (l == null) {
            if (entitiesLoadedAround(level, 24) && ++towLost > 40) {
                uncouple(level, null);
                return false;
            }
            return true;
        }
        towLost = 0;
        if (entityData.get(DATA_TOW) != l.getId() + 1) syncTow(l);
        if (l.distanceTo(this) > trackReach() + l.trackReach() + BAR_REACH + 2) {
            uncouple(level, null);
            return false;
        }
        return true;
    }

    /** Whether the entities within r blocks are all loaded, so a missing leader is really gone. */
    boolean entitiesLoadedAround(ServerLevel level, double r) {
        int x0 = Mth.floor(getX() - r) >> 4, x1 = Mth.floor(getX() + r) >> 4, z0 = Mth.floor(getZ() - r) >> 4, z1 = Mth.floor(getZ() + r) >> 4;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) return false;
        return true;
    }

    // ---------------------------------------------------------------- track access

    static Rail rail(TrackGraph.Segment sg) {
        return new Rail(sg.id, sg.path, sg.baseY, sg.live);
    }

    @Nullable Rail rail(long id) {
        if (level() instanceof ServerLevel sl) {
            TrackGraph.Segment sg = TrackGraph.get(sl).segment(id);
            return sg == null ? null : rail(sg);
        }
        return clientTrack.apply(id);
    }

    /** Snaps a new vehicle to the nearest point of the section, facing the way the player looks. */
    void snapTo(TrackGraph.Segment sg, Vec3 at, Vec3 look) {
        double best = 0, bd = Double.MAX_VALUE;
        for (double t = 0; t <= sg.path.length; t += 0.05) {
            double[] p = sg.path.pose(t);
            double d = (p[0] - at.x) * (p[0] - at.x) + (p[1] - at.z) * (p[1] - at.z);
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        double h = sg.path.pose(best)[2];
        seg = sg.id;
        s = best;
        front = look.x * Math.cos(h) + look.z * Math.sin(h) >= 0;
        placeOn(rail(sg));
    }

    /** Puts a vehicle on the nearest live track if there is room for it. */
    static boolean put(ServerLevel level, Player player, Vec3 at, @Nullable RailVehicle v) {
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
        if (v == null) return false;
        v.snapTo(sg, at, player.getLookAngle());
        if (!level.getEntitiesOfClass(RailVehicle.class, v.getBoundingBox().inflate(0.5)).isEmpty()) {
            say(player, Component.translatable("message.railworks.trolley.occupied"));
            return false;
        }
        BlockPos hit = v.outlineHit(level, sg.id, v.s, 0);
        if (hit != null) {
            say(player, Component.translatable("message.railworks.trolley.no_room", hit.getX(), hit.getY(), hit.getZ()));
            return false;
        }
        v.sync(true);
        level.addFreshEntity(v);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1, 0.8f);
        return true;
    }

    // ---------------------------------------------------------------- trains

    /** Bumped whenever any tow bar goes on or off, so every train checks its members again. */
    static int topology;
    private @Nullable Train train, savedTrain;
    private int trainTopo = -1, trainWait, lastTrainSync;
    private boolean trainDirty;

    public @Nullable Train currentTrain() {
        return train;
    }

    /** The train this root moves, found again whenever the couplings change. Null while members are still loading. */
    @Nullable Train train(ServerLevel level) {
        if (train != null && trainTopo == topology && intact(train)) return train;
        Train t = null;
        if (savedTrain != null) {
            Train sv = savedTrain;
            List<Member> found = new ArrayList<>();
            boolean missing = false;
            for (int i = 0; i < sv.ids.size(); i++) {
                if (level.getEntity(sv.ids.get(i)) instanceof RailVehicle v && v.isAlive()) found.add(new Member(v, sv.flip[i]));
                else missing = true;
            }
            if (missing && ++trainWait < 200) return null;
            savedTrain = null;
            if (!missing && sameSet(found, consist()) && validPath(level, sv)) {
                sv.members.addAll(found);
                t = sv;
                if (ECHO) LOG.info("[train] loaded {} vehicles from the save", found.size());
            }
        }
        if (t == null) t = Trains.build(level, this);
        train = t;
        trainTopo = topology;
        trainDirty = true;
        return t;
    }

    private boolean intact(Train t) {
        if (t.members.isEmpty() || t.members.getFirst().v() == null) return false;
        for (Member m : t.members) if (!m.v().isAlive()) return false;
        return t.indexOf(this) >= 0;
    }

    private static boolean sameSet(List<Member> a, List<Member> b) {
        if (a.size() != b.size()) return false;
        for (Member x : a) if (b.stream().noneMatch(y -> y.v() == x.v())) return false;
        return true;
    }

    private static boolean validPath(ServerLevel level, Train t) {
        TrackGraph g = TrackGraph.get(level);
        for (Train.Step st : t.steps) {
            TrackGraph.Segment sg = g.segment(st.seg());
            if (sg == null || !sg.live) return false;
        }
        return t.ref >= 0 && t.ref + t.span() <= t.total(id -> Trains.rail(g, id)) + 1e-6;
    }

    /** The section over a node, throwing trailing points and reporting them. Null at a buffer or dead end. */
    TrackGraph.@Nullable Segment cross(ServerLevel level, TrackGraph g, TrackGraph.Segment sg, long nodeId) {
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
                    tell(Component.translatable("message.railworks.trolley.thrown", n.pos.getX(), n.pos.getY(), n.pos.getZ()).withStyle(ChatFormatting.GOLD), true);
                }
                pointsPassed(n, Component.translatable(thrown ? "railworks.report.trailing_thrown" : "railworks.report.trailing"));
            } else {
                int idx = legs.indexOf(nx);
                if (idx >= 0 && idx != n.route) {
                    g.setRoute(level, n, idx);
                    TrackActions.updateFrames(level, n);
                    level.playSound(null, n.pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 1.3f);
                }
                pointsPassed(n, Component.translatable("railworks.report.facing", side(g, n, legs, nx)));
            }
        }
        entered(level, nx);
        return nx;
    }

    /** Puts every member where the train says, turning each to face the way its flip says. */
    private void placeMembers(Train t, double[] moved) {
        LongFunction<@Nullable Rail> track = this::rail;
        for (int i = 0; i < t.members.size(); i++) {
            RailVehicle v = t.members.get(i).v();
            Train.Pos p = t.at(track, t.ref + t.d[i]);
            if (p == null) continue;
            long seg0 = v.seg;
            double s0 = v.s;
            boolean f0 = v.front;
            v.seg = p.rail().id();
            v.s = p.s();
            v.front = -p.dir() * t.flip[i] > 0;
            moved[i] = seg0 == v.seg && f0 == v.front ? v.s - s0 : moved[i];
            v.placeOn(p.rail());
        }
    }

    /** Along a member's +s for each unit along the root's +s. */
    private int k(Train t, int i) {
        RailVehicle v = t.members.get(i).v();
        return t.flip[i] * v.f() * f();
    }

    void sendTrain(ServerLevel level, Train t, boolean moving) {
        if (!trainDirty && !(moving || tickCount - lastTrainSync >= 20)) return;
        trainDirty = false;
        lastTrainSync = tickCount;
        Train c = Trains.lookahead(level, t, 10);
        TrainSyncPayload p = TrainSyncPayload.of(this, level.getGameTime(), u * f(), c, t);
        double range = 160 + t.span();
        for (net.minecraft.server.level.ServerPlayer sp : level.players())
            if (sp.distanceToSqr(this) < range * range) sp.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(p));
    }

    static void notifyNear(ServerLevel level, Entity at, Component c) {
        for (Player p : level.getEntitiesOfClass(Player.class, at.getBoundingBox().inflate(16))) say(p, c);
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

    @Override
    public long trackSegment() {
        return seg;
    }

    @Override
    public double trackS() {
        return s;
    }

    @Override
    public double trackSpeed() {
        return done() ? u : 0;
    }

    @Override
    public void bumpTrackSpeed(double nu) {
        if (!done()) return;
        RailVehicle r = root();
        r.u = Mth.clamp(nu * rootK(), -CAP, CAP);
        r.sync(true);
        if (r != this) {
            u = Mth.clamp(nu, -CAP, CAP);
            sync(true);
        }
    }

    /** Its train, found from any member: the root's while it lives. */
    public @Nullable Train trainOf() {
        return root().train;
    }

    @Override
    public double trackMass() {
        if (!done()) return 1000;
        return level().isClientSide() ? ownMass() : mass(consist());
    }

    void placeOn(Rail r) {
        double ss = Mth.clamp(s, 0, r.length());
        double[] p = r.path().pose(ss);
        double h = p[2] + (front ? 0 : Math.PI);
        double grade = r.path().gradeAt(ss) * f();
        setYRot(Mth.wrapDegrees((float) Math.toDegrees(h) - 90));
        setPos(p[0], r.top(ss), p[1]);
        setXRot((float) -Math.toDegrees(Math.atan(grade)));
    }

    static double step(double u, double force, double drag) {
        if (u == 0) return Math.abs(force) <= drag ? 0 : Math.signum(force) * (Math.abs(force) - drag) * DT;
        double v = u + force * DT - Math.signum(u) * drag * DT;
        if (Math.signum(v) != Math.signum(u)) v = 0;
        return Mth.clamp(v, -CAP, CAP);
    }

    /** Grade pull on the whole consist, along the root's +s. */
    double gradeForce(List<Member> ms, double m) {
        double f = 0;
        for (Member x : ms) {
            RailVehicle v = x.v;
            Rail r = v.rail(v.seg);
            if (r == null) continue;
            f += v.ownMass() * r.path().gradeAt(Mth.clamp(v.s, 0, r.length())) * x.sign * v.f() * f();
        }
        return -G * f / m;
    }

    /** Someone leaning on it this tick, or holding the push for a few ticks more. */
    void push(Player p, int dir) {
        pushers.removeIf(x -> x.player() == p);
        pushers.add(new Push(p, dir, tickCount + 6));
    }

    boolean pushed() {
        pushers.removeIf(x -> tickCount > x.until() || !x.player().isAlive());
        return !pushers.isEmpty();
    }

    /** The speed hand pushing fades out at, for n people at once. */
    protected double pushTop(int n) {
        return PUSH_TOP;
    }

    static int pusherCount(List<Member> ms) {
        int n = 0;
        for (Member x : ms) if (x.v.pushed()) n += x.v.pushers.size();
        return n;
    }

    /** People pushing by hand, along the root's +s. */
    double pushForce(List<Member> ms, double m) {
        int n = pusherCount(ms);
        if (n == 0) return 0;
        double top = 0;
        for (Member x : ms) top = Math.max(top, x.v.pushTop(n));
        double f = 0;
        for (Member x : ms) {
            if (!x.v.pushed()) continue;
            for (Push p : x.v.pushers) {
                int d = p.dir() * x.sign * f();
                f += d * PUSH_F * Mth.clamp((top - u * d) / 0.4, 0, 1);
            }
        }
        return f / m;
    }

    static boolean anyPushed(List<Member> ms) {
        return pusherCount(ms) > 0;
    }

    static void pushersOf(List<Member> ms, List<Entity> out) {
        for (Member x : ms) if (x.v.pushed()) for (Push p : x.v.pushers) out.add(p.player());
    }

    record Stop(double dist, boolean buffer) {}

    static final Stop NONE = new Stop(Double.MAX_VALUE, false);

    Stop stopAhead(ServerLevel level, int dir) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment sg = g.segment(seg);
        if (sg == null || !sg.live) return new Stop(0, false);
        double d = dir > 0 ? sg.path.length - s : s;
        boolean atB = dir > 0;
        for (int i = 0; i < 8 && d < 80; i++) {
            long nodeId = atB ? sg.b : sg.a;
            TrackGraph.Node n = g.node(nodeId);
            if (n != null && n.buffer) return new Stop(d - 0.59 - trackReach(), true);
            TrackGraph.Segment nx = n == null ? null : g.next(nodeId, sg.id);
            if (nx == null || !nx.live) return new Stop(d - trackReach() + 0.05, false);
            atB = !(nx.b == nodeId && nx.a != nodeId);
            d += nx.path.length;
            sg = nx;
        }
        return NONE;
    }

    /** The member whose body reaches furthest the way the train moves, dir +1 towards the front. */
    private static int leading(Train t, int dir) {
        int best = 0;
        double bx = 0;
        for (int i = 0; i < t.members.size(); i++) {
            double x = t.d[i] - dir * t.members.get(i).v().trackReach();
            if (i == 0 || x * dir < bx * dir) {
                best = i;
                bx = x;
            }
        }
        return best;
    }

    protected void animate(double ds) {
        wheelO = wheel;
        wheel += ds * f() / wheelRadius();
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) serverTick(sl);
        else clientTick();
    }

    protected abstract void serverTick(ServerLevel level);

    /** Who gets the blame for a run down, how much it hurts, and who is safe from it. */
    record Ride(@Nullable Entity driver, float harm, List<Entity> safe) {}

    /**
     * Moves the whole train one tick, this vehicle being its root. force and drag are accelerations along
     * our +s. Every member moves by exactly the same distance. Returns the distance moved along our +s.
     */
    double drive(ServerLevel level, Train t, double force, double drag, boolean hold, Ride ride, boolean changed) {
        List<Member> ms = t.members;
        int n = ms.size(), fr = f();
        int[] k = new int[n];
        for (int i = 0; i < n; i++) k[i] = k(t, i);
        int dir = u > 0.001 ? 1 : u < -0.001 ? -1 : 0;
        if (dir == 0) dir = (int) Math.signum(force);
        // dir along our +s; towards the train's front when dir * f() > 0
        int lead = dir == 0 ? 0 : leading(t, dir * fr);
        Stop stop = dir == 0 ? NONE : ms.get(lead).v().stopAhead(level, dir * k[lead]);
        boolean endBrake = false;
        if (!stop.buffer() && stop.dist() < 60 && u * u / (2 * END_BRAKE) >= stop.dist() - 0.1) {
            drag += END_BRAKE;
            endBrake = true;
            if (!endBraking && Math.abs(u) > 0.3) tell(Component.translatable("message.railworks.trolley.end").withStyle(ChatFormatting.YELLOW), true);
        }
        endBraking = endBrake;
        double nu = step(u, force, drag);
        double ds = (u + nu) / 2 * DT;
        u = nu;
        boolean stopped = false;
        if (dir != 0 && Math.abs(ds) >= Math.max(0, stop.dist())) {
            ds = dir * Math.max(0, stop.dist());
            stopped = Math.abs(u) > 0.01 || hold;
            if (Math.abs(u) > 0.3 && stopCooldown <= 0 && (stop.buffer() || !endBraking)) {
                if (stop.buffer()) play(level, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, Math.min(1, (float) Math.abs(u) / 4 + 0.2f), 0.7f);
                tell(Component.translatable(stop.buffer() ? "message.railworks.trolley.buffer" : "message.railworks.trolley.end").withStyle(ChatFormatting.YELLOW), true);
                stopCooldown = 40;
            }
            u = 0;
        }
        if (ds != 0) {
            int sd = (int) Math.signum(ds);
            for (int i = 0; i < n; i++) {
                RailVehicle v = ms.get(i).v();
                if (!v.trackContact()) continue;
                double[] at = Trains.walk(TrackGraph.get(level), v.seg, v.s, ds * k[i]);
                BlockPos hit = at == null ? null : v.outlineHit(level, (long) at[0], at[1], sd * k[i] * (int) at[2]);
                if (hit == null) continue;
                if (Math.abs(u) > 0.3 || stopCooldown <= 0) {
                    BlockState bs = level.getBlockState(hit);
                    tell(Component.translatable("message.railworks.trolley.blocked", bs.getBlock().getName(), hit.getX(), hit.getY(), hit.getZ()).withStyle(ChatFormatting.RED), true);
                    v.play(level, SoundEvents.WOOD_HIT, 0.8f, 0.7f);
                    stopCooldown = 40;
                }
                ds = 0;
                u = 0;
                stopped = true;
                break;
            }
        }
        if (ds != 0) {
            int d = (int) Math.signum(ds);
            int li = leading(t, d * fr);
            RailVehicle by = ms.get(li).v();
            int along = d * k[li];
            TrackRunner.Contact c = TrackRunner.ahead(level, by, along, Math.abs(ds) + 0.05, r -> r instanceof RailVehicle rv && t.indexOf(rv) >= 0);
            if (c != null && c.gap() < Math.abs(ds)) {
                double v1 = d * u, v2 = c.k() * c.other().trackSpeed() * along;
                if (v1 > v2) {
                    double[] v = TrackRunner.exchange(mass(ms), v1, c.other().trackMass(), v2);
                    if (v1 - v2 > 0.5) {
                        by.play(level, SoundEvents.ANVIL_LAND, Math.min(0.8f, (float) (v1 - v2) * 0.1f + 0.15f), 1.6f);
                        if (stopCooldown <= 0) {
                            tell(Component.translatable("message.railworks.trolley.bump").withStyle(ChatFormatting.YELLOW), true);
                            stopCooldown = 40;
                        }
                    }
                    u = d * v[0];
                    c.other().bumpTrackSpeed(c.k() * v[1] * along);
                }
                ds = d * Math.max(0, c.gap());
                stopped = true;
            }
        }
        stopCooldown--;
        long[] before = new long[n];
        double[] moved = new double[n];
        for (int i = 0; i < n; i++) {
            before[i] = ms.get(i).v().seg;
            moved[i] = ds * k[i];
        }
        if (ds != 0 && !Trains.move(level, this, t, -ds * fr)) {
            stopped = true;
            u = 0;
        }
        placeMembers(t, moved);
        for (int i = 0; i < n; i++) {
            RailVehicle v = ms.get(i).v();
            v.u = u * k(t, i);
            Rail now = v.rail(v.seg);
            if (now == null) continue;
            v.moved(level, now, moved[i], changed || stopped || before[i] != v.seg, ride);
            for (Entity p : v.getPassengers()) v.positionRider(p);
        }
        sendTrain(level, t, ds != 0 || u != 0);
        return ds;
    }

    /** After a tick's move: wheels, clack at the joints, anyone in the way, and the clients. */
    protected void moved(ServerLevel level, Rail r, double ds, boolean changed, Ride ride) {
        animate(ds);
        double speed = Math.abs(ds) / DT;
        clack += Math.abs(ds);
        if (clack >= 2) {
            clack -= 2;
            play(level, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.12f + (float) speed * 0.02f, 1.5f + (float) speed * 0.03f);
        }
        nudge(level, ds / DT, ride);
        sync(changed);
    }

    void play(ServerLevel level, SoundEvent e, float vol, float pitch) {
        level.playSound(null, getX(), getY(), getZ(), e, SoundSource.NEUTRAL, vol, pitch);
    }

    void sync(boolean force) {
        if (level().isClientSide()) return;
        if (force || tickCount - lastSync >= 20) {
            lastSync = tickCount;
            entityData.set(DATA_SEG, seg);
            entityData.set(DATA_S, (float) s);
            entityData.set(DATA_U, (float) u);
            entityData.set(DATA_FRONT, front);
        }
    }

    /** The first solid block inside the outline, checking only the leading end when moving, dir along +s. */
    @Nullable BlockPos outlineHit(ServerLevel level, long seg, double at, int dir) {
        TrackGraph g = TrackGraph.get(level);
        double e = trackReach(), hw = halfWidth();
        double[] as = dir == 0 ? new double[]{-e + 0.05, -e / 2, 0, e / 2, e - 0.05} : new double[]{dir * e};
        for (double a : as) {
            double[] w0 = Trains.walk(g, seg, at, a);
            Rail r = w0 == null ? null : Trains.rail(g, (long) w0[0]);
            if (r == null) continue;
            double ss = Mth.clamp(w0[1], 0, r.length());
            double[] c = r.path().pose(ss);
            double h = c[2], base = r.top(ss);
            for (int i = 0; i <= 8; i++) {
                double w = -hw + i * hw / 4;
                double x = c[0] - w * Math.sin(h), z = c[1] + w * Math.cos(h);
                for (double y = base + 0.15; y <= base + outlineHeight(); y += 0.4) {
                    BlockPos bp = BlockPos.containing(x, y, z);
                    BlockState st = level.getBlockState(bp);
                    if (st.isAir() || st.is(ModContent.TRACK_NODE)) continue;
                    VoxelShape shape = st.getCollisionShape(level, bp);
                    if (shape.isEmpty()) continue;
                    double lx = x - bp.getX(), ly = y - bp.getY(), lz = z - bp.getZ();
                    for (AABB box : shape.toAabbs()) if (box.contains(lx, ly, lz)) return bp;
                }
            }
        }
        return null;
    }

    /** Runs down anything standing on the line, harder the faster it goes, and eases slow walkers aside. */
    private void nudge(ServerLevel level, double v, Ride ride) {
        double hf = frontHeading(), speed = Math.abs(v), e = trackReach(), hw = halfWidth();
        int dir = (int) Math.signum(v) * f();
        for (Entity t : level.getEntities(this, getBoundingBox().inflate(1.0, 0.5, 1.0), x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator()
                && !(x.getRootVehicle() instanceof TrackRunner) && !ride.safe().contains(x))) {
            double dx = t.getX() - getX(), dz = t.getZ() - getZ();
            double a = dx * Math.cos(hf) + dz * Math.sin(hf), w = -dx * Math.sin(hf) + dz * Math.cos(hf);
            double r = t.getBbWidth() / 2;
            if (Math.abs(a) > e + r || Math.abs(w) > hw + r || t.getY() > getY() + 1.5 || t.getY() + t.getBbHeight() < getY() - 0.3) continue;
            double side = w >= 0 ? 1 : -1;
            double sx = -Math.sin(hf) * side, sz = Math.cos(hf) * side;
            if (speed >= HIT_MIN && a * dir > -e / 2) {
                float dmg = ride.harm() * (float) (HIT_TOP * Math.min(1, (speed - HIT_MIN) / (CAP - HIT_MIN)));
                if (!((LivingEntity) t).hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(runDown()), this, ride.driver()), dmg)) continue;
                double k = HIT_KNOCK + 0.06 * speed, fx = Math.cos(hf) * dir, fz = Math.sin(hf) * dir;
                t.setDeltaMovement(t.getDeltaMovement().multiply(0.3, 0, 0.3).add(sx * k + fx * speed * 0.03, HIT_LIFT + 0.02 * speed, sz * k + fz * speed * 0.03));
                t.hurtMarked = true;
                root().u *= HIT_SLOW;
                if (ECHO) LOG.info("[{}] hit {} at {} b/s for {}", getType().toShortString(), t.getName().getString(), String.format(java.util.Locale.ROOT, "%.1f", speed), dmg);
            } else if (speed > 0.05) {
                t.push(sx * 0.04, 0, sz * 0.04);
                t.hurtMarked = true;
            }
        }
    }

    static ResourceKey<DamageType> damage(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, RailWorks.id(name));
    }

    // ---------------------------------------------------------------- client

    /** Client: the game time a train last placed this vehicle; it leaves its own moving alone while one does. */
    public long trainPlaced = Long.MIN_VALUE;

    /** Client: puts it where its train says, u its speed along +s, fallback the move to show if it changed section. */
    public void placeByTrain(Rail r, double ns, boolean nf, double nu, double fallback) {
        double moved = r.id() == seg && nf == front ? ns - s : fallback;
        seg = r.id();
        s = ns;
        front = nf;
        u = nu;
        placeOn(r);
        animate(moved);
        trainPlaced = level().getGameTime();
    }

    /** The client's own guess at its train's next speed towards the train's front, or NaN to follow the server. */
    public double predictTrain(double speed, int flip, boolean settled) {
        return Double.NaN;
    }

    private void clientTick() {
        if (level().getGameTime() - trainPlaced <= 2) return;
        // no train has placed it lately: sit where the server last said
        Rail r = rail(entityData.get(DATA_SEG));
        if (r == null) return;
        seg = r.id();
        s = entityData.get(DATA_S);
        front = entityData.get(DATA_FRONT);
        u = entityData.get(DATA_U);
        placeOn(r);
        animate(0);
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        seg = in.getLongOr("seg", 0);
        s = in.getDoubleOr("s", 0);
        u = in.getDoubleOr("u", 0);
        front = in.getBooleanOr("front", true);
        entityData.set(DATA_STAGE, in.getIntOr("stage", 0));
        String tow = in.getStringOr("tow", "");
        towId = tow.isEmpty() ? null : UUID.fromString(tow);
        towSign = in.getIntOr("tow_sign", 1);
        towEnd = in.getIntOr("tow_end", 1);
        if (towId != null) entityData.set(DATA_TOW_END, (byte) ((towEnd > 0 ? 1 : 0) | (towSign > 0 ? 2 : 0)));
        savedTrain = towId == null ? in.read("train", CompoundTag.CODEC).map(Train::load).orElse(null) : null;
        train = null;
        sync(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putLong("seg", seg);
        out.putDouble("s", s);
        out.putDouble("u", u);
        out.putBoolean("front", front);
        out.putInt("stage", stage());
        if (towId != null) {
            out.putString("tow", towId.toString());
            out.putInt("tow_sign", towSign);
            out.putInt("tow_end", towEnd);
        } else if (train != null && train.members.size() > 1) out.store("train", CompoundTag.CODEC, train.save());
    }
}
