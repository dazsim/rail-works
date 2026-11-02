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
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
    static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    static final float DOOR_SWING = (float) Math.toRadians(100);
    // meshes by build state, shared by every carriage built the same way
    private static final Map<String, CarriageModel.Built> BUILT = new HashMap<>();
    private static final Map<String, CarriageModel.Built> GHOSTS = new HashMap<>();
    private static final Map<String, CarriageModel.Mesh> LIT = new HashMap<>();
    private static CarriageModel.Built whole;
    private final BlockModelResolver blockModels;

    public CarriageRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 2.4f;
        blockModels = context.getBlockModelResolver();
    }

    static CarriageModel.Mesh lit(String st, String look) {
        if (LIT.size() > 64) LIT.clear();
        return LIT.computeIfAbsent(st + "|" + look, k -> CarriageModel.lit(st, look));
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
        s.look = c.look();
        int n = 0;
        for (Slot sl : CarriageLayout.SLOTS) {
            Model m = CarriageLayout.modelAt(s.slots, sl.i());
            if (m == null || m.block() == null) continue;
            BlockState bs = m.block().get();
            if (bs.hasProperty(BlockStateProperties.LIT)) bs = bs.setValue(BlockStateProperties.LIT, Carriage.lit(s.look, sl.i()));
            if (s.blocks.size() <= n) s.blocks.add(new BlockModelRenderState());
            blockModels.update(s.blocks.get(n), bs, BLOCK_CONTEXT);
            if (s.blockSlots.size() <= n) s.blockSlots.add(sl);
            else s.blockSlots.set(n, sl);
            n++;
        }
        while (s.blocks.size() > n) s.blocks.removeLast();
        while (s.blockSlots.size() > n) s.blockSlots.removeLast();
        for (int i = 0; i < s.door.length; i++) s.door[i] = Mth.lerp(pt, c.doorO[i], c.door[i]);
        s.coupled = 0;
        if (c.towed() && !c.mounted() && c.towKind() == RailVehicle.SCREW) s.coupled |= c.towEnd() > 0 ? 2 : 1;
        for (Carriage o : c.level().getEntitiesOfClass(Carriage.class, c.getBoundingBox().inflate(24), o -> o != c && o.towed() && o.leader() == c && o.towKind() == RailVehicle.SCREW))
            s.coupled |= o.leaderEnd() > 0 ? 2 : 1;
        s.ghost = null;
        Player p = Minecraft.getInstance().player;
        if (p != null && p.distanceToSqr(c) < 24 * 24) {
            InteractionHand h = Carriage.partHand(p);
            if (h != null) {
                Item it = p.getItemInHand(h).getItem();
                List<Slot> can = CarriageLayout.fitsFor(s.slots, it);
                if (c.done()) can.removeIf(Slot::required);
                if (!can.isEmpty()) {
                    String key = s.slots + it;
                    if (GHOSTS.size() > 64) GHOSTS.clear();
                    s.ghost = GHOSTS.computeIfAbsent(key, k -> CarriageModel.ghost(can, it));
                }
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
        CarriageModel.Mesh lm = lit(s.slots, s.look);
        if (!lm.empty()) out.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (p, vc) -> lm.emit(p, vc, 0xF000F0, -1));
        for (int i = 0; i < s.blocks.size(); i++) {
            Slot sl = s.blockSlots.get(i);
            float[] hb = sl.halfBay();
            if (hb == null) continue;
            int k = sl.side() < 0 ? -1 : 1;
            float px = CarriageLayout.PX;
            pose.pushPose();
            pose.translate((hb[0] + hb[1]) / 2, CarriageLayout.FLOOR, k * (CarriageLayout.WALL - 5 * px));
            if (k < 0) pose.mulPose(Axis.YP.rotation((float) Math.PI));
            pose.translate(-5 * px, 0, -5 * px);
            pose.scale(10 / 16f, 10 / 16f, 10 / 16f);
            s.blocks.get(i).submit(pose, out, light, OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
        CarriageModel.Built g = s.ghost;
        if (g != null && !g.solid().empty()) out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(GHOST), (p, vc) -> g.solid().emit(p, vc, 0xF000F0, -1));
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
