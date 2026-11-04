package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Client side smoke, steam and beats: four chuffs a turn of the wheels while there is steam on. */
final class EngineEffects {
    static final Vec3 CHIMNEY = new Vec3(3.72, 5.55, 0), VALVE = new Vec3(-0.6, 3.95, 0), WHISTLE = new Vec3(-1.0, 3.98, 0.3);

    private EngineEffects() {}

    static void tick(Engine e) {
        Level level = e.level();
        CarriageBody.Pose pose = e.pose();
        Vec3 ch = pose.world(CHIMNEY);
        // smoke and steam keep most of the engine's speed, so they rise clear instead of blowing back through the cab
        Vec3 mv = e.moved.scale(0.9);
        int beat = (int) Math.floor(e.spin / (Math.PI / 2));
        boolean beatNow = beat != e.beat;
        e.beat = beat;
        int reg = e.regulator();
        if (beatNow && com.workshopcraft.railworks.RailWorksConfig.debugObstacles && reg > 0)
            RailVehicle.LOG.info(String.format(java.util.Locale.ROOT, "[chuff-client] beat %d pace %.4f %s", beat, e.pace, Math.abs(e.pace) > 0.01 ? "plays" : "held, not moving"));
        if (beatNow && reg > 0 && e.steam() > 0.05f && Math.abs(e.pace) > 0.01) {
            float vol = 0.35f + 0.05f * reg;
            level.playLocalSound(ch.x, ch.y, ch.z, ModContent.ENGINE_CHUFF, SoundSource.NEUTRAL, vol, 0.9f + e.getRandom().nextFloat() * 0.2f, false);
            for (int i = 0; i < 3 + reg / 2; i++)
                level.addParticle(ParticleTypes.LARGE_SMOKE, ch.x + jitter(e), ch.y, ch.z + jitter(e), mv.x + jitter(e) * 0.1, 0.35 + 0.03 * reg, mv.z + jitter(e) * 0.1);
        }
        if (e.lit() && e.tickCount % 6 == 0)
            level.addParticle(ParticleTypes.SMOKE, ch.x + jitter(e), ch.y, ch.z + jitter(e), mv.x, 0.12, mv.z);
        if (e.lifting()) {
            Vec3 v = pose.world(VALVE);
            if (e.tickCount % 2 == 0) level.addParticle(ParticleTypes.CLOUD, v.x + jitter(e) * 0.3, v.y, v.z + jitter(e) * 0.3, mv.x, 0.3, mv.z);
            if (--e.valveIn <= 0) {
                e.valveIn = 40 + e.getRandom().nextInt(21);
                level.playLocalSound(v.x, v.y, v.z, ModContent.ENGINE_VALVES, SoundSource.NEUTRAL, 0.1f, 0.9f + e.getRandom().nextFloat() * 0.2f, false);
            }
        }
        if (e.whistling()) {
            Vec3 w = pose.world(WHISTLE);
            level.addParticle(ParticleTypes.CLOUD, w.x, w.y, w.z, mv.x, 0.2, mv.z);
        }
    }

    static double jitter(Engine e) {
        return (e.getRandom().nextDouble() - 0.5) * 0.3;
    }
}
