package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.track.TrackView;
import com.workshopcraft.railworks.trolley.HandTrolley;
import com.workshopcraft.railworks.trolley.Rail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class TrolleyClient {
    private TrolleyClient() {}

    public static void init() {
        ClientAboard.init();
        com.workshopcraft.railworks.trolley.RailVehicle.clientTrack = id -> {
            TrackView.Seg s = ClientTrack.seg(id);
            return s == null ? null : new Rail(s.id, s.path, s.y, s.live);
        };
        HandTrolley.clientInput = t -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && p.getVehicle() == t && t.isPumper(p) ? p.input.keyPresses : null;
        };
    }

    /** Pumpers stand on the deck instead of sitting. */
    public static boolean standing(Entity e) {
        return e.getVehicle() instanceof HandTrolley t && t.isPumper(e);
    }
}
