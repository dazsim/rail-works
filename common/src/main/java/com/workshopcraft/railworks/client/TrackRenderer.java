package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.TrackNodeBlock;
import com.workshopcraft.railworks.block.TrackNodeBlockEntity;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import com.workshopcraft.railworks.track.TrackPath;
import com.workshopcraft.railworks.track.TrackView;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Draws a node's stake, buffer stop, points and end marker from a cached mesh. Plain sections are drawn by {@link ClientTrack}. */
public class TrackRenderer implements BlockEntityRenderer<TrackNodeBlockEntity, TrackRenderer.State> {
    static final double GAUGE = 1.5, TIE_L = 3, TIE_H = 1 / 3.0, TIE_W = 0.5, BLADE = 5;
    // bottom above the tie top, height, width: foot, web, head of a bullhead rail
    static final double[][] RAIL = {{0.04, 0.05, 0.11}, {0.09, 0.08, 0.05}, {0.17, 0.07, 0.13}};
    static final int WHITE = 0xFFFFFFFF, CHALK = 0xE8FFFFFF, CHALK_RED = 0xE8FF3A30, EDGE = 0x90FFFFFF, EDGE_RED = 0x90FF5040,
            GHOST = 0x7898CCFF, GHOST_RED = 0x78FF6050, GHOST_RAIL = 0xD0B8DCFF, GHOST_RAIL_RED = 0xD0FF7060, BLOCKED = 0x55FF2020,
            BUFFER_RED = 0xFFB02020, LAMP = 0xFFFF3020, END = 0xF0FFC830, END_DARK = 0xD0201A10, END_SOFT = 0x60FFD040;
    static final double VIEW = 192, DETAIL = 40, GLASS = 128, FROG_IN = 1.4, FROG_OUT = 1.6;

    public static class State extends BlockEntityRenderState {
        @Nullable TrackMesh mesh;
    }

    public TrackRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrackNodeBlockEntity be, State s, float partialTick, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTick, camera, breaking);
        s.mesh = mesh(be);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        TrackMesh m = s.mesh;
        if (m == null) return;
        Frustum f = camera.cullFrustum;
        Vec3 c = camera.pos;
        boolean tool = holdingTool();
        for (TrackMesh.Part p : m.list) {
            if (f != null && !f.isVisible(p.bounds)) continue;
            double d = p.bounds.distanceToSqr(c);
            if (d > VIEW * VIEW) continue;
            if (p.solid.verts > 0) out.submitCustomGeometry(pose, Sheets.cutoutBlockSheet(), p.solid::emit);
            if (d < DETAIL * DETAIL && p.detail.verts > 0) out.submitCustomGeometry(pose, Sheets.cutoutBlockSheet(), p.detail::emit);
            if (d < GLASS * GLASS && p.glass.verts > 0) out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), p.glass::emit);
            if (tool && p.marker.verts > 0) out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), p.marker::emit);
        }
    }

    static boolean holdingTool() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(h);
            if (st.is(ModTags.HAMMERS) || st.is(ModContent.SURVEY_STAKE)) return true;
        }
        return false;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return (int) VIEW;
    }

    @Override
    public boolean shouldRender(TrackNodeBlockEntity be, Vec3 camera) {
        if (be.renderCache instanceof TrackMesh m) return m.bounds.distanceToSqr(camera) < VIEW * VIEW;
        return BlockEntityRenderer.super.shouldRender(be, camera);
    }

    /** NeoForge culls against this; a plain method so it also compiles against vanilla. */
    public AABB getRenderBoundingBox(TrackNodeBlockEntity be) {
        return be.renderCache instanceof TrackMesh m ? m.bounds : TrackMesh.EVERYWHERE;
    }

    private static @Nullable TrackMesh mesh(TrackNodeBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return null;
        long now = level.getGameTime();
        if (be.renderCache instanceof TrackMesh m && m.version == be.version && now - m.built < 100 && now >= m.built) return m;
        TrackMesh m = new Builder(level, be.getBlockPos(), be).build();
        m.version = be.version;
        m.built = now - Math.floorMod(be.getBlockPos().hashCode(), 50);
        be.renderCache = m;
        return m;
    }

    /** The mesh of one plain section, in coordinates local to origin. */
    static TrackMesh sectionMesh(Level level, BlockPos origin, TrackView.Seg seg) {
        Builder b = new Builder(level, origin, null);
        b.section(seg);
        b.mesh.finish(b.ox, b.oy, b.oz);
        return b.mesh;
    }

    static TextureAtlasSprite sprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
    }

    private static final class Builder {
        final @Nullable TrackNodeBlockEntity be;
        final Level level;
        final BlockPos origin;
        final double ox, oy, oz;
        final TrackMesh mesh = new TrackMesh();
        TrackMesh.Part cur;
        final Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        final TextureAtlasSprite wood = sprite(RailWorks.id("block/treated_beam")), steel = sprite(RailWorks.id("block/steel_block")),
                cast = sprite(RailWorks.id("block/set_steel")), white = sprite(Identifier.withDefaultNamespace("block/white_concrete")),
                stakeWood = sprite(Identifier.withDefaultNamespace("block/stripped_spruce_log")),
                red = sprite(Identifier.withDefaultNamespace("block/red_concrete")), black = sprite(Identifier.withDefaultNamespace("block/black_concrete"));

        Builder(Level level, BlockPos origin, @Nullable TrackNodeBlockEntity be) {
            this.be = be;
            this.level = level;
            this.origin = origin;
            this.ox = origin.getX();
            this.oy = origin.getY();
            this.oz = origin.getZ();
        }

        TrackMesh build() {
            TrackNodeBlockEntity be = this.be;
            if (be == null) throw new IllegalStateException();
            TrackView v = be.view;
            BlockState st = be.getBlockState();
            boolean staked = st.hasProperty(TrackNodeBlock.STAKE) && st.getValue(TrackNodeBlock.STAKE);
            part(ox + 0.5, oz + 0.5);
            if (staked) stake(v == null ? 0 : v.heading, v == null ? 0 : v.lift);
            if (v != null) {
                if (v.legs.size() == 2) points(v);
                part(ox + 0.5, oz + 0.5);
                if (v.buffer && v.hasOut) buffer(v.out, oy + v.lift / 8.0);
                if (!staked && !v.buffer && v.segs.size() == 1 && v.legs.isEmpty()) endMarker(v.segs.get(0));
            }
            mesh.finish(ox, oy, oz);
            return mesh;
        }

        int light(double x, double y, double z) {
            BlockPos p = BlockPos.containing(x, y, z);
            long k = p.asLong();
            int l = lights.getOrDefault(k, -1);
            if (l == -1) {
                l = level.isLoaded(p) ? LevelRenderer.getLightCoords(level, p) : 0xF000F0;
                lights.put(k, l);
            }
            return l;
        }

        // world point to local
        double[] loc(double x, double y, double z) {
            return new double[]{x - ox, y - oy, z - oz};
        }

        /** An oriented box centred on a world point; th heading, ph pitch, la along, lu up, lw across. */
        void box(TrackMesh.Buf b, TextureAtlasSprite sp, int color, double cx, double cy, double cz, double th, double ph,
                 double la, double lu, double lw, boolean bottom) {
            double c = Math.cos(th), s = Math.sin(th), cp = Math.cos(ph), sp2 = Math.sin(ph);
            double[][] ax = {{c * cp, sp2, s * cp}, {-c * sp2, cp, -s * sp2}, {-s, 0, c}};
            double[] h = {la / 2, lu / 2, lw / 2};
            int light = light(cx, cy + 0.3, cz);
            double[] ctr = loc(cx, cy, cz);
            for (int a = 0; a < 3; a++)
                for (int sg = -1; sg <= 1; sg += 2) {
                    if (a == 1 && sg < 0 && !bottom) continue;
                    int t1 = (a + 1) % 3, t2 = (a + 2) % 3;
                    if (a == 1) {
                        t1 = 0;
                        t2 = 2;
                    }
                    double[] n = {ax[a][0] * sg, ax[a][1] * sg, ax[a][2] * sg};
                    double[][] q = new double[4][];
                    int[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                    for (int i = 0; i < 4; i++) {
                        double[] p = new double[3];
                        for (int k = 0; k < 3; k++)
                            p[k] = ctr[k] + n[k] * h[a] + ax[t1][k] * h[t1] * signs[i][0] + ax[t2][k] * h[t2] * signs[i][1];
                        q[i] = p;
                    }
                    float u1 = (float) Math.min(1, h[t1] * 2), v1 = (float) Math.min(1, h[t2] * 2);
                    b.quad(q[0], q[1], q[2], q[3], n, sp, 0, 0, u1, v1, color, light, light);
                }
        }

        /** A flat strip on the ground along a path, from s0 to s1, w across, at height above the track bed. */
        void strip(TrackMesh.Buf b, TrackView.Seg seg, double s0, double s1, double off, double w, double up, int color) {
            double[] a = seg.path.pose(s0), c = seg.path.pose(s1);
            double ya = seg.height(s0) + up, yc = seg.height(s1) + up;
            double[] p0 = side(a, off - w / 2, ya), p1 = side(c, off - w / 2, yc), p2 = side(c, off + w / 2, yc), p3 = side(a, off + w / 2, ya);
            b.quad(p0, p1, p2, p3, new double[]{0, 1, 0}, white, 0, 0, 1, (float) Math.min(1, w), color,
                    light(a[0], ya + 0.3, a[1]), light(c[0], yc + 0.3, c[1]));
        }

        double[] side(double[] pose, double off, double y) {
            return loc(pose[0] - Math.sin(pose[2]) * off, y, pose[1] + Math.cos(pose[2]) * off);
        }

        static boolean curved(TrackPath p) {
            for (double k : p.curv) if (Math.abs(k) > 1e-9) return true;
            return false;
        }

        static boolean bent(TrackPath p) {
            if (Math.abs(p.rise) > 1e-6) return true;
            for (double k : p.curv) if (Math.abs(k) > 1e-9) return true;
            return false;
        }

        /** One rail from s0 to s1 at an offset across the path; off0 moves the s0 end and fades out by s1. */
        void rail(TrackView.Seg seg, double s0, double s1, double off, double off0, boolean capA, boolean capB) {
            if (s1 - s0 < 1e-4) return;
            int pieces = bent(seg.path) || off0 != 0 ? Math.max(1, (int) Math.ceil((s1 - s0) / 0.5)) : 1;
            double[][] cs = new double[pieces + 1][];
            for (int i = 0; i <= pieces; i++) {
                double s = s0 + (s1 - s0) * i / pieces;
                double[] p = seg.path.pose(s);
                double o = off + off0 * (1 - (double) i / pieces);
                cs[i] = frame(p[0] - Math.sin(p[2]) * o, seg.height(s) + TIE_H, p[1] + Math.cos(p[2]) * o, p[2]);
            }
            emit(cs, capA, capB);
        }

        static double[] frame(double x, double y, double z, double th) {
            return new double[]{x, y, z, -Math.sin(th), Math.cos(th), Math.cos(th), Math.sin(th)};
        }

        /** A rail through world points {x, y, z}, its heading taken from the neighbours. */
        void railLine(List<double[]> pts, boolean capA, boolean capB) {
            int n = pts.size();
            if (n < 2) return;
            double[][] cs = new double[n][];
            for (int i = 0; i < n; i++) {
                double[] a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(n - 1, i + 1));
                cs[i] = frame(pts.get(i)[0], pts.get(i)[1], pts.get(i)[2], Math.atan2(b[2] - a[2], b[0] - a[0]));
            }
            emit(cs, capA, capB);
        }

        void emit(double[][] cs, boolean capA, boolean capB) {
            int pieces = cs.length - 1;
            int[] lt = new int[pieces + 1];
            for (int i = 0; i <= pieces; i++) lt[i] = light(cs[i][0], cs[i][1] + 0.3, cs[i][2]);
            for (int r = 0; r < RAIL.length; r++) {
                TrackMesh.Buf b = r == RAIL.length - 1 ? cur.solid : cur.detail;
                double bot = RAIL[r][0], hh = RAIL[r][1], w = RAIL[r][2] / 2;
                for (int i = 0; i < pieces; i++) {
                    double[] A = cs[i], B = cs[i + 1];
                    float len = (float) Math.min(1, Math.hypot(B[0] - A[0], B[2] - A[2]));
                    double[] a0 = at(A, -w, bot), a1 = at(A, w, bot), a2 = at(A, w, bot + hh), a3 = at(A, -w, bot + hh);
                    double[] b0 = at(B, -w, bot), b1 = at(B, w, bot), b2 = at(B, w, bot + hh), b3 = at(B, -w, bot + hh);
                    double[] nl = {(A[3] + B[3]) / 2, 0, (A[4] + B[4]) / 2};
                    b.quad(a3, b3, b2, a2, new double[]{0, 1, 0}, steel, 0, 0, len, (float) (w * 2), WHITE, lt[i], lt[i + 1]);
                    b.quad(a1, b1, b2, a2, nl, steel, 0, 0, len, (float) hh, WHITE, lt[i], lt[i + 1]);
                    b.quad(a0, b0, b3, a3, new double[]{-nl[0], 0, -nl[2]}, steel, 0, 0, len, (float) hh, WHITE, lt[i], lt[i + 1]);
                    if (r == 2) b.quad(a0, b0, b1, a1, new double[]{0, -1, 0}, steel, 0, 0, len, (float) (w * 2), WHITE, lt[i], lt[i + 1]);
                    if (i == 0 && capA) b.quad(a0, a1, a2, a3, new double[]{-A[5], 0, -A[6]}, steel, 0, 0, (float) (w * 2), (float) hh, WHITE, lt[i], lt[i]);
                    if (i == pieces - 1 && capB) b.quad(b0, b1, b2, b3, new double[]{B[5], 0, B[6]}, steel, 0, 0, (float) (w * 2), (float) hh, WHITE, lt[i + 1], lt[i + 1]);
                }
            }
        }

        double[] at(double[] c, double w, double y) {
            return loc(c[0] + c[3] * w, c[1] + y, c[2] + c[4] * w);
        }

        void tie(TrackView.Seg seg, double s, double halfL0, double halfL1, TextureAtlasSprite sp, int color, TrackMesh.Buf b, double lu) {
            double[] p = seg.path.pose(s);
            double mid = (halfL0 + halfL1) / 2;
            double cx = p[0] - Math.sin(p[2]) * mid, cz = p[1] + Math.cos(p[2]) * mid;
            double ph = Math.atan(seg.path.gradeAt(s));
            box(b, sp, color, cx, seg.height(s) + lu / 2, cz, p[2], ph, TIE_W, lu, halfL1 - halfL0, false);
        }

        void chairs(TrackView.Seg seg, double s, int sides) {
            double[] p = seg.path.pose(s);
            double ph = Math.atan(seg.path.gradeAt(s)), y = seg.height(s) + TIE_H;
            for (int side = -1; side <= 1; side += 2) {
                if (sides != 0 && side != sides) continue;
                double o = side * GAUGE / 2;
                double cx = p[0] - Math.sin(p[2]) * o, cz = p[1] + Math.cos(p[2]) * o;
                box(cur.detail, cast, WHITE, cx, y + 0.03, cz, p[2], ph, 0.32, 0.06, 0.3, false);
                for (int e = -1; e <= 1; e += 2) {
                    double w = e * 0.06;
                    box(cur.detail, cast, WHITE, cx - Math.sin(p[2]) * w, y + 0.1, cz + Math.cos(p[2]) * w, p[2], ph, 0.22, 0.12, 0.05, false);
                }
            }
        }

        boolean inSkip(TrackView.Seg seg, double s) {
            return s < seg.skip[0] || s > seg.path.length - seg.skip[1];
        }

        void part(double x, double z) {
            cur = mesh.part(x, z);
        }

        /** The stretches of a rail cut out because the points at either end draw their own steel there. */
        List<double[]> cuts(TrackView.Seg seg, int side) {
            List<double[]> c = new ArrayList<>();
            double L = seg.path.length;
            for (int e = 0; e < 2; e++) {
                if (!seg.blade[e] || side != seg.side[e]) continue;
                c.add(e == 0 ? new double[]{-1, BLADE} : new double[]{L - BLADE, L + 1});
                double f = seg.frog[e];
                if (f >= 0) c.add(e == 0 ? new double[]{f - FROG_IN, f + FROG_OUT} : new double[]{L - f - FROG_OUT, L - f + FROG_IN});
            }
            return c;
        }

        void section(TrackView.Seg seg) {
            TrackPath path = seg.path;
            double L = path.length, step = L / seg.n;
            boolean fails = path.fails();
            List<double[]> cutL = cuts(seg, -1), cutR = cuts(seg, 1);
            for (int i = 0; i < seg.n; i++) {
                double s = seg.tieS(i);
                double[] ps = path.pose(s);
                part(ps[0], ps[1]);
                boolean tie = seg.ties.get(i), rail = seg.rails.get(i);
                if (tie && !inSkip(seg, s)) {
                    tie(seg, s, -TIE_L / 2, TIE_L / 2, wood, WHITE, cur.solid, TIE_H);
                    if (rail) chairs(seg, s, 0);
                } else if (!tie && !seg.live) {
                    boolean bad = fails || seg.unsupported.get(i);
                    tie(seg, s, -TIE_L / 2, TIE_L / 2, white, bad ? GHOST_RED : GHOST, cur.glass, 0.04);
                }
                double a = i * step, b = (i + 1) * step;
                for (int side = -1; side <= 1; side += 2) {
                    if (rail) {
                        boolean capA = i == 0 || !seg.rails.get(i - 1), capB = i == seg.n - 1 || !seg.rails.get(i + 1);
                        List<double[]> keep = new ArrayList<>();
                        keep.add(new double[]{a, b});
                        for (double[] c : side < 0 ? cutL : cutR) {
                            List<double[]> next = new ArrayList<>();
                            for (double[] k : keep) {
                                if (c[1] <= k[0] || c[0] >= k[1]) next.add(k);
                                else {
                                    if (c[0] > k[0]) next.add(new double[]{k[0], c[0]});
                                    if (c[1] < k[1]) next.add(new double[]{c[1], k[1]});
                                }
                            }
                            keep = next;
                        }
                        for (double[] k : keep)
                            rail(seg, k[0], k[1], side * GAUGE / 2, 0, capA || k[0] > a, capB || k[1] < b);
                    } else if (!seg.live) {
                        double y = tie ? TIE_H + 0.2 : 0.1;
                        for (double d = a; d < b - 1e-6; d += 0.5)
                            strip(cur.glass, seg, d, Math.min(b, d + 0.5), side * GAUGE / 2, 0.11, y, fails ? GHOST_RAIL_RED : GHOST_RAIL);
                    }
                }
            }
            if (seg.live) return;
            int chalk = fails ? CHALK_RED : CHALK, edge = fails ? EDGE_RED : EDGE;
            for (double d = 0; d < L; d += 1.0) {
                double[] p = path.pose(d);
                part(p[0], p[1]);
                strip(cur.glass, seg, d, Math.min(L, d + 0.5), 0, 0.07, 0.015, chalk);
            }
            double half = curved(path) ? 3.5 : 3;
            for (double d = 0; d < L; d += 1.5) {
                double[] p = path.pose(d);
                part(p[0], p[1]);
                for (int side = -1; side <= 1; side += 2) strip(cur.glass, seg, d, Math.min(L, d + 0.6), side * half, 0.05, 0.015, edge);
            }
            for (long l : seg.blocked) {
                BlockPos p = BlockPos.of(l);
                part(p.getX(), p.getZ());
                box(cur.glass, white, BLOCKED, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0, 1.02, 1.02, 1.02, true);
            }
        }

        static int end(TrackView.Seg leg) {
            return leg.toeAtB ? 1 : 0;
        }

        double[] legAt(TrackView.Seg leg, double d) {
            return leg.path.pose(Math.max(0, Math.min(leg.path.length, legS(leg, d))));
        }

        double legS(TrackView.Seg leg, double d) {
            return leg.toeAtB ? leg.path.length - d : d;
        }

        boolean laid(TrackView.Seg leg, double d, boolean rails) {
            double s = legS(leg, d);
            int i = (int) Math.floor(s / (leg.path.length / leg.n));
            if (i < 0 || i >= leg.n) return false;
            return rails ? leg.rails.get(i) : leg.ties.get(i);
        }

        /** World point {x, y, z} on a leg, d from the toe, off across the leg's own direction of travel from the toe. */
        double[] legPt(TrackView.Seg leg, double d, double off) {
            double s = Math.max(0, Math.min(leg.path.length, legS(leg, d)));
            double[] p = leg.path.pose(s);
            double th = p[2] + (leg.toeAtB ? Math.PI : 0);
            return new double[]{p[0] - Math.sin(th) * off, leg.height(s) + TIE_H, p[1] + Math.cos(th) * off, th};
        }

        /** Offset across a leg that puts a point on the side the other leg turns to, measured in the toe frame. */
        static double across(TrackView.Seg leg, int sd, double o) {
            return (leg.toeAtB ? -1 : 1) * sd * o;
        }

        void points(TrackView v) {
            TrackView.Seg l0 = v.legs.get(0), l1 = v.legs.get(1);
            int e0 = end(l0);
            double zone = l0.skip[e0];
            for (double d = 0.5; d < zone; d += 1.0) {
                boolean t0 = laid(l0, d, false), t1 = laid(l1, d, false);
                if (!t0 && !t1) continue;
                double[] p0 = legAt(l0, d), p1 = legAt(l1, d);
                part(p0[0], p0[1]);
                double th = p0[2], nx = -Math.sin(th), nz = Math.cos(th);
                double acr = (p1[0] - p0[0]) * nx + (p1[1] - p0[1]) * nz;
                double lo = Math.min(0, acr) - TIE_L / 2, hi = Math.max(0, acr) + TIE_L / 2;
                double s0 = legS(l0, d);
                tie(l0, s0, lo, hi, wood, WHITE, cur.solid, TIE_H);
                if (laid(l0, d, true)) chairs(l0, s0, 0);
                if (laid(l1, d, true)) chairs(l1, legS(l1, d), 0);
            }
            double[] toe = legAt(l0, 0);
            part(toe[0], toe[1]);
            double[][] tips = new double[2][];
            for (int i = 0; i < 2; i++) {
                TrackView.Seg leg = v.legs.get(i);
                int e = end(leg), sd = leg.side[e];
                double swing = leg.closed[e] ? 0.1 : 0.42;
                List<double[]> pts = new ArrayList<>();
                boolean first = true;
                for (double d = 0; d <= BLADE + 1e-6; d += 0.25) {
                    boolean on = d < BLADE ? laid(leg, d + 0.01, true) : laid(leg, d - 0.01, true);
                    if (on) {
                        double o = GAUGE / 2 - swing * Math.pow(1 - d / BLADE, 1.3);
                        pts.add(legPt(leg, d, across(leg, sd, o)));
                    }
                    if ((!on || d + 0.25 > BLADE + 1e-6) && !pts.isEmpty()) {
                        railLine(pts, true, true);
                        if (first && pts.size() > 0) first = false;
                        pts = new ArrayList<>();
                    }
                }
                if (laid(leg, 0.4, true)) tips[i] = legPt(leg, 0.4, across(leg, sd, GAUGE / 2 - swing * Math.pow(1 - 0.4 / BLADE, 1.3)));
            }
            if (tips[0] != null && tips[1] != null) {
                double[] a = tips[0], b = tips[1];
                double len = Math.hypot(b[0] - a[0], b[2] - a[2]) + GAUGE;
                double th = Math.atan2(b[2] - a[2], b[0] - a[0]);
                double mx = (a[0] + b[0]) / 2, mz = (a[2] + b[2]) / 2;
                box(cur.solid, steel, WHITE, mx, (a[1] + b[1]) / 2 + 0.06, mz, th, 0, len, 0.05, 0.08, true);
            }
            frog(l0, l1);
        }

        /**
         * A plain common crossing: the V nose where the two inner rails meet, a wing rail for each leg that runs in on its
         * own inner rail, knuckles at the throat and lies beside the nose, and a check rail inside the opposite outer rail.
         */
        void frog(TrackView.Seg l0, TrackView.Seg l1) {
            TrackView.Seg[] legs = {l0, l1};
            double fr = l0.frog[end(l0)];
            if (fr < 0) return;
            boolean[] on = {laid(l0, fr, true), laid(l1, fr, true)};
            if (!on[0] && !on[1]) return;
            double[] x0 = inner(l0, fr);
            part(x0[0], x0[2]);
            double throat = fr - 0.3;
            for (int i = 0; i < 2; i++) {
                if (!on[i]) continue;
                TrackView.Seg leg = legs[i], other = legs[1 - i];
                List<double[]> pts = new ArrayList<>();
                for (double d = fr + 0.08; d <= fr + FROG_OUT + 1e-6; d += 0.4) pts.add(inner(leg, d));
                pts.add(inner(leg, fr + FROG_OUT));
                railLine(pts, true, true);
                pts = new ArrayList<>();
                pts.add(inner(leg, fr - FROG_IN));
                pts.add(inner(leg, throat));
                pts.add(beside(other, leg, fr + 0.25, 0.17));
                pts.add(beside(other, leg, fr + 0.95, 0.17));
                pts.add(beside(other, leg, fr + 1.3, 0.24));
                railLine(pts, true, true);
                int sd = leg.side[end(leg)];
                pts = new ArrayList<>();
                pts.add(legPt(leg, fr - 1.3, across(leg, -sd, GAUGE / 2 - 0.24)));
                pts.add(legPt(leg, fr - 1.0, across(leg, -sd, GAUGE / 2 - 0.17)));
                pts.add(legPt(leg, fr + 1.0, across(leg, -sd, GAUGE / 2 - 0.17)));
                pts.add(legPt(leg, fr + 1.3, across(leg, -sd, GAUGE / 2 - 0.24)));
                part(pts.get(1)[0], pts.get(1)[2]);
                railLine(pts, true, true);
                part(x0[0], x0[2]);
            }
        }

        /** A point gap across from a leg's inner rail toward the leg that wing rail belongs to, d from the toe. */
        double[] beside(TrackView.Seg nose, TrackView.Seg wing, double d, double gap) {
            double[] q = inner(nose, d), n = toward(wing, d);
            // the nose rail of one leg is crossed by the other leg; its wing lies on the far side from the nose leg's centre
            return new double[]{q[0] + n[0] * gap, q[1], q[2] + n[1] * gap};
        }

        /** The rail of a leg on the side facing the other leg, d from the toe. */
        double[] inner(TrackView.Seg leg, double d) {
            return legPt(leg, d, across(leg, leg.side[end(leg)], GAUGE / 2));
        }

        /** Unit vector across a leg toward the other leg, in the ground plane {x, z}. */
        double[] toward(TrackView.Seg leg, double d) {
            double[] a = legPt(leg, d, 0), b = inner(leg, d);
            double h = Math.hypot(b[0] - a[0], b[2] - a[2]);
            return new double[]{(b[0] - a[0]) / h, (b[2] - a[2]) / h};
        }

        void stake(double heading, int lift) {
            double cx = ox + 0.5, cz = oz + 0.5;
            box(cur.solid, stakeWood, WHITE, cx, oy + 0.31, cz, heading, 0, 0.18, 0.62, 0.18, false);
            box(cur.solid, white, WHITE, cx, oy + 0.7475, cz, heading, 0, 0.19, 0.255, 0.19, false);
            if (lift > 0) box(cur.solid, steel, WHITE, cx, oy + 0.875 + lift / 16.0, cz, heading, 0, 0.04, lift / 8.0, 0.04, false);
            double c = Math.cos(heading), s = Math.sin(heading);
            box(cur.glass, white, CHALK, cx + c * 0.42, oy + 0.01, cz + s * 0.42, heading, 0, 0.5, 0.02, 0.07, false);
            for (int e = -1; e <= 1; e += 2) {
                double px = cx + c * 0.6 - s * e * 0.07, pz = cz + s * 0.6 + c * e * 0.07;
                box(cur.glass, white, CHALK, px, oy + 0.012, pz, heading - e * 0.7, 0, 0.2, 0.02, 0.06, false);
            }
        }

        /** A post and arrows at an open track end, pointing the way new track would go. */
        void endMarker(TrackView.Seg seg) {
            double[] p0 = seg.path.pose(0), p1 = seg.path.pose(seg.path.length);
            double cx = ox + 0.5, cz = oz + 0.5;
            boolean atA = seg.aPos != 0 ? seg.aPos == origin.asLong()
                    : Math.hypot(p0[0] - cx, p0[1] - cz) <= Math.hypot(p1[0] - cx, p1[1] - cz);
            double sEnd = atA ? 0 : seg.path.length;
            double th = (atA ? p0 : p1)[2] + (atA ? Math.PI : 0);
            double y = seg.height(sEnd) + TIE_H + 0.2;
            TrackMesh.Buf b = cur.marker;
            box(b, white, END, cx, y + 1.25, cz, th, 0, 0.16, 2.5, 0.16, true);
            arrow(b, END, cx, y + 2.35, cz, th, -0.1, 1.3, 0.55, 0.7, 0.2, 0.16);
            box(b, white, END_SOFT, cx, y + 0.02, cz, th, 0, 1.0, 0.03, GAUGE + 0.3, true);
            for (int k = 0; k < 2; k++) {
                double a0 = 0.7 + k * 1.7;
                arrow(b, END_DARK, cx, y + 0.03, cz, th, a0 - 0.08, 1.56, 0.8, 1.36, 0.42, 0.03);
                arrow(b, END, cx, y + 0.07, cz, th, a0, 1.4, 0.7, 1.1, 0.28, 0.04);
            }
        }

        /** A flat arrow from a0 to a0 + len along th: a shaft, then a solid head of the given length and base width. */
        void arrow(TrackMesh.Buf b, int color, double cx, double y, double cz, double th, double a0, double len, double head,
                   double headW, double shaftW, double thick) {
            double c = Math.cos(th), s = Math.sin(th);
            double shaft = len - head;
            box(b, white, color, cx + c * (a0 + shaft / 2), y, cz + s * (a0 + shaft / 2), th, 0, shaft + 0.02, thick, shaftW, true);
            int steps = 7;
            double st = head / steps;
            for (int k = 0; k < steps; k++) {
                double a = a0 + shaft + st * (k + 0.5);
                double w = headW * (1 - (k + 0.5) / steps);
                box(b, white, color, cx + c * a, y, cz + s * a, th, 0, st + 0.01, thick, w, true);
            }
        }

        void buffer(double th, double h) {
            double c = Math.cos(th), s = Math.sin(th), nx = -s, nz = c, x0 = ox + 0.5, z0 = oz + 0.5;
            double top = h + TIE_H + 0.24;
            for (int side = -1; side <= 1; side += 2) {
                double w = side * GAUGE / 2;
                fbox(steel, WHITE, x0, z0, th, 0, top + 0.6, w, 0.14, 1.2, 0.12, 0);
                double a0 = 0.05, y0 = top + 1.15, a1 = 1.6, y1 = top;
                double L = Math.hypot(a1 - a0, y1 - y0);
                fbox(steel, WHITE, x0, z0, th, (a0 + a1) / 2, (y0 + y1) / 2, w, L, 0.1, 0.1, Math.atan2(y1 - y0, a1 - a0));
                fbox(cast, WHITE, x0, z0, th, 1.6, top + 0.05, w, 0.3, 0.1, 0.3, 0);
            }
            fbox(red, WHITE, x0, z0, th, -0.12, h + 1.44, 0, 0.3, 0.42, 3.4, 0);
            fbox(wood, WHITE, x0, z0, th, 0.06, h + 1.44, 0, 0.08, 0.42, 3.4, 0);
            for (double w : new double[]{-1.5, 1.5}) {
                fbox(cast, WHITE, x0, z0, th, -0.4, h + 1.44, w, 0.3, 0.22, 0.22, 0);
                fbox(steel, WHITE, x0, z0, th, -0.56, h + 1.44, w, 0.06, 0.4, 0.4, 0);
            }
            fbox(steel, WHITE, x0, z0, th, 0, h + 1.9, 0, 0.08, 0.5, 0.08, 0);
            fbox(black, WHITE, x0, z0, th, 0, h + 2.25, 0, 0.26, 0.26, 0.26, 0);
            fbox(red, LAMP, x0, z0, th, -0.135, h + 2.25, 0, 0.02, 0.16, 0.16, 0);
        }

        void fbox(TextureAtlasSprite sp, int color, double x0, double z0, double th, double a, double y, double w,
                  double la, double lu, double lw, double ph) {
            double c = Math.cos(th), s = Math.sin(th);
            box(cur.solid, sp, color, x0 + c * a - s * w, y, z0 + s * a + c * w, th, ph, la, lu, lw, true);
        }
    }
}
