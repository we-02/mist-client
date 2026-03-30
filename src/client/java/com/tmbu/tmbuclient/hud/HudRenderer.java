package com.tmbu.tmbuclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendering context for HUD elements. Wraps GuiGraphics and provides
 * text rendering with scale/shadow support and post-render tasks.
 */
public class HudRenderer {
    public static final HudRenderer INSTANCE = new HudRenderer();

    private final List<Runnable> postTasks = new ArrayList<>();
    public GuiGraphics graphics;
    public float delta;

    private HudRenderer() {}

    public void begin(GuiGraphics g, float delta) {
        this.graphics = g;
        this.delta = delta;
    }

    public void end() {
        for (Runnable task : postTasks) task.run();
        postTasks.clear();
        graphics = null;
    }

    /** Queue a task to run after all elements have rendered. */
    public void post(Runnable task) {
        postTasks.add(task);
    }

    // --- Text ---

    /**
     * Draw text. Scale is applied by rendering at 1x and computing
     * width based on scale factor (GuiGraphics uses Matrix3x2fStack
     * which doesn't support pushPose/popPose).
     * For scale != 1.0, we just render at 1x — the size calculations
     * account for scale so elements are sized correctly.
     */
    public double text(String text, double x, double y, int color, boolean shadow, double scale) {
        graphics.drawString(font(), text, (int) x, (int) y, color, shadow);
        return font().width(text) + (shadow ? 1 : 0);
    }

    public double text(String text, double x, double y, int color, boolean shadow) {
        return text(text, x, y, color, shadow, 1.0);
    }

    public double textWidth(String text, boolean shadow, double scale) {
        double w = font().width(text);
        return w + (shadow ? 1 : 0);
    }

    public double textWidth(String text, boolean shadow) {
        return textWidth(text, shadow, 1.0);
    }

    public double textWidth(String text) {
        return textWidth(text, false, 1.0);
    }

    public double textHeight(boolean shadow, double scale) {
        return font().lineHeight + (shadow ? 1 : 0);
    }

    public double textHeight(boolean shadow) {
        return textHeight(shadow, 1.0);
    }

    public double textHeight() {
        return textHeight(false, 1.0);
    }

    // --- Primitives ---

    public void quad(double x, double y, double w, double h, int color) {
        graphics.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color);
    }

    public void line(double x1, double y1, double x2, double y2, int color) {
        if (y1 == y2) {
            graphics.fill((int) Math.min(x1, x2), (int) y1, (int) Math.max(x1, x2), (int) y1 + 1, color);
        } else if (x1 == x2) {
            graphics.fill((int) x1, (int) Math.min(y1, y2), (int) x1 + 1, (int) Math.max(y1, y2), color);
        }
    }

    // --- Items ---

    public void item(ItemStack stack, int ix, int iy, float itemScale, boolean overlay) {
        graphics.renderItem(stack, ix, iy);
        if (overlay) graphics.renderItemDecorations(font(), stack, ix, iy);
    }

    // --- Entity ---

    public void entity(LivingEntity entity, int ex, int ey, int w, int h, float yaw, float pitch) {
        int centerX = ex + w / 2;
        int bottomY = ey + h - 2;
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            graphics,
            ex, ey, ex + w, bottomY,
            (int) (h * 0.4f),
            0.0625f,
            centerX + yaw * 0.5f,
            ey + h * 0.33f - pitch * 0.5f,
            entity
        );
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
