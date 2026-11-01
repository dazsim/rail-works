package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A train as the server has it this tick: its path with a little track either end, where the front member
 * stands, its speed towards the front, and each member's offset and facing.
 */
public record TrainSyncPayload(int root, long tick, double speed, double ref, long[] segs, byte[] dirs, int[] ids, float[] d, byte[] flip)
        implements CustomPacketPayload {
    public static final Type<TrainSyncPayload> TYPE = new Type<>(RailWorks.id("train_sync"));
    public static final StreamCodec<ByteBuf, TrainSyncPayload> CODEC = StreamCodec.of(TrainSyncPayload::write, TrainSyncPayload::read);

    static TrainSyncPayload of(RailVehicle root, long tick, double speed, Train path, Train t) {
        int n = path.steps.size(), m = t.members.size();
        long[] segs = new long[n];
        byte[] dirs = new byte[n];
        for (int k = 0; k < n; k++) {
            segs[k] = path.steps.get(k).seg();
            dirs[k] = (byte) path.steps.get(k).dir();
        }
        int[] ids = new int[m];
        float[] d = new float[m];
        byte[] flip = new byte[m];
        for (int i = 0; i < m; i++) {
            ids[i] = t.members.get(i).v().getId();
            d[i] = (float) t.d[i];
            flip[i] = (byte) t.flip[i];
        }
        return new TrainSyncPayload(root.getId(), tick, speed, path.ref, segs, dirs, ids, d, flip);
    }

    private static void write(ByteBuf b, TrainSyncPayload p) {
        b.writeInt(p.root);
        b.writeLong(p.tick);
        b.writeDouble(p.speed);
        b.writeDouble(p.ref);
        b.writeShort(p.segs.length);
        for (int k = 0; k < p.segs.length; k++) {
            b.writeLong(p.segs[k]);
            b.writeByte(p.dirs[k]);
        }
        b.writeShort(p.ids.length);
        for (int i = 0; i < p.ids.length; i++) {
            b.writeInt(p.ids[i]);
            b.writeFloat(p.d[i]);
            b.writeByte(p.flip[i]);
        }
    }

    private static TrainSyncPayload read(ByteBuf b) {
        int root = b.readInt();
        long tick = b.readLong();
        double speed = b.readDouble(), ref = b.readDouble();
        int n = Math.min(b.readShort(), 64);
        long[] segs = new long[n];
        byte[] dirs = new byte[n];
        for (int k = 0; k < n; k++) {
            segs[k] = b.readLong();
            dirs[k] = b.readByte();
        }
        int m = Math.min(b.readShort(), 32);
        int[] ids = new int[m];
        float[] d = new float[m];
        byte[] flip = new byte[m];
        for (int i = 0; i < m; i++) {
            ids[i] = b.readInt();
            d[i] = b.readFloat();
            flip[i] = b.readByte();
        }
        return new TrainSyncPayload(root, tick, speed, ref, segs, dirs, ids, d, flip);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
