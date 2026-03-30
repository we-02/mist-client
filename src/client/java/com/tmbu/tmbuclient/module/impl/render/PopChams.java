package com.tmbu.tmbuclient.module.impl.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.TotemPopEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.render.WireframeEntityRenderer;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * PopChams — renders a ghost wireframe model at the position where a player
 * popped a totem. The ghost floats upward and fades out over time.
 */
public class PopChams extends Module {

    private final BooleanSetting onlyOne      = addSetting(new BooleanSetting("Only One", false).group("General"));
    private final BooleanSetting selfPops     = addSetting(new BooleanSetting("Self Pops", false).group("General"));
    private final SliderSetting  renderTime   = addSetting(new SliderSetting("Render Time", 1.5, 0.2, 6.0, 0.1).group("General"));

    public enum Animation { FLOAT_UP, SINK_DOWN, EXPAND, IMPLODE, SPIN_FADE, PULSE_FADE, STATIC }
    private final EnumSetting<Animation> animation = addSetting(new EnumSetting<>("Animation", Animation.FLOAT_UP).group("Animation"));
    private final SliderSetting  animSpeed   = addSetting(new SliderSetting("Anim Speed", 1.0, 0.1, 5.0, 0.1).group("Animation"));
    private final BooleanSetting fadeOut      = addSetting(new BooleanSetting("Fade Out", true).group("Animation"));

    private final ColorSetting   lineColor    = addSetting(new ColorSetting("Line Color", 0xAAFFFFFF).group("Colors"));
    private final ColorSetting   fillColor    = addSetting(new ColorSetting("Fill Color", 0x30FFFFFF).group("Colors"));
    private final BooleanSetting showFill     = addSetting(new BooleanSetting("Show Fill", true).group("Colors"));
    private final SliderSetting  lineWidth    = addSetting(new SliderSetting("Line Width", 1.5, 0.5, 5.0, 0.5).group("Colors"));

    private final List<Ghost> ghosts = new ArrayList<>();
    private long lastFrameTime = 0;

    private final Consumer<TotemPopEvent> popHandler = this::onTotemPop;

    public PopChams() {
        super("PopChams", "Renders a ghost where players pop totem", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(TotemPopEvent.class, popHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(TotemPopEvent.class, popHandler);
    }

    @Override
    public void onEnable() {
        ghosts.clear();
        lastFrameTime = System.nanoTime();
    }

    @Override
    public void onDisable() {
        ghosts.clear();
    }

    private void onTotemPop(TotemPopEvent event) {
        Player player = event.player();
        Minecraft mc = Minecraft.getInstance();

        if (!selfPops.getValue() && player == mc.player) return;

        synchronized (ghosts) {
            if (onlyOne.getValue()) {
                ghosts.removeIf(g -> g.uuid.equals(player.getUUID()));
            }
            ghosts.add(new Ghost(player));
        }
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        long now = System.nanoTime();
        float dt = (now - lastFrameTime) / 1_000_000_000f;
        lastFrameTime = now;
        final float frameTime = dt > 0.5f ? 0.05f : dt;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double maxTime = renderTime.getValue();

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            PoseStack.Pose pose = matrices.last();

            synchronized (ghosts) {
                var it = ghosts.iterator();
                while (it.hasNext()) {
                    Ghost ghost = it.next();

                    ghost.timer += frameTime;
                    if (ghost.timer > maxTime) { it.remove(); continue; }

                    float speed = animSpeed.getValue().floatValue();
                    float progress = (float)(ghost.timer / maxTime); // 0→1

                    // Apply animation
                    switch (animation.getValue()) {
                        case FLOAT_UP -> {
                            ghost.y += 0.75 * speed * frameTime;
                            ghost.scale = Math.max(0.01, 1.0 - progress * 0.3);
                        }
                        case SINK_DOWN -> {
                            ghost.y -= 0.5 * speed * frameTime;
                            ghost.scale = Math.max(0.01, 1.0 - progress * 0.5);
                        }
                        case EXPAND -> {
                            ghost.scale = 1.0 + progress * speed * 1.5;
                        }
                        case IMPLODE -> {
                            ghost.scale = Math.max(0.01, 1.0 - progress * speed);
                        }
                        case SPIN_FADE -> {
                            ghost.y += 0.3 * speed * frameTime;
                            ghost.rotationOffset += 180.0 * speed * frameTime;
                        }
                        case PULSE_FADE -> {
                            ghost.scale = 1.0 + 0.15 * Math.sin(ghost.timer * speed * Math.PI * 4);
                        }
                        case STATIC -> {}
                    }

                    if (ghost.scale <= 0.01) { it.remove(); continue; }

                    float alphaMultiplier = fadeOut.getValue()
                        ? (float)(1.0 - ghost.timer / maxTime)
                        : 1.0f;

                    int lc = lineColor.getValue();
                    int la = (int)(((lc >> 24) & 0xFF) * alphaMultiplier);
                    int lr = (lc >> 16) & 0xFF, lg = (lc >> 8) & 0xFF, lb = lc & 0xFF;

                    // Wireframe lines
                    VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);
                    float w = lineWidth.getValue().floatValue();
                    renderGhostWireframe(ghost, cam, lines, pose, lr, lg, lb, Math.max(1, la), w);
                    bufferSource.endBatch(RenderTypes.LINES);

                    // Fill
                    if (showFill.getValue()) {
                        VertexConsumer tris = bufferSource.getBuffer(RenderTypes.debugFilledBox());
                        int fc = fillColor.getValue();
                        int fa = (int)(((fc >> 24) & 0xFF) * alphaMultiplier);
                        int fr = (fc >> 16) & 0xFF, fg = (fc >> 8) & 0xFF, fb = fc & 0xFF;
                        renderGhostFill(ghost, cam, tris, pose, fr, fg, fb, Math.max(1, fa));
                        bufferSource.endBatch(RenderTypes.debugFilledBox());
                    }
                }
            }
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void renderGhostWireframe(Ghost ghost, Vec3 cam, VertexConsumer lines,
                                      PoseStack.Pose pose, int r, int g, int b, int a, float w) {
        float offX = (float)(ghost.x - cam.x);
        float offY = (float)(ghost.y - cam.y);
        float offZ = (float)(ghost.z - cam.z);
        float scale = (float) ghost.scale;
        float sin = (float) Math.sin(Math.toRadians(ghost.rotationOffset));
        float cos = (float) Math.cos(Math.toRadians(ghost.rotationOffset));

        for (int i = 0; i + 1 < ghost.outlineEdges.length; i += 2) {
            float[] va = ghost.vertices.get(ghost.outlineEdges[i]);
            float[] vb = ghost.vertices.get(ghost.outlineEdges[i + 1]);
            // Scale then rotate around Y
            float ax = va[0]*scale, az = va[2]*scale;
            float bx = vb[0]*scale, bz = vb[2]*scale;
            line(lines, pose,
                offX + ax*cos - az*sin, offY + va[1]*scale, offZ + ax*sin + az*cos,
                offX + bx*cos - bz*sin, offY + vb[1]*scale, offZ + bx*sin + bz*cos,
                r, g, b, a, w);
        }
    }

    private void renderGhostFill(Ghost ghost, Vec3 cam, VertexConsumer tris,
                                 PoseStack.Pose pose, int r, int g, int b, int a) {
        float offX = (float)(ghost.x - cam.x);
        float offY = (float)(ghost.y - cam.y);
        float offZ = (float)(ghost.z - cam.z);
        float scale = (float) ghost.scale;
        float sin = (float) Math.sin(Math.toRadians(ghost.rotationOffset));
        float cos = (float) Math.cos(Math.toRadians(ghost.rotationOffset));

        for (int i = 0; i + 3 < ghost.vertices.size(); i += 4) {
            for (int v = 0; v < 4; v++) {
                float[] vert = ghost.vertices.get(i + v);
                float vx = vert[0]*scale, vz = vert[2]*scale;
                tris.addVertex(pose,
                    offX + vx*cos - vz*sin,
                    offY + vert[1]*scale,
                    offZ + vx*sin + vz*cos
                ).setColor(r, g, b, a);
            }
        }
    }

    private static void line(VertexConsumer c, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a, float w) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
    }

    /**
     * Snapshot of a player at the moment they popped a totem.
     * Captures the model geometry so we can render it even after the player moves away.
     */
    private static class Ghost {
        final UUID uuid;
        final List<float[]> vertices;
        /** Precomputed outline edges: pairs of vertex indices [i0, i1, i0, i1, ...] */
        final int[] outlineEdges;
        double x, y, z;
        double timer = 0;
        double scale = 1;
        double rotationOffset = 0;

        Ghost(Player player) {
            this.uuid = player.getUUID();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();

            Minecraft mc = Minecraft.getInstance();
            com.tmbu.tmbuclient.render.QuadCapturingVertexConsumer capturer =
                new com.tmbu.tmbuclient.render.QuadCapturingVertexConsumer();

            var rawRenderer = mc.getEntityRenderDispatcher().getRenderer(player);
            if (rawRenderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?,?,?> livingRenderer) {
                @SuppressWarnings("unchecked")
                var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<
                    net.minecraft.world.entity.LivingEntity,
                    net.minecraft.client.renderer.entity.state.LivingEntityRenderState,
                    net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState>>) livingRenderer;
                @SuppressWarnings("unchecked")
                var model = (net.minecraft.client.model.EntityModel<
                    net.minecraft.client.renderer.entity.state.LivingEntityRenderState>) livingRenderer.getModel();

                var state = renderer.createRenderState();
                renderer.extractRenderState(player, state, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                model.setupAnim(state);

                PoseStack pose = new PoseStack();
                pose.pushPose();
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0f - state.bodyRot));
                if (state.scale != 1.0f) pose.scale(state.scale, state.scale, state.scale);
                pose.scale(-1.0f, -1.0f, 1.0f);
                pose.translate(0.0f, -1.501f, 0.0f);
                model.root().render(pose, capturer, 0xF000F0, 0);
                pose.popPose();
            }

            this.vertices = new ArrayList<>(capturer.getVertices());
            this.outlineEdges = computeOutlineEdges(this.vertices);
        }

        /** Compute which edges are outlines (appear only once across all quads). */
        private static int[] computeOutlineEdges(List<float[]> verts) {
            float SNAP = 1024.0f;
            java.util.Map<Long, int[]> counts = new java.util.HashMap<>();
            // Count edge occurrences
            for (int i = 0; i + 3 < verts.size(); i += 4) {
                for (int e = 0; e < 4; e++) {
                    float[] a = verts.get(i + e);
                    float[] b = verts.get(i + (e + 1) % 4);
                    long key = edgeKey(a, b, SNAP);
                    counts.computeIfAbsent(key, k -> new int[]{0})[0]++;
                }
            }
            // Collect outline edges (count == 1)
            java.util.List<Integer> edges = new ArrayList<>();
            for (int i = 0; i + 3 < verts.size(); i += 4) {
                for (int e = 0; e < 4; e++) {
                    int ia = i + e;
                    int ib = i + (e + 1) % 4;
                    long key = edgeKey(verts.get(ia), verts.get(ib), SNAP);
                    if (counts.getOrDefault(key, new int[]{0})[0] <= 1) {
                        edges.add(ia);
                        edges.add(ib);
                    }
                }
            }
            return edges.stream().mapToInt(Integer::intValue).toArray();
        }

        private static long edgeKey(float[] a, float[] b, float snap) {
            int ax = Math.round(a[0]*snap), ay = Math.round(a[1]*snap), az = Math.round(a[2]*snap);
            int bx = Math.round(b[0]*snap), by = Math.round(b[1]*snap), bz = Math.round(b[2]*snap);
            long ka = ((long)(ax & 0x1FFFFF) << 42) | ((long)(ay & 0x1FFFFF) << 21) | (az & 0x1FFFFF);
            long kb = ((long)(bx & 0x1FFFFF) << 42) | ((long)(by & 0x1FFFFF) << 21) | (bz & 0x1FFFFF);
            return ka <= kb ? ka * 2654435761L + kb : kb * 2654435761L + ka;
        }
    }
}
