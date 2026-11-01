package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.track.TrackRunner;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The tow bar: click one vehicle end, then the end of the next one along the line. */
final class Coupling {
    private record Pick(UUID vehicle, int end, long time) {}

    private static final Map<UUID, Pick> PICKS = new HashMap<>();

    private Coupling() {}

    static InteractionResult click(RailVehicle v, Player player, Vec3 loc, ItemStack held) {
        if (!(v.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        int end = v.endAt(loc);
        // boxes overlap where vehicles stand close, so take the tow eye nearest the player's line of sight
        Vec3 o = player.getEyePosition(), d = player.getLookAngle();
        double best = 1.2;
        for (RailVehicle x : level.getEntitiesOfClass(RailVehicle.class, v.getBoundingBox().inflate(4), x -> x.isAlive() && !x.mounted())) {
            for (int e = -1; e <= 1; e += 2) {
                Vec3 w = x.eye(e).subtract(o);
                double t = w.dot(d), miss = t < 0 ? w.length() : w.subtract(d.scale(t)).length();
                if (miss < best) {
                    best = miss;
                    v = x;
                    end = e;
                }
            }
        }
        if (!v.done()) {
            RailVehicle.say(player, Component.translatable("message.railworks.tow.unfinished"));
            return InteractionResult.SUCCESS;
        }
        if (v.coupledAt(end)) {
            RailVehicle.say(player, Component.translatable("message.railworks.tow.taken"));
            return InteractionResult.SUCCESS;
        }
        Pick p = PICKS.get(player.getUUID());
        RailVehicle a = p == null ? null : level.getEntity(p.vehicle) instanceof RailVehicle x && x.isAlive() ? x : null;
        if (a == null || a == v || level.getGameTime() - p.time > 600 || a.coupledAt(p.end)) {
            PICKS.put(player.getUUID(), new Pick(v.getUUID(), end, level.getGameTime()));
            level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 0.5f, 1.4f);
            RailVehicle.say(player, Component.translatable("message.railworks.tow.first"));
            return InteractionResult.SUCCESS;
        }
        PICKS.remove(player.getUUID());
        String why = check(level, a, p.end, v, end);
        if (why != null) {
            RailVehicle.say(player, Component.translatable("message.railworks.tow." + why).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        // the trolley stays the root, so it does the driving
        boolean vLeads = v.consist().stream().anyMatch(m -> m.v().isTrolley());
        RailVehicle leader = vLeads ? v : a, follower = vLeads ? a : v;
        int le = vLeads ? end : p.end, fe = vLeads ? p.end : end;
        RailVehicle.reroot(follower);
        follower.setTow(leader, -le * fe, fe);
        if (!player.getAbilities().instabuild) held.shrink(1);
        Vec3 at = follower.eye(fe).add(leader.eye(le)).scale(0.5);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.9f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.3f, 1.5f);
        RailVehicle.say(player, Component.translatable("message.railworks.tow.coupled").withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    static String check(ServerLevel level, RailVehicle a, int ea, RailVehicle b, int eb) {
        return check(level, a, ea, b, eb, RailVehicle.BAR_REACH, RailVehicle.MAX_BOGIES);
    }

    /** Why two ends may not couple, or null; reach is how far apart they may be, loose how many points go without a trolley. */
    static String check(ServerLevel level, RailVehicle a, int ea, RailVehicle b, int eb, double reach, int loose) {
        List<RailVehicle.Member> ca = a.consist(), cb = b.consist();
        if (ca.stream().anyMatch(m -> m.v() == b)) return "same";
        long trolleys = ca.stream().filter(m -> m.v().isTrolley()).count() + cb.stream().filter(m -> m.v().isTrolley()).count();
        int points = RailVehicle.points(ca) + RailVehicle.points(cb);
        if (trolleys > 1) return "two_trolleys";
        if (RailVehicle.vehicles(ca) + RailVehicle.vehicles(cb) > RailVehicle.MAX_VEHICLES) return "too_long";
        if (points > (trolleys > 0 ? RailVehicle.TROLLEY_POINTS : loose)) return trolleys > 0 ? "too_heavy" : "too_many";
        TrackRunner.Contact c = TrackRunner.ahead(level, a, ea * a.f(), reach);
        TrackRunner.Contact d = TrackRunner.ahead(level, b, eb * b.f(), reach);
        if (c == null || c.other() != b || d == null || d.other() != a) return "not_lined";
        return null;
    }
}
