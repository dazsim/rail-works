package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.trolley.Bogie;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A GWR style carriage bogie in blocks: x along (front +x), y up from the rail top, z across. Steel plate side
 * frames on cast axle boxes, leaf springs under a timber bolster. Drawn with the machine sheet.
 */
public class BogieModel extends EntityModel<BogieRenderState> {
    static final double WR = 0.5, AX = 1.0, GAUGE = 1.5, PZ = 1.0, PT = 0.04, EYE = 1.56, EYE_Y = 0.55;
    private static final float Q = (float) (Math.PI / 2);

    enum Zone {
        WOOD(0, 0), STEEL(128, 0), CAST(0, 128), DARK(128, 128), RED(128, 192), ROUGH(192, 192), FINE(128, 64), PLATE(128, 96), BRASS(224, 176);

        final int u, v;

        Zone(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    private static int count;

    static void box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz) {
        box(p, z, cx, cy, cz, sx, sy, sz, 0);
    }

    static void box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz, double rz) {
        float x = (float) (sx * 16), y = (float) (sy * 16), d = (float) (sz * 16);
        p.addOrReplaceChild("b" + count++, CubeListBuilder.create().texOffs(z.u, z.v).addBox(-x / 2, -y / 2, -d / 2, x, y, d),
                PartPose.offsetAndRotation((float) (cx * 16), (float) (cy * 16), (float) (cz * 16), 0, 0, (float) rz));
    }

    /** A box given by its two corners. */
    static void span(PartDefinition p, Zone z, double x0, double y0, double z0, double x1, double y1, double z1) {
        box(p, z, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0));
    }

    static PartDefinition group(PartDefinition p, String name, double x, double y, double z) {
        return group(p, name, x, y, z, 0);
    }

    static PartDefinition group(PartDefinition p, String name, double x, double y, double z, float xr) {
        return p.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offsetAndRotation((float) (x * 16), (float) (y * 16), (float) (z * 16), xr, 0, 0));
    }

    /** Facing z. Each turned box is a little thinner than the last, so no two faces share a plane. */
    static void disc(PartDefinition p, Zone zone, double cx, double cy, double cz, double r, double t) {
        double b = r * Math.tan(Math.PI / 8) * 1.08, e = Math.min(0.006, t * 0.12);
        for (int i = 0; i < 4; i++) box(p, zone, cx, cy, cz, 2 * r, 2 * b, t - 2 * e * i, i * Math.PI / 4);
    }

    private static void rivets(PartDefinition p, double x0, double x1, double y, double side) {
        int n = Math.max(1, (int) Math.round((x1 - x0) / 0.2));
        for (int i = 0; i <= n; i++) box(p, Zone.DARK, x0 + (x1 - x0) * i / n, y, side * (PZ + PT + 0.005), 0.035, 0.035, 0.01);
    }

    public static LayerDefinition layer() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition frame = group(root, "frame", 0, 0, 0);
        for (int side : new int[]{-1, 1}) {
            double z0 = side * PZ, z1 = side * (PZ + PT);
            // side plate: a deep middle, two ends and strips over the horn gaps
            span(frame, Zone.PLATE, -0.78, 0.30, z0, 0.78, 0.85, z1);
            for (int e : new int[]{-1, 1}) {
                span(frame, Zone.PLATE, e * 1.22, 0.40, z0, e * 1.48, 0.85, z1);
                span(frame, Zone.PLATE, e * 0.78, 0.68, z0, e * 1.22, 0.85, z1);
                for (double hx : new double[]{0.8275, 1.1725}) span(frame, Zone.ROUGH, e * (hx - 0.0275), 0.30, side * 0.96, e * (hx + 0.0275), 0.70, side * 1.10);
                rivets(frame, e * 1.26, e * 1.40, 0.45, side);
                // brake shoes, hung from the transoms
                box(frame, Zone.CAST, e * 0.475, WR, side * GAUGE / 2, 0.04, 0.22, 0.1);
                box(frame, Zone.STEEL, e * 0.47, 0.67, side * GAUGE / 2, 0.025, 0.24, 0.025);
                span(frame, Zone.DARK, e * 0.42, 0.68, side * 0.72, e * 0.485, 0.72, side * 0.78);
            }
            rivets(frame, -1.40, 1.40, 0.81, side);
            rivets(frame, -0.70, 0.70, 0.34, side);
            // transoms, each in two halves across
            for (int e : new int[]{-1, 1}) span(frame, Zone.PLATE, e * 0.30, 0.55, 0, e * 0.42, 0.80, side * (PZ - 0.005));
            // headstocks, stepped up over the flanges
            for (int e : new int[]{-1, 1}) {
                span(frame, Zone.PLATE, e * 1.47, 0.81, 0, e * 1.53, 0.845, side * (PZ + PT - 0.01));
                span(frame, Zone.PLATE, e * 1.475, 0.45, side * 0.86, e * 1.525, 0.82, side * (PZ + 0.02));
            }
            // spring plank links
            for (int e : new int[]{-1, 1}) box(frame, Zone.DARK, e * 0.32, 0.48, side * 0.62, 0.03, 0.15, 0.06);
        }
        span(frame, Zone.DARK, -0.345, 0.40, -0.75, 0.345, 0.44, 0.75);
        for (int e : new int[]{-1, 1}) {
            span(frame, Zone.PLATE, e * 1.475, 0.45, -0.6, e * 1.525, 0.82, 0.6);
            for (int side : new int[]{-1, 1}) span(frame, Zone.DARK, e * 1.525, 0.50, side * 0.04, e * 1.595, 0.60, side * 0.08);
            box(frame, Zone.STEEL, e * EYE, EYE_Y, 0, 0.03, 0.03, 0.17);
        }
        // hand brake on one corner
        box(frame, Zone.CAST, -1.32, 0.55, PZ + PT + 0.03, 0.1, 0.12, 0.06);
        PartDefinition lever = group(frame, "lever", -1.32, 0.55, PZ + PT + 0.08);
        box(lever, Zone.STEEL, 0, 0.3, 0, 0.04, 0.6, 0.04);
        box(lever, Zone.RED, 0, 0.62, 0, 0.07, 0.1, 0.07);

        for (int k = 0; k < 2; k++) {
            double ax = k == 0 ? AX : -AX;
            PartDefinition spin = group(root, "spin" + k, ax, WR, 0);
            for (int side : new int[]{-1, 1}) {
                double w = side * GAUGE / 2;
                disc(spin, Zone.FINE, 0, 0, w, WR, 0.12);
                disc(spin, Zone.FINE, 0, 0, w - side * 0.085, WR + 0.06, 0.05);
                box(spin, Zone.DARK, 0, 0, w + side * 0.075, 0.2, 0.2, 0.03);
                disc(spin, Zone.FINE, 0, 0, side * 0.515, 0.065, 1.03);
            }
            PartDefinition pack = group(root, "packing" + k, ax, 0, 0);
            for (int side : new int[]{-1, 1}) {
                span(pack, Zone.WOOD, 0.08, -0.24, side * 0.86, 0.38, 0.30, side * 1.16);
                span(pack, Zone.WOOD, -0.02, -0.24, side * 0.88, 0.08, 0.10, side * 1.14);
            }
        }
        for (int i = 0; i < 4; i++) {
            PartDefinition b = group(root, "box" + i, i < 2 ? AX : -AX, WR, (i % 2 == 0 ? 1 : -1) * (PZ + PT / 2));
            box(b, Zone.FINE, 0, 0, 0, 0.28, 0.30, 0.22);
            box(b, Zone.BRASS, 0, 0.16, 0, 0.16, 0.02, 0.14);
        }
        for (int j = 0; j < 2; j++) {
            PartDefinition sp = group(root, "spring" + j, 0, 0.53, j == 0 ? 0.62 : -0.62);
            double[] len = {0.54, 0.44, 0.34, 0.24};
            for (int i = 0; i < 4; i++) box(sp, Zone.STEEL, 0, 0.0675 - 0.025 * i, 0, len[i], 0.025 - 0.002 * (i % 2), 0.1 - 0.004 * (i % 2));
            box(sp, Zone.DARK, 0, 0.0025, 0, 0.07, 0.175, 0.14);
            for (int e : new int[]{-1, 1}) box(sp, Zone.DARK, e * 0.27, 0.015, 0, 0.03, 0.11, 0.05);
        }
        PartDefinition bolster = group(root, "bolster", 0, 0.7315, 0);
        span(bolster, Zone.WOOD, -0.18, -0.1085, -0.94, 0.18, 0.1085, 0.94);
        for (int side : new int[]{-1, 1}) {
            box(bolster, Zone.DARK, 0, 0, side * 0.945, 0.37, 0.12, 0.01);
            box(bolster, Zone.CAST, 0, 0.1285, side * 0.7, 0.2, 0.04, 0.14);
        }
        PartDefinition cp = group(bolster, "centre", 0, 0.1285, 0, Q);
        disc(cp, Zone.ROUGH, 0, 0, 0, 0.2, 0.04);
        box(cp, Zone.DARK, 0, 0, -0.022, 0.06, 0.06, 0.008);
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** The tow bar, one block long along x from its own end, plus the item look. */
    public static LayerDefinition bar() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        box(group(root, "pole", 0, 0, 0), Zone.WOOD, 0.5, 0, 0, 1.0, 0.09, 0.09);
        PartDefinition end = group(root, "end", 0, 0, 0);
        box(end, Zone.STEEL, 0.06, 0, 0, 0.14, 0.11, 0.11);
        box(end, Zone.DARK, 0, 0, 0, 0.06, 0.04, 0.2);
        PartDefinition item = group(root, "tow_bar", 0, 0, 0);
        box(item, Zone.WOOD, 0, 0, 0, 1.6, 0.09, 0.09);
        for (int e : new int[]{-1, 1}) {
            box(item, Zone.STEEL, e * 0.72, 0, 0, 0.14, 0.11, 0.11);
            box(item, Zone.DARK, e * 0.8, 0, 0, 0.06, 0.04, 0.2);
        }
        return LayerDefinition.create(mesh, 256, 256);
    }

    private final ModelPart[] spins = new ModelPart[2], packs = new ModelPart[2], boxes = new ModelPart[4], springs = new ModelPart[2];
    private final ModelPart bolster, lever;

    public BogieModel(ModelPart root) {
        super(root);
        for (int k = 0; k < 2; k++) {
            spins[k] = root.getChild("spin" + k);
            packs[k] = root.getChild("packing" + k);
            springs[k] = root.getChild("spring" + k);
        }
        for (int i = 0; i < 4; i++) boxes[i] = root.getChild("box" + i);
        bolster = root.getChild("bolster");
        lever = root.getChild("frame").getChild("lever");
    }

    @Override
    public void setupAnim(BogieRenderState s) {
        super.setupAnim(s);
        for (int k = 0; k < 2; k++) {
            spins[k].visible = s.stage > k;
            packs[k].visible = s.stage <= k;
            spins[k].zRot = -s.wheel;
            springs[k].visible = s.stage > Bogie.BOXES + k;
        }
        for (int i = 0; i < 4; i++) boxes[i].visible = s.stage > Bogie.SETS + i;
        bolster.visible = s.stage > Bogie.SPRINGS;
        lever.zRot = (float) Math.toRadians(s.brake ? 18 : -18);
    }
}
