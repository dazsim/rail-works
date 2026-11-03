package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.trolley.Engine;
import com.workshopcraft.railworks.trolley.EngineShape;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class EngineRenderer extends EntityRenderer<Engine, EngineRenderState> {
    static final Identifier TEXTURE = RailWorks.id("textures/entity/engine.png");
    private static final Map<String, CarriageModel.Built> BUILT = new HashMap<>();
    private static final Map<String, CarriageModel.Built> GHOSTS = new HashMap<>();
    private static final CarriageModel.Mesh[] WHEELS = new CarriageModel.Mesh[8];

    public EngineRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 2.2f;
    }

    @Override
    public EngineRenderState createRenderState() {
        return new EngineRenderState();
    }

    static CarriageModel.Mesh wheel(int s, int paint) {
        int k = (s > 0 ? 4 : 0) + paint;
        if (WHEELS[k] == null) WHEELS[k] = EngineModel.wheel(s, paint);
        return WHEELS[k];
    }

    @Override
    public void extractRenderState(Engine e, EngineRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        s.heading = (float) Math.toRadians(e.getYRot(pt) + 90);
        s.pitch = (float) Math.toRadians(-e.getXRot(pt));
        s.paint = EngineModel.paint(e);
        s.key = e.stage() + "/" + e.fit() + "/" + e.work() + "/" + s.paint;
        if (BUILT.size() > 64) BUILT.clear();
        s.built = BUILT.computeIfAbsent(s.key, k -> EngineModel.build(e));
        s.wheels = e.fitted(1);
        s.rods = e.fitted(2) > 0;
        s.controls = e.fitted(9) > 0;
        s.bunker = e.fitted(8) > 1;
        s.spin = Mth.lerp(pt, e.spinO, e.spin);
        s.reg = e.regulator();
        s.rev = e.reverser();
        s.brake = e.brakeOn();
        s.coal = e.coal();
        s.lit = e.lit();
        s.ghost = null;
        Player p = Minecraft.getInstance().player;
        EngineShape.Unit nx = e.done() ? null : nextUnit(e);
        if (p != null && nx != null && p.distanceToSqr(e) < 24 * 24
                && (p.getMainHandItem().is(engineItem(nx)) || p.getOffhandItem().is(engineItem(nx)))) {
            String k = e.stage() + "/" + e.fit();
            if (GHOSTS.size() > 64) GHOSTS.clear();
            s.ghost = GHOSTS.computeIfAbsent(k, x -> EngineModel.ghost(nx));
        }
    }

    static EngineShape.Unit nextUnit(Engine e) {
        var us = EngineShape.STAGES.get(e.stage()).units();
        return e.fit() < us.size() ? us.get(e.fit()) : null;
    }

    static net.minecraft.world.item.Item engineItem(EngineShape.Unit u) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(RailWorks.id(u.item()));
    }

    @Override
    protected AABB getBoundingBoxForCulling(Engine e) {
        return e.getBoundingBox().inflate(4, 1, 4);
    }

    @Override
    public void submit(EngineRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-s.heading));
        pose.mulPose(Axis.ZP.rotation(s.pitch));
        int light = s.lightCoords;
        CarriageModel.Built b = s.built;
        if (b != null && !b.solid().empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> b.solid().emit(p, vc, light, -1));
        for (int i = 0; i < s.wheels; i++)
            for (int k = -1; k <= 1; k += 2) {
                CarriageModel.Mesh w = wheel(k, s.paint);
                pose.pushPose();
                pose.translate(EngineShape.AXLES[i], EngineShape.WY, 0);
                pose.mulPose(Axis.ZP.rotation(s.spin + (k > 0 ? (float) Math.PI / 2 : 0)));
                out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> w.emit(p, vc, light, -1));
                pose.popPose();
            }
        CarriageModel.Mesh mv = new CarriageModel.Mesh(), lit = new CarriageModel.Mesh();
        EngineModel.moving(mv, lit, s);
        if (!mv.empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> mv.emit(p, vc, light, -1));
        if (!lit.empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> lit.emit(p, vc, 0xF000F0, -1));
        if (b != null && !b.glass().empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (p, vc) -> b.glass().emit(p, vc, light, -1));
        CarriageModel.Built g = s.ghost;
        if (g != null && !g.solid().empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(CarriageRenderer.GHOST), (p, vc) -> g.solid().emit(p, vc, 0xF000F0, -1));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
