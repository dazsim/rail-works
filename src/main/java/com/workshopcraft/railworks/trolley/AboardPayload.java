package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** From the client: where its player stands in a carriage (a, y, w), or carriage -1 and the world spot it stepped off at. */
public record AboardPayload(int carriage, double a, double y, double w) implements CustomPacketPayload {
    public static final Type<AboardPayload> TYPE = new Type<>(RailWorks.id("aboard"));
    public static final StreamCodec<ByteBuf, AboardPayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeInt(p.carriage);
                b.writeDouble(p.a);
                b.writeDouble(p.y);
                b.writeDouble(p.w);
            },
            b -> new AboardPayload(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
