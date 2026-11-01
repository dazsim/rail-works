package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.railworks.RailWorks;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

/** Draws a work piece item with the same model parts the lathe and drill show, so the two never drift apart. */
public class WorkPieceRenderer implements NoDataSpecialModelRenderer {
    public static final Identifier ID = RailWorks.id("work_piece");
    private static final Identifier TEXTURE = RailWorks.id("textures/entity/machine.png");

    private enum Piece {
        ROUGH_CARRIAGE_WHEEL(true, 0.85F, "spin", "rough_wheel"),
        CARRIAGE_WHEEL(true, 0.85F, "spin", "turned_wheel"),
        ROUGH_CARRIAGE_AXLE(true, 0.85F, "spin", "rough_axle"),
        CARRIAGE_AXLE(true, 0.85F, "spin", "turned_axle"),
        ROUGH_AXLE_BOX(false, 2.0F, "axle_box"),
        AXLE_BOX(false, 2.0F, "fine_box"),
        STEEL_PLATE(false, 2.0F, "plate"),
        DRILLED_PLATE(false, 2.0F, "plate");

        final boolean lathe;
        final float scale;
        final String[] path;

        Piece(boolean lathe, float scale, String... path) {
            this.lathe = lathe;
            this.scale = scale;
            this.path = path;
        }
    }

    private final Piece piece;
    private final ModelPart part;

    private WorkPieceRenderer(Piece piece) {
        this.piece = piece;
        ModelPart p = (piece.lathe ? MachineModel.lathe() : MachineModel.drill()).bakeRoot();
        for (String n : piece.path) p = p.getChild(n);
        p.x = p.y = p.z = 0;
        if (piece == Piece.STEEL_PLATE || piece == Piece.DRILLED_PLATE) {
            boolean drilled = piece == Piece.DRILLED_PLATE;
            p.getChild("raw_plate").visible = !drilled;
            p.getChild("fine_plate").visible = drilled;
            for (int i = 0; i < 4; i++) p.getChild("hole" + i).visible = drilled;
        }
        part = p;
    }

    private void place(PoseStack pose) {
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(piece.scale, piece.scale, piece.scale);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        pose.pushPose();
        place(pose);
        out.submitModelPart(part, pose, RenderTypes.entityCutout(TEXTURE), light, overlay, null, false, foil, -1, null, outline);
        pose.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        PoseStack pose = new PoseStack();
        place(pose);
        part.getExtentsForGui(pose, output);
    }

    public record Unbaked(String piece) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("piece").forGetter(Unbaked::piece)).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public WorkPieceRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new WorkPieceRenderer(Piece.valueOf(piece.toUpperCase()));
        }
    }
}
