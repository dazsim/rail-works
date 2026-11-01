package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.trolley.Bogie;
import com.workshopcraft.railworks.trolley.RailVehicle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BogieRenderer extends EntityRenderer<Bogie, BogieRenderState> {
    private static final Identifier TEXTURE = RailWorks.id("textures/entity/machine.png");
    private final BogieModel model = new BogieModel(BogieModel.layer().bakeRoot());
    private final ModelPart pole, end;

    public BogieRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.2f;
        ModelPart bar = BogieModel.bar().bakeRoot();
        pole = bar.getChild("pole");
        end = bar.getChild("end");
    }

    @Override
    public BogieRenderState createRenderState() {
        return new BogieRenderState();
    }

    @Override
    public void extractRenderState(Bogie b, BogieRenderState s, float pt) {
        super.extractRenderState(b, s, pt);
        s.heading = (float) Math.toRadians(b.getYRot(pt) + 90);
        s.pitch = (float) Math.toRadians(-b.getXRot(pt));
        s.wheel = (float) Mth.lerp(pt, b.wheelO, b.wheel);
        s.stage = b.stage();
        s.brake = b.braking();
        s.barFrom = s.barTo = null;
        RailVehicle l = b.towed() ? b.leader() : null;
        if (l != null) {
            Vec3 at = b.getPosition(pt);
            s.barFrom = b.eye(b.towEnd(), at, b.getYRot(pt), b.getXRot(pt)).subtract(at);
            s.barTo = l.eye(b.leaderEnd(), l.getPosition(pt), l.getYRot(pt), l.getXRot(pt)).subtract(at);
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(Bogie b) {
        return b.getBoundingBox().inflate(b.towed() ? 4 : 1.2, 1.2, b.towed() ? 4 : 1.2);
    }

    @Override
    public void submit(BogieRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-s.heading));
        pose.mulPose(Axis.ZP.rotation(s.pitch));
        out.submitModel(model, s, pose, TEXTURE, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor, null);
        pose.popPose();
        if (s.barFrom != null && s.barTo != null) {
            Vec3 d = s.barTo.subtract(s.barFrom);
            double len = d.length();
            if (len > 0.05) {
                float yaw = (float) Math.atan2(d.z, d.x), pitch = (float) Math.atan2(d.y, Math.hypot(d.x, d.z));
                pose.pushPose();
                pose.translate(s.barFrom.x, s.barFrom.y, s.barFrom.z);
                pose.mulPose(Axis.YP.rotation(-yaw));
                pose.mulPose(Axis.ZP.rotation(pitch));
                drawPart(end, pose, out, s);
                pose.pushPose();
                pose.scale((float) len, 1, 1);
                drawPart(pole, pose, out, s);
                pose.popPose();
                pose.translate(len, 0, 0);
                pose.mulPose(Axis.YP.rotation((float) Math.PI));
                drawPart(end, pose, out, s);
                pose.popPose();
            }
        }
        super.submit(s, pose, out, camera);
    }

    private static void drawPart(ModelPart p, PoseStack pose, SubmitNodeCollector out, BogieRenderState s) {
        out.submitModelPart(p, pose, RenderTypes.entityCutout(TEXTURE), s.lightCoords, OverlayTexture.NO_OVERLAY, null, false, false, -1, null, s.outlineColor);
    }
}
