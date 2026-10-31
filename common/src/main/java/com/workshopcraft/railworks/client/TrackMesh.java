package com.workshopcraft.railworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Arrays;

/** Baked quads for one track node, in block-local coordinates, replayed every frame. */
final class TrackMesh {
    static final int STRIDE = 10;

    final Buf solid = new Buf(), glass = new Buf();
    int version;
    long built;
    static final AABB EVERYWHERE = new AABB(-3.0E7, -2048, -3.0E7, 3.0E7, 2048, 3.0E7);
    AABB bounds = EVERYWHERE;

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
        double minX = Math.min(solid.minX, glass.minX), minY = Math.min(solid.minY, glass.minY), minZ = Math.min(solid.minZ, glass.minZ);
        double maxX = Math.max(solid.maxX, glass.maxX), maxY = Math.max(solid.maxY, glass.maxY), maxZ = Math.max(solid.maxZ, glass.maxZ);
        bounds = minX > maxX ? new AABB(ox, oy, oz, ox + 1, oy + 1, oz + 1)
                : new AABB(ox + minX, oy + minY, oz + minZ, ox + maxX, oy + maxY, oz + maxZ).inflate(0.5);
    }
}
