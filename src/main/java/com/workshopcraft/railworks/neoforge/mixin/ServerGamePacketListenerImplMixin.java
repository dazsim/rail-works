package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.trolley.Aboard;
import com.workshopcraft.railworks.trolley.AboardConnection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player standing in a moving carriage moves with it, so its own move packets are taken in the carriage's frame. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin implements AboardConnection {
    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double firstGoodY;
    @Shadow private double firstGoodZ;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private @Nullable Vec3 awaitingPositionFromClient;
    @Shadow private boolean clientIsFloating;

    @Inject(method = "handleMovePlayer", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void railworks$aboard(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (awaitingPositionFromClient == null && Aboard.swallow(player, packet)) {
            railworks$synced();
            ci.cancel();
        }
    }

    @Override
    public void railworks$synced() {
        firstGoodX = lastGoodX = player.getX();
        firstGoodY = lastGoodY = player.getY();
        firstGoodZ = lastGoodZ = player.getZ();
        clientIsFloating = false;
    }

    @Override
    public boolean railworks$awaitingTeleport() {
        return awaitingPositionFromClient != null;
    }
}
