package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.workshopcraft.railworks.RailWorks;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
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

    private enum Source {
        LATHE, DRILL, BOGIE, BAR;

        LayerDefinition layer() {
            return switch (this) {
                case LATHE -> MachineModel.lathe();
                case DRILL -> MachineModel.drill();
                case BOGIE -> BogieModel.layer();
                case BAR -> BogieModel.bar();
            };
        }
    }

    private enum Piece {
        ROUGH_CARRIAGE_WHEEL(Source.LATHE, 0.85F, 0, "spin", "rough_wheel"),
        CARRIAGE_WHEEL(Source.LATHE, 0.85F, 0, "spin", "turned_wheel"),
        ROUGH_CARRIAGE_AXLE(Source.LATHE, 0.85F, 0, "spin", "rough_axle"),
        CARRIAGE_AXLE(Source.LATHE, 0.85F, 0, "spin", "turned_axle"),
        ROUGH_AXLE_BOX(Source.DRILL, 2.0F, 0, "axle_box"),
        AXLE_BOX(Source.DRILL, 2.0F, 0, "fine_box"),
        STEEL_PLATE(Source.DRILL, 2.0F, 0, "plate"),
        DRILLED_PLATE(Source.DRILL, 2.0F, 0, "plate"),
        CARRIAGE_WHEELSET(Source.BOGIE, 0.5F, 0, "spin0"),
        BOGIE_FRAME(Source.BOGIE, 0.36F, 0.58F, "frame"),
        BOGIE_SPRING(Source.BOGIE, 1.4F, 0, "spring0"),
        BOLSTER(Source.BOGIE, 0.5F, 0, "bolster"),
        TOW_BAR(Source.BAR, 0.6F, 0, "tow_bar"),
        BOGIE(Source.BOGIE, 0.32F, 0.5F);

        final Source source;
        final float scale, dy;
        final String[] path;

        Piece(Source source, float scale, float dy, String... path) {
            this.source = source;
            this.scale = scale;
            this.dy = dy;
            this.path = path;
        }
    }

    private final Piece piece;
    private final ModelPart part;

    private WorkPieceRenderer(Piece piece) {
        this.piece = piece;
        ModelPart p = piece.source.layer().bakeRoot();
        for (String n : piece.path) p = p.getChild(n);
        p.x = p.y = p.z = 0;
        if (piece == Piece.STEEL_PLATE || piece == Piece.DRILLED_PLATE) {
            boolean drilled = piece == Piece.DRILLED_PLATE;
            p.getChild("raw_plate").visible = !drilled;
            p.getChild("fine_plate").visible = drilled;
            for (int i = 0; i < 4; i++) p.getChild("hole" + i).visible = drilled;
        }
        if (piece == Piece.BOGIE) for (int k = 0; k < 2; k++) p.getChild("packing" + k).visible = false;
        part = p;
    }

    private void place(PoseStack pose) {
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(piece.scale, piece.scale, piece.scale);
        pose.translate(0, -piece.dy, 0);
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
