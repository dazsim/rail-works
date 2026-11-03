package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.trolley.CarriageLayout.Box;
import com.workshopcraft.railworks.trolley.Engine;
import com.workshopcraft.railworks.trolley.EngineShape;
import com.workshopcraft.railworks.trolley.EngineShape.Part;
import com.workshopcraft.railworks.trolley.EngineShape.Unit;
import java.util.List;

/** Engine geometry from EngineShape, drawn on the carriage sheet recoloured: CHOC is the livery green, CREAM the primer. */
public final class EngineModel {
    private EngineModel() {}

    /** Paint flags: bit 0 green done, bit 1 black done. */
    static String zone(String z, int paint) {
        return switch (z) {
            case "P" -> (paint & 1) != 0 ? "CHOC" : "CREAM";
            case "PB" -> (paint & 2) != 0 ? "BLACK" : "CREAM";
            case "PR" -> (paint & 2) != 0 ? "RED" : "CREAM";
            case "COAL" -> "MOQUETTE";
            case "DIAL" -> "PORCELAIN";
            default -> z;
        };
    }

    /** A part, turned by its own matrix, then about z by a and moved by (ox, oy, oz). */
    static void part(CarriageModel.Mesh out, Part p, String zone, double a, float ox, float oy, float oz) {
        CarriageModel.Mesh t = new CarriageModel.Mesh();
        CarriageModel.box(t, new Box(zone, 0, 0, 0, p.sx(), p.sy(), p.sz(), 0, 0), 0, 0, 0);
        float[] m = p.m();
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        for (int i = 0; i < t.verts; i++) {
            int o = i * 8;
            float[] v = mul(m, t.d[o], t.d[o + 1], t.d[o + 2]), n = mul(m, t.d[o + 5], t.d[o + 6], t.d[o + 7]);
            v[0] += p.cx();
            v[1] += p.cy();
            v[2] += p.cz();
            float vx = v[0] * ca - v[1] * sa, vy = v[0] * sa + v[1] * ca, nx = n[0] * ca - n[1] * sa, ny = n[0] * sa + n[1] * ca;
            out.put(vx + ox, vy + oy, v[2] + oz, t.d[o + 3], t.d[o + 4], nx, ny, n[2]);
        }
    }

    static float[] mul(float[] m, float x, float y, float z) {
        if (m == null) return new float[] {x, y, z};
        return new float[] {m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    static void part(CarriageModel.Mesh solid, CarriageModel.Mesh glass, Part p, int paint) {
        String z = zone(p.zone(), paint);
        part(z.equals("GLASS") ? glass : solid, p, z, 0, 0, 0, 0);
    }

    /** Everything fitted that does not move, with its rivets set or owed. */
    public static CarriageModel.Built build(Engine e) {
        CarriageModel.Mesh solid = new CarriageModel.Mesh(), glass = new CarriageModel.Mesh();
        int paint = paint(e);
        for (int st = 0; st < Engine.DONE; st++) {
            List<Unit> us = EngineShape.STAGES.get(st).units();
            int f = e.fitted(st);
            for (int i = 0; i < f; i++) {
                Unit u = us.get(i);
                for (Part p : u.parts()) part(solid, glass, p, paint);
                int set = e.rivetsSet(st, i);
                String rz = u.parts().isEmpty() ? "BLACK" : zone(u.parts().getFirst().zone(), paint);
                for (int k = 0; k < u.rivets().size(); k++) {
                    float[] r = u.rivets().get(k);
                    CarriageModel.box(solid, new Box(k < set ? rz : "RIVET", r[0], r[1], r[2], r[3], r[4], r[5], 0, 0), 0, 0, 0);
                }
            }
        }
        return new CarriageModel.Built(solid, glass);
    }

    static int paint(Engine e) {
        return (e.green() ? 1 : 0) | (e.black() ? 2 : 0);
    }

    /** One side's wheel about its centre. */
    public static CarriageModel.Mesh wheel(int s, int paint) {
        CarriageModel.Mesh m = new CarriageModel.Mesh();
        for (Part p : EngineShape.wheel(s)) part(m, p, zone(p.zone(), paint), 0, 0, 0, 0);
        return m;
    }

    /** The unit that goes on next, as a ghost. */
    public static CarriageModel.Built ghost(Unit u) {
        CarriageModel.Mesh m = new CarriageModel.Mesh();
        for (Part p : u.parts()) part(m, p, "CREAM", 0, 0, 0, 0);
        if (u.item().equals("engine_wheelset")) for (int s = -1; s <= 1; s += 2) for (Part p : EngineShape.wheel(s)) {
            Part ax = u.parts().getFirst();
            part(m, p, "CREAM", 0, ax.cx(), ax.cy(), 0);
        }
        return new CarriageModel.Built(m, new CarriageModel.Mesh());
    }

    /** Handles, rods, coal and the fire: rebuilt each frame. */
    public static void moving(CarriageModel.Mesh out, CarriageModel.Mesh lit, EngineRenderState s) {
        if (s.rods) for (int k = -1; k <= 1; k += 2) {
            double a = s.spin + (k > 0 ? Math.PI / 2 : 0);
            Part r = EngineShape.rod(k, a);
            part(out, r, r.zone(), 0, 0, 0, 0);
        }
        if (s.controls) for (Part p : EngineShape.handles(s.reg, s.rev, s.brake ? 1 : 0)) part(out, p, zone(p.zone(), s.paint), 0, 0, 0, 0);
        if (s.bunker && s.coal > 0) {
            Part c = EngineShape.coal(Math.min(1, s.coal / 256.0));
            part(out, c, zone(c.zone(), s.paint), 0, 0, 0, 0);
        }
        if (s.lit) {
            Part f = EngineShape.fire();
            part(lit, f, f.zone(), 0, 0, 0, 0);
        }
    }
}
