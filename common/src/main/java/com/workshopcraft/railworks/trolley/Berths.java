package com.workshopcraft.railworks.trolley;

import com.mojang.serialization.Codec;
import com.workshopcraft.railworks.RailWorks;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Sleeping on a berth and waking up at it again, wherever the carriage has got to. */
public final class Berths {
    /** From the middle of a berth to the pillow, along the body. */
    static final double HEAD = 0.75, PILLOW = 0.125;

    private Berths() {}

    static @Nullable Carriage carriage(Entity e) {
        return e.getVehicle() instanceof Carriage c && berth(c, c.seatOf(e)) ? c : null;
    }

    static boolean berth(Carriage c, int seat) {
        float[] p = Carriage.seat(c.slots(), seat);
        return p != null && Carriage.lying(p);
    }

    /** Asleep on a berth: lain flat with the head on the pillow. */
    public static boolean asleep(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity le && le.isSleeping() && carriage(e) != null;
    }

    /** The world yaw from the feet of a berth sleeper to the head, or NaN. */
    public static float headYaw(Entity e) {
        Carriage c = carriage(e);
        if (c == null) return Float.NaN;
        float[] p = Carriage.seat(c.slots(), c.seatOf(e));
        double hf = c.frontHeading(), s = Math.signum(p[3]);
        return (float) Math.toDegrees(Math.atan2(-s * Math.cos(hf), s * Math.sin(hf)));
    }

    /** A player has just lain down on a berth: a spawn point, and sleep if it is night. */
    static void lieDown(Carriage c, ServerPlayer sp, int seat) {
        ServerLevel level = sp.level();
        Vec3 head = c.pose().world(new Vec3(Carriage.seat(c.slots(), seat)[0] + Math.signum(Carriage.seat(c.slots(), seat)[3]) * HEAD,
                Carriage.seat(c.slots(), seat)[1], Carriage.seat(c.slots(), seat)[2]));
        BedRule rule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, head);
        boolean sleep = rule.canSleep(level), spawn = rule.canSetSpawn(level);
        if (spawn) Spawns.get(level).set(sp, c, seat);
        if (!sleep) {
            Component why = rule.asProblem().message();
            if (why != null) sp.sendOverlayMessage(why);
            return;
        }
        // shut doors keep the night out; an open one lets it in like any bed
        if (!sp.isCreative() && c.doors() != 0 && !level.getEntitiesOfClass(Monster.class, new AABB(head, head).inflate(8, 5, 8),
                m -> m.isPreventingPlayerRest(level, sp)).isEmpty()) {
            sp.sendOverlayMessage(Component.translatable("block.minecraft.bed.not_safe"));
            return;
        }
        sp.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        sp.setSleepingPos(BlockPos.containing(head));
        sp.setPose(Pose.SLEEPING);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.awardStat(Stats.SLEEP_IN_BED);
        CriteriaTriggers.SLEPT_IN_BED.trigger(sp);
        if (!level.canSleepThroughNights()) sp.sendOverlayMessage(Component.translatable("sleep.not_possible"));
        level.updateSleepingPlayerList();
    }

    /** The berth respawn, or the vanilla missing-bed fallback once it is gone; null when there is none. */
    public static @Nullable TeleportTransition respawn(ServerPlayer sp, TeleportTransition.PostTeleportTransition post) {
        ServerLevel over = sp.level().getServer().overworld();
        Spawns s = Spawns.get(over);
        Spawn e = s.byPlayer.get(sp.getUUID());
        if (e == null) return null;
        if (e.seat < 0) {
            s.forget(sp.getUUID());
            return TeleportTransition.missingRespawnBlock(sp, post);
        }
        ServerLevel level = sp.level().getServer().getLevel(e.dim);
        if (level != null && level.getEntity(e.cart) instanceof Carriage c && !c.isRemoved()) {
            if (berth(c, e.seat)) {
                s.track(c);
                e = s.byPlayer.get(sp.getUUID());
                return new TeleportTransition(level, e.at, Vec3.ZERO, e.yaw, 0, post);
            }
        } else if (level != null) {
            // the carriage is not loaded, and so has not moved since it was last seen
            return new TeleportTransition(level, e.at, Vec3.ZERO, e.yaw, 0, post);
        }
        s.forget(sp.getUUID());
        return TeleportTransition.missingRespawnBlock(sp, post);
    }

    public static void forget(ServerPlayer sp) {
        Spawns.get(sp.level().getServer().overworld()).forget(sp.getUUID());
    }

    static void track(ServerLevel level, Carriage c) {
        Spawns s = Spawns.get(level.getServer().overworld());
        if (s.carts.contains(c.getUUID())) s.track(c);
    }

    /** A berth taken off, or -1 for the whole carriage gone. Its sleepers get the missing-bed message on their next respawn. */
    static void gone(ServerLevel level, Carriage c, int slot) {
        Spawns s = Spawns.get(level.getServer().overworld());
        if (!s.carts.contains(c.getUUID())) return;
        s.byPlayer.replaceAll((p, e) -> e.cart.equals(c.getUUID()) && e.seat >= 0 && (slot < 0 || e.seat / 4 == slot)
                ? new Spawn(e.cart, -1, e.dim, e.at, e.yaw) : e);
        s.setDirty();
    }

    record Spawn(UUID cart, int seat, ResourceKey<Level> dim, Vec3 at, float yaw) {}

    static final class Spawns extends SavedData {
        static final Codec<Spawns> CODEC = CompoundTag.CODEC.xmap(Spawns::load, Spawns::save);
        static final SavedDataType<Spawns> TYPE = new SavedDataType<>(RailWorks.id("berth_spawns"), Spawns::new, CODEC,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        final Map<UUID, Spawn> byPlayer = new HashMap<>();
        final Set<UUID> carts = new HashSet<>();

        static Spawns get(ServerLevel overworld) {
            return overworld.getDataStorage().computeIfAbsent(TYPE);
        }

        void set(ServerPlayer sp, Carriage c, int seat) {
            Spawn was = byPlayer.get(sp.getUUID());
            if (was == null || !was.cart.equals(c.getUUID()) || was.seat != seat) {
                sp.setRespawnPosition(null, false);
                sp.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }
            byPlayer.put(sp.getUUID(), at(c, seat));
            reindex();
        }

        static Spawn at(Carriage c, int seat) {
            return new Spawn(c.getUUID(), seat, c.level().dimension(), c.pose().world(c.aisleBeside(seat)), c.getYRot());
        }

        void track(Carriage c) {
            boolean moved = false;
            for (Map.Entry<UUID, Spawn> en : byPlayer.entrySet()) {
                Spawn e = en.getValue();
                if (!e.cart.equals(c.getUUID()) || e.seat < 0) continue;
                Spawn n = at(c, e.seat);
                if (n.at.distanceToSqr(e.at) > 1e-4 || n.dim != e.dim) {
                    en.setValue(n);
                    moved = true;
                }
            }
            if (moved) setDirty();
        }

        void forget(UUID player) {
            if (byPlayer.remove(player) != null) reindex();
        }

        void reindex() {
            carts.clear();
            for (Spawn e : byPlayer.values()) carts.add(e.cart);
            setDirty();
        }

        private CompoundTag save() {
            CompoundTag t = new CompoundTag();
            ListTag l = new ListTag();
            byPlayer.forEach((p, e) -> {
                CompoundTag c = new CompoundTag();
                c.putString("player", p.toString());
                c.putString("cart", e.cart.toString());
                c.putInt("seat", e.seat);
                c.putString("dim", e.dim.identifier().toString());
                c.putDouble("x", e.at.x);
                c.putDouble("y", e.at.y);
                c.putDouble("z", e.at.z);
                c.putFloat("yaw", e.yaw);
                l.add(c);
            });
            t.put("spawns", l);
            return t;
        }

        private static Spawns load(CompoundTag t) {
            Spawns s = new Spawns();
            for (Tag x : t.getListOrEmpty("spawns")) {
                if (!(x instanceof CompoundTag c)) continue;
                try {
                    s.byPlayer.put(UUID.fromString(c.getStringOr("player", "")), new Spawn(UUID.fromString(c.getStringOr("cart", "")), c.getIntOr("seat", 0),
                            ResourceKey.create(Registries.DIMENSION, Identifier.parse(c.getStringOr("dim", "minecraft:overworld"))),
                            new Vec3(c.getDoubleOr("x", 0), c.getDoubleOr("y", 0), c.getDoubleOr("z", 0)), c.getFloatOr("yaw", 0)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            for (Spawn e : s.byPlayer.values()) s.carts.add(e.cart);
            return s;
        }
    }
}
