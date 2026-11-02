package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A carriage bogie, built on the track a part at a time and finished with the hammer. Pushed by hand or towed
 * by the trolley; left alone, its hand brake goes on.
 */
public class Bogie extends RailVehicle {
    public static final double LENGTH = 3.0, WIDTH = 2.3, HALF_WB = 1.0, WHEEL_R = 0.5;
    static final double MASS = 0.8, BRAKE = 1.45;
    static final ResourceKey<DamageType> RUN_DOWN = damage("bogie");
    // the stage counts parts fitted: 2 wheelsets, 4 axle boxes, 2 springs, the bolster, then the hammer
    public static final int FRAME = 0, SETS = 2, BOXES = 6, SPRINGS = 8, FITTED = 9, DONE = 10;

    private int rumble;

    public Bogie(EntityType<? extends Bogie> type, Level level) {
        super(type, level);
    }

    @Override
    public int doneStage() {
        return DONE;
    }

    @Override
    protected double ownMass() {
        return MASS;
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
        return 1.1;
    }

    @Override
    protected double outlineHeight() {
        return 1.0;
    }

    @Override
    public double eyeReach() {
        return 1.56;
    }

    @Override
    public double eyeHeight() {
        return 0.55;
    }

    @Override
    protected ResourceKey<DamageType> runDown() {
        return RUN_DOWN;
    }

    // ---------------------------------------------------------------- placing and taking off

    /** Puts a bogie frame (or a whole bogie) on the nearest live track, facing the way the player looks. */
    public static boolean place(ServerLevel level, Player player, Vec3 at, boolean complete) {
        Bogie b = ModContent.BOGIE_ENTITY.create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (b != null) b.entityData.set(DATA_STAGE, complete ? DONE : FRAME);
        return put(level, player, at, b);
    }

    @Override
    public void takeOff(ServerLevel level, Player player) {
        for (ItemStack i : drops()) if (!player.getInventory().add(i)) spawnAtLocation(level, i);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.4f, 1.2f);
        discard();
    }

    private List<ItemStack> drops() {
        int st = stage();
        List<ItemStack> l = new ArrayList<>();
        if (st >= DONE) {
            l.add(new ItemStack(ModContent.BOGIE));
            return l;
        }
        l.add(new ItemStack(ModContent.BOGIE_FRAME));
        if (st > 0) l.add(new ItemStack(ModContent.CARRIAGE_WHEELSET, Math.min(st, SETS)));
        if (st > SETS) l.add(new ItemStack(ModContent.AXLE_BOX, Math.min(st, BOXES) - SETS));
        if (st > BOXES) l.add(new ItemStack(ModContent.BOGIE_SPRING, Math.min(st, SPRINGS) - BOXES));
        if (st > SPRINGS) l.add(new ItemStack(ModContent.BOLSTER));
        return l;
    }

    private static Item next(int st) {
        return st < SETS ? ModContent.CARRIAGE_WHEELSET : st < BOXES ? ModContent.AXLE_BOX : st < SPRINGS ? ModContent.BOGIE_SPRING : ModContent.BOLSTER;
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player hp && other(hp) instanceof Picked pk) return pk.v().hurtServer(level, source, damage);
        if (mounted() && leader() != null) return leader().hurtServer(level, source, damage);
        if (source.getEntity() instanceof Player p && p.getMainHandItem().is(ModContent.HAMMER) && !p.isSecondaryUseActive()) hammer(level, p);
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        Picked pk = other(player);
        if (pk != null) return pk.v().interact(player, hand, pk.loc());
        ItemStack held = player.getItemInHand(hand);
        int st = stage();
        if (held.is(ModContent.TOW_BAR)) return Coupling.click(this, player, location, held);
        RailVehicle body = mounted() ? leader() : null;
        if (body != null && (held.is(ModContent.HAMMER) || held.isEmpty() || CarriageLayout.isPart(held.getItem()))) return body.interact(player, hand, location);
        if (held.is(ModContent.SOLEBAR) && done() && !mounted()) return Carriage.start(this, player, held);
        if (held.is(ModContent.HAMMER)) {
            if (level() instanceof ServerLevel sl) {
                if (!player.isSecondaryUseActive()) hammer(sl, player);
                else if (!hammerBar(sl, player, location)) {
                    uncoupleAll(sl, player);
                    takeOff(sl, player);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (st < FITTED && held.is(next(st))) {
            if (level() instanceof ServerLevel sl) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                entityData.set(DATA_STAGE, st + 1);
                sl.playSound(null, getX(), getY(), getZ(), st < BOXES ? SoundEvents.ANVIL_PLACE : SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.5f, st < SETS ? 0.9f : 1.2f);
                say(player, progress(st + 1));
            }
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (st < DONE) {
            if (level() instanceof ServerLevel) say(player, progress(st));
            return InteractionResult.SUCCESS;
        }
        if (!held.isEmpty()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            Vec3 look = player.getLookAngle();
            double hf = frontHeading();
            push(player, look.x * Math.cos(hf) + look.z * Math.sin(hf) >= 0 ? 1 : -1);
        }
        return InteractionResult.SUCCESS;
    }

    /** What the bogie needs next, or that it is ready for the hammer. */
    private Component progress(int st) {
        if (st >= FITTED) return Component.translatable("message.railworks.bogie.fitted");
        int of = st < SETS ? SETS : st < BOXES ? BOXES - SETS : SPRINGS - BOXES, on = st < SETS ? st : st < BOXES ? st - SETS : st < SPRINGS ? st - BOXES : 0;
        if (st == SPRINGS) of = 1;
        return Component.translatable("message.railworks.bogie.next", new ItemStack(next(st)).getHoverName(), on, of);
    }

    private void hammer(ServerLevel level, Player player) {
        int st = stage();
        if (st < FITTED) {
            play(level, SoundEvents.ANVIL_LAND, 0.25f, 1.8f);
            say(player, progress(st));
        } else if (st == FITTED) {
            entityData.set(DATA_STAGE, DONE);
            play(level, SoundEvents.ANVIL_USE, 0.7f, 0.8f);
            play(level, SoundEvents.CHAIN_PLACE, 0.8f, 0.6f);
            say(player, Component.translatable("message.railworks.bogie.done").withStyle(ChatFormatting.GREEN));
        } else {
            play(level, SoundEvents.ANVIL_LAND, 0.2f, 2.0f);
            say(player, Component.translatable("message.railworks.bogie.status", Component.translatable(braking() ? "railworks.bogie.brake_on" : "railworks.bogie.brake_off"),
                    String.format(java.util.Locale.ROOT, "%.1f", Math.abs(u))));
        }
    }

    // ---------------------------------------------------------------- motion

    @Override
    protected void serverTick(ServerLevel level) {
        Rail r = rail(seg);
        if (r == null) {
            uncoupleAll(level, null);
            for (ItemStack i : drops()) spawnAtLocation(level, i);
            discard();
            return;
        }
        if (!done()) {
            u = 0;
            placeOn(r);
            sync(false);
            return;
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
        drive(level, t, force, drag, false, new Ride(driver, 0.25f, safe), false);
    }

    @Override
    protected void moved(ServerLevel level, Rail r, double ds, boolean changed, Ride ride) {
        super.moved(level, r, ds, changed, ride);
        double speed = Math.abs(ds) / DT;
        if (speed > 0.3 && --rumble <= 0) {
            play(level, SoundEvents.DEEPSLATE_BRICKS_STEP, 0.15f + (float) speed * 0.04f, (mounted() ? 0.35f : 0.5f) + (float) speed * 0.02f);
            rumble = Mth.clamp((int) (10 - speed * 1.5), 3, 10);
        }
    }
}
