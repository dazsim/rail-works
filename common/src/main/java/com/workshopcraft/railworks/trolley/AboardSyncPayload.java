package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** From the server: every entity standing in a carriage this tick, and where in it. */
public record AboardSyncPayload(int[] ids, int[] carriages, float[] local) implements CustomPacketPayload {
    public static final Type<AboardSyncPayload> TYPE = new Type<>(RailWorks.id("aboard_sync"));
    public static final StreamCodec<ByteBuf, AboardSyncPayload> CODEC = StreamCodec.of(AboardSyncPayload::write, AboardSyncPayload::read);

    private static void write(ByteBuf b, AboardSyncPayload p) {
        b.writeShort(p.ids.length);
        for (int i = 0; i < p.ids.length; i++) {
            b.writeInt(p.ids[i]);
            b.writeInt(p.carriages[i]);
            for (int k = 0; k < 3; k++) b.writeFloat(p.local[i * 3 + k]);
        }
    }

    private static AboardSyncPayload read(ByteBuf b) {
        int n = Math.min(b.readShort(), 512);
        int[] ids = new int[n], cs = new int[n];
        float[] l = new float[n * 3];
        for (int i = 0; i < n; i++) {
            ids[i] = b.readInt();
            cs[i] = b.readInt();
            for (int k = 0; k < 3; k++) l[i * 3 + k] = b.readFloat();
        }
        return new AboardSyncPayload(ids, cs, l);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
