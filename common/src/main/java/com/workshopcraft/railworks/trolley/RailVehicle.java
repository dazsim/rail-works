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
    static final EntityDataAccessor<Long> DATA_NEXT_A = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Long> DATA_NEXT_B = SynchedEntityData.defineId(RailVehicle.class, EntityDataSerializers.LONG);
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
    protected @Nullable Player pusher;
    protected int pushUntil, pushDir;

    // client only
    private boolean fresh = true, pending;
    private long snapSeg;
    private double snapS, snapU, lastAlong;
    private int snapAge, mismatch;
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
                .define(DATA_BRAKE, true).define(DATA_NEXT_A, -1L).define(DATA_NEXT_B, -1L).define(DATA_TOW, 0).define(DATA_TOW_END, (byte) 3);
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
            if (++towLost > 40) {
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
        BlockPos hit = v.outlineHit(rail(sg), v.s, 0);
        if (hit != null) {
            say(player, Component.translatable("message.railworks.trolley.no_room", hit.getX(), hit.getY(), hit.getZ()));
            return false;
        }
        v.sync(true);
        level.addFreshEntity(v);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1, 0.8f);
        return true;
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
        TrackGraph.Segment nx = nextFor(g, sg, nodeId);
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
                    root().tell(Component.translatable("message.railworks.trolley.thrown", n.pos.getX(), n.pos.getY(), n.pos.getZ()).withStyle(ChatFormatting.GOLD), true);
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
        return new Hop(rail(nx), nx.b == nodeId && nx.a != nodeId);
    }

    static final double FOLLOW = 12;

    /** The section taken over a node: at facing points the leg a coupled neighbour is already on, else the route. */
    TrackGraph.@Nullable Segment nextFor(TrackGraph g, TrackGraph.Segment sg, long nodeId) {
        TrackGraph.Segment nx = g.next(nodeId, sg.id);
        TrackGraph.Node n = g.node(nodeId);
        if (nx == null || n == null) return nx;
        int p = g.pointsPort(n);
        if (p < 0 || (sg.a == nodeId ? sg.aPort : sg.bPort) == p) return nx;
        List<RailVehicle> near = coupled();
        if (near.isEmpty()) return nx;
        List<TrackGraph.Segment> legs = g.legs(n);
        for (TrackGraph.Segment leg : legs) for (RailVehicle o : near) if (o.seg == leg.id) return leg;
        for (TrackGraph.Segment leg : legs) for (RailVehicle o : near) if (beyond(g, leg, nodeId, o.seg, FOLLOW)) return leg;
        return nx;
    }

    List<RailVehicle> coupled() {
        List<RailVehicle> out = new ArrayList<>(followers());
        RailVehicle l = leader();
        if (l != null) out.add(l);
        return out;
    }

    /** Whether section target lies on sg or past it, entering sg at from, within left blocks of legal moves. */
    static boolean beyond(TrackGraph g, TrackGraph.Segment sg, long from, long target, double left) {
        if (sg.id == target) return true;
        long far = sg.a == from ? sg.b : sg.a;
        left -= sg.path.length;
        TrackGraph.Node n = g.node(far);
        if (left <= 0 || n == null || n.buffer || far == from) return false;
        int p = g.pointsPort(n);
        if (p >= 0 && (sg.a == far ? sg.aPort : sg.bPort) != p) {
            for (TrackGraph.Segment l : g.legs(n)) if (l.live && beyond(g, l, far, target, left)) return true;
            return false;
        }
        TrackGraph.Segment nx = g.next(far, sg.id);
        return nx != null && nx.live && beyond(g, nx, far, target, left);
    }

    /**
     * Puts a consist found split over both legs of a set of points (an old save) back on the root's leg,
     * or takes the bar off where that can't be done.
     */
    void mend(ServerLevel level, List<Member> ms) {
        TrackGraph g = TrackGraph.get(level);
        for (int i = 1; i < ms.size(); i++) {
            RailVehicle b = ms.get(i).v, a = null;
            for (int j = 0; j < i && a == null; j++) {
                RailVehicle o = ms.get(j).v;
                if (o.getUUID().equals(b.towId) || b.getUUID().equals(o.towId)) a = o;
            }
            if (a == null || a.seg == b.seg) continue;
            TrackGraph.Segment sa = g.segment(a.seg), sb = g.segment(b.seg);
            if (sa == null || sb == null) continue;
            long node = sa.a == sb.a || sa.a == sb.b ? sa.a : sa.b == sb.a || sa.b == sb.b ? sa.b : -1;
            TrackGraph.Node n = g.node(node);
            if (n == null || g.pointsPort(n) < 0) continue;
            List<TrackGraph.Segment> legs = g.legs(n);
            if (!legs.contains(sa) || !legs.contains(sb) || sa.a == sa.b || sb.a == sb.b) continue;
            if (beyond(g, sa, node, sb.id, sa.path.length + 16)) continue;
            double db = sb.a == node ? b.s : sb.path.length - b.s;
            double da = sa.a == node ? a.s : sa.path.length - a.s;
            boolean ok = db <= sa.path.length && Math.abs(da - db) >= a.trackReach() + b.trackReach() - 0.3;
            double ns = sa.a == node ? db : sa.path.length - db;
            Rail r = rail(sa.id);
            if (ok && r != null) ok = b.outlineHit(r, ns, 0) == null && clearOf(level, b, sa.id, ns, ms);
            if (!ok || r == null) {
                b.uncoupleFrom(level, a);
                notifyNear(level, b, Component.translatable("message.railworks.tow.split").withStyle(ChatFormatting.YELLOW));
                if (ECHO) LOG.info("[tow] split over points at {}, uncoupled", n.pos);
                return;
            }
            if ((sa.a == node) != (sb.a == node)) {
                b.u = -b.u;
                b.front = !b.front;
            }
            b.seg = sa.id;
            b.s = ns;
            b.placeOn(r);
            b.sync(true);
            notifyNear(level, b, Component.translatable("message.railworks.tow.rejoined").withStyle(ChatFormatting.GOLD));
            if (ECHO) LOG.info("[tow] split over points at {}, put back on {} at {}", n.pos, sa.id, ns);
        }
    }

    private static boolean clearOf(ServerLevel level, RailVehicle b, long seg, double at, List<Member> ms) {
        for (RailVehicle o : level.getEntitiesOfClass(RailVehicle.class, b.getBoundingBox().inflate(60), o -> o != b && o.seg == seg)) {
            boolean ours = false;
            for (Member m : ms) ours |= m.v == o;
            if (!ours && Math.abs(o.s - at) < o.trackReach() + b.trackReach()) return false;
        }
        return true;
    }

    private void uncoupleFrom(ServerLevel level, RailVehicle other) {
        if (other.getUUID().equals(towId)) uncouple(level, null);
        else other.uncouple(level, null);
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

    boolean pushed() {
        return pusher != null && tickCount <= pushUntil && pusher.isAlive();
    }

    /** People pushing by hand, along the root's +s. */
    double pushForce(List<Member> ms, double m) {
        double f = 0;
        for (Member x : ms) {
            if (!x.v.pushed()) continue;
            int d = x.v.pushDir * x.sign * f();
            f += d * PUSH_F * Mth.clamp((PUSH_TOP - u * d) / 0.4, 0, 1);
        }
        return f / m;
    }

    static boolean anyPushed(List<Member> ms) {
        for (Member x : ms) if (x.v.pushed()) return true;
        return false;
    }

    record Stop(double dist, boolean buffer) {}

    static final Stop NONE = new Stop(Double.MAX_VALUE, false);

    Stop stopAhead(ServerLevel level, int dir) {
        TrackGraph g = TrackGraph.get(level);
        TrackGraph.Segment sg = g.segment(seg);
        if (sg == null || !sg.live) return new Stop(0, false);
        double d = dir > 0 ? sg.path.length - s : s;
        boolean atB = dir > 0;
        for (int i = 0; i < 6 && d < 80; i++) {
            long nodeId = atB ? sg.b : sg.a;
            TrackGraph.Node n = g.node(nodeId);
            if (n != null && n.buffer) return new Stop(d - 0.59 - trackReach(), true);
            TrackGraph.Segment nx = n == null ? null : nextFor(g, sg, nodeId);
            if (nx == null || !nx.live) return new Stop(d - trackReach() + 0.05, false);
            atB = !(nx.b == nodeId && nx.a != nodeId);
            d += nx.path.length;
            sg = nx;
        }
        return NONE;
    }

    /** Moves along the line, hopping sections. Returns false if it ran out of track. */
    boolean advance(double ds) {
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
     * Moves the whole consist one tick, this vehicle being its root. force and drag are accelerations along
     * our +s. Returns the distance moved along our +s.
     */
    double drive(ServerLevel level, List<Member> ms, double force, double drag, boolean hold, Ride ride, boolean changed) {
        if (ms.size() > 1 && tickCount % 10 == 0) mend(level, ms);
        int n = ms.size(), fr = f();
        int[] k = new int[n];
        for (int i = 0; i < n; i++) k[i] = ms.get(i).sign * ms.get(i).v.f() * fr;
        int dir = u > 0.001 ? 1 : u < -0.001 ? -1 : 0;
        if (dir == 0) dir = (int) Math.signum(force);
        Stop stop = NONE;
        if (dir != 0) {
            for (int i = 0; i < n; i++) {
                Stop st = ms.get(i).v.stopAhead(level, dir * k[i]);
                if (st.dist() < stop.dist()) stop = st;
            }
        }
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
            for (int i = 0; i < n; i++) {
                RailVehicle v = ms.get(i).v;
                Rail r = v.rail(v.seg);
                if (r == null) continue;
                double dm = ds * k[i];
                BlockPos hit = v.outlineHit(r, Mth.clamp(v.s + dm, 0, r.length()), (int) Math.signum(dm));
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
            TrackRunner.Contact c = null;
            RailVehicle by = this;
            for (int i = 0; i < n; i++) {
                TrackRunner.Contact ci = TrackRunner.ahead(level, ms.get(i).v, d * k[i], Math.abs(ds) + 0.05);
                if (ci == null || ms.stream().anyMatch(x -> x.v == ci.other())) continue;
                if (c == null || ci.gap() < c.gap()) {
                    c = ci;
                    by = ms.get(i).v;
                }
            }
            if (c != null && c.gap() < Math.abs(ds)) {
                double v1 = d * u, v2 = c.k() * c.other().trackSpeed();
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
                    c.other().bumpTrackSpeed(c.k() * v[1]);
                }
                ds = d * Math.max(0, c.gap());
                stopped = true;
            }
        }
        stopCooldown--;
        long[] before = new long[n];
        for (int i = 0; i < n; i++) {
            RailVehicle v = ms.get(i).v;
            before[i] = v.seg;
            if (ds != 0 && !v.advance(ds * k[i])) {
                stopped = true;
                u = 0;
            }
        }
        for (int i = 0; i < n; i++) {
            RailVehicle v = ms.get(i).v;
            v.u = u * ms.get(i).sign * v.f() * f();
            Rail now = v.rail(v.seg);
            if (now == null) continue;
            v.placeOn(now);
            v.moved(level, now, ds * k[i], changed || stopped || before[i] != v.seg, ride);
        }
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

    private long nextCode(TrackGraph g, TrackGraph.Segment sg, long nodeId) {
        TrackGraph.Node n = g.node(nodeId);
        if (n == null || n.buffer) return -1;
        TrackGraph.Segment nx = nextFor(g, sg, nodeId);
        if (nx == null || !nx.live) return -1;
        return nx.id * 2 + (nx.b == nodeId && nx.a != nodeId ? 1 : 0);
    }

    /** The first solid block inside the outline, checking only the leading end when moving. */
    @Nullable BlockPos outlineHit(Rail r, double at, int dir) {
        double[] c = r.path().pose(at);
        double h = c[2], e = trackReach(), hw = halfWidth();
        double[] as = dir == 0 ? new double[]{-e + 0.05, -e / 2, 0, e / 2, e - 0.05} : new double[]{dir * e};
        for (double a : as) {
            double base = r.top(Mth.clamp(at + a, 0, r.length()));
            double x0 = c[0] + a * Math.cos(h), z0 = c[1] + a * Math.sin(h);
            for (int i = 0; i <= 8; i++) {
                double w = -hw + i * hw / 4;
                double x = x0 - w * Math.sin(h), z = z0 + w * Math.cos(h);
                for (double y = base + 0.15; y <= base + outlineHeight(); y += 0.4) {
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

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide() && (key == DATA_S || key == DATA_SEG || key == DATA_U)) pending = true;
    }

    /** The client's own guess at this tick's move along +s, or NaN to follow the server. */
    protected double predict(Rail r) {
        return Double.NaN;
    }

    protected double snapU() {
        return snapU;
    }

    protected boolean nearSnap() {
        return snapAge < 10 && Math.abs(snapS - s) < 0.5 && snapSeg == seg;
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
        RailVehicle l = towed() ? leader() : null;
        double ds = l == null || l.fresh ? predict(r) : Double.NaN;
        boolean own = !Double.isNaN(ds);
        if (l != null && !l.fresh) {
            u += (snapU - u) * 0.5;
            ds = l.lastAlong * towSign() * f();
            own = true;
        } else if (!own) {
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
            else s += err * (own ? 0.1 : 0.25);
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
        lastAlong = moved * f();
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
        }
    }
}
