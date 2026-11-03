package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.block.MachineBlock;
import com.workshopcraft.railworks.block.MachineBlockEntity;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;

/**
 * The lathe and pillar drill, in blocks from the main part's floor centre. x runs to the operator's right,
 * z towards the operator, y up. Painted parts are machine green, bright steel is left bare.
 */
public class MachineModel extends Model<MachineRenderer.State> {
    static final double CH = 1.62, HX = -1.08, HY = 1.32, DY = 1.6, DZ = -0.05;
    private static final float Q = (float) (Math.PI / 2);
    private static final double STEP = 0.006;

    private enum Zone {
        WOOD(0, 0), STEEL(128, 0), CAST(0, 128), DARK(128, 128), RED(128, 192), ROUGH(192, 192), FINE(128, 64), PLATE(128, 96);

        final int u, v;

        Zone(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    private static int count;

    private static void box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz) {
        box(p, z, cx, cy, cz, sx, sy, sz, 0);
    }

    private static void box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz, double rz) {
        box(p, z, cx, cy, cz, sx, sy, sz, rz, EnumSet.allOf(Direction.class));
    }

    /** A box that sits on another, so its bottom face is never seen. */
    private static void seated(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz) {
        box(p, z, cx, cy, cz, sx, sy, sz, 0, EnumSet.complementOf(EnumSet.of(Direction.DOWN)));
    }

    private static void box(PartDefinition p, Zone z, double cx, double cy, double cz, double sx, double sy, double sz, double rz, Set<Direction> faces) {
        float x = (float) (sx * 16), y = (float) (sy * 16), d = (float) (sz * 16);
        p.addOrReplaceChild("b" + count++, CubeListBuilder.create().texOffs(z.u, z.v).addBox(-x / 2, -y / 2, -d / 2, x, y, d, faces),
                PartPose.offsetAndRotation((float) (cx * 16), (float) (cy * 16), (float) (cz * 16), 0, 0, (float) rz));
    }

    private static PartDefinition group(PartDefinition p, String name, double x, double y, double z) {
        return group(p, name, x, y, z, 0, 0);
    }

    private static PartDefinition group(PartDefinition p, String name, double x, double y, double z, float xr, float yr) {
        return p.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offsetAndRotation((float) (x * 16), (float) (y * 16), (float) (z * 16), xr, yr, 0));
    }

    /** Small depth step between overlapping faces, so no two of them share a plane. */
    private static double step(double t) {
        return Math.min(STEP, t * 0.12);
    }

    /** Facing z, unless put in a group turned by {@link #Q}. Each turned box is a step thinner than the last. */
    private static void disc(PartDefinition p, Zone zone, double cx, double cy, double cz, double r, double t) {
        double b = r * Math.tan(Math.PI / 8) * 1.08, e = step(t);
        for (int i = 0; i < 4; i++) box(p, zone, cx, cy, cz, 2 * r, 2 * b, t - 2 * e * i, i * Math.PI / 4);
    }

    private static void wheel(PartDefinition p, Zone zone, double r, double t, int spokes) {
        int n = 18;
        double e = step(t);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            box(p, zone, r * Math.cos(a), r * Math.sin(a), 0, 2 * Math.PI * r / n * 1.15, t, t - 2 * e * (i % 2), a + Math.PI / 2);
        }
        for (int i = 0; i < spokes; i++) box(p, zone, 0, 0, 0, 2 * r, t * 0.6, t * 0.6 - 2 * e * i, Math.PI * i / spokes);
        box(p, Zone.DARK, 0, 0, 0, t * 2, t * 2, t * 1.2);
    }

    private static void handle(PartDefinition p, double r, double out) {
        box(p, Zone.STEEL, r, 0, out * 0.09, 0.05, 0.05, 0.18);
        box(p, Zone.WOOD, r, 0, out * 0.24, 0.08, 0.08, 0.16);
    }

    public static LayerDefinition lathe() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition f = group(root, "frame", 0, 0, 0);
        for (double a : new double[]{-1.2, 1.15}) {
            box(f, Zone.CAST, a, 0.45, 0, 0.5, 0.82, 0.6);
            box(f, Zone.CAST, a, 0.03, 0, 0.66, 0.06, 0.76);
        }
        box(f, Zone.CAST, 0, 0.95, 0, 3.1, 0.22, 0.5);
        for (double w : new double[]{-0.16, 0.16}) box(f, Zone.STEEL, 0, 1.075, w, 3.06, 0.04, 0.07);
        box(f, Zone.DARK, 0, 0.66, 0, 2.0, 0.04, 0.42);
        seated(f, Zone.CAST, HX, 1.34, 0, 0.7, 0.6, 0.56);
        box(f, Zone.CAST, HX, 1.66, 0, 0.6, 0.06, 0.48);
        box(f, Zone.STEEL, -0.65, CH, 0, 0.22, 0.12, 0.12);
        box(f, Zone.CAST, 1.0, 1.1325, 0, 0.46, 0.075, 0.44);
        box(f, Zone.CAST, 1.0, 1.44, 0, 0.38, 0.54, 0.3);
        box(f, Zone.STEEL, 0.71, CH, 0, 0.2, 0.08, 0.08);
        box(f, Zone.STEEL, 1.255, CH, 0, 0.15, 0.04, 0.04);
        wheel(group(f, "tail_wheel", 1.29, CH, 0, 0, Q), Zone.STEEL, 0.12, 0.03, 3);
        box(f, Zone.STEEL, 0, 0.99, 0.29, 2.9, 0.035, 0.035);
        box(f, Zone.STEEL, 0, 0.91, 0.29, 2.9, 0.028, 0.028);
        for (double e : new double[]{-1.47, 1.47}) box(f, Zone.CAST, e, 0.95, 0.29, 0.06, 0.18, 0.08);
        box(f, Zone.STEEL, HX, HY, 0.36, 0.07, 0.07, 0.2);
        box(f, Zone.STEEL, HX, HY, -0.36, 0.07, 0.07, 0.2);

        PartDefinition spin = group(root, "spin", -0.52, CH, 0);
        PartDefinition fp = group(spin, "plate", 0, 0, 0, 0, Q);
        disc(fp, Zone.CAST, 0, 0, 0, 0.3, 0.06);
        for (double t : new double[]{0.4, 2.5, 4.6}) box(fp, Zone.STEEL, 0.25 * Math.cos(t), 0.25 * Math.sin(t), 0.06, 0.08, 0.08, 0.08);
        PartDefinition rw = group(spin, "rough_wheel", 0.12, 0, 0, 0, Q);
        disc(rw, Zone.ROUGH, 0, 0, 0, 0.56, 0.17);
        box(rw, Zone.DARK, 0, 0, 0.085, 0.2, 0.2, 0.03);
        PartDefinition tw = group(spin, "turned_wheel", 0.12, 0, 0, 0, Q);
        disc(tw, Zone.FINE, 0, 0, 0.02, 0.5, 0.12);
        disc(tw, Zone.FINE, 0, 0, -0.055, 0.56, 0.05);
        box(tw, Zone.DARK, 0, 0, 0.085, 0.2, 0.2, 0.03);
        PartDefinition ra = group(spin, "rough_axle", 0.57, 0, 0, 0, Q);
        disc(ra, Zone.ROUGH, 0, 0, 0, 0.08, 1.14);
        PartDefinition ta = group(spin, "turned_axle", 0.57, 0, 0, 0, Q);
        disc(ta, Zone.FINE, 0, 0, 0, 0.065, 1.14);
        for (double e : new double[]{-0.5, 0.5}) disc(ta, Zone.DARK, 0, 0, e, 0.075, 0.08);

        PartDefinition saddle = group(root, "saddle", 0.1, 1.145, 0);
        box(saddle, Zone.CAST, 0, 0, 0, 0.36, 0.1, 0.62);
        seated(saddle, Zone.CAST, 0, 0.08, 0.04, 0.2, 0.06, 0.52);
        seated(saddle, Zone.CAST, 0, 0.1475, 0.18, 0.16, 0.075, 0.22);
        seated(saddle, Zone.DARK, 0, 0.2825, 0.2, 0.1, 0.205, 0.1);
        seated(saddle, Zone.STEEL, 0, 0.455, 0.2, 0.14, 0.14, 0.14);
        box(saddle, Zone.CAST, 0, -0.1675, 0.291, 0.34, 0.235, 0.078);
        box(saddle, Zone.STEEL, 0, 0.08, 0.35, 0.03, 0.03, 0.1);
        wheel(group(saddle, "cross_wheel", 0, 0.08, 0.38), Zone.STEEL, 0.07, 0.025, 2);
        box(saddle, Zone.STEEL, 0.06, -0.19, 0.365, 0.03, 0.03, 0.07);
        wheel(group(saddle, "apron_wheel", 0.06, -0.19, 0.38), Zone.STEEL, 0.08, 0.025, 3);
        PartDefinition face = group(saddle, "face_tool", 0, 0, 0);
        box(face, Zone.STEEL, -0.21, 0.27, 0.2, 0.38, 0.05, 0.05);
        PartDefinition side = group(saddle, "side_tool", 0, 0, 0);
        box(side, Zone.STEEL, 0, 0.455, 0.15, 0.05, 0.05, 0.14);

        PartDefinition crank = group(root, "crank", HX, HY, 0.47);
        wheel(crank, Zone.RED, 0.46, 0.05, 3);
        handle(crank, 0.42, 1);
        PartDefinition pulley = group(root, "pulley", HX, HY, -0.44);
        disc(pulley, Zone.CAST, 0, 0, 0, 0.22, 0.14);
        box(pulley, Zone.DARK, 0, 0, 0, 0.06, 0.06, 0.16);
        return LayerDefinition.create(mesh, 256, 256);
    }

    public static LayerDefinition drill() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition f = group(root, "frame", 0, 0, 0);
        box(f, Zone.CAST, 0, 0.05, 0, 0.8, 0.1, 0.8);
        box(f, Zone.CAST, 0, 0.2, -0.22, 0.3, 0.2, 0.3);
        box(f, Zone.STEEL, 0, 1.05, -0.22, 0.12, 1.7, 0.12);
        box(f, Zone.CAST, 0, 0.89, -0.22, 0.18, 0.12, 0.18);
        box(f, Zone.CAST, 0, 0.89, -0.06, 0.14, 0.1, 0.18);
        box(f, Zone.CAST, 0, 0.92, 0.12, 0.46, 0.05, 0.46);
        box(f, Zone.CAST, 0, 1.75, -0.05, 0.28, 0.5, 0.5);
        box(f, Zone.CAST, 0, 1.46, 0.12, 0.12, 0.08, 0.12);
        box(f, Zone.STEEL, 0, DY, DZ, 0.74, 0.05, 0.05);
        for (double e : new double[]{-0.165, 0.165}) disc(group(f, "boss" + (e < 0 ? 0 : 1), e, DY, DZ, 0, Q), Zone.CAST, 0, 0, 0, 0.065, 0.05);
        box(f, Zone.STEEL, 0, 2.02, -0.05, 0.04, 0.06, 0.04);
        wheel(group(f, "feed", 0, 2.06, -0.05, Q, 0), Zone.STEEL, 0.14, 0.03, 3);

        PartDefinition plate = group(root, "plate", 0, 0.955, 0.12);
        seated(group(plate, "raw_plate", 0, 0, 0), Zone.PLATE, 0, 0, 0, 0.32, 0.02, 0.42);
        seated(group(plate, "fine_plate", 0, 0, 0), Zone.FINE, 0, 0, 0, 0.32, 0.02, 0.42);
        for (int i = 0; i < 4; i++) {
            PartDefinition h = group(plate, "hole" + i, 0, 0, 0);
            box(h, Zone.DARK, (i % 2 == 0 ? -1 : 1) * 0.09, 0.013, (i < 2 ? -1 : 1) * 0.12, 0.05, 0.008, 0.05);
        }
        for (Zone z : new Zone[]{Zone.ROUGH, Zone.FINE}) {
            PartDefinition ab = group(root, z == Zone.ROUGH ? "axle_box" : "fine_box", 0, 1.055, 0.12);
            seated(ab, z, 0, 0, 0, 0.3, 0.22, 0.26);
            for (int e : new int[]{-1, 1}) seated(ab, z, e * 0.19, -0.08, 0, 0.08, 0.06, 0.2);
            if (z == Zone.FINE) box(ab, Zone.DARK, 0, 0.114, 0, 0.12, 0.01, 0.12);
        }

        PartDefinition spindle = group(root, "spindle", 0, 1.6, 0.12);
        box(spindle, Zone.STEEL, 0, 0, 0, 0.05, 0.56, 0.05);
        box(spindle, Zone.STEEL, 0, -0.3, 0, 0.08, 0.07, 0.08);
        box(spindle, Zone.DARK, 0, -0.41, 0, 0.03, 0.16, 0.03);

        PartDefinition crank = group(root, "crank", -0.33, DY, DZ);
        PartDefinition cf = group(crank, "face", 0, 0, 0, 0, Q);
        wheel(cf, Zone.RED, 0.36, 0.045, 3);
        handle(cf, 0.32, -1);
        PartDefinition pulley = group(root, "pulley", 0.33, DY, DZ);
        PartDefinition pf = group(pulley, "face", 0, 0, 0, 0, Q);
        disc(pf, Zone.CAST, 0, 0, 0, 0.2, 0.12);
        box(pf, Zone.DARK, 0, 0, 0, 0.075, 0.075, 0.14);
        return LayerDefinition.create(mesh, 256, 256);
    }

    /** The hand cranked plate rolls, three blocks across: two cheeks, three rolls and the crank on the right. */
    public static LayerDefinition rolls() {
        count = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition f = group(root, "frame", 0, 0, 0);
        box(f, Zone.CAST, 0, 0.06, 0, 2.8, 0.12, 0.8);
        for (double a : new double[]{-1.25, 1.25}) {
            seated(f, Zone.CAST, a, 0.7, 0, 0.2, 1.16, 0.64);
            box(f, Zone.DARK, a, 1.05, 0, 0.22, 0.14, 0.4);
        }
        box(f, Zone.CAST, 0, 0.4, -0.25, 2.3, 0.1, 0.1);
        PartDefinition gear = group(f, "gear", -1.4, 0.95, 0, 0, Q);
        disc(gear, Zone.CAST, 0, 0, 0, 0.26, 0.06);
        String[] names = {"roll_top", "roll_a", "roll_b"};
        double[][] at = {{1.05, 0}, {0.8, 0.17}, {0.8, -0.17}};
        for (int i = 0; i < 3; i++) {
            PartDefinition r = group(root, names[i], 0, at[i][0], at[i][1]);
            PartDefinition rf = group(r, "face", 0, 0, 0, 0, Q);
            disc(rf, Zone.STEEL, 0, 0, 0, i == 0 ? 0.12 : 0.1, 2.3);
        }
        PartDefinition plate = group(root, "plate", 0, 0, 0);
        PartDefinition flat = group(plate, "flat", 0, 0.92, 0);
        box(flat, Zone.PLATE, 0, 0, 0, 1.9, 0.03, 0.62);
        PartDefinition bent = group(plate, "bent", 0, 1.05, 0);
        for (int k = -3; k <= 3; k++) {
            double a = k * 0.32;
            box(bent, Zone.PLATE, 0, -0.32 * Math.cos(a) + 0.12, 0.32 * Math.sin(a), 1.9, 0.03, 0.11);
        }
        PartDefinition crank = group(root, "crank", 1.05, 1.0, 0.47);
        wheel(crank, Zone.RED, 0.38, 0.05, 3);
        handle(crank, 0.34, 1);
        PartDefinition pulley = group(root, "pulley", 1.05, 1.0, -0.42);
        disc(pulley, Zone.CAST, 0, 0, 0, 0.2, 0.12);
        box(pulley, Zone.DARK, 0, 0, 0, 0.06, 0.06, 0.14);
        return LayerDefinition.create(mesh, 256, 256);
    }

    private final boolean lathe, rolling;
    private ModelPart rollTop, rollA, rollB, flat, bent, rollPlate;
    private final ModelPart crank, pulley;
    private ModelPart spin, roughWheel, turnedWheel, roughAxle, turnedAxle, saddle, faceTool, sideTool;
    private ModelPart plate, rawPlate, finePlate, axleBox, fineBox, spindle;
    private final ModelPart[] holes = new ModelPart[4];

    public MachineModel(ModelPart root, MachineBlock.Kind kind) {
        super(root, RenderTypes::entityCutout);
        this.lathe = kind == MachineBlock.Kind.LATHE;
        this.rolling = kind == MachineBlock.Kind.ROLLS;
        crank = root.getChild("crank");
        pulley = root.getChild("pulley");
        if (rolling) {
            rollTop = root.getChild("roll_top");
            rollA = root.getChild("roll_a");
            rollB = root.getChild("roll_b");
            rollPlate = root.getChild("plate");
            flat = rollPlate.getChild("flat");
            bent = rollPlate.getChild("bent");
        } else if (lathe) {
            spin = root.getChild("spin");
            roughWheel = spin.getChild("rough_wheel");
            turnedWheel = spin.getChild("turned_wheel");
            roughAxle = spin.getChild("rough_axle");
            turnedAxle = spin.getChild("turned_axle");
            saddle = root.getChild("saddle");
            faceTool = saddle.getChild("face_tool");
            sideTool = saddle.getChild("side_tool");
        } else {
            plate = root.getChild("plate");
            rawPlate = plate.getChild("raw_plate");
            finePlate = plate.getChild("fine_plate");
            for (int i = 0; i < 4; i++) holes[i] = plate.getChild("hole" + i);
            axleBox = root.getChild("axle_box");
            fineBox = root.getChild("fine_box");
            spindle = root.getChild("spindle");
        }
    }

    @Override
    public void setupAnim(MachineRenderer.State s) {
        super.setupAnim(s);
        MachineBlockEntity.Job j = s.job;
        float p = s.progress;
        if (rolling) {
            crank.zRot = -s.crank;
            pulley.zRot = -s.crank;
            rollTop.xRot = s.crank * 2;
            rollA.xRot = -s.crank * 2;
            rollB.xRot = -s.crank * 2;
            rollPlate.visible = j == MachineBlockEntity.Job.ROLLED_PLATE;
            flat.visible = p < 0.5F;
            bent.visible = p >= 0.5F;
        } else if (lathe) {
            crank.zRot = -s.crank;
            pulley.zRot = -s.crank;
            spin.xRot = s.crank * 2.5F;
            boolean wheel = j == MachineBlockEntity.Job.CARRIAGE_WHEEL || j == MachineBlockEntity.Job.ENGINE_WHEEL,
                    axle = j == MachineBlockEntity.Job.CARRIAGE_AXLE || j == MachineBlockEntity.Job.CYLINDER;
            roughWheel.visible = wheel && p < 0.5F;
            turnedWheel.visible = wheel && p >= 0.5F;
            roughAxle.visible = axle && p < 0.5F;
            turnedAxle.visible = axle && p >= 0.5F;
            faceTool.visible = !axle;
            sideTool.visible = axle;
            saddle.x = (float) ((axle ? -0.35 + 0.9 * p : 0.1) * 16);
        } else {
            crank.xRot = s.crank;
            pulley.xRot = s.crank;
            boolean pl = j == MachineBlockEntity.Job.PLATE || j == MachineBlockEntity.Job.COUPLING_ROD, box = j == MachineBlockEntity.Job.AXLE_BOX;
            plate.visible = pl;
            rawPlate.visible = p < 1;
            finePlate.visible = p >= 1;
            for (int i = 0; i < 4; i++) holes[i].visible = p >= (i + 1) / 4.0F;
            axleBox.visible = box && p < 1;
            fineBox.visible = box && p >= 1;
            spindle.yRot = s.crank * 3;
            double top = pl ? 1.52 : box ? 1.73 : 1.6, drop = j == null ? 0 : 0.1 * (s.turning || p >= 1 ? 1 : 0.3);
            spindle.y = (float) ((top - drop) * 16);
        }
    }
}
