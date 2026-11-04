package com.workshopcraft.railworks.trolley;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** With debugObstacles on: outlines the block stopping a train for everyone near and names it on their action bar. */
final class ObstacleDebug {
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF2020, 1.2f);

    private ObstacleDebug() {}

    static void show(ServerLevel level, RailVehicle root, RailVehicle by, RailVehicle.Obstacle o, RailVehicle.Ride ride) {
        if (root.tickCount % 5 != 0) return;
        BlockPos p = o.pos();
        VoxelShape shape = o.state().getCollisionShape(level, p);
        AABB box = shape.isEmpty() ? new AABB(p) : shape.bounds().move(p);
        outline(level, box.inflate(0.02));
        if (root.tickCount % 20 != 0) return;
        Component msg = Component.literal("[debug] ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.railworks.trolley.blocked", o.state().getBlock().getName(), p.getX(), p.getY(), p.getZ(), RailVehicle.why(o)).withStyle(ChatFormatting.RED))
                .append(Component.literal(" " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(o.state().getBlock()) + " by " + by.getType().toShortString()).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer pl : level.players()) if (pl.distanceToSqr(Vec3.atCenterOf(p)) < 64 * 64) pl.sendSystemMessage(msg, true);
        RailVehicle.LOG.info("[obstacle] {}", msg.getString());
    }

    private static void outline(ServerLevel level, AABB b) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) line(level, b.minX, y, z, b.maxX, y, z);
        for (double x : xs) for (double z : zs) line(level, x, b.minY, z, x, b.maxY, z);
        for (double x : xs) for (double y : ys) line(level, x, y, b.minZ, x, y, b.maxZ);
    }

    private static void line(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1) {
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int n = Math.max(2, (int) (len / 0.15));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            level.sendParticles(RED, true, true, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t, 1, 0, 0, 0, 0);
        }
    }
}
