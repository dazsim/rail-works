package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackGraph;
import com.workshopcraft.railworks.track.TrackView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/** Holds nothing on the server (the TrackGraph does); on the client it carries what to draw. */
public class TrackNodeBlockEntity extends BlockEntity {
    public @Nullable TrackView view;
    public int version;
    public @Nullable Object renderCache;

    public TrackNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.TRACK_NODE_ENTITY, pos, state);
    }

    public void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (level instanceof ServerLevel sl) tag.put("track", TrackGraph.get(sl).clientTag(sl, worldPosition));
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.read("track", CompoundTag.CODEC).ifPresent(t -> {
            view = TrackView.parse(t);
            version++;
        });
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel sl) TrackGraph.get(sl).nodeRemoved(sl, pos);
    }
}
