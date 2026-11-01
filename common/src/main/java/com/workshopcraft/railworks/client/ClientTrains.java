package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.trolley.RailVehicle;
import com.workshopcraft.railworks.trolley.Train;
import com.workshopcraft.railworks.trolley.TrainSyncPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of each moving train. Every tick it runs the train on along the path the server sent,
 * pulls it gently towards where the server says it is, and puts every member on the one path, so coupled
 * vehicles always keep their spacing on screen.
 */
public final class ClientTrains {
    private ClientTrains() {}

    static final double DT = 0.05;

    static final class Live {
        final Train path = new Train();
        int[] ids = new int[0];
        double speed, serverSpeed, serverRef;
        int age;
    }

    private static final Map<Integer, Live> TRAINS = new HashMap<>();
    private static @Nullable ClientLevel seen;

    public static void receive(TrainSyncPayload p) {
        Live old = TRAINS.get(p.root());
        Live t = new Live();
        for (int k = 0; k < p.segs().length; k++) t.path.steps.add(new Train.Step(p.segs()[k], p.dirs()[k]));
        t.ids = p.ids();
        t.path.d = new double[p.d().length];
        t.path.flip = new int[p.flip().length];
        for (int i = 0; i < p.d().length; i++) {
            t.path.d[i] = p.d()[i];
            t.path.flip[i] = p.flip()[i];
        }
        t.serverRef = p.ref();
        t.serverSpeed = p.speed();
        t.path.ref = p.ref();
        t.speed = p.speed();
        if (old != null) {
            // carry on from where we showed it, mapped onto the new path through the front member
            double x = remap(old, t);
            if (!Double.isNaN(x) && Math.abs(x - p.ref()) < 3) {
                t.path.ref = x;
                t.speed = old.speed;
            }
        }
        TRAINS.put(p.root(), t);
        // a vehicle can only be in one train: drop older ones sharing a member
        for (Iterator<Map.Entry<Integer, Live>> it = TRAINS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Live> e = it.next();
            if (e.getKey() == p.root()) continue;
            for (int id : e.getValue().ids) for (int o : t.ids) if (id == o) {
                it.remove();
                break;
            }
        }
    }

    private static double remap(Live old, Live now) {
        for (int i = 0; i < old.ids.length; i++)
            for (int j = 0; j < now.ids.length; j++) {
                if (old.ids[i] != now.ids[j]) continue;
                Train.Pos p = old.path.at(RailVehicle.clientTrack, old.path.ref + old.path.d[i]);
                if (p == null) return Double.NaN;
                double x = now.path.xOf(RailVehicle.clientTrack, p.rail().id(), p.s());
                return Double.isNaN(x) ? x : x - now.path.d[j];
            }
        return Double.NaN;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != seen) {
            TRAINS.clear();
            seen = level;
        }
        if (level == null || mc.isPaused()) return;
        for (Iterator<Live> it = TRAINS.values().iterator(); it.hasNext(); ) {
            Live t = it.next();
            if (++t.age > 100) {
                it.remove();
                continue;
            }
            step(level, t);
        }
    }

    private static void step(ClientLevel level, Live t) {
        RailVehicle[] vs = new RailVehicle[t.ids.length];
        double own = Double.NaN;
        boolean settled = t.serverSpeed == 0 && t.age < 10 && Math.abs(t.path.ref - t.serverRef) < 0.5;
        for (int i = 0; i < vs.length; i++) {
            Entity e = level.getEntity(t.ids[i]);
            if (e instanceof RailVehicle v && !v.isRemoved()) {
                vs[i] = v;
                double p = v.predictTrain(t.speed, t.path.flip[i], settled);
                if (!Double.isNaN(p)) own = p;
            }
        }
        boolean predicting = !Double.isNaN(own);
        double before = t.speed;
        t.speed = predicting ? own : t.speed + (t.serverSpeed - t.speed) * 0.5;
        double ref0 = t.path.ref;
        t.path.ref -= (before + t.speed) / 2 * DT;
        double target = t.serverRef - t.serverSpeed * Math.min(t.age, 6) * DT;
        double err = target - t.path.ref;
        if (Math.abs(err) > 3) t.path.ref = target;
        else t.path.ref += err * (predicting ? 0.1 : 0.25);
        t.path.ref = Math.max(0, Math.min(t.path.ref, t.path.total(RailVehicle.clientTrack) - t.path.span()));
        double dx = t.path.ref - ref0;
        for (int i = 0; i < vs.length; i++) {
            RailVehicle v = vs[i];
            if (v == null) continue;
            Train.Pos p = t.path.at(RailVehicle.clientTrack, t.path.ref + t.path.d[i]);
            if (p == null) continue;
            // x runs towards the rear, so a member's +s speed is the train's speed times -dir
            v.placeByTrain(p.rail(), p.s(), -p.dir() * t.path.flip[i] > 0, -t.speed * p.dir(), dx * p.dir());
            for (Entity rider : v.getPassengers()) v.positionRider(rider);
        }
    }
}
