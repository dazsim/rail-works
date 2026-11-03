package com.workshopcraft.railworks.track;

import com.workshopcraft.railworks.RailWorks;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sections a client should draw: "set" holds new or changed ones, "drop" the ids it can forget, "reset" clears what it had. */
public record TrackSyncPayload(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<TrackSyncPayload> TYPE = new Type<>(RailWorks.id("track_sync"));
    public static final StreamCodec<ByteBuf, TrackSyncPayload> CODEC = ByteBufCodecs.COMPOUND_TAG.map(TrackSyncPayload::new, TrackSyncPayload::tag);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
