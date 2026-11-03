package com.workshopcraft.railworks.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Built in blocks with x along the trolley (front +x), y up from the rail top and z towards the tool box side. */
public class TrolleyModel extends EntityModel<TrolleyRenderState> {
    static final double WR = 0.28, AXH = WR, DECK = AXH + 0.26, TOP = DECK + 0.1, L = 2.7, W = 2.1, WB = 0.8, GAUGE = 1.5;
    static final double PH = TOP + 1.05, BL = 1.5;

    private enum Zone {
        WOOD(0, 0), STEEL(128, 0), CAST(0, 128), DARK(128, 128), RED(128, 192);

        final int u, v;

        Zone(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    private static int count;

    private static PartDefinition box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz) {
        return box(p, z, cx, cy, cz, sx, sy, sz, 0, 0);
    }

    /** A box centred at (cx, cy, cz), turned by rz about z then ry about y. */
    private static PartDefinition box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz, double rz, double ry) {
        float x = (float) (sx * 16), y = (float) (sy * 16), d = (float) (sz * 16);
        return p.addOrReplaceChild("b" + count++, CubeListBuilder.create().texOffs(z.u, z.v).addBox(-x / 2, -y / 2, -d / 2, x, y, d),
                PartPose.offsetAndRotation((float) (cx * 16), (float) (cy * 16), (float) (cz * 16), 0, (float) ry, (float) rz));
    }

    private static PartDefinition group(PartDefinition p, String name, double x, double y, double z) {
        return p.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset((float) (x * 16), (float) (y * 16), (float) (z * 16)));
    }

    /** Each turned box is a little thinner than the last, so no two faces share a plane. */
    private static void disc(PartDefinition p, Zone zone, double cx, double cy, double cz, double r, double t) {
        double b = r * Math.tan(Math.PI / 8) * 1.08, e = Math.min(0.006, t * 0.12);
        for (int i = 0; i < 4; i++) box(p, zone, cx, cy, cz, 2 * r, 2 * b, t - 2 * e * i, i * Math.PI / 4, 0);
    }

    public static LayerDefinition layer() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition frame = group(root, "frame", 0, 0, 0);
        for (int side : new int[]{-1, 1}) box(frame, Zone.WOOD, 0, DECK - 0.1, side * 0.98, L, 0.2, 0.16);
        for (int e : new int[]{-1, 1}) {
            box(frame, Zone.WOOD, e * (L / 2 - 0.08), DECK - 0.1, 0, 0.16, 0.2, W - 0.3);
            for (int side : new int[]{-1, 1}) box(frame, Zone.DARK, e * (L / 2 + 0.01), DECK - 0.1, side * 0.75, 0.02, 0.22, 0.12);
        }
        int n = 9;
        double pw = L / n;
        for (int i = 0; i < n; i++) box(frame, Zone.WOOD, -L / 2 + pw * (i + 0.5), DECK + 0.05, 0, pw - 0.025, 0.1, W);
        box(frame, Zone.WOOD, 0.05, TOP + 0.17, 0.72, 1.0, 0.34, 0.5);
        for (double av : new double[]{-0.35, 0.45}) box(frame, Zone.DARK, av, TOP + 0.17, 0.72, 0.03, 0.36, 0.52);
        box(frame, Zone.DARK, 0.05, TOP + 0.24, 0.465, 0.2, 0.05, 0.02);
        box(frame, Zone.WOOD, 0.05, TOP + 0.36, 0.72, 1.06, 0.04, 0.56);
        box(frame, Zone.STEEL, -L / 2 - 0.04, DECK + 0.2, -0.75, 0.04, 0.5, 0.04);
        box(frame, Zone.DARK, -L / 2 - 0.12, DECK + 0.48, -0.75, 0.16, 0.2, 0.16);
        box(frame, Zone.RED, -L / 2 - 0.205, DECK + 0.48, -0.75, 0.01, 0.1, 0.1);
        box(frame, Zone.STEEL, 0.1, TOP + 0.03, -0.72, 0.9, 0.05, 0.05);
        box(frame, Zone.CAST, 0.53, TOP + 0.06, -0.72, 0.12, 0.12, 0.16);
        for (double d : new double[]{-0.4, -0.12}) box(frame, Zone.CAST, d, TOP + 0.05, -0.75, 0.24, 0.1, 0.24);

        for (int k = 0; k < 2; k++) {
            double ax = k == 0 ? WB : -WB;
            PartDefinition set = group(root, "set" + k, 0, 0, 0);
            for (int side : new int[]{-1, 1}) {
                box(set, Zone.CAST, ax, AXH, side * 0.98, 0.22, 0.22, 0.19);
                box(set, Zone.CAST, ax - 0.16, AXH, side * 0.98, 0.04, 0.34, 0.2);
                box(set, Zone.CAST, ax + 0.16, AXH, side * 0.98, 0.04, 0.34, 0.2);
            }
            PartDefinition spin = group(root, "spin" + k, ax, AXH, 0);
            for (int side : new int[]{-1, 1}) {
                double w = side * GAUGE / 2;
                disc(spin, Zone.CAST, 0, 0, w + side * 0.03, WR, 0.1);
                disc(spin, Zone.CAST, 0, 0, w - side * 0.04, WR + 0.05, 0.03);
                box(spin, Zone.DARK, 0, 0, w + side * 0.085, 0.12, 0.12, 0.03);
                box(spin, Zone.DARK, 0, 0, w + side * 0.085, 0.4, 0.04, 0.02);
            }
            box(spin, Zone.DARK, 0, 0, 0, 0.08, 0.08, 1.95);
        }

        PartDefinition pump = group(root, "pump", 0, 0, 0);
        for (int side : new int[]{-1, 1}) {
            box(pump, Zone.WOOD, 0, TOP + 0.5, side * 0.32, 0.12, 1.0, 0.12);
            for (int e : new int[]{-1, 1}) {
                double a1 = e * 0.55, h0 = TOP + 0.75, h1 = TOP;
                box(pump, Zone.STEEL, a1 / 2, (h0 + h1) / 2, side * 0.32, Math.hypot(a1, h1 - h0), 0.04, e > 0 ? 0.04 : 0.034, Math.atan2(h1 - h0, a1), 0);
            }
        }
        box(pump, Zone.CAST, 0, PH, 0, 0.18, 0.18, 0.78);
        disc(pump, Zone.CAST, WB, AXH, 0.22, 0.26, 0.05);
        disc(pump, Zone.CAST, WB - 0.33, AXH + 0.15, 0.21, 0.1, 0.05);
        box(pump, Zone.DARK, WB - 0.33, AXH + 0.15, 0.05, 0.06, 0.06, 0.5);
        box(pump, Zone.STEEL, 0.2, (PH + AXH) / 2 + 0.1, 0.16, 0.05, PH - AXH - 0.1, 0.05, Math.atan2(PH - AXH, 0.35 - (WB - 0.33)) - Math.PI / 2, 0);
        PartDefinition beam = group(pump, "beam", 0, PH + 0.08, 0);
        box(beam, Zone.STEEL, 0, 0, 0, BL, 0.07, 0.07);
        for (int side : new int[]{-1, 1}) box(beam, Zone.STEEL, 0, 0, side * 0.07, BL * 0.55, 0.12, 0.03);
        for (int e : new int[]{-1, 1}) {
            box(beam, Zone.STEEL, e * BL / 2, 0, 0, 0.06, 0.06, 1.06);
            for (int side : new int[]{-1, 1}) box(beam, Zone.WOOD, e * BL / 2, 0, side * 0.42, 0.09, 0.09, 0.26);
        }

        PartDefinition brake = group(root, "brake", 0, 0, 0);
        double ba = -L / 2 + 0.25, bw = 0.85;
        box(brake, Zone.CAST, ba, TOP + 0.05, bw, 0.16, 0.1, 0.14);
        PartDefinition lever = group(brake, "lever", ba + 0.05, TOP + 0.05, bw);
        box(lever, Zone.STEEL, 0, 0.42, 0, 0.05, 0.85, 0.05);
        box(lever, Zone.RED, 0, 0.84, 0, 0.08, 0.14, 0.08);
        for (int side : new int[]{-1, 1}) box(brake, Zone.CAST, -WB - WR - 0.05, AXH, side * GAUGE / 2, 0.06, 0.24, 0.1);
        box(brake, Zone.STEEL, -WB - WR - 0.05, AXH, 0, 0.04, 0.04, GAUGE);
        return LayerDefinition.create(mesh, 256, 256);
    }

    private final ModelPart[] sets = new ModelPart[2], spins = new ModelPart[2];
    private final ModelPart pump, beam, brake, lever;

    public TrolleyModel(ModelPart root) {
        super(root);
        for (int k = 0; k < 2; k++) {
            sets[k] = root.getChild("set" + k);
            spins[k] = root.getChild("spin" + k);
        }
        pump = root.getChild("pump");
        beam = pump.getChild("beam");
        brake = root.getChild("brake");
        lever = brake.getChild("lever");
    }

    @Override
    public void setupAnim(TrolleyRenderState s) {
        super.setupAnim(s);
        for (int k = 0; k < 2; k++) {
            sets[k].visible = spins[k].visible = s.stage > k;
            spins[k].zRot = -s.wheel;
        }
        pump.visible = s.stage >= 3;
        brake.visible = s.stage >= 4;
        beam.zRot = (float) Math.toRadians(9) * Mth.sin(s.beam);
        lever.zRot = (float) Math.toRadians(s.brake ? 14 : -12);
    }
}
