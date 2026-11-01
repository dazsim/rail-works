package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.trolley.Carriage;
import com.workshopcraft.railworks.trolley.CarriageLayout;
import com.workshopcraft.railworks.trolley.CarriageLayout.Model;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import com.workshopcraft.railworks.trolley.RailVehicle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;

public class CarriageRenderer extends EntityRenderer<Carriage, CarriageRenderState> {
    static final Identifier TEXTURE = RailWorks.id("textures/entity/carriage.png"), GHOST = RailWorks.id("textures/entity/carriage_ghost.png");
    static final float DOOR_SWING = (float) Math.toRadians(100);
    // meshes by build state, shared by every carriage built the same way
    private static final Map<String, CarriageModel.Built> BUILT = new HashMap<>();
    private static final Map<String, CarriageModel.Built> GHOSTS = new HashMap<>();
    private static CarriageModel.Built whole;

    public CarriageRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 2.4f;
    }

    static CarriageModel.Built built(String st) {
        if (BUILT.size() > 64) BUILT.clear();
        return BUILT.computeIfAbsent(st, CarriageModel::build);
    }

    /** The whole carriage in outline, for the ghost over two bogies. */
    static CarriageModel.Built whole() {
        if (whole == null) whole = CarriageModel.ghost(CarriageLayout.SLOTS.stream().filter(s -> s.required()).toList(), null);
        return whole;
    }

    @Override
    public CarriageRenderState createRenderState() {
        return new CarriageRenderState();
    }

    @Override
    public void extractRenderState(Carriage c, CarriageRenderState s, float pt) {
        super.extractRenderState(c, s, pt);
        s.heading = (float) Math.toRadians(c.getYRot(pt) + 90);
        s.pitch = (float) Math.toRadians(-c.getXRot(pt));
        s.slots = c.slots();
        for (int i = 0; i < s.door.length; i++) s.door[i] = Mth.lerp(pt, c.doorO[i], c.door[i]);
        s.coupled = 0;
        if (c.towed() && !c.mounted() && c.towKind() == RailVehicle.SCREW) s.coupled |= c.towEnd() > 0 ? 2 : 1;
        for (Carriage o : c.level().getEntitiesOfClass(Carriage.class, c.getBoundingBox().inflate(24), o -> o != c && o.towed() && o.leader() == c && o.towKind() == RailVehicle.SCREW))
            s.coupled |= o.leaderEnd() > 0 ? 2 : 1;
        s.ghost = null;
        Player p = Minecraft.getInstance().player;
        if (p != null && p.distanceToSqr(c) < 24 * 24) {
            for (InteractionHand h : InteractionHand.values()) {
                Item it = p.getItemInHand(h).getItem();
                if (!CarriageLayout.isPart(it)) continue;
                List<Slot> can = CarriageLayout.fitsFor(s.slots, it);
                if (c.done()) can.removeIf(Slot::required);
                if (can.isEmpty()) break;
                String key = s.slots + it;
                if (GHOSTS.size() > 64) GHOSTS.clear();
                s.ghost = GHOSTS.computeIfAbsent(key, k -> CarriageModel.ghost(can, it));
                break;
            }
        }
    }

    @Override
    protected AABB getBoundingBoxForCulling(Carriage c) {
        return c.getBoundingBox().inflate(9, 1, 9);
    }

    @Override
    public void submit(CarriageRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(-s.heading));
        pose.mulPose(Axis.ZP.rotation(s.pitch));
        CarriageModel.Built b = built(s.slots);
        CarriageModel.Mesh ls = new CarriageModel.Mesh(), lg = new CarriageModel.Mesh();
        for (Slot sl : CarriageLayout.SLOTS) {
            Model m = CarriageLayout.modelAt(s.slots, sl.i());
            if (m == null || m.leaf() == null) continue;
            float a;
            if (m.leaf().axis() == 'y') {
                float f = sl.door() >= 0 ? s.door[sl.door()] : 0;
                double lx = m.leaf().solid().isEmpty() ? 0 : m.leaf().solid().getFirst().cx();
                a = -Math.signum(sl.side()) * Math.signum((float) lx) * f * DOOR_SWING;
            } else {
                a = (s.coupled >> (sl.end() > 0 ? 1 : 0) & 1) != 0 ? sl.end() * (float) Math.PI / 2 : 0;
            }
            CarriageModel.leaf(ls, lg, m.leaf(), a);
        }
        int light = s.lightCoords;
        if (!b.solid().empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> b.solid().emit(p, vc, light, -1));
        if (!ls.empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> ls.emit(p, vc, light, -1));
        if (!b.glass().empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (p, vc) -> b.glass().emit(p, vc, light, -1));
        if (!lg.empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (p, vc) -> lg.emit(p, vc, light, -1));
        CarriageModel.Built g = s.ghost;
        if (g != null && !g.solid().empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(GHOST), (p, vc) -> g.solid().emit(p, vc, 0xF000F0, -1));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
