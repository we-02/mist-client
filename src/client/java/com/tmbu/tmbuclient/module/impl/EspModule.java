package com.tmbu.tmbuclient.module.impl;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public class EspModule extends Module {
    // Toggles
    private final BooleanSetting players  = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting hostiles = addSetting(new BooleanSetting("Hostiles", true));
    private final BooleanSetting passives = addSetting(new BooleanSetting("Passives", false));
    private final BooleanSetting items    = addSetting(new BooleanSetting("Items", false));
    private final BooleanSetting tracers  = addSetting(new BooleanSetting("Tracers", false));
    
    // Colors
    private final ColorSetting playersColor  = addSetting(new ColorSetting("Players Color", 0xFF3D9EFF));
    private final ColorSetting hostilesColor = addSetting(new ColorSetting("Hostiles Color", 0xFFFF5555));
    private final ColorSetting passivesColor = addSetting(new ColorSetting("Passives Color", 0xFF55FF55));
    private final ColorSetting itemsColor    = addSetting(new ColorSetting("Items Color", 0xFFFFFF55));
    
    // Dynamic settings
    private final BooleanSetting healthColor = addSetting(new BooleanSetting("Health Color", true));

    // Styles
    public enum EspStyle { BOX, FILLED, BOX_AND_FILL, CORNERS }
    private final EnumSetting<EspStyle> style = addSetting(new EnumSetting<>("Style", EspStyle.BOX_AND_FILL));

    // Tweaks
    private final BooleanSetting throughWalls = addSetting(new BooleanSetting("Through Walls", true));
    private final SliderSetting range         = addSetting(new SliderSetting("Range", 64.0, 8.0, 256.0, 1.0));
    private final SliderSetting lineWidth     = addSetting(new SliderSetting("Line Width", 2.0, 1.0, 5.0, 0.5));
    private final SliderSetting fillAlpha     = addSetting(new SliderSetting("Fill Alpha", 50.0, 0.0, 255.0, 1.0)); // Transparent fill by default
    private final SliderSetting glowIntensity = addSetting(new SliderSetting("Glow Intensity", 0.0, 0.0, 3.0, 0.5));

    public EspModule() {
        super("ESP", "Highlights entities through walls with customizable colors, fills, and tracers.",
              Category.RENDER, GLFW.GLFW_KEY_K);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        
        // Quick exit if all are disabled
        if (!players.getValue() && !hostiles.getValue() && !passives.getValue() && !items.getValue()) return;

        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
        double r = range.getValue();
        AABB searchBox = new AABB(
            client.player.getX() - r, client.player.getY() - r, client.player.getZ() - r,
            client.player.getX() + r, client.player.getY() + r, client.player.getZ() + r
        );

        if (throughWalls.getValue()) GL11.glDisable(GL11.GL_DEPTH_TEST);
        else GL11.glEnable(GL11.GL_DEPTH_TEST);

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            PoseStack.Pose pose = matrices.last();
            VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);
            
            float width = lineWidth.getValue().floatValue();
            int glow = (int) (glowIntensity.getValue() * 2);
            int alphaFill = fillAlpha.getValue().intValue();

            for (Entity entity : client.level.getEntities(client.player, searchBox, Entity::isAlive)) {
                if (entity == client.player) continue;

                boolean isPlayer = entity instanceof Player p && !p.isSpectator();
                boolean isHostile = entity instanceof Enemy;
                boolean isPassive = entity instanceof Animal || (entity instanceof LivingEntity && !isHostile && !isPlayer);
                boolean isItem = entity instanceof ItemEntity;

                if ((isPlayer && !players.getValue()) || 
                    (isHostile && !hostiles.getValue()) || 
                    (isPassive && !passives.getValue()) || 
                    (isItem && !items.getValue())) continue;

                int color = getEntityColor(entity, isPlayer, isHostile, isPassive, isItem);
                
                AABB box = entity.getBoundingBox()
                    .inflate(0.05)
                    .move(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                // Draw Tracers
                // Draw Tracers
                if (tracers.getValue()) {
                    Vec3 center = box.getCenter();
                    
                    // Rename variables to avoid conflict with 'r' (range)
                    int red   = (color >> 16) & 0xFF;
                    int green = (color >> 8) & 0xFF;
                    int blue  = color & 0xFF;
                    int alpha = (color >> 24) & 0xFF;
                    
                    // Draw from camera origin (0,0,0) to entity center
                    emitLine(lines, pose, 0.0f, 0.0f, 0.0f, (float)center.x, (float)center.y, (float)center.z, red, green, blue, alpha, width);
                }
                // Render Box / Outline
                switch (style.getValue()) {
                    case BOX, BOX_AND_FILL -> renderBox(lines, pose, box, color, width);
                    case CORNERS -> renderCorners(lines, pose, box, color, width);
                    case FILLED -> {} // Handled below
                }

                // Render Fill
                if (style.getValue() == EspStyle.FILLED || style.getValue() == EspStyle.BOX_AND_FILL) {
                    bufferSource.endBatch(RenderTypes.LINES);
                    // Using debugFilledBox. Ensure your render engine supports translucency on this RenderType
                    VertexConsumer triangles = bufferSource.getBuffer(RenderTypes.debugFilledBox());
                    
                    // Replace the alpha of the outline color with the custom fill alpha
                    int fillColor = (color & 0x00FFFFFF) | (alphaFill << 24);
                    renderFilledBox(triangles, pose, box, fillColor);
                    
                    bufferSource.endBatch(RenderTypes.debugFilledBox());
                    lines = bufferSource.getBuffer(RenderTypes.LINES); // Re-open lines buffer
                }

                // Render Glow effect
                if (glow > 0) {
                    int baseAlpha = (color >> 24) & 0xFF;
                    for (int i = 1; i <= glow; i++) {
                        float glowWidth = width + i * 0.5f;
                        int glowAlpha = (int) (baseAlpha * (0.4f / (i + 1)));
                        int glowColor = (color & 0x00FFFFFF) | (glowAlpha << 24);
                        renderBox(lines, pose, box, glowColor, glowWidth);
                    }
                }
            }

            bufferSource.endBatch(RenderTypes.LINES);
        }

        if (throughWalls.getValue()) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Determines the correct color based on entity type and health.
     */
    private int getEntityColor(Entity entity, boolean isPlayer, boolean isHostile, boolean isPassive, boolean isItem) {
        int baseColor = itemsColor.getValue();
        if (isPlayer) baseColor = playersColor.getValue();
        else if (isHostile) baseColor = hostilesColor.getValue();
        else if (isPassive) baseColor = passivesColor.getValue();

        if (healthColor.getValue() && entity instanceof LivingEntity living) {
            float health = living.getHealth();
            float maxHealth = living.getMaxHealth();
            float pct = Math.max(0, Math.min(1, health / maxHealth));
            
            // Red to Green gradient
            int r = (int) (255 * (1 - pct));
            int g = (int) (255 * pct);
            int b = 0;
            int a = (baseColor >> 24) & 0xFF; // Keep original alpha
            
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        
        return baseColor;
    }

    // --- RENDER HELPERS REMAIN MOSTLY THE SAME ---

    private void renderBox(VertexConsumer consumer, PoseStack.Pose pose, AABB box, int color, float width) {
        // (Your existing renderBox code here)
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        emitLine(consumer, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, width);

        emitLine(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, width);

        emitLine(consumer, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, width);
    }

    private void renderFilledBox(VertexConsumer consumer, PoseStack.Pose pose, AABB box, int color) {
        // (Your existing renderFilledBox code here, removed the width param as it's not used for quads)
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        emitQuad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        emitQuad(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        emitQuad(consumer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        emitQuad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        emitQuad(consumer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
        emitQuad(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
    }

    private void renderCorners(VertexConsumer consumer, PoseStack.Pose pose, AABB box, int color, float width) {
        // (Your existing renderCorners code here)
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;
        float len = 0.2f;

        emitLine(consumer, pose, minX, minY, minZ, minX + len, minY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, minZ, minX, minY + len, minZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, minZ, minX, minY, minZ + len, r, g, b, a, width);

        emitLine(consumer, pose, maxX, minY, minZ, maxX - len, minY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, minZ, maxX, minY + len, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, minZ, maxX, minY, minZ + len, r, g, b, a, width);

        emitLine(consumer, pose, minX, minY, maxZ, minX + len, minY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, maxZ, minX, minY + len, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, minY, maxZ, minX, minY, maxZ - len, r, g, b, a, width);

        emitLine(consumer, pose, maxX, minY, maxZ, maxX - len, minY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, maxZ, maxX, minY + len, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, minY, maxZ, maxX, minY, maxZ - len, r, g, b, a, width);

        emitLine(consumer, pose, minX, maxY, minZ, minX + len, maxY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, maxY, minZ, minX, maxY - len, minZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, maxY, minZ, minX, maxY, minZ + len, r, g, b, a, width);

        emitLine(consumer, pose, maxX, maxY, minZ, maxX - len, maxY, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, minZ, maxX, maxY - len, minZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, minZ, maxX, maxY, minZ + len, r, g, b, a, width);

        emitLine(consumer, pose, minX, maxY, maxZ, minX + len, maxY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, maxY, maxZ, minX, maxY - len, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, minX, maxY, maxZ, minX, maxY, maxZ - len, r, g, b, a, width);

        emitLine(consumer, pose, maxX, maxY, maxZ, maxX - len, maxY, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, maxZ, maxX, maxY - len, maxZ, r, g, b, a, width);
        emitLine(consumer, pose, maxX, maxY, maxZ, maxX, maxY, maxZ - len, r, g, b, a, width);
    }

    private static void emitLine(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int r, int g, int b, int a, float width) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(width);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0, 1, 0).setLineWidth(width);
    }

    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float x4, float y4, float z4,
                                 int r, int g, int b, int a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }
}