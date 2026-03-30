package com.tmbu.tmbuclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an entity's actual model geometry as wireframe lines.
 *
 * Uses edge deduplication to only draw outline edges — internal edges where
 * two quads meet on the same cube face are skipped, producing a clean
 * silhouette instead of a dense grid.
 */
public final class WireframeEntityRenderer {
    private static final PoseStack POSE = new PoseStack();
    // Precision for snapping vertex positions to detect shared edges
    private static final float SNAP = 1024.0f;

    private WireframeEntityRenderer() {}

    @SuppressWarnings("unchecked")
    private static List<float[]> captureVertices(Entity entity, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher() == null) return null;

        EntityRenderer<?, ?> rawRenderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (!(rawRenderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) return null;
        if (!(entity instanceof LivingEntity livingEntity)) return null;

        var renderer = (LivingEntityRenderer<LivingEntity, LivingEntityRenderState, EntityModel<LivingEntityRenderState>>) livingRenderer;
        var model = (EntityModel<LivingEntityRenderState>) livingRenderer.getModel();

        LivingEntityRenderState state = renderer.createRenderState();
        renderer.extractRenderState(livingEntity, state, partialTick);
        model.setupAnim(state);

        QuadCapturingVertexConsumer capturer = new QuadCapturingVertexConsumer();

        POSE.pushPose();
        POSE.mulPose(Axis.YP.rotationDegrees(180.0f - state.bodyRot));
        float scale = state.scale;
        if (scale != 1.0f) POSE.scale(scale, scale, scale);
        POSE.scale(-1.0f, -1.0f, 1.0f);
        POSE.translate(0.0f, -1.501f, 0.0f);
        model.root().render(POSE, capturer, 0xF000F0, 0);
        POSE.popPose();

        return capturer.getVertices();
    }

    /**
     * Render wireframe with edge deduplication — only outline edges are drawn.
     */
    public static boolean renderWireframe(
            Entity entity, float partialTick, Vec3 cam,
            VertexConsumer lineOutput, PoseStack.Pose outputPose,
            int r, int g, int b, int a, float lineWidth) {

        List<float[]> verts = captureVertices(entity, partialTick);
        if (verts == null || verts.isEmpty()) return false;

        float offX = (float)(Mth.lerp(partialTick, entity.xOld, entity.getX()) - cam.x);
        float offY = (float)(Mth.lerp(partialTick, entity.yOld, entity.getY()) - cam.y);
        float offZ = (float)(Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cam.z);

        // Count how many times each edge appears. Edges shared by 2 quads are internal.
        Map<Long, int[]> edgeCounts = new HashMap<>();
        for (int i = 0; i + 3 < verts.size(); i += 4) {
            for (int e = 0; e < 4; e++) {
                float[] va = verts.get(i + e);
                float[] vb = verts.get(i + (e + 1) % 4);
                long key = edgeKey(va, vb);
                edgeCounts.computeIfAbsent(key, k -> new int[]{0})[0]++;
            }
        }

        // Only draw edges that appear exactly once (outline edges)
        for (int i = 0; i + 3 < verts.size(); i += 4) {
            for (int e = 0; e < 4; e++) {
                float[] va = verts.get(i + e);
                float[] vb = verts.get(i + (e + 1) % 4);
                long key = edgeKey(va, vb);
                if (edgeCounts.getOrDefault(key, new int[]{0})[0] <= 1) {
                    line(lineOutput, outputPose,
                        offX + va[0], offY + va[1], offZ + va[2],
                        offX + vb[0], offY + vb[1], offZ + vb[2],
                        r, g, b, a, lineWidth);
                }
            }
        }

        return true;
    }

    /**
     * Render filled quads (unchanged — fill looks fine with all quads).
     */
    public static boolean renderFilled(
            Entity entity, float partialTick, Vec3 cam,
            VertexConsumer fillOutput, PoseStack.Pose outputPose,
            int r, int g, int b, int a) {

        List<float[]> verts = captureVertices(entity, partialTick);
        if (verts == null || verts.isEmpty()) return false;

        float offX = (float)(Mth.lerp(partialTick, entity.xOld, entity.getX()) - cam.x);
        float offY = (float)(Mth.lerp(partialTick, entity.yOld, entity.getY()) - cam.y);
        float offZ = (float)(Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cam.z);

        for (int i = 0; i + 3 < verts.size(); i += 4) {
            float[] v0 = verts.get(i), v1 = verts.get(i+1), v2 = verts.get(i+2), v3 = verts.get(i+3);
            fillOutput.addVertex(outputPose, offX+v0[0], offY+v0[1], offZ+v0[2]).setColor(r, g, b, a);
            fillOutput.addVertex(outputPose, offX+v1[0], offY+v1[1], offZ+v1[2]).setColor(r, g, b, a);
            fillOutput.addVertex(outputPose, offX+v2[0], offY+v2[1], offZ+v2[2]).setColor(r, g, b, a);
            fillOutput.addVertex(outputPose, offX+v3[0], offY+v3[1], offZ+v3[2]).setColor(r, g, b, a);
        }

        return true;
    }

    /**
     * Create a canonical key for an edge between two vertices.
     * Snaps coordinates to a grid to handle floating-point imprecision,
     * and orders the two endpoints so (A,B) and (B,A) produce the same key.
     */
    private static long edgeKey(float[] a, float[] b) {
        int ax = Math.round(a[0] * SNAP), ay = Math.round(a[1] * SNAP), az = Math.round(a[2] * SNAP);
        int bx = Math.round(b[0] * SNAP), by = Math.round(b[1] * SNAP), bz = Math.round(b[2] * SNAP);

        // Pack each vertex into a long: 21 bits per component (enough for snapped coords)
        long ka = ((long)(ax & 0x1FFFFF) << 42) | ((long)(ay & 0x1FFFFF) << 21) | (az & 0x1FFFFF);
        long kb = ((long)(bx & 0x1FFFFF) << 42) | ((long)(by & 0x1FFFFF) << 21) | (bz & 0x1FFFFF);

        // Canonical order so (A→B) == (B→A)
        return ka <= kb ? ka * 2654435761L + kb : kb * 2654435761L + ka;
    }

    private static void line(VertexConsumer c, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a, float w) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
    }
}
