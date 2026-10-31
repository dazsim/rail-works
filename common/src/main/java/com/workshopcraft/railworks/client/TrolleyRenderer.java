package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.trolley.HandTrolley;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public class TrolleyRenderer extends EntityRenderer<HandTrolley, TrolleyRenderState> {
    private static final Identifier TEXTURE = RailWorks.id("textures/entity/hand_trolley.png");
    private final TrolleyModel model = new TrolleyModel(TrolleyModel.layer().bakeRoot());

    public TrolleyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.1f;
    }

    @Override
    public TrolleyRenderState createRenderState() {
        return new TrolleyRenderState();
    }

    @Override
    public void extractRenderState(HandTrolley t, TrolleyRenderState s, float pt) {
        super.extractRenderState(t, s, pt);
        s.heading = (float) Math.toRadians(t.getYRot(pt) + 90);
        s.pitch = (float) Math.toRadians(-t.getXRot(pt));
        s.wheel = (float) Mth.lerp(pt, t.wheelO, t.wheel);
        s.beam = (float) Mth.lerp(pt, t.beamO, t.beam);
        s.stage = t.stage();
        s.brake = t.braking();
    }

    @Override
    protected AABB getBoundingBoxForCulling(HandTrolley t) {
        return t.getBoundingBox().inflate(1.2, 1.2, 1.2);
    }

    @Override
    public void submit(TrolleyRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-s.heading));
        pose.mulPose(Axis.ZP.rotation(s.pitch));
        out.submitModel(model, s, pose, TEXTURE, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor, null);
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
