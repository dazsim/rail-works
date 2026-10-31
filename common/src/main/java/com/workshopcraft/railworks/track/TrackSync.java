package com.workshopcraft.railworks.track;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each player every section that passes within their view distance, so a section draws wherever any part of it is
 * in view, not only when one of its end nodes is loaded.
 */
public final class TrackSync {
    private static final int PERIOD = 10, PER_PACKET = 12;

    private static final class Sent {
        final ServerPlayer player;
        final ServerLevel level;
        final Long2IntMap revs = new Long2IntOpenHashMap();

        Sent(ServerPlayer player, ServerLevel level) {
            this.player = player;
            this.level = level;
        }
    }

    private static final Map<UUID, Sent> SENT = new ConcurrentHashMap<>();

    private TrackSync() {}

    public static void tick(ServerLevel level) {
        TrackActions.tickWarnings(level);
        TrackGraph.get(level).restoreBroken(level);
        if (level.getGameTime() % PERIOD != 0) return;
        TrackGraph g = TrackGraph.get(level);
        int view = level.getServer().getPlayerList().getViewDistance();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) online.add(p.getUUID());
        SENT.keySet().retainAll(online);
        for (ServerPlayer p : level.players()) {
            Sent sent = SENT.get(p.getUUID());
            boolean reset = false;
            if (sent == null || sent.player != p || sent.level != level) {
                sent = new Sent(p, level);
                SENT.put(p.getUUID(), sent);
                reset = true;
            }
            double reach = (Math.max(2, view) + 1) * 16.0;
            List<CompoundTag> set = new ArrayList<>();
            List<Long> drop = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (TrackGraph.Segment s : g.segments()) {
                seen.add(s.id);
                boolean near = s.reachFrom(p.getX(), p.getZ()) <= reach;
                if (near) {
                    if (sent.revs.containsKey(s.id) && sent.revs.get(s.id) == s.rev) continue;
                    CompoundTag t = g.syncTag(level, s);
                    t.putInt("rev", s.rev);
                    set.add(t);
                    sent.revs.put(s.id, s.rev);
                } else if (sent.revs.containsKey(s.id)) {
                    drop.add(s.id);
                    sent.revs.remove(s.id);
                }
            }
            for (long id : sent.revs.keySet().toLongArray()) {
                if (!seen.contains(id)) {
                    drop.add(id);
                    sent.revs.remove(id);
                }
            }
            if (!reset && set.isEmpty() && drop.isEmpty()) continue;
            for (int i = 0; i < Math.max(1, set.size()); i += PER_PACKET) {
                CompoundTag out = new CompoundTag();
                if (i == 0) {
                    if (reset) out.putBoolean("reset", true);
                    long[] d = new long[drop.size()];
                    for (int k = 0; k < d.length; k++) d[k] = drop.get(k);
                    out.putLongArray("drop", d);
                }
                ListTag lt = new ListTag();
                for (int k = i; k < Math.min(set.size(), i + PER_PACKET); k++) lt.add(set.get(k));
                out.put("set", lt);
                p.connection.send(new ClientboundCustomPayloadPacket(new TrackSyncPayload(out)));
            }
        }
    }
}
