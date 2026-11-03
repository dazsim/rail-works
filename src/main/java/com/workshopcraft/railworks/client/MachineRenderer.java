package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.MachineBlock;
import com.workshopcraft.railworks.block.MachineBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity, MachineRenderer.State> {
    private static final Identifier TEXTURE = RailWorks.id("textures/entity/machine.png");
    private final MachineModel lathe = new MachineModel(MachineModel.lathe().bakeRoot(), MachineBlock.Kind.LATHE);
    private final MachineModel drill = new MachineModel(MachineModel.drill().bakeRoot(), MachineBlock.Kind.DRILL);
    private final MachineModel rolls = new MachineModel(MachineModel.rolls().bakeRoot(), MachineBlock.Kind.ROLLS);

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        MachineBlock.Kind kind = MachineBlock.Kind.LATHE;
        boolean turning;
        MachineBlockEntity.@Nullable Job job;
        float progress, crank;
    }

    public MachineRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MachineBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        s.facing = be.getBlockState().getValue(MachineBlock.FACING);
        s.kind = be.kind();
        s.job = be.job();
        s.progress = be.progress();
        s.turning = be.turning();
        s.crank = Mth.lerp(partialTick, be.crankO, be.crank);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - s.facing.toYRot()));
        out.submitModel(s.kind == MachineBlock.Kind.LATHE ? lathe : s.kind == MachineBlock.Kind.DRILL ? drill : rolls, s, pose, TEXTURE, s.lightCoords, OverlayTexture.NO_OVERLAY, 0, s.breakProgress);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    public AABB getRenderBoundingBox(MachineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
