package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Baked quads for one track node, in block-local coordinates, split into 16-block parts so each can be culled. */
final class TrackMesh {
    static final int STRIDE = 10;

    /** solid is always drawn, detail (chairs, rail webs and feet) only up close, glass is the survey overlay, marker only with a track tool in hand. */
    static final class Part {
        final Buf solid = new Buf(), detail = new Buf(), glass = new Buf(), marker = new Buf();
        AABB bounds;
        int cx, cz;
    }

    final Map<Long, Part> parts = new HashMap<>();
    final List<Part> list = new ArrayList<>();
    int version;
    long built;
    static final AABB EVERYWHERE = new AABB(-3.0E7, -2048, -3.0E7, 3.0E7, 2048, 3.0E7);
    AABB bounds = EVERYWHERE;

    Part part(double wx, double wz) {
        long k = ((long) Math.floor(wx / 16) << 32) ^ ((long) Math.floor(wz / 16) & 0xFFFFFFFFL);
        return parts.computeIfAbsent(k, x -> {
            Part p = new Part();
            p.cx = (int) Math.floor(wx / 16);
            p.cz = (int) Math.floor(wz / 16);
            list.add(p);
            return p;
        });
    }

    static final class Buf {
        float[] d = new float[STRIDE * 256];
        int verts;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        void vertex(double x, double y, double z, float u, float v, int color, int light, double nx, double ny, double nz) {
            if ((verts + 1) * STRIDE > d.length) d = Arrays.copyOf(d, d.length * 2);
            int o = verts++ * STRIDE;
            d[o] = (float) x;
            d[o + 1] = (float) y;
            d[o + 2] = (float) z;
            d[o + 3] = u;
            d[o + 4] = v;
            d[o + 5] = Float.intBitsToFloat(color);
            d[o + 6] = Float.intBitsToFloat(light);
            d[o + 7] = (float) nx;
            d[o + 8] = (float) ny;
            d[o + 9] = (float) nz;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        /** A quad facing roughly along n; the winding is fixed up so it faces the right way. */
        void quad(double[] p0, double[] p1, double[] p2, double[] p3, double[] n, TextureAtlasSprite sp,
                  float u0, float v0, float u1, float v1, int color, int light0, int light1) {
            double ax = p1[0] - p0[0], ay = p1[1] - p0[1], az = p1[2] - p0[2];
            double bx = p2[0] - p0[0], by = p2[1] - p0[1], bz = p2[2] - p0[2];
            double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
            float su0 = sp.getU(u0), su1 = sp.getU(u1), sv0 = sp.getV(v0), sv1 = sp.getV(v1);
            if (cx * n[0] + cy * n[1] + cz * n[2] >= 0) {
                vertex(p0[0], p0[1], p0[2], su0, sv0, color, light0, n[0], n[1], n[2]);
                vertex(p1[0], p1[1], p1[2], su1, sv0, color, light1, n[0], n[1], n[2]);
                vertex(p2[0], p2[1], p2[2], su1, sv1, color, light1, n[0], n[1], n[2]);
                vertex(p3[0], p3[1], p3[2], su0, sv1, color, light0, n[0], n[1], n[2]);
            } else {
                vertex(p0[0], p0[1], p0[2], su0, sv0, color, light0, n[0], n[1], n[2]);
                vertex(p3[0], p3[1], p3[2], su0, sv1, color, light0, n[0], n[1], n[2]);
                vertex(p2[0], p2[1], p2[2], su1, sv1, color, light1, n[0], n[1], n[2]);
                vertex(p1[0], p1[1], p1[2], su1, sv0, color, light1, n[0], n[1], n[2]);
            }
        }

        void emit(PoseStack.Pose p, VertexConsumer vc) {
            Matrix4f m = p.pose();
            Vector3f pos = new Vector3f(), nn = new Vector3f();
            for (int i = 0; i < verts; i++) {
                int o = i * STRIDE;
                m.transformPosition(d[o], d[o + 1], d[o + 2], pos);
                p.transformNormal(d[o + 7], d[o + 8], d[o + 9], nn);
                vc.addVertex(pos.x(), pos.y(), pos.z(), Float.floatToRawIntBits(d[o + 5]), d[o + 3], d[o + 4],
                        OverlayTexture.NO_OVERLAY, Float.floatToRawIntBits(d[o + 6]), nn.x(), nn.y(), nn.z());
            }
        }
    }

    void finish(double ox, double oy, double oz) {
        AABB all = null;
        for (Part p : list) {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (Buf b : new Buf[]{p.solid, p.detail, p.glass, p.marker}) {
                minX = Math.min(minX, b.minX);
                minY = Math.min(minY, b.minY);
                minZ = Math.min(minZ, b.minZ);
                maxX = Math.max(maxX, b.maxX);
                maxY = Math.max(maxY, b.maxY);
                maxZ = Math.max(maxZ, b.maxZ);
            }
            if (minX > maxX) {
                p.bounds = new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1);
                continue;
            }
            p.bounds = new AABB(ox + minX, oy + minY, oz + minZ, ox + maxX, oy + maxY, oz + maxZ).inflate(0.5);
            all = all == null ? p.bounds : all.minmax(p.bounds);
        }
        bounds = all == null ? new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1) : all;
    }
}
