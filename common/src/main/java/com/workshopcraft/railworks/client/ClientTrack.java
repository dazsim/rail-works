package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.track.TrackView;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every section the server says is near this player, drawn from vertex buffers baked once and kept on the GPU. A section
 * draws wherever any piece of it is in a loaded chunk, whether or not its end nodes are.
 */
public final class ClientTrack {
    private static RenderPipeline pipeline(String name, boolean blend) {
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(RailWorks.id("pipeline/" + name))
                .withVertexShader(RailWorks.id("core/track"))
                .withFragmentShader(Identifier.withDefaultNamespace("core/particle"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT);
        if (blend) b.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT));
        return b.build();
    }

    public static final RenderPipeline SOLID = pipeline("track", false), GLASS = pipeline("track_glass", true);

    /** One layer of a baked section: a vertex buffer with each 16-block piece's vertices stored together. */
    private static final class Layer {
        final GpuBuffer buf;
        final int[] first, count;

        Layer(GpuBuffer buf, int[] first, int[] count) {
            this.buf = buf;
            this.first = first;
            this.count = count;
        }
    }

    private static final class Baked {
        final BlockPos origin;
        final AABB[] bounds;
        final int[] cx, cz;
        final @Nullable Layer solid, detail, glass;

        Baked(BlockPos origin, AABB[] bounds, int[] cx, int[] cz, @Nullable Layer solid, @Nullable Layer detail, @Nullable Layer glass) {
            this.origin = origin;
            this.bounds = bounds;
            this.cx = cx;
            this.cz = cz;
            this.solid = solid;
            this.detail = detail;
            this.glass = glass;
        }

        void close() {
            for (Layer l : new Layer[]{solid, detail, glass}) if (l != null) l.buf.close();
        }
    }

    private static final class Entry {
        TrackView.Seg seg;
        boolean dirty = true;
        long built;
        long lastLoadCheck;
        boolean missing;
        @Nullable Baked baked;

        Entry(TrackView.Seg seg) {
            this.seg = seg;
        }
    }

    private static final Long2ObjectOpenHashMap<Entry> SEGS = new Long2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel level;
    private static final int REBUILDS_PER_FRAME = 2, RELIGHT = 200;

    private ClientTrack() {}

    private static void clear() {
        for (Entry e : SEGS.values()) if (e.baked != null) e.baked.close();
        SEGS.clear();
    }

    private static boolean sameLevel() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != level) {
            clear();
            level = l;
        }
        return l != null;
    }

    /** A sync packet from the server; called on the client thread. */
    public static void receive(CompoundTag t) {
        if (!sameLevel()) return;
        if (t.getBooleanOr("reset", false)) clear();
        for (long id : t.getLongArray("drop").orElse(new long[0])) {
            Entry e = SEGS.remove(id);
            if (e != null && e.baked != null) e.baked.close();
        }
        for (Tag x : t.getListOrEmpty("set")) {
            if (!(x instanceof CompoundTag c)) continue;
            TrackView.Seg s = TrackView.seg(c);
            Entry e = SEGS.get(s.id);
            if (e == null) SEGS.put(s.id, new Entry(s));
            else {
                e.seg = s;
                e.dirty = true;
            }
        }
    }

    private record Draw(Baked baked, Layer layer, int piece) {}

    public static void submit(SubmitNodeCollector out, LevelRenderState state) {
        if (!sameLevel() || SEGS.isEmpty()) return;
        ClientLevel lvl = level;
        CameraRenderState camera = state.cameraRenderState;
        Vec3 c = camera.pos;
        Frustum f = camera.cullFrustum;
        long now = lvl.getGameTime();
        int budget = REBUILDS_PER_FRAME;
        double view = TrackRenderer.VIEW, near = TrackRenderer.DETAIL, glassView = TrackRenderer.GLASS;
        List<Draw> solid = new ArrayList<>(), glass = new ArrayList<>();
        for (Entry e : SEGS.values()) {
            boolean stale = e.dirty || e.baked == null || now - e.built > RELIGHT || now < e.built;
            if (!stale && e.missing && now - e.lastLoadCheck >= 20) {
                e.lastLoadCheck = now;
                Baked b = e.baked;
                for (int i = 0; i < b.cx.length && !stale; i++) if (lvl.getChunkSource().hasChunk(b.cx[i], b.cz[i])) stale = true;
            }
            if (stale && budget > 0) {
                budget--;
                rebuild(lvl, e, now);
            }
            Baked b = e.baked;
            if (b == null) continue;
            for (int i = 0; i < b.bounds.length; i++) {
                if (!lvl.getChunkSource().hasChunk(b.cx[i], b.cz[i])) continue;
                AABB box = b.bounds[i];
                double d = box.distanceToSqr(c);
                if (d > view * view) continue;
                if (f != null && !f.isVisible(box)) continue;
                if (b.solid != null && b.solid.count[i] > 0) solid.add(new Draw(b, b.solid, i));
                if (d < near * near && b.detail != null && b.detail.count[i] > 0) solid.add(new Draw(b, b.detail, i));
                if (d < glassView * glassView && b.glass != null && b.glass.count[i] > 0) glass.add(new Draw(b, b.glass, i));
            }
        }
        if (!solid.isEmpty() || !glass.isEmpty()) out.submitParticleGroup(new Group(solid, glass, c));
    }

    private static void rebuild(ClientLevel lvl, Entry e, long now) {
        TrackView.Seg seg = e.seg;
        double[] p0 = seg.path.pose(0);
        BlockPos origin = BlockPos.containing(p0[0], seg.height(0), p0[1]);
        TrackMesh m = TrackRenderer.sectionMesh(lvl, origin, seg);
        int n = m.list.size();
        AABB[] bounds = new AABB[n];
        int[] cx = new int[n], cz = new int[n];
        boolean missing = false;
        for (int i = 0; i < n; i++) {
            TrackMesh.Part p = m.list.get(i);
            bounds[i] = p.bounds;
            cx[i] = p.cx;
            cz[i] = p.cz;
            if (!lvl.getChunkSource().hasChunk(p.cx, p.cz)) missing = true;
        }
        Baked b = new Baked(origin, bounds, cx, cz, upload(m, 0), upload(m, 1), upload(m, 2));
        if (e.baked != null) e.baked.close();
        e.baked = b;
        e.dirty = false;
        e.missing = missing;
        e.built = now - Math.floorMod(Long.hashCode(seg.id) * 31, 100);
        e.lastLoadCheck = now;
    }

    private static TrackMesh.Buf layer(TrackMesh.Part p, int which) {
        return which == 0 ? p.solid : which == 1 ? p.detail : p.glass;
    }

    private static @Nullable Layer upload(TrackMesh m, int which) {
        int total = 0;
        for (TrackMesh.Part p : m.list) total += layer(p, which).verts;
        if (total == 0) return null;
        int n = m.list.size();
        int[] first = new int[n], count = new int[n];
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(total * DefaultVertexFormat.PARTICLE.getVertexSize())) {
            BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            int at = 0;
            for (int i = 0; i < n; i++) {
                TrackMesh.Buf buf = layer(m.list.get(i), which);
                first[i] = at;
                count[i] = buf.verts;
                at += buf.verts;
                float[] d = buf.d;
                for (int v = 0; v < buf.verts; v++) {
                    int o = v * TrackMesh.STRIDE;
                    int color = Float.floatToRawIntBits(d[o + 5]);
                    double nx = d[o + 7], ny = d[o + 8], nz = d[o + 9];
                    double shade = nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (ny > 0 ? 1.0 : 0.5);
                    int r = (int) (((color >> 16) & 0xFF) * shade), g = (int) (((color >> 8) & 0xFF) * shade), bl = (int) ((color & 0xFF) * shade);
                    bb.addVertex(d[o], d[o + 1], d[o + 2]).setUv(d[o + 3], d[o + 4])
                            .setColor((color & 0xFF000000) | (r << 16) | (g << 8) | bl).setLight(Float.floatToRawIntBits(d[o + 6]));
                }
            }
            try (MeshData mesh = bb.buildOrThrow()) {
                GpuBuffer gpu = RenderSystem.getDevice().createBuffer(() -> "RailWorks track", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                return new Layer(gpu, first, count);
            }
        }
    }

    /** Hands the baked buffers to the particle passes, which already carry projection, fog and the lightmap. */
    private static final class Group implements SubmitNodeCollector.ParticleGroupRenderer {
        final List<Draw> solid, glass;
        final Vec3 cam;
        boolean translucent;
        final Map<Baked, GpuBufferSlice> transforms = new java.util.IdentityHashMap<>();

        Group(List<Draw> solid, List<Draw> glass, Vec3 cam) {
            this.solid = solid;
            this.glass = glass;
            this.cam = cam;
        }

        @Override
        public boolean isEmpty() {
            return solid.isEmpty() && glass.isEmpty();
        }

        @Override
        public QuadParticleRenderState.@Nullable PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache cache, boolean translucent) {
            this.translucent = translucent;
            List<Draw> list = translucent ? glass : solid;
            if (list.isEmpty()) return null;
            transforms.clear();
            int max = 0;
            GpuBufferSlice any = null;
            for (Draw d : list) {
                max = Math.max(max, d.layer.count[d.piece]);
                GpuBufferSlice s = transforms.get(d.baked);
                if (s == null) {
                    BlockPos o = d.baked.origin;
                    s = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1, 1, 1, 1),
                            new Vector3f((float) (o.getX() - cam.x), (float) (o.getY() - cam.y), (float) (o.getZ() - cam.z)), new Matrix4f());
                    transforms.put(d.baked, s);
                }
                any = s;
            }
            return new QuadParticleRenderState.PreparedBuffers(max / 4 * 6, any, Map.of());
        }

        @Override
        public void render(QuadParticleRenderState.PreparedBuffers prepared, ParticleFeatureRenderer.ParticleBufferCache cache, RenderPass pass, TextureManager textures) {
            List<Draw> list = translucent ? glass : solid;
            RenderSystem.AutoStorageIndexBuffer idx = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            pass.setPipeline(translucent ? GLASS : SOLID);
            AbstractTexture atlas = textures.getTexture(TextureAtlas.LOCATION_BLOCKS);
            pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
            pass.setIndexBuffer(idx.getBuffer(prepared.indexCount()), idx.type());
            Baked last = null;
            GpuBuffer lastBuf = null;
            for (Draw d : list) {
                if (d.baked != last) {
                    pass.setUniform("DynamicTransforms", transforms.get(d.baked));
                    last = d.baked;
                }
                if (d.layer.buf != lastBuf) {
                    pass.setVertexBuffer(0, d.layer.buf);
                    lastBuf = d.layer.buf;
                }
                pass.drawIndexed(d.layer.first[d.piece], 0, d.layer.count[d.piece] / 4 * 6, 1);
            }
        }
    }
}
