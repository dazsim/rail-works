package com.workshopcraft.railworks.fabric.mixin;

import com.workshopcraft.railworks.track.TrackSync;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets unfinished track re-check its clearance when a block near it changes. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void railworks$blockChanged(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo ci) {
        TrackSync.blockChanged((ServerLevel) (Object) this, pos, old, current);
    }
}
