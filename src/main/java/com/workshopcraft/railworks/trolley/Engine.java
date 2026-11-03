package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.RailWorksConfig;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.trolley.EngineShape.Fix;
import com.workshopcraft.railworks.trolley.EngineShape.Part;
import com.workshopcraft.railworks.trolley.EngineShape.Stage;
import com.workshopcraft.railworks.trolley.EngineShape.Unit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pannier tank engine: built stage by stage on the track like a carriage, then fired, watered and driven
 * from the cab. It drives whatever train it is coupled into.
 */
public class Engine extends Carriage {
    public static final int DONE = EngineShape.STAGES.size();
    static final double OWN_MASS = 10, TANK = 24000, PSI = 200;
    static final int BUNKER = 256, GREEN = 4, BLACK_COATS = 2;
    static final Vec3 DRIVER = new Vec3(-3.2, 1.7, 1.1);
    static final EntityDataAccessor<Integer> DATA_FIT = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_WORK = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_PAINT = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> DATA_CTRL = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> DATA_STEAM = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> DATA_WATER = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> DATA_HEAT = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> DATA_COAL = SynchedEntityData.defineId(Engine.class, EntityDataSerializers.INT);

    // controls handles in the engine's frame: x0, x1, y0, y1, z0, z1
    static final double[][] HANDLES = {
            {-2.75, -2.35, 2.9, 3.1, -0.05, 0.8},   // regulator
            {-2.8, -2.35, 2.7, 2.9, 1.0, 1.4},      // brake valve
            {-4.25, -3.93, 1.7, 2.86, 1.62, 1.92},  // hand brake
            {-2.5, -2.33, 4.05, 4.65, 0.82, 1.0},   // whistle
            {-2.3, -1.48, 2.7, 3.06, 1.58, 1.94},   // reverser
            {-2.45, -2.3, 2.05, 2.6, -0.3, 0.3},    // firehole
    };
    static final int REGULATOR = 0, BRAKE_VALVE = 1, HAND_BRAKE = 2, WHISTLE = 3, REVERSER = 4, FIREHOLE = 5;

    private double burn;
    private int coke;
    private int coal, notch, whistleFor, readoutIn, sayCold;
    private @Nullable CarriageBody shape;
    private int shapeKey = -1;
    public float spin, spinO;
    int beat, valveIn;
    private double lastX, lastZ;

    public Engine(EntityType<? extends Engine> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_FIT, 0).define(DATA_WORK, 0).define(DATA_PAINT, 0).define(DATA_CTRL, 5 << 4)
                .define(DATA_STEAM, 0f).define(DATA_WATER, 0).define(DATA_HEAT, 0f).define(DATA_COAL, 0);
    }

    // ---------------------------------------------------------------- shape

    @Override
    public int doneStage() {
        return DONE;
    }

    @Override
    protected double ownMass() {
        return OWN_MASS;
    }

    @Override
    public double trackReach() {
        return 5.38;
    }

    @Override
    protected double wheelRadius() {
        return EngineShape.WR;
    }

    @Override
    protected double halfWidth() {
        return 2.2;
    }

    @Override
    protected double outlineHeight() {
        return 5.45;
    }

    @Override
    public double eyeReach() {
        return 5.02;
    }

    @Override
    boolean placed() {
        return true;
    }

    @Override
    public void afterPlace() {
        carry();
    }

    @Override
    boolean seatsRiders() {
        return false;
    }

    @Override
    boolean aboardBox(Vec3 l) {
        return Math.abs(l.x) <= 5.4 && Math.abs(l.z) <= 2.3 && l.y >= 0.45 && l.y <= 6.4;
    }

    /** Pitched to the chord between the outer axles, as the frames ride. */
    @Override
    void placeOn(Rail r) {
        double a = s - 2.3, b = s + 2.3;
        if (a < 0 || b > r.length()) {
            super.placeOn(r);
            return;
        }
        double[] p = r.path().pose(s), pa = r.path().pose(a), pb = r.path().pose(b);
        double h = Math.atan2(pb[1] - pa[1], pb[0] - pa[0]) + (front ? 0 : Math.PI);
        setYRot((float) Math.toDegrees(h) - 90);
        setPos(p[0], r.top(s), p[1]);
        setXRot((float) -Math.toDegrees(Math.atan((r.top(b) - r.top(a)) / 4.6 * (front ? 1 : -1))));
    }

    public int stage() {
        return Math.min(entityData.get(DATA_STAGE), DONE);
    }

    public int fit() {
        return entityData.get(DATA_FIT);
    }

    public int work() {
        return entityData.get(DATA_WORK);
    }

    /** Green coats in the low four bits, black above. */
    public int paint() {
        return entityData.get(DATA_PAINT);
    }

    public boolean green() {
        return (paint() & 15) >= GREEN;
    }

    public boolean black() {
        return (paint() >> 4) >= BLACK_COATS;
    }

    static List<Unit> units(int st) {
        return EngineShape.STAGES.get(st).units();
    }

    /** How many units of stage st are on. */
    public int fitted(int st) {
        int cur = stage();
        return st < cur ? units(st).size() : st == cur ? fit() : 0;
    }

    /** Blows owed to stage st's first f units. */
    static int allowed(int st, int f) {
        Stage g = EngineShape.STAGES.get(st);
        if (g.fix() == Fix.BLOW) return f;
        if (g.fix() == Fix.NONE) return 0;
        int n = 0;
        for (int i = 0; i < f; i++) n += g.units().get(i).rivets().size();
        return n;
    }

    /** Rivet sets driven on unit ui of stage st, or every one once the stage is done. */
    public int rivetsSet(int st, int ui) {
        int cur = stage();
        List<Unit> us = units(st);
        if (st < cur) return us.get(ui).rivets().size();
        if (st > cur) return 0;
        int w = work();
        for (int i = 0; i < ui; i++) w -= us.get(i).rivets().size();
        return Mth.clamp(w, 0, us.get(ui).rivets().size());
    }

    /** Whether unit ui of stage st is hammered home, for BLOW stages. */
    public boolean blown(int st, int ui) {
        return st < stage() || (st == stage() && ui < work());
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(RailWorks.id(id));
    }

    @Nullable Unit next() {
        int st = stage();
        if (st >= DONE) return null;
        int f = fit();
        return f < units(st).size() ? units(st).get(f) : null;
    }

    @Override
    CarriageBody body() {
        int key = stage() * 64 + fit();
        if (shape == null || shapeKey != key) {
            List<double[]> bs = new ArrayList<>();
            for (int st = 0; st < DONE; st++) {
                int f = fitted(st);
                for (int i = 0; i < f; i++) for (Part p : units(st).get(i).parts()) {
                    double[] e = extent(p);
                    double[] d = {e[3] - e[0], e[4] - e[1], e[5] - e[2]};
                    java.util.Arrays.sort(d);
                    if (d[1] < 0.12 || p.zone().equals("GLASS")) continue;
                    bs.add(new double[] {e[0], e[1], e[2], e[3], e[4], e[5]});
                }
            }
            shape = CarriageBody.of("engine" + key, bs, 5.9);
            shapeKey = key;
        }
        return shape;
    }

    @Override
    CarriageBody shutBody() {
        return body();
    }

    /** The box a turned part fills, in the engine's frame. */
    static double[] extent(Part p) {
        double hx = p.sx() / 2, hy = p.sy() / 2, hz = p.sz() / 2, ex = hx, ey = hy, ez = hz;
        float[] m = p.m();
        if (m != null) {
            ex = Math.abs(m[0]) * hx + Math.abs(m[1]) * hy + Math.abs(m[2]) * hz;
            ey = Math.abs(m[3]) * hx + Math.abs(m[4]) * hy + Math.abs(m[5]) * hz;
            ez = Math.abs(m[6]) * hx + Math.abs(m[7]) * hy + Math.abs(m[8]) * hz;
        }
        return new double[] {p.cx() - ex, p.cy() - ey, p.cz() - ez, p.cx() + ex, p.cy() + ey, p.cz() + ez};
    }

    public static boolean place(ServerLevel level, Player player, Vec3 at, ItemStack stack) {
        Engine e = ModContent.ENGINE_ENTITY.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (e == null) return false;
        e.entityData.set(DATA_STAGE, 0);
        e.entityData.set(DATA_FIT, 1);
        return put(level, player, at, e);
    }

    // ---------------------------------------------------------------- controls state

    public int ctrl() {
        return entityData.get(DATA_CTRL);
    }

    public int regulator() {
        return ctrl() & 15;
    }

    public int reverser() {
        return (ctrl() >> 4 & 15) - 5;
    }

    public boolean brakeOn() {
        return (ctrl() >> 8 & 1) != 0;
    }

    public boolean handBrake() {
        return (ctrl() >> 9 & 1) != 0;
    }

    public boolean whistling() {
        return (ctrl() >> 10 & 1) != 0;
    }

    public boolean lit() {
        return (ctrl() >> 11 & 1) != 0;
    }

    public boolean slipping() {
        return (ctrl() >> 12 & 1) != 0;
    }

    public boolean lifting() {
        return (ctrl() >> 13 & 1) != 0;
    }

    public float steam() {
        return entityData.get(DATA_STEAM);
    }

    public int water() {
        return entityData.get(DATA_WATER);
    }

    public float heat() {
        return entityData.get(DATA_HEAT);
    }

    public int coal() {
        return entityData.get(DATA_COAL);
    }

    void setCtrl(int reg, int rev, boolean brake, boolean hand, boolean whistle, boolean lit, boolean slip, boolean lift) {
        int c = Mth.clamp(reg, 0, 10) | (Mth.clamp(rev, -5, 5) + 5) << 4 | (brake ? 1 : 0) << 8 | (hand ? 1 : 0) << 9
                | (whistle ? 1 : 0) << 10 | (lit ? 1 : 0) << 11 | (slip ? 1 : 0) << 12 | (lift ? 1 : 0) << 13;
        if (c != ctrl()) entityData.set(DATA_CTRL, c);
    }

    private void setBit(int bit, boolean on) {
        int c = on ? ctrl() | 1 << bit : ctrl() & ~(1 << bit);
        if (c != ctrl()) entityData.set(DATA_CTRL, c);
    }

    private void setReg(int r) {
        entityData.set(DATA_CTRL, ctrl() & ~15 | Mth.clamp(r, 0, 10));
    }

    private void setRev(int r) {
        entityData.set(DATA_CTRL, ctrl() & ~(15 << 4) | (Mth.clamp(r, -5, 5) + 5) << 4);
    }

    @Nullable ServerPlayer driver() {
        for (Entity e : getPassengers()) if (e instanceof ServerPlayer sp) return sp;
        return null;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected void clientTick() {
        super.clientTick();
        spinO = spin;
        double hf = frontHeading(), d = (getX() - lastX) * Math.cos(hf) + (getZ() - lastZ) * Math.sin(hf);
        if (Math.abs(d) < 2) spin -= (float) (d / EngineShape.WR);
        lastX = getX();
        lastZ = getZ();
        if (done()) EngineEffects.tick(this);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Rail r = rail(seg);
        if (r == null) return;
        if (!done()) {
            u = 0;
            afterPlace();
            sync(false);
            return;
        }
        boiler(level);
        if (towTick(level)) return;
        Train t = train(level);
        if (t == null) return;
        drives(level, this, t);
    }

    /** Fire, water and steam, one tick. */
    private void boiler(ServerLevel level) {
        float p = steam(), h = heat();
        int w = water();
        boolean lit = lit();
        ServerPlayer dr = driver();
        if (RailWorksConfig.endlessTrain) {
            if (whistleFor > 0) whistleFor--;
            setBit(10, whistleFor > 0);
            readout(dr);
            return;
        }
        double demand = regulator() / 10.0 * cutoff() * (0.3 + 0.7 * Math.min(1, Math.abs(root().u) / 10));
        double want = !lit ? 0 : dr == null ? 0.25 : p < 0.9 ? 1 : 0.35 + 0.65 * demand;
        if (p > 0.95) want = Math.min(want, 0.9 * demand + 0.04);
        if (lit && coal == 0 && coke <= 0 && burn <= 0) want = 0;
        if (lit) want = Math.min(want, w / 6000.0);
        h += (float) Mth.clamp(want - h, -1 / 200.0, 1 / 200.0);
        if (lit) {
            burn -= h;
            if (burn <= 0 && want > 0) {
                if (coke > 0) {
                    coke--;
                    burn += 600;
                } else if (coal > 0) {
                    coal--;
                    burn += 400;
                }
            }
            if (h < 0.02 && burn <= 0 && coal == 0 && coke == 0) lit = false;
        }
        double gen = h / 1200.0;
        double use = p > 0.1 ? demand * 0.9 / 1200 : 0;
        p += (float) (gen - use - 0.05 / 1200 * p);
        w -= (int) Math.round(gen * 2000);
        boolean lift = false;
        if (p >= 1) {
            p = 1;
            lift = true;
        }
        if (w <= 0) {
            w = 0;
            if (h > 0) h = Math.max(0, h - 0.02f);
        }
        p = Mth.clamp(p, 0, 1);
        if (whistleFor > 0) whistleFor--;
        if (Math.abs(p - steam()) > 1e-5) entityData.set(DATA_STEAM, p);
        if (Math.abs(h - heat()) > 1e-5) entityData.set(DATA_HEAT, h);
        if (w != water()) entityData.set(DATA_WATER, w);
        int c = coal + (int) coke;
        if (c != coal()) entityData.set(DATA_COAL, c);
        setBit(11, lit);
        setBit(13, lift);
        setBit(10, whistleFor > 0 && p > 0.05);
        readout(dr);
    }

    /** Cut off as a share of full gear. */
    double cutoff() {
        return reverser() == 0 ? 0 : 0.5 + 0.5 * Math.abs(reverser()) / 5.0;
    }

    @Override
    protected void tell(Component c, boolean echo) {
        ServerPlayer dr = driver();
        if (dr != null) {
            dr.sendOverlayMessage(c);
            readoutIn = 50;
        }
        super.tell(c, echo);
    }

    private void readout(@Nullable ServerPlayer dr) {
        if (dr == null || --readoutIn > 0) return;
        readoutIn = 10;
        int rev = reverser();
        String gear = rev == 0 ? "mid" : (rev > 0 ? "fwd " : "back ") + Math.round(cutoff() * 75) + "%";
        Component brake = Component.translatable(handBrake() ? "message.railworks.engine.hand_brake_on" : brakeOn() ? "message.railworks.engine.brake_on" : "message.railworks.engine.brake_off");
        dr.sendOverlayMessage(Component.translatable("message.railworks.engine.readout",
                String.format("%.1f", Math.abs(root().u)), Math.round(steam() * PSI), water() / 1000, (int) (TANK / 1000), regulator(), gear, brake)
                .withStyle(slipping() ? ChatFormatting.RED : lifting() ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
    }

    /** The driver's keys: W/S the regulator, D/A the reverser, jump the brake, sprint the whistle. */
    private void keys(ServerPlayer dr) {
        Input in = dr.getLastClientInput();
        boolean brake = in.jump();
        if (brake != brakeOn()) setBit(8, brake);
        if (in.sprint()) whistle();
        if (notch > 0) {
            notch--;
            return;
        }
        int reg = regulator(), rev = reverser();
        if (in.forward()) reg++;
        if (in.backward()) reg--;
        if (in.right()) rev++;
        if (in.left()) rev--;
        reg = Mth.clamp(reg, 0, 10);
        rev = Mth.clamp(rev, -5, 5);
        if (reg != regulator() || rev != reverser()) {
            setReg(reg);
            setRev(rev);
            notch = 4;
            readoutIn = 0;
        }
    }

    private void whistle() {
        if (whistleFor <= 0 && (steam() > 0.05 || RailWorksConfig.endlessTrain) && level() instanceof ServerLevel sl)
            play(sl, ModContent.ENGINE_WHISTLE, 2.5f, 1.0f);
        whistleFor = Math.max(whistleFor, 8);
    }

    /** Moves the train t from its root when a finished engine is in it; false leaves it to the root. */
    static boolean drives(ServerLevel level, Carriage root, Train t) {
        for (Member x : t.members) if (x.v() instanceof Engine e && e.done()) {
            e.drive(level, root, t, x.sign() * root.f());
            return true;
        }
        return false;
    }

    private void drive(ServerLevel level, Carriage root, Train t, int sgn) {
        List<Member> ms = t.members;
        double m = mass(ms);
        double ru = root.u, ue = ru * sgn;
        ServerPlayer dr = driver();
        if (dr != null) keys(dr);
        boolean pushed = anyPushed(ms);
        List<Entity> safe = new ArrayList<>();
        pushersOf(ms, safe);
        for (Entity e : getPassengers()) safe.add(e);
        Entity blame = dr != null ? dr : safe.isEmpty() ? null : safe.getFirst();
        double force = root.gradeForce(ms, m), drag = 0.04 + 0.0018 * ru * ru;
        boolean slip = false, brake;
        if (RailWorksConfig.endlessTrain) {
            int dir = reverser() < 0 ? -1 : 1;
            force += 3.0 * dir * sgn;
            brake = false;
        } else {
            double p = steam();
            if (dr == null && regulator() != 0) setReg(0);
            int rev = reverser();
            double tr = 0;
            if (rev != 0 && regulator() > 0) {
                double pf = Mth.clamp((p - 0.1) / 0.7, 0, 1);
                tr = Math.min(14, 115 / Math.max(Math.abs(ue), 0.1)) * regulator() / 10.0 * pf * cutoff();
                if (tr > 13 && Math.abs(ue) < 2.5) {
                    tr = 6;
                    slip = true;
                }
            }
            force += tr / m * Math.signum(rev) * sgn;
            if (p <= 0.05) force += root.pushForce(ms, m);
            if (dr != null) brake = brakeOn();
            else brake = p > 0.05 || !pushed;
            double bf = (brake ? 14.5 + 0.6 * (m - OWN_MASS) : 0) + (handBrake() ? 14.5 : 0);
            drag += bf / m;
            if (dr == null) setBit(8, brake);
        }
        setBit(12, slip);
        for (Member x : ms) if (x.v() != this && !x.v().isTrolley()) x.v().towedBrake(brake);
        towedBrake(brake || handBrake());
        boolean hold = dr != null || Math.abs(ru) > 0.01;
        root.drive(level, t, force, drag, hold, new Ride(blame, 0.5f, safe), false);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player hp && other(hp) instanceof Picked pk) return pk.v().hurtServer(level, source, damage);
        if (source.getEntity() instanceof Player p && p.getMainHandItem().is(ModContent.HAMMER) && !p.isSecondaryUseActive()) hammer(level, p, null);
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        Picked pk = other(player);
        if (pk != null) return pk.v().interact(player, hand, pk.loc());
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModContent.TOW_BAR)) return Coupling.click(this, player, location, held);
        boolean server = level() instanceof ServerLevel;
        ServerLevel sl = server ? (ServerLevel) level() : null;
        if (held.is(ModContent.HAMMER)) {
            if (sl != null) {
                if (player.isSecondaryUseActive()) sneakHammer(sl, player, location);
                else hammer(sl, player, location);
            }
            return InteractionResult.SUCCESS;
        }
        Unit nx = next();
        if (nx != null && !held.isEmpty() && held.is(item(nx.item()))) {
            if (sl != null) fit(sl, player, held);
            return InteractionResult.SUCCESS;
        }
        if (!done()) {
            if (!held.isEmpty() && held.getItem() instanceof net.minecraft.world.item.BlockItem) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND && sl != null) say(player, status());
            return hand == InteractionHand.MAIN_HAND ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (held.is(Items.GREEN_DYE) || held.is(Items.BLACK_DYE)) {
            if (sl != null) paintWith(sl, player, held);
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.COAL) || held.is(Items.CHARCOAL) || held.is(ModContent.COKE)) {
            if (sl != null) {
                int room = BUNKER - coal - (int) coke, n = Math.min(room, held.getCount());
                if (n <= 0) say(player, Component.translatable("message.railworks.engine.bunker_full"));
                else {
                    if (held.is(ModContent.COKE)) coke += n;
                    else coal += n;
                    if (!player.getAbilities().instabuild) held.shrink(n);
                    entityData.set(DATA_COAL, coal + (int) coke);
                    play(sl, SoundEvents.GRAVEL_PLACE, 0.8f, 0.8f);
                    say(player, Component.translatable("message.railworks.engine.bunker", coal + (int) coke, BUNKER));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.WATER_BUCKET)) {
            if (sl != null) {
                if (water() + 1000 > TANK) say(player, Component.translatable("message.railworks.engine.tanks_full"));
                else {
                    entityData.set(DATA_WATER, water() + 1000);
                    if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
                    play(sl, SoundEvents.BUCKET_EMPTY, 1, 1);
                    say(player, Component.translatable("message.railworks.engine.tanks", water() / 1000, (int) (TANK / 1000)));
                }
            }
            return InteractionResult.SUCCESS;
        }
        int hdl = handle(player);
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) {
            if (sl != null) light(sl, player, hand, held);
            return InteractionResult.SUCCESS;
        }
        if (hdl >= 0 && hand == InteractionHand.MAIN_HAND) {
            if (sl != null) handle(sl, player, hdl);
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || !held.isEmpty()) return InteractionResult.PASS;
        if (sl != null) {
            if (player.getVehicle() == this) return InteractionResult.PASS;
            if (!getPassengers().isEmpty()) say(player, Component.translatable("message.railworks.engine.taken"));
            else say(player, status());
        }
        return InteractionResult.SUCCESS;
    }

    /** Which handle the player is looking at, by their own ray in the engine's frame. */
    int handle(Player player) {
        CarriageBody.Pose ps = pose();
        Vec3 o = ps.local(player.getEyePosition()), d = ps.localVec(player.getViewVector(1));
        int best = -1;
        double bt = player.blockInteractionRange() + 1;
        for (int i = 0; i < HANDLES.length; i++) {
            double[] b = HANDLES[i];
            double t = hit(o, d, b[0] - 0.04, b[1] + 0.04, b[2] - 0.04, b[3] + 0.04, b[4] - 0.04, b[5] + 0.04);
            if (t >= 0 && t < bt) {
                bt = t;
                best = i;
            }
        }
        return best;
    }

    static double hit(Vec3 o, Vec3 d, double x0, double x1, double y0, double y1, double z0, double z1) {
        double t0 = 0, t1 = 1e9;
        double[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z}, lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(dd[k]) < 1e-9) {
                if (oo[k] < lo[k] || oo[k] > hi[k]) return -1;
                continue;
            }
            double a = (lo[k] - oo[k]) / dd[k], b = (hi[k] - oo[k]) / dd[k];
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
        }
        return t0 <= t1 ? t0 : -1;
    }

    private void handle(ServerLevel level, Player player, int h) {
        switch (h) {
            case REGULATOR -> {
                setReg(regulator() + (player.isSecondaryUseActive() ? -1 : 1));
                play(level, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.4f, 1.6f);
                say(player, Component.translatable("message.railworks.engine.regulator", regulator()));
            }
            case BRAKE_VALVE -> {
                setBit(8, !brakeOn());
                play(level, SoundEvents.FIRE_EXTINGUISH, 0.3f, 0.6f);
                say(player, Component.translatable(brakeOn() ? "message.railworks.engine.brake_on" : "message.railworks.engine.brake_off"));
            }
            case HAND_BRAKE -> {
                setBit(9, !handBrake());
                play(level, SoundEvents.CHAIN_PLACE, 0.8f, 0.8f);
                say(player, Component.translatable(handBrake() ? "message.railworks.engine.hand_brake_on" : "message.railworks.engine.hand_brake_off"));
            }
            case WHISTLE -> whistle();
            case REVERSER -> takeControls(player);
            default -> say(player, firebox());
        }
    }

    private void takeControls(Player player) {
        if (player.getVehicle() == this) return;
        if (!getPassengers().isEmpty()) {
            say(player, Component.translatable("message.railworks.engine.taken"));
            return;
        }
        if (player.startRiding(this)) say(player, Component.translatable("message.railworks.engine.controls"));
    }

    private void light(ServerLevel level, Player player, InteractionHand hand, ItemStack held) {
        if (lit()) {
            say(player, firebox());
            return;
        }
        if (coal == 0 && coke == 0) {
            say(player, Component.translatable("message.railworks.engine.no_fuel"));
            return;
        }
        if (water() < 2000) {
            say(player, Component.translatable("message.railworks.engine.no_water"));
            return;
        }
        setBit(11, true);
        if (held.is(Items.FIRE_CHARGE)) {
            if (!player.getAbilities().instabuild) held.shrink(1);
        } else held.hurtAndBreak(1, player, hand);
        play(level, SoundEvents.FLINTANDSTEEL_USE, 1, 1);
        play(level, SoundEvents.FIRECHARGE_USE, 0.5f, 0.8f);
        say(player, Component.translatable("message.railworks.engine.lit"));
    }

    Component firebox() {
        return Component.translatable("message.railworks.engine.firebox",
                Component.translatable(lit() ? "message.railworks.engine.fire_lit" : "message.railworks.engine.fire_out"),
                Math.round(steam() * PSI), water() / 1000, (int) (TANK / 1000), coal(), BUNKER);
    }

    Component status() {
        if (done()) return firebox();
        Unit nx = next();
        Stage g = EngineShape.STAGES.get(stage());
        if (nx != null && work() >= allowed(stage(), fit()))
            return Component.translatable("message.railworks.engine.next", new ItemStack(item(nx.item())).getHoverName(),
                    Component.translatable("railworks.engine.stage." + g.id()), stage() + 1, DONE);
        return Component.translatable(g.fix() == Fix.RIVETS ? "message.railworks.engine.rivet" : "message.railworks.engine.blow",
                work(), allowed(stage(), fit()), Component.translatable("railworks.engine.stage." + g.id()));
    }

    private void fit(ServerLevel level, Player player, ItemStack held) {
        if (work() < allowed(stage(), fit())) {
            say(player, status());
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(1);
        entityData.set(DATA_FIT, fit() + 1);
        play(level, SoundEvents.ANVIL_PLACE, 0.5f, 0.7f);
        advance(level, player);
    }

    private void advance(ServerLevel level, Player player) {
        int st = stage();
        if (st >= DONE) return;
        Stage g = EngineShape.STAGES.get(st);
        if (fit() < g.units().size() || work() < g.work()) {
            say(player, status());
            return;
        }
        entityData.set(DATA_STAGE, st + 1);
        entityData.set(DATA_FIT, 0);
        entityData.set(DATA_WORK, 0);
        if (st + 1 >= DONE) {
            play(level, SoundEvents.ANVIL_USE, 0.8f, 0.6f);
            say(player, Component.translatable("message.railworks.engine.finished").withStyle(ChatFormatting.GREEN));
        } else say(player, status());
    }

    private void hammer(ServerLevel level, Player player, @Nullable Vec3 loc) {
        if (!done()) {
            int st = stage(), a = allowed(st, fit());
            if (work() >= a) {
                advance(level, player);
                return;
            }
            Fix fx = EngineShape.STAGES.get(st).fix();
            if (fx == Fix.RIVETS && !takeRivet(player)) {
                say(player, Component.translatable("message.railworks.carriage.need_rivets"));
                return;
            }
            entityData.set(DATA_WORK, work() + 1);
            play(level, SoundEvents.ANVIL_LAND, 0.35f, 1.4f + getRandom().nextFloat() * 0.3f);
            advance(level, player);
            return;
        }
        int e = loc == null ? 0 : end(loc);
        if (e != 0) {
            if (coupledAt(e)) say(player, Component.translatable("message.railworks.tow.taken"));
            else screw(level, player, e);
            return;
        }
        say(player, firebox());
    }

    /** The end clicked, by where the click landed, or 0 for the middle. */
    int end(Vec3 loc) {
        double x = pose().local(position().add(loc)).x;
        return Math.abs(x) > 4.4 ? (x > 0 ? 1 : -1) : 0;
    }

    private void sneakHammer(ServerLevel level, Player player, Vec3 loc) {
        if (done()) {
            int e = end(loc);
            if (e != 0 && coupledAt(e)) {
                uncoupleEnd(level, player, e);
                return;
            }
            if (Math.abs(root().u) > 0.05 || !getPassengers().isEmpty()) {
                say(player, Component.translatable("message.railworks.engine.busy"));
                return;
            }
            for (int k = -1; k <= 1; k += 2) if (coupledAt(k)) {
                say(player, Component.translatable("message.railworks.engine.coupled"));
                return;
            }
            entityData.set(DATA_STAGE, DONE - 1);
            entityData.set(DATA_FIT, units(DONE - 1).size());
            entityData.set(DATA_WORK, EngineShape.STAGES.get(DONE - 1).work());
            setCtrl(0, 0, false, handBrake(), false, false, false, false);
            entityData.set(DATA_STEAM, 0f);
            entityData.set(DATA_HEAT, 0f);
            burn = 0;
        }
        takeLast(level, player);
    }

    private void takeLast(ServerLevel level, Player player) {
        int st = stage(), f = fit();
        if (f == 0 && st > 0) {
            st--;
            f = units(st).size();
        }
        if (f == 0) return;
        Unit un = units(st).get(f - 1);
        f--;
        int w = st == stage() ? Math.min(work(), allowed(st, f)) : allowed(st, f);
        if (EngineShape.STAGES.get(st).fix() == Fix.NONE) w = 0;
        give(level, player, new ItemStack(item(un.item())), position());
        if (un.item().equals("coal_bunker")) {
            dropFuel(level, player);
        }
        if (un.item().equals("pannier_tank")) entityData.set(DATA_WATER, Math.min(water(), (int) (TANK / 2 * f)));
        if (st == 0 && f == 0) {
            discard();
            return;
        }
        entityData.set(DATA_STAGE, st);
        entityData.set(DATA_FIT, f);
        entityData.set(DATA_WORK, w);
        play(level, SoundEvents.ANVIL_LAND, 0.3f, 0.8f);
        say(player, Component.translatable("message.railworks.engine.removed", new ItemStack(item(un.item())).getHoverName()));
    }

    private void dropFuel(ServerLevel level, @Nullable Player player) {
        int c = coal, k = (int) coke;
        coal = 0;
        coke = 0;
        entityData.set(DATA_COAL, 0);
        for (int n; c > 0; c -= n) {
            n = Math.min(64, c);
            if (player != null) give(level, player, new ItemStack(Items.COAL, n), position());
            else spawnAtLocation(level, new ItemStack(Items.COAL, n));
        }
        for (int n; k > 0; k -= n) {
            n = Math.min(64, k);
            if (player != null) give(level, player, new ItemStack(ModContent.COKE, n), position());
            else spawnAtLocation(level, new ItemStack(ModContent.COKE, n));
        }
    }

    private void paintWith(ServerLevel level, Player player, ItemStack held) {
        int g = paint() & 15, b = paint() >> 4;
        boolean isGreen = held.is(Items.GREEN_DYE);
        if (isGreen ? g >= GREEN : b >= BLACK_COATS) {
            say(player, Component.translatable("message.railworks.engine.painted", g, GREEN, b, BLACK_COATS));
            return;
        }
        if (isGreen) g++;
        else b++;
        entityData.set(DATA_PAINT, g | b << 4);
        if (!player.getAbilities().instabuild) held.shrink(1);
        play(level, SoundEvents.DYE_USE, 1, 1);
        say(player, Component.translatable("message.railworks.engine.painted", g, GREEN, b, BLACK_COATS));
    }

    static boolean takeRivet(Player p) {
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

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason == RemovalReason.KILLED && level() instanceof ServerLevel sl) {
            for (int st = 0; st < DONE; st++) for (int i = 0; i < fitted(st); i++) spawnAtLocation(sl, new ItemStack(item(units(st).get(i).item())));
            dropFuel(sl, null);
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- riding

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return done() && getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        double hf = frontHeading(), a = DRIVER.x, z = DRIVER.z;
        double y = DRIVER.y + (passenger instanceof Player ? 0.6 : 0) + a * tanPitch();
        return new Vec3(a * Math.cos(hf) - z * Math.sin(hf), y, a * Math.sin(hf) + z * Math.cos(hf));
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return pose().world(new Vec3(-3.3, 1.71, 1.0));
    }

    public boolean shouldRiderSit() {
        return false;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        int st = Mth.clamp(in.getIntOr("stage", 0), 0, DONE);
        entityData.set(DATA_STAGE, st);
        entityData.set(DATA_FIT, st >= DONE ? 0 : Mth.clamp(in.getIntOr("fit", 1), 0, units(st).size()));
        entityData.set(DATA_WORK, Math.max(0, in.getIntOr("work", 0)));
        entityData.set(DATA_PAINT, in.getIntOr("paint", 0));
        entityData.set(DATA_CTRL, in.getIntOr("ctrl", 5 << 4) & 0xBFF);
        entityData.set(DATA_STEAM, in.getFloatOr("steam", 0));
        entityData.set(DATA_HEAT, in.getFloatOr("heat", 0));
        entityData.set(DATA_WATER, Mth.clamp(in.getIntOr("water", 0), 0, (int) TANK));
        coal = Mth.clamp(in.getIntOr("coal", 0), 0, BUNKER);
        coke = Mth.clamp(in.getIntOr("coke", 0), 0, BUNKER - coal);
        burn = in.getDoubleOr("burn", 0);
        entityData.set(DATA_COAL, coal + (int) coke);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("stage", stage());
        out.putInt("fit", fit());
        out.putInt("work", work());
        out.putInt("paint", paint());
        out.putInt("ctrl", ctrl());
        out.putFloat("steam", steam());
        out.putFloat("heat", heat());
        out.putInt("water", water());
        out.putInt("coal", coal);
        out.putInt("coke", (int) coke);
        out.putDouble("burn", burn);
    }
}
