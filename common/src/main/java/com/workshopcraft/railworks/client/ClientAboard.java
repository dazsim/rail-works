package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.trolley.AboardPayload;
import com.workshopcraft.railworks.trolley.AboardSyncPayload;
import com.workshopcraft.railworks.trolley.Carriage;
import com.workshopcraft.railworks.trolley.CarriageBody;
import com.workshopcraft.railworks.trolley.CarriageBody.Pose;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of walking in carriages. The player's own place in a body is kept in the body's frame and
 * sent to the server each tick; others standing in bodies are pinned to where the server says they are.
 */
public final class ClientAboard {
    private ClientAboard() {}

    record Pin(int carriage, Vec3 local, int tick) {}

    private static int aboard = -1, ticks;
    private static final Map<Integer, Pin> PINS = new HashMap<>();

    static void init() {
        CarriageBody.clientCarry = ClientAboard::carry;
    }

    public static int aboard() {
        return aboard;
    }

    private static void carry(Carriage c, Pose was, Pose now) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.level() != c.level() || aboard != c.getId() || p.isPassenger()) return;
        p.setPos(now.world(was.local(p.position())));
        float turn = Mth.wrapDegrees((float) Math.toDegrees(now.hf() - was.hf()));
        if (turn != 0) {
            p.setYRot(p.getYRot() + turn);
            p.setYHeadRot(p.getYHeadRot() + turn);
            p.yBodyRot += turn;
        }
    }

    public static void receive(AboardSyncPayload p) {
        PINS.clear();
        for (int i = 0; i < p.ids().length; i++)
            PINS.put(p.ids()[i], new Pin(p.carriages()[i], new Vec3(p.local()[i * 3], p.local()[i * 3 + 1], p.local()[i * 3 + 2]), ticks));
    }

    /** After the player has moved and before the trains do: are we standing in a body, and where. */
    static void beforeTrains(Minecraft mc, ClientLevel level) {
        ticks++;
        LocalPlayer p = mc.player;
        if (p == null || mc.getConnection() == null) {
            aboard = -1;
            return;
        }
        if (p.isPassenger() || p.isSpectator() || !p.isAlive()) {
            aboard = -1;
            return;
        }
        Carriage in = null;
        Vec3 l = null;
        for (Carriage c : CarriageBody.near(level, p.position(), 16)) {
            Vec3 v = c.pose().local(p.position());
            if (CarriageBody.aboard(v)) {
                in = c;
                l = v;
                break;
            }
        }
        if (in != null) {
            aboard = in.getId();
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new AboardPayload(in.getId(), l.x, l.y, l.z)));
        } else if (aboard >= 0) {
            // off at the body's own speed
            Entity was = level.getEntity(aboard);
            if (was != null) p.setDeltaMovement(p.getDeltaMovement().add(was.getX() - was.xo, 0, was.getZ() - was.zo));
            aboard = -1;
            mc.getConnection().send(new ServerboundCustomPayloadPacket(new AboardPayload(-1, p.getX(), p.getY(), p.getZ())));
        }
    }

    /** After the trains have moved: everyone else standing in a body goes where it now is. */
    static void afterTrains(Minecraft mc, ClientLevel level) {
        PINS.values().removeIf(pin -> ticks - pin.tick > 4);
        for (Map.Entry<Integer, Pin> e : PINS.entrySet()) {
            Entity en = level.getEntity(e.getKey());
            if (en == null || en == mc.player || en.isPassenger()) continue;
            if (level.getEntity(e.getValue().carriage) instanceof Carriage c) en.setPos(c.pose().world(e.getValue().local));
        }
    }
}
