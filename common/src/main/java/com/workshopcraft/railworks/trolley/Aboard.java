package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.trolley.CarriageBody.Pose;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server side of walking in carriages: which players stand in which carriage and where, who else rides
 * along on the floor, and where players who logged out aboard go back to.
 */
public final class Aboard {
    record Claim(int carriage, Vec3 local) {}

    record Saved(UUID carriage, int seat, Vec3 local, String dim, long until) {}

    private static final Map<UUID, Claim> CLAIMS = new HashMap<>();
    private static final Map<UUID, Long> TENTATIVE = new HashMap<>();
    private static final Map<UUID, Saved> RETURNING = new HashMap<>();
    private static final Map<ServerLevel, Boolean> SENT = new HashMap<>();

    private Aboard() {}

    static boolean anyAboard(Carriage c) {
        for (Claim k : CLAIMS.values()) if (k.carriage == c.getId()) return true;
        return false;
    }

    public static boolean aboard(Player p, Carriage c) {
        Claim k = CLAIMS.get(p.getUUID());
        return k != null && k.carriage == c.getId();
    }

    /** Things other than players that stand in the body: mobs, dropped items and orbs. */
    static boolean carried(Entity e) {
        return !(e instanceof Player) && !(e instanceof RailVehicle) && !e.isPassenger() && !e.isVehicle() && !e.noPhysics
                && (e instanceof LivingEntity || e instanceof ItemEntity || e instanceof ExperienceOrb);
    }

    static AABB around(Pose p) {
        return new AABB(p.x() - 12, p.y() - 2, p.z() - 12, p.x() + 12, p.y() + 8, p.z() + 12);
    }

    // ---------------------------------------------------------------- moving with the body

    /** The body moved from was to now: everything standing in it goes with it. */
    static void carry(Carriage c, ServerLevel level, Pose was, Pose now) {
        for (Map.Entry<UUID, Claim> e : CLAIMS.entrySet()) {
            if (e.getValue().carriage != c.getId()) continue;
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (sp != null && sp.level() == level) place(sp, now.world(e.getValue().local));
        }
        float turn = Mth.wrapDegrees((float) Math.toDegrees(now.hf() - was.hf()));
        for (Entity e : level.getEntities(c, around(was), Aboard::carried)) {
            Vec3 l = was.local(e.position());
            if (!CarriageBody.aboard(l)) continue;
            e.setPos(now.world(l));
            if (turn != 0) {
                e.setYRot(e.getYRot() + turn);
                e.setYHeadRot(e.getYHeadRot() + turn);
                if (e instanceof LivingEntity le) le.yBodyRot += turn;
            }
        }
    }

    private static void place(ServerPlayer sp, Vec3 at) {
        sp.setPos(at);
        sp.resetFallDistance();
        ((AboardConnection) sp.connection).railworks$synced();
        sp.level().getChunkSource().move(sp);
    }

    // ---------------------------------------------------------------- from the client

    public static void receive(ServerPlayer sp, AboardPayload p) {
        UUID id = sp.getUUID();
        TENTATIVE.remove(id);
        if (sp.isPassenger() || sp.isSpectator() || !sp.isAlive() || ((AboardConnection) sp.connection).railworks$awaitingTeleport()) {
            CLAIMS.remove(id);
            return;
        }
        if (!Double.isFinite(p.a()) || !Double.isFinite(p.y()) || !Double.isFinite(p.w())) return;
        Vec3 v = new Vec3(p.a(), p.y(), p.w());
        if (p.carriage() < 0) {
            if (CLAIMS.remove(id) != null && v.distanceToSqr(sp.position()) < 16) place(sp, v);
            return;
        }
        Claim was = CLAIMS.get(id);
        if (!(sp.level().getEntity(p.carriage()) instanceof Carriage c) || !CarriageBody.aboard(v)) {
            CLAIMS.remove(id);
            return;
        }
        Pose pose = c.pose();
        boolean ok = was != null && was.carriage == c.getId() ? was.local.distanceToSqr(v) < 6.25 : pose.world(v).distanceToSqr(sp.position()) < 16;
        if (!ok) {
            CLAIMS.remove(id);
            sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot());
            return;
        }
        CLAIMS.put(id, new Claim(c.getId(), v));
        place(sp, pose.world(v));
    }

    /** A vanilla move packet from a player standing in a carriage: only its turn and footing count, the payload has its place. */
    public static boolean swallow(ServerPlayer sp, ServerboundMovePlayerPacket p) {
        if (sp.isPassenger() || sp.isSpectator()) return false;
        UUID id = sp.getUUID();
        if (!CLAIMS.containsKey(id)) {
            Vec3 to = new Vec3(p.getX(sp.getX()), p.getY(sp.getY()), p.getZ(sp.getZ()));
            boolean inside = false;
            for (Carriage c : CarriageBody.near(sp.level(), to, 16)) if (CarriageBody.aboard(c.pose().local(to))) inside = true;
            if (!inside) {
                TENTATIVE.remove(id);
                return false;
            }
            long now = sp.level().getGameTime();
            Long t0 = TENTATIVE.putIfAbsent(id, now);
            if (t0 != null && now - t0 > 10) return false;
        }
        float yr = p.getYRot(sp.getYRot()), xr = p.getXRot(sp.getXRot());
        if (Float.isFinite(yr) && Float.isFinite(xr)) sp.absSnapRotationTo(Mth.wrapDegrees(yr), Mth.wrapDegrees(xr));
        sp.setOnGround(p.isOnGround());
        sp.resetFallDistance();
        return true;
    }

    // ---------------------------------------------------------------- each tick

    public static void tick(ServerLevel level) {
        List<Carriage> cs = CarriageBody.all(level);
        Iterator<Map.Entry<UUID, Claim>> it = CLAIMS.entrySet().iterator();
        List<int[]> ids = new ArrayList<>();
        List<Vec3> ls = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Claim> e = it.next();
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (sp == null || !sp.isAlive() || sp.isPassenger()) {
                it.remove();
                continue;
            }
            if (sp.level() != level) continue;
            Entity c = level.getEntity(e.getValue().carriage);
            if (!(c instanceof Carriage) || c.isRemoved()) {
                it.remove();
                continue;
            }
            ids.add(new int[] {sp.getId(), c.getId()});
            ls.add(e.getValue().local);
        }
        for (Carriage c : cs) {
            Pose pose = c.pose();
            for (Entity e : level.getEntities(c, around(pose), Aboard::carried)) {
                Vec3 l = pose.local(e.position());
                if (!CarriageBody.aboard(l)) continue;
                ids.add(new int[] {e.getId(), c.getId()});
                ls.add(l);
            }
        }
        boolean any = !ids.isEmpty();
        if (any || SENT.getOrDefault(level, false)) {
            int n = ids.size();
            int[] a = new int[n], b = new int[n];
            float[] l = new float[n * 3];
            for (int i = 0; i < n; i++) {
                a[i] = ids.get(i)[0];
                b[i] = ids.get(i)[1];
                l[i * 3] = (float) ls.get(i).x;
                l[i * 3 + 1] = (float) ls.get(i).y;
                l[i * 3 + 2] = (float) ls.get(i).z;
            }
            ClientboundCustomPayloadPacket pk = new ClientboundCustomPayloadPacket(new AboardSyncPayload(a, b, l));
            for (ServerPlayer sp : level.players()) sp.connection.send(pk);
        }
        SENT.put(level, any);
        returning(level);
    }

    // ---------------------------------------------------------------- logging out aboard

    public static void logout(ServerPlayer sp) {
        UUID id = sp.getUUID();
        Claim k = CLAIMS.remove(id);
        TENTATIVE.remove(id);
        RETURNING.remove(id);
        Map<UUID, String> all = load(sp.level());
        all.remove(id);
        Carriage c = null;
        int seat = -1;
        if (sp.getVehicle() instanceof Carriage v) {
            c = v;
            seat = v.seatOf(sp);
        } else if (k != null && sp.level().getEntity(k.carriage) instanceof Carriage v) c = v;
        if (c != null) {
            Vec3 l = c.pose().local(sp.position());
            all.put(id, String.format(Locale.ROOT, "%s %d %.4f %.4f %.4f %s", c.getUUID(), seat, l.x, l.y, l.z, sp.level().dimension().identifier()));
        }
        save(sp.level(), all);
    }

    public static void login(ServerPlayer sp) {
        Map<UUID, String> all = load(sp.level());
        String v = all.remove(sp.getUUID());
        if (v == null) return;
        save(sp.level(), all);
        try {
            String[] f = v.split(" ");
            RETURNING.put(sp.getUUID(), new Saved(UUID.fromString(f[0]), Integer.parseInt(f[1]),
                    new Vec3(Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4])), f[5], sp.level().getGameTime() + 200));
        } catch (RuntimeException e) {
            RailVehicle.LOG.warn("bad aboard entry for {}: {}", sp.getUUID(), v);
        }
    }

    private static void returning(ServerLevel level) {
        Iterator<Map.Entry<UUID, Saved>> it = RETURNING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Saved> e = it.next();
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (sp == null) {
                it.remove();
                continue;
            }
            if (sp.level() != level) continue;
            Saved s = e.getValue();
            if (level.getEntity(s.carriage) instanceof Carriage c && c.done() && c.placed()) {
                it.remove();
                if (s.seat >= 0 && c.seatAgain(sp, s.seat)) continue;
                Vec3 l = s.seat >= 0 ? c.aisleBeside(s.seat) : s.local;
                Vec3 at = c.pose().world(l);
                sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
                continue;
            }
            if (level.getGameTime() < s.until) continue;
            it.remove();
            safeGround(sp);
        }
    }

    /** Nothing left to stand in: down onto the ground if the player is in the air. */
    private static void safeGround(ServerPlayer sp) {
        ServerLevel level = sp.level();
        BlockPos at = sp.blockPosition();
        for (int dy = 0; dy <= 3; dy++) if (!level.getBlockState(at.below(dy + 1)).getCollisionShape(level, at.below(dy + 1)).isEmpty()) return;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        sp.connection.teleport(sp.getX(), top, sp.getZ(), sp.getYRot(), sp.getXRot());
    }

    private static @Nullable Path file(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("railworks_aboard.txt");
    }

    private static Map<UUID, String> load(ServerLevel level) {
        Map<UUID, String> m = new LinkedHashMap<>();
        Path f = file(level);
        if (f == null || !Files.exists(f)) return m;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int k = line.indexOf(' ');
                if (k > 0) m.put(UUID.fromString(line.substring(0, k)), line.substring(k + 1));
            }
        } catch (IOException | RuntimeException e) {
            RailVehicle.LOG.warn("cannot read {}", f, e);
        }
        return m;
    }

    private static void save(ServerLevel level, Map<UUID, String> m) {
        Path f = file(level);
        if (f == null) return;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<UUID, String> e : m.entrySet()) lines.add(e.getKey() + " " + e.getValue());
        try {
            Files.createDirectories(f.getParent());
            Files.write(f, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            RailVehicle.LOG.warn("cannot write {}", f, e);
        }
    }
}
