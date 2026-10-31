package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.TrackNodeBlock;
import com.workshopcraft.railworks.block.TrackNodeBlockEntity;
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
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws a node's stake, buffer stop, points furniture and every section it owns, from a cached mesh. */
public class TrackRenderer implements BlockEntityRenderer<TrackNodeBlockEntity, TrackRenderer.State> {
    static final double GAUGE = 1.5, TIE_L = 3, TIE_H = 1 / 3.0, TIE_W = 0.5, BLADE = 5;
    // bottom above the tie top, height, width: foot, web, head of a bullhead rail
    static final double[][] RAIL = {{0.04, 0.05, 0.11}, {0.09, 0.08, 0.05}, {0.17, 0.07, 0.13}};
    static final int WHITE = 0xFFFFFFFF, CHALK = 0xE8FFFFFF, CHALK_RED = 0xE8FF3A30, EDGE = 0x90FFFFFF, EDGE_RED = 0x90FF5040,
            GHOST = 0x6090C8FF, GHOST_RED = 0x60FF6050, BLOCKED = 0x55FF2020, BUFFER_RED = 0xFFB02020, LAMP = 0xFFFF3020;

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
        if (m.solid.verts > 0) out.submitCustomGeometry(pose, Sheets.cutoutBlockSheet(), m.solid::emit);
        if (m.glass.verts > 0) out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), m.glass::emit);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    /** NeoForge culls against this; a plain method so it also compiles against vanilla. */
    public AABB getRenderBoundingBox(TrackNodeBlockEntity be) {
        return be.renderCache instanceof TrackMesh m ? m.bounds : TrackMesh.EVERYWHERE;
    }

    private static @Nullable TrackMesh mesh(TrackNodeBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return null;
        long now = level.getGameTime();
        if (be.renderCache instanceof TrackMesh m && m.version == be.version && now - m.built < 40 && now >= m.built) return m;
        TrackMesh m = new Builder(be, level).build();
        m.version = be.version;
        m.built = now - Math.floorMod(be.getBlockPos().hashCode(), 20);
        be.renderCache = m;
        return m;
    }

    static TextureAtlasSprite sprite(Identifier id) {
        return Minecraft.getInstance().getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, id));
    }

    private static final class Builder {
        final TrackNodeBlockEntity be;
        final Level level;
        final BlockPos origin;
        final double ox, oy, oz;
        final TrackMesh mesh = new TrackMesh();
        final Long2IntOpenHashMap lights = new Long2IntOpenHashMap();
        final TextureAtlasSprite wood = sprite(RailWorks.id("block/treated_beam")), steel = sprite(RailWorks.id("block/steel_block")),
                cast = sprite(RailWorks.id("block/set_steel")), white = sprite(Identifier.withDefaultNamespace("block/white_concrete")),
                stakeWood = sprite(Identifier.withDefaultNamespace("block/stripped_spruce_log")),
                red = sprite(Identifier.withDefaultNamespace("block/red_concrete")), black = sprite(Identifier.withDefaultNamespace("block/black_concrete"));

        Builder(TrackNodeBlockEntity be, Level level) {
            this.be = be;
            this.level = level;
            this.origin = be.getBlockPos();
            this.ox = origin.getX();
            this.oy = origin.getY();
            this.oz = origin.getZ();
        }

        TrackMesh build() {
            TrackView v = be.view;
            BlockState st = be.getBlockState();
            boolean staked = st.hasProperty(TrackNodeBlock.STAKE) && st.getValue(TrackNodeBlock.STAKE);
            if (staked) stake(v == null ? 0 : v.heading, v == null ? 0 : v.lift);
            if (v != null) {
                for (TrackView.Seg s : v.segs) section(s);
                if (v.legs.size() == 2) points(v);
                if (v.buffer && v.hasOut) buffer(v.out, oy + v.lift / 8.0);
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

        /** One rail from s0 to s1 at an offset across the path; off2 moves the s0 end (switch blades). */
        void rail(TrackView.Seg seg, double s0, double s1, double off, double off0, boolean capA, boolean capB) {
            if (s1 - s0 < 1e-4) return;
            int pieces = bent(seg.path) || off0 != 0 ? Math.max(1, (int) Math.ceil((s1 - s0) / 0.5)) : 1;
            double[][] cs = new double[pieces + 1][];
            for (int i = 0; i <= pieces; i++) {
                double s = s0 + (s1 - s0) * i / pieces;
                double[] p = seg.path.pose(s);
                double o = off + off0 * (1 - (double) i / pieces);
                double y = seg.height(s) + TIE_H;
                cs[i] = new double[]{p[0] - Math.sin(p[2]) * o, y, p[1] + Math.cos(p[2]) * o, -Math.sin(p[2]), Math.cos(p[2]), Math.cos(p[2]), Math.sin(p[2])};
            }
            int[] lt = new int[pieces + 1];
            for (int i = 0; i <= pieces; i++) lt[i] = light(cs[i][0], cs[i][1] + 0.3, cs[i][2]);
            TrackMesh.Buf b = mesh.solid;
            for (int r = 0; r < RAIL.length; r++) {
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
                box(mesh.solid, cast, WHITE, cx, y + 0.03, cz, p[2], ph, 0.32, 0.06, 0.3, false);
                for (int e = -1; e <= 1; e += 2) {
                    double w = e * 0.06;
                    box(mesh.solid, cast, WHITE, cx - Math.sin(p[2]) * w, y + 0.1, cz + Math.cos(p[2]) * w, p[2], ph, 0.22, 0.12, 0.05, false);
                }
            }
        }

        boolean inSkip(TrackView.Seg seg, double s) {
            return s < seg.skipA || s > seg.path.length - seg.skipB;
        }

        void section(TrackView.Seg seg) {
            TrackPath path = seg.path;
            double L = path.length, step = L / seg.n;
            boolean fails = path.fails();
            for (int i = 0; i < seg.n; i++) {
                double s = seg.tieS(i);
                boolean tie = seg.ties.get(i), rail = seg.rails.get(i);
                if (tie && !inSkip(seg, s)) {
                    tie(seg, s, -TIE_L / 2, TIE_L / 2, wood, WHITE, mesh.solid, TIE_H);
                    if (rail) chairs(seg, s, 0);
                } else if (!tie && !seg.live) {
                    tie(seg, s, -TIE_L / 2, TIE_L / 2, white, fails ? GHOST_RED : GHOST, mesh.glass, 0.04);
                }
                double a = i * step, b = (i + 1) * step;
                for (int side = -1; side <= 1; side += 2) {
                    double ra = a, rb = b;
                    if (seg.bladeEnd >= 0 && side == seg.bladeSide) {
                        if (seg.bladeEnd == 0) ra = Math.max(ra, BLADE);
                        else rb = Math.min(rb, L - BLADE);
                    }
                    if (rail) {
                        if (ra < rb) rail(seg, ra, rb, side * GAUGE / 2, 0, i == 0 || !seg.rails.get(i - 1), i == seg.n - 1 || !seg.rails.get(i + 1));
                    } else if (!seg.live) {
                        double y = tie ? TIE_H + 0.2 : 0.08;
                        for (double d = a; d < b - 1e-6; d += 0.5)
                            strip(mesh.glass, seg, d, Math.min(b, d + 0.5), side * GAUGE / 2, 0.08, y, fails ? GHOST_RED : GHOST);
                    }
                }
            }
            if (seg.live) return;
            int chalk = fails ? CHALK_RED : CHALK, edge = fails ? EDGE_RED : EDGE;
            for (double d = 0; d < L; d += 1.0) strip(mesh.glass, seg, d, Math.min(L, d + 0.5), 0, 0.07, 0.015, chalk);
            double half = curved(path) ? 3.5 : 3;
            for (double d = 0; d < L; d += 1.5)
                for (int side = -1; side <= 1; side += 2) strip(mesh.glass, seg, d, Math.min(L, d + 0.6), side * half, 0.05, 0.015, edge);
            for (long l : seg.blocked) {
                BlockPos p = BlockPos.of(l);
                box(mesh.glass, white, BLOCKED, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0, 1.02, 1.02, 1.02, true);
            }
        }

        double[] legAt(TrackView.Seg leg, double d) {
            double s = leg.toeAtB ? leg.path.length - d : d;
            return leg.path.pose(Math.max(0, Math.min(leg.path.length, s)));
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

        void points(TrackView v) {
            TrackView.Seg l0 = v.legs.get(0), l1 = v.legs.get(1);
            double zone = l0.toeAtB ? l0.skipB : l0.skipA;
            for (double d = 0.5; d < zone; d += 1.0) {
                boolean t0 = laid(l0, d, false), t1 = laid(l1, d, false);
                if (!t0 && !t1) continue;
                double[] p0 = legAt(l0, d), p1 = legAt(l1, d);
                double th = p0[2], nx = -Math.sin(th), nz = Math.cos(th);
                double across = (p1[0] - p0[0]) * nx + (p1[1] - p0[1]) * nz;
                double lo = Math.min(0, across) - TIE_L / 2, hi = Math.max(0, across) + TIE_L / 2;
                double s0 = legS(l0, d);
                tie(l0, s0, lo, hi, wood, WHITE, mesh.solid, TIE_H);
                if (laid(l0, d, true)) chairs(l0, s0, 0);
                if (laid(l1, d, true)) chairs(l1, legS(l1, d), 0);
            }
            for (int i = 0; i < 2; i++) {
                TrackView.Seg leg = v.legs.get(i);
                double off = leg.bladeSide * GAUGE / 2;
                double open = leg.closed ? 0 : -leg.bladeSide * 0.12;
                for (int k = 0; k < Math.ceil(BLADE); k++) {
                    double d0 = k, d1 = Math.min(BLADE, k + 1);
                    if (!laid(leg, (d0 + d1) / 2, true)) continue;
                    double f0 = 1 - d0 / BLADE, f1 = 1 - d1 / BLADE;
                    double sa = legS(leg, d0), sb = legS(leg, d1);
                    // the blade tapers to the heel; draw with s increasing
                    if (sa < sb) railSwing(leg, sa, sb, off, open * f0, open * f1, k == 0);
                    else railSwing(leg, sb, sa, off, open * f1, open * f0, k == 0);
                }
            }
            double cross = -1;
            for (double d = 0.5; d < 30; d += 0.25) {
                double[] p0 = legAt(l0, d), p1 = legAt(l1, d);
                if (Math.hypot(p0[0] - p1[0], p0[1] - p1[1]) >= GAUGE) {
                    cross = d;
                    break;
                }
            }
            if (cross < 0) return;
            for (TrackView.Seg leg : v.legs) {
                if (!laid(leg, cross, true)) continue;
                double off = -leg.bladeSide * (GAUGE / 2 - 0.16);
                double sa = legS(leg, Math.max(0, cross - 1.8)), sb = legS(leg, cross + 1.8);
                rail(leg, Math.max(0, Math.min(sa, sb)), Math.min(leg.path.length, Math.max(sa, sb)), off, 0, true, true);
            }
        }

        void railSwing(TrackView.Seg leg, double sa, double sb, double off, double oa, double ob, boolean tip) {
            if (Math.abs(oa - ob) < 1e-6 && oa == 0) {
                rail(leg, sa, sb, off, 0, tip, tip);
                return;
            }
            // rail() offsets the s0 end by off0 and fades it out; shift the base so both ends land where wanted
            rail(leg, sa, sb, off + ob, oa - ob, true, true);
        }

        void stake(double heading, int lift) {
            double cx = ox + 0.5, cz = oz + 0.5;
            box(mesh.solid, stakeWood, WHITE, cx, oy + 0.31, cz, heading, 0, 0.18, 0.62, 0.18, false);
            box(mesh.solid, white, WHITE, cx, oy + 0.7475, cz, heading, 0, 0.19, 0.255, 0.19, false);
            if (lift > 0) box(mesh.solid, steel, WHITE, cx, oy + 0.875 + lift / 16.0, cz, heading, 0, 0.04, lift / 8.0, 0.04, false);
            double c = Math.cos(heading), s = Math.sin(heading);
            box(mesh.glass, white, CHALK, cx + c * 0.42, oy + 0.01, cz + s * 0.42, heading, 0, 0.5, 0.02, 0.07, false);
            for (int e = -1; e <= 1; e += 2) {
                double px = cx + c * 0.6 - s * e * 0.07, pz = cz + s * 0.6 + c * e * 0.07;
                box(mesh.glass, white, CHALK, px, oy + 0.012, pz, heading - e * 0.7, 0, 0.2, 0.02, 0.06, false);
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
            box(mesh.solid, sp, color, x0 + c * a - s * w, y, z0 + s * a + c * w, th, ph, la, lu, lw, true);
        }
    }
}
