package com.tmbu.tmbuclient.module.impl;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.render.WireframeEntityRenderer;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.ModeSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class EspModule extends Module {

    // ── Entity filters ───────────────────────────────────────────────────────
    private final BooleanSetting showPlayers   = addSetting(new BooleanSetting("Players", true).group("Entities"));
    private final BooleanSetting showHostiles  = addSetting(new BooleanSetting("Hostiles", true).group("Entities"));
    private final BooleanSetting showPassives  = addSetting(new BooleanSetting("Passives", false).group("Entities"));
    private final BooleanSetting showItems     = addSetting(new BooleanSetting("Items", false).group("Entities"));
    private final BooleanSetting showCrystals  = addSetting(new BooleanSetting("Crystals", false).group("Entities"));
    private final BooleanSetting showVehicles  = addSetting(new BooleanSetting("Vehicles", false).group("Entities"));
    private final BooleanSetting showInvisible = addSetting(new BooleanSetting("Invisible", true).group("Entities"));

    // ── Per-type colors ──────────────────────────────────────────────────────
    private final ColorSetting playersColor  = addSetting(new ColorSetting("Players Color", 0xFF3D9EFF).group("Colors").visibleWhen(() -> showPlayers.getValue()));
    private final ColorSetting hostilesColor = addSetting(new ColorSetting("Hostiles Color", 0xFFFF5555).group("Colors").visibleWhen(() -> showHostiles.getValue()));
    private final ColorSetting passivesColor = addSetting(new ColorSetting("Passives Color", 0xFF55FF55).group("Colors").visibleWhen(() -> showPassives.getValue()));
    private final ColorSetting itemsColor    = addSetting(new ColorSetting("Items Color", 0xFFFFFF55).group("Colors").visibleWhen(() -> showItems.getValue()));
    private final ColorSetting crystalsColor = addSetting(new ColorSetting("Crystals Color", 0xFFFF00FF).group("Colors").visibleWhen(() -> showCrystals.getValue()));
    private final ColorSetting vehiclesColor = addSetting(new ColorSetting("Vehicles Color", 0xFF8888FF).group("Colors").visibleWhen(() -> showVehicles.getValue()));
    private final ColorSetting friendColor   = addSetting(new ColorSetting("Friend Color", 0xFF00FFAA).group("Colors"));

    // ── Color modes ──────────────────────────────────────────────────────────
    public enum ColorMode { STATIC, HEALTH, DISTANCE, RAINBOW }
    private final EnumSetting<ColorMode> colorMode = addSetting(new EnumSetting<>("Color Mode", ColorMode.STATIC).group("Color Mode"));
    private final SliderSetting rainbowSpeed   = addSetting(new SliderSetting("Rainbow Speed", 4.0, 0.5, 20.0, 0.5).group("Color Mode").visibleWhen(() -> colorMode.getValue() == ColorMode.RAINBOW));
    private final SliderSetting rainbowSpread  = addSetting(new SliderSetting("Rainbow Spread", 6.0, 0.0, 20.0, 0.5).group("Color Mode").visibleWhen(() -> colorMode.getValue() == ColorMode.RAINBOW));
    private final SliderSetting distFadeStart  = addSetting(new SliderSetting("Dist Fade Start", 8.0, 1.0, 64.0, 1.0).group("Color Mode").visibleWhen(() -> colorMode.getValue() == ColorMode.DISTANCE));
    private final SliderSetting distFadeEnd    = addSetting(new SliderSetting("Dist Fade End", 48.0, 8.0, 256.0, 1.0).group("Color Mode").visibleWhen(() -> colorMode.getValue() == ColorMode.DISTANCE));

    // ── Outline style ────────────────────────────────────────────────────────
    public enum EspStyle { NONE, BOX, CORNERS, CROSS, DIAMOND }
    private final EnumSetting<EspStyle> outlineStyle = addSetting(new EnumSetting<>("Outline Style", EspStyle.BOX).group("Outline"));
    private final BooleanSetting tightBox    = addSetting(new BooleanSetting("Tight Box", true).group("Outline").visibleWhen(() -> outlineStyle.getValue() != EspStyle.NONE && outlineStyle.getValue() != EspStyle.CORNERS));
    private final SliderSetting lineWidth    = addSetting(new SliderSetting("Line Width", 2.0, 1.0, 5.0, 0.5).group("Outline"));
    private final SliderSetting cornerLen    = addSetting(new SliderSetting("Corner Length", 0.25, 0.05, 1.0, 0.05).group("Outline").visibleWhen(() -> outlineStyle.getValue() == EspStyle.CORNERS));
    private final SliderSetting boxInflate   = addSetting(new SliderSetting("Box Inflate", 0.05, 0.0, 0.5, 0.01).group("Outline").visibleWhen(() -> outlineStyle.getValue() != EspStyle.NONE));

    // ── Wireframe ────────────────────────────────────────────────────────────
    private final BooleanSetting wireframe       = addSetting(new BooleanSetting("Wireframe", false).group("Wireframe"));
    private final BooleanSetting wireframeFill   = addSetting(new BooleanSetting("Wireframe Fill", false).group("Wireframe").visibleWhen(wireframe::getValue));

    // ── Fill ─────────────────────────────────────────────────────────────────
    private final BooleanSetting fill        = addSetting(new BooleanSetting("Fill", true).group("Fill"));
    private final SliderSetting  fillAlpha   = addSetting(new SliderSetting("Fill Alpha", 40.0, 0.0, 255.0, 1.0).group("Fill").visibleWhen(fill::getValue));
    public enum FillAnim { NONE, PULSE, BREATHE, FLASH, WAVE, HEARTBEAT, STROBE }
    private final EnumSetting<FillAnim> fillAnim = addSetting(new EnumSetting<>("Fill Animation", FillAnim.NONE).group("Fill").visibleWhen(fill::getValue));
    private final SliderSetting  fillAnimSpeed = addSetting(new SliderSetting("Fill Anim Speed", 2.0, 0.5, 10.0, 0.5).group("Fill").visibleWhen(() -> fill.getValue() && fillAnim.getValue() != FillAnim.NONE));
    private final SliderSetting  fillAnimMin   = addSetting(new SliderSetting("Fill Anim Min", 10.0, 0.0, 100.0, 1.0).group("Fill").visibleWhen(() -> fill.getValue() && fillAnim.getValue() != FillAnim.NONE));

    // ── Glow ─────────────────────────────────────────────────────────────────
    private final SliderSetting glowLayers   = addSetting(new SliderSetting("Glow Layers", 0.0, 0.0, 6.0, 1.0).group("Glow"));
    private final SliderSetting glowSpacing  = addSetting(new SliderSetting("Glow Spacing", 0.5, 0.1, 2.0, 0.1).group("Glow").visibleWhen(() -> glowLayers.getValue() > 0));

    // ── Tracers ──────────────────────────────────────────────────────────────
    private final BooleanSetting tracers       = addSetting(new BooleanSetting("Tracers", false).group("Tracers"));
    private final ModeSetting    tracerOrigin  = addSetting(new ModeSetting("Tracer Origin", "Center",
                                                     new String[]{"Center", "Feet", "Eyes"}).group("Tracers").visibleWhen(tracers::getValue));
    private final SliderSetting  tracerAlpha   = addSetting(new SliderSetting("Tracer Alpha", 180.0, 10.0, 255.0, 1.0).group("Tracers").visibleWhen(tracers::getValue));
    private final BooleanSetting tracerFade    = addSetting(new BooleanSetting("Tracer Fade", true).group("Tracers").visibleWhen(tracers::getValue));

    // ── General ──────────────────────────────────────────────────────────────
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true).group("General"));
    private final SliderSetting  range       = addSetting(new SliderSetting("Range", 64.0, 8.0, 256.0, 1.0).group("General"));

    public EspModule() {
        super("ESP", "Highlights entities through walls with customizable styles, colors, and effects.",
              Category.RENDER, GLFW.GLFW_KEY_K);
    }

    // ── Main render ──────────────────────────────────────────────────────────

    @Override
    public void onWorldRender(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (!showPlayers.getValue() && !showHostiles.getValue() && !showPassives.getValue()
            && !showItems.getValue() && !showCrystals.getValue() && !showVehicles.getValue()) return;

        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3 cam = client.gameRenderer.getMainCamera().position();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double r = range.getValue();
        AABB searchBox = new AABB(
            client.player.getX() - r, client.player.getY() - r, client.player.getZ() - r,
            client.player.getX() + r, client.player.getY() + r, client.player.getZ() + r
        );

        if (throughWalls.getValue()) GL11.glDisable(GL11.GL_DEPTH_TEST);

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            PoseStack.Pose pose = matrices.last();
            VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);

            float w = lineWidth.getValue().floatValue();
            int glowN = glowLayers.getValue().intValue();
            float glowSp = glowSpacing.getValue().floatValue();
            long now = System.currentTimeMillis();

            int entityIndex = 0;
            for (Entity entity : client.level.getEntities(client.player, searchBox, Entity::isAlive)) {
                if (entity == client.player) continue;
                if (!showInvisible.getValue() && entity.isInvisible()) continue;

                EntityType type = classifyEntity(entity);
                if (type == null) continue;
                if (!isTypeEnabled(type)) continue;

                int color = computeColor(entity, type, entityIndex, now, cam);
                double dist = client.player.distanceTo(entity);

                // Build a box from the entity's visual dimensions at its interpolated position
                // instead of using the raw collision hitbox which is wider and jitters
                AABB box = getInterpolatedVisualBox(entity, partialTick, cam, tightBox.getValue())
                    .inflate(boxInflate.getValue());

                // ── Tracers ──────────────────────────────────────────────────
                if (tracers.getValue()) {
                    Vec3 target = box.getCenter();
                    int tAlpha = tracerAlpha.getValue().intValue();
                    if (tracerFade.getValue()) {
                        float fadePct = (float) Math.max(0, Math.min(1,
                            (dist - distFadeStart.getValue()) / (distFadeEnd.getValue() - distFadeStart.getValue())));
                        tAlpha = (int) (tAlpha * (1.0f - fadePct * 0.7f));
                    }
                    int tColor = (color & 0x00FFFFFF) | (Math.max(1, tAlpha) << 24);
                    Vec3 origin = getTracerOrigin(box, cam);
                    emitLine(lines, pose,
                        (float) origin.x, (float) origin.y, (float) origin.z,
                        (float) target.x, (float) target.y, (float) target.z,
                        tColor, w);
                }

                // ── Wireframe (model-accurate, independent of outline style) ─
                if (wireframe.getValue()) {
                    int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF, ca = (color >> 24) & 0xFF;
                    WireframeEntityRenderer.renderWireframe(entity, partialTick, cam, lines, pose, cr, cg, cb, ca, w);
                }

                // ── Outline (box/corners/cross/diamond around the bounding box) ─
                switch (outlineStyle.getValue()) {
                    case BOX     -> renderBox(lines, pose, box, color, w);
                    case CORNERS -> renderCorners(lines, pose, box, color, w, cornerLen.getValue().floatValue());
                    case CROSS   -> renderCross(lines, pose, box, color, w);
                    case DIAMOND -> renderDiamond(lines, pose, box, color, w);
                    case NONE    -> {}
                }

                // ── Glow layers ──────────────────────────────────────────────
                if (glowN > 0 && outlineStyle.getValue() != EspStyle.NONE) {
                    int baseA = (color >> 24) & 0xFF;
                    for (int i = 1; i <= glowN; i++) {
                        float gw = w + i * glowSp;
                        int ga = (int) (baseA * (0.35f / i));
                        int gc = (color & 0x00FFFFFF) | (Math.max(1, ga) << 24);
                        renderBox(lines, pose, box, gc, gw);
                    }
                }

                // ── Fill ─────────────────────────────────────────────────────
                if (fill.getValue()) {
                    bufferSource.endBatch(RenderTypes.LINES);
                    VertexConsumer tris = bufferSource.getBuffer(RenderTypes.debugFilledBox());

                    int fa = fillAlpha.getValue().intValue();
                    float animSpd = fillAnimSpeed.getValue().floatValue();
                    int minA = fillAnimMin.getValue().intValue();
                    switch (fillAnim.getValue()) {
                        case PULSE -> {
                            double pulse = (Math.sin(now / 1000.0 * animSpd * Math.PI) + 1.0) * 0.5;
                            fa = minA + (int) ((fa - minA) * pulse);
                        }
                        case BREATHE -> {
                            // Slower, smoother sine wave
                            double breath = (Math.sin(now / 1000.0 * animSpd * Math.PI * 0.5) + 1.0) * 0.5;
                            fa = minA + (int) ((fa - minA) * breath * breath); // squared for ease-in-out
                        }
                        case FLASH -> {
                            // Sharp on/off
                            fa = Math.sin(now / 1000.0 * animSpd * Math.PI * 2) > 0 ? fa : minA;
                        }
                        case WAVE -> {
                            // Per-entity wave offset based on entity index
                            double wave = (Math.sin(now / 1000.0 * animSpd * Math.PI + entityIndex * 0.5) + 1.0) * 0.5;
                            fa = minA + (int) ((fa - minA) * wave);
                        }
                        case HEARTBEAT -> {
                            // Double-beat pattern like a heartbeat
                            double t = (now / 1000.0 * animSpd) % 1.0;
                            double beat = t < 0.15 ? Math.sin(t / 0.15 * Math.PI) :
                                          t < 0.3 ? 0 :
                                          t < 0.45 ? Math.sin((t - 0.3) / 0.15 * Math.PI) * 0.7 : 0;
                            fa = minA + (int) ((fa - minA) * beat);
                        }
                        case STROBE -> {
                            // Rapid strobe with random-ish timing per entity
                            double strobe = Math.sin(now / 1000.0 * animSpd * Math.PI * 6 + entityIndex * 1.7);
                            fa = strobe > 0.3 ? fa : minA;
                        }
                        case NONE -> {}
                    }
                    int fillColor = (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, fa)) << 24);

                    // Use wireframe fill if wireframe + wireframe fill are both on
                    if (wireframe.getValue() && wireframeFill.getValue()) {
                        int fr = (fillColor >> 16) & 0xFF, fg = (fillColor >> 8) & 0xFF, fb = fillColor & 0xFF, ffa = (fillColor >> 24) & 0xFF;
                        if (!WireframeEntityRenderer.renderFilled(entity, partialTick, cam, tris, pose, fr, fg, fb, ffa)) {
                            renderFilledBox(tris, pose, box, fillColor);
                        }
                    } else {
                        renderFilledBox(tris, pose, box, fillColor);
                    }

                    bufferSource.endBatch(RenderTypes.debugFilledBox());
                    lines = bufferSource.getBuffer(RenderTypes.LINES);
                }

                entityIndex++;
            }

            bufferSource.endBatch(RenderTypes.LINES);
        }

        if (throughWalls.getValue()) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // ── Interpolated visual box ─────────────────────────────────────────────

    /**
     * Builds an AABB at the entity's tick-interpolated position, relative to the camera.
     *
     * When tightBox is true, the width is shrunk to approximate the visual model
     * rather than the full collision hitbox. Players for example have a 0.6-wide
     * hitbox but the model is visually ~0.4 wide. This makes the ESP hug the
     * model much more closely.
     */
    private static AABB getInterpolatedVisualBox(Entity entity, float partialTick, Vec3 cam, boolean tight) {
        // Interpolated world position (smooth between ticks)
        double x = net.minecraft.util.Mth.lerp(partialTick, entity.xOld, entity.getX()) - cam.x;
        double y = net.minecraft.util.Mth.lerp(partialTick, entity.yOld, entity.getY()) - cam.y;
        double z = net.minecraft.util.Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cam.z;

        net.minecraft.world.entity.EntityDimensions dims = entity.getDimensions(entity.getPose());
        float width = dims.width();
        float height = dims.height();

        if (tight) {
            // The visual model is narrower than the hitbox for most humanoid entities.
            // Players: hitbox 0.6, model ~0.38. Zombies/skeletons similar.
            // We scale width by 0.65 which closely matches the shoulder width of
            // the player model and most humanoid mobs.
            if (entity instanceof Player) {
                width *= 0.62f;
            } else if (entity instanceof LivingEntity) {
                // Most living entities have models narrower than their hitbox
                width *= 0.72f;
            }
            // Items, crystals, vehicles — keep hitbox width, it's accurate
        }

        float halfW = width * 0.5f;

        return new AABB(
            x - halfW, y, z - halfW,
            x + halfW, y + height, z + halfW
        );
    }

    // ── Entity classification ────────────────────────────────────────────────

    private enum EntityType { PLAYER, HOSTILE, PASSIVE, ITEM, CRYSTAL, VEHICLE }

    private static EntityType classifyEntity(Entity e) {
        if (e instanceof Player p && !p.isSpectator()) return EntityType.PLAYER;
        if (e instanceof Enemy)                        return EntityType.HOSTILE;
        if (e instanceof EndCrystal)                   return EntityType.CRYSTAL;
        if (e instanceof ItemEntity)                   return EntityType.ITEM;
        if (!(e instanceof LivingEntity) && !(e instanceof ItemEntity) && !(e instanceof EndCrystal))
            return EntityType.VEHICLE; // boats, minecarts, etc.
        if (e instanceof Animal || e instanceof LivingEntity) return EntityType.PASSIVE;
        return null;
    }

    private boolean isTypeEnabled(EntityType t) {
        return switch (t) {
            case PLAYER  -> showPlayers.getValue();
            case HOSTILE  -> showHostiles.getValue();
            case PASSIVE  -> showPassives.getValue();
            case ITEM     -> showItems.getValue();
            case CRYSTAL  -> showCrystals.getValue();
            case VEHICLE  -> showVehicles.getValue();
        };
    }

    private int getBaseColor(EntityType t) {
        return switch (t) {
            case PLAYER  -> playersColor.getValue();
            case HOSTILE  -> hostilesColor.getValue();
            case PASSIVE  -> passivesColor.getValue();
            case ITEM     -> itemsColor.getValue();
            case CRYSTAL  -> crystalsColor.getValue();
            case VEHICLE  -> vehiclesColor.getValue();
        };
    }

    // ── Color computation ────────────────────────────────────────────────────

    private int computeColor(Entity entity, EntityType type, int index, long now, Vec3 cam) {
        int base = getBaseColor(type);
        int alpha = (base >> 24) & 0xFF;

        return switch (colorMode.getValue()) {
            case STATIC   -> base;
            case HEALTH   -> healthGradient(entity, alpha);
            case DISTANCE -> distanceGradient(entity, base, cam);
            case RAINBOW  -> rainbowColor(index, now, alpha);
        };
    }

    private int healthGradient(Entity entity, int alpha) {
        if (!(entity instanceof LivingEntity living)) return (alpha << 24) | 0x888888;
        float pct = Math.max(0, Math.min(1, living.getHealth() / living.getMaxHealth()));
        // Red → Yellow → Green
        int r, g;
        if (pct < 0.5f) {
            r = 255;
            g = (int) (255 * (pct * 2));
        } else {
            r = (int) (255 * ((1 - pct) * 2));
            g = 255;
        }
        return (alpha << 24) | (r << 16) | (g << 8);
    }

    private int distanceGradient(Entity entity, int baseColor, Vec3 cam) {
        double dist = entity.position().distanceTo(cam);
        float start = (float) distFadeStart.getValue().doubleValue();
        float end = (float) distFadeEnd.getValue().doubleValue();
        float pct = (float) Math.max(0, Math.min(1, (dist - start) / (end - start)));
        int alpha = (int) (255 * (1.0f - pct * 0.8f));
        return (baseColor & 0x00FFFFFF) | (Math.max(1, alpha) << 24);
    }

    private int rainbowColor(int index, long now, int alpha) {
        float hue = ((now / 1000.0f * rainbowSpeed.getValue().floatValue())
                     + index * rainbowSpread.getValue().floatValue() * 0.01f) % 1.0f;
        if (hue < 0) hue += 1.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.9f, 1.0f);
        return (rgb & 0x00FFFFFF) | (alpha << 24);
    }

    // ── Tracer origin ────────────────────────────────────────────────────────

    private Vec3 getTracerOrigin(AABB box, Vec3 cam) {
        // All coordinates are already camera-relative (box is moved by -cam)
        return switch (tracerOrigin.getMode()) {
            case "Feet"   -> new Vec3(0, 0, 0);
            case "Eyes"   -> new Vec3(0, 0, 0); // camera is at eye level
            default        -> new Vec3(0, 0, 0.1); // slight offset so line isn't invisible
        };
    }

    // ── Render: Box ──────────────────────────────────────────────────────────

    private static void renderBox(VertexConsumer c, PoseStack.Pose pose, AABB box, int color, float w) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // Bottom
        emitLine(c, pose, x0, y0, z0, x1, y0, z0, color, w);
        emitLine(c, pose, x1, y0, z0, x1, y0, z1, color, w);
        emitLine(c, pose, x1, y0, z1, x0, y0, z1, color, w);
        emitLine(c, pose, x0, y0, z1, x0, y0, z0, color, w);
        // Top
        emitLine(c, pose, x0, y1, z0, x1, y1, z0, color, w);
        emitLine(c, pose, x1, y1, z0, x1, y1, z1, color, w);
        emitLine(c, pose, x1, y1, z1, x0, y1, z1, color, w);
        emitLine(c, pose, x0, y1, z1, x0, y1, z0, color, w);
        // Verticals
        emitLine(c, pose, x0, y0, z0, x0, y1, z0, color, w);
        emitLine(c, pose, x1, y0, z0, x1, y1, z0, color, w);
        emitLine(c, pose, x1, y0, z1, x1, y1, z1, color, w);
        emitLine(c, pose, x0, y0, z1, x0, y1, z1, color, w);
    }

    // ── Render: Corners ──────────────────────────────────────────────────────

    private static void renderCorners(VertexConsumer c, PoseStack.Pose pose, AABB box, int color, float w, float len) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        // 8 corners × 3 edges each
        corner(c, pose, x0, y0, z0, +len, +len, +len, color, w);
        corner(c, pose, x1, y0, z0, -len, +len, +len, color, w);
        corner(c, pose, x0, y0, z1, +len, +len, -len, color, w);
        corner(c, pose, x1, y0, z1, -len, +len, -len, color, w);
        corner(c, pose, x0, y1, z0, +len, -len, +len, color, w);
        corner(c, pose, x1, y1, z0, -len, -len, +len, color, w);
        corner(c, pose, x0, y1, z1, +len, -len, -len, color, w);
        corner(c, pose, x1, y1, z1, -len, -len, -len, color, w);
    }

    private static void corner(VertexConsumer c, PoseStack.Pose pose,
                               float x, float y, float z, float dx, float dy, float dz,
                               int color, float w) {
        emitLine(c, pose, x, y, z, x + dx, y, z, color, w);
        emitLine(c, pose, x, y, z, x, y + dy, z, color, w);
        emitLine(c, pose, x, y, z, x, y, z + dz, color, w);
    }

    // ── Render: Cross (X through center of each face) ────────────────────────

    private static void renderCross(VertexConsumer c, PoseStack.Pose pose, AABB box, int color, float w) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // Diagonals on each face
        // Bottom
        emitLine(c, pose, x0, y0, z0, x1, y0, z1, color, w);
        emitLine(c, pose, x1, y0, z0, x0, y0, z1, color, w);
        // Top
        emitLine(c, pose, x0, y1, z0, x1, y1, z1, color, w);
        emitLine(c, pose, x1, y1, z0, x0, y1, z1, color, w);
        // Front
        emitLine(c, pose, x0, y0, z0, x1, y1, z0, color, w);
        emitLine(c, pose, x1, y0, z0, x0, y1, z0, color, w);
        // Back
        emitLine(c, pose, x0, y0, z1, x1, y1, z1, color, w);
        emitLine(c, pose, x1, y0, z1, x0, y1, z1, color, w);
        // Left
        emitLine(c, pose, x0, y0, z0, x0, y1, z1, color, w);
        emitLine(c, pose, x0, y0, z1, x0, y1, z0, color, w);
        // Right
        emitLine(c, pose, x1, y0, z0, x1, y1, z1, color, w);
        emitLine(c, pose, x1, y0, z1, x1, y1, z0, color, w);
    }

    // ── Render: Diamond (rotated box, looks like a gem around the entity) ────

    private static void renderDiamond(VertexConsumer c, PoseStack.Pose pose, AABB box, int color, float w) {
        float cx = (float) ((box.minX + box.maxX) * 0.5);
        float cy = (float) ((box.minY + box.maxY) * 0.5);
        float cz = (float) ((box.minZ + box.maxZ) * 0.5);
        float rx = (float) ((box.maxX - box.minX) * 0.5);
        float ry = (float) ((box.maxY - box.minY) * 0.5);
        float rz = (float) ((box.maxZ - box.minZ) * 0.5);

        // 6 points: top, bottom, +x, -x, +z, -z
        float tx = cx, ty = cy + ry, tz = cz; // top
        float bx = cx, by = cy - ry, bz = cz; // bottom
        float px = cx + rx, py = cy, pz = cz;  // +x
        float nx = cx - rx, ny = cy, nz = cz;  // -x
        float fpx = cx, fpy = cy, fpz = cz + rz; // +z
        float fnx = cx, fny = cy, fnz = cz - rz; // -z

        // Top pyramid (4 edges from top to equator)
        emitLine(c, pose, tx, ty, tz, px, py, pz, color, w);
        emitLine(c, pose, tx, ty, tz, nx, ny, nz, color, w);
        emitLine(c, pose, tx, ty, tz, fpx, fpy, fpz, color, w);
        emitLine(c, pose, tx, ty, tz, fnx, fny, fnz, color, w);
        // Bottom pyramid
        emitLine(c, pose, bx, by, bz, px, py, pz, color, w);
        emitLine(c, pose, bx, by, bz, nx, ny, nz, color, w);
        emitLine(c, pose, bx, by, bz, fpx, fpy, fpz, color, w);
        emitLine(c, pose, bx, by, bz, fnx, fny, fnz, color, w);
        // Equator ring
        emitLine(c, pose, px, py, pz, fpx, fpy, fpz, color, w);
        emitLine(c, pose, fpx, fpy, fpz, nx, ny, nz, color, w);
        emitLine(c, pose, nx, ny, nz, fnx, fny, fnz, color, w);
        emitLine(c, pose, fnx, fny, fnz, px, py, pz, color, w);
    }

    // ── Render: Filled box ───────────────────────────────────────────────────

    private static void renderFilledBox(VertexConsumer c, PoseStack.Pose pose, AABB box, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        quad(c, pose, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,b,a); // bottom
        quad(c, pose, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, r,g,b,a); // top
        quad(c, pose, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,b,a); // north
        quad(c, pose, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,a); // south
        quad(c, pose, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,a); // west
        quad(c, pose, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,b,a); // east
    }

    // ── Vertex helpers ───────────────────────────────────────────────────────

    private static void emitLine(VertexConsumer c, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int color, float w) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(w);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }
}
