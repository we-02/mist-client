package com.tmbu.tmbuclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Font wrapper for HUD rendering. Uses Minecraft's built-in font with
 * scale and shadow options for a cleaner look.
 *
 * Scale is applied by pre-computing the position offset rather than
 * using matrix transforms (GuiGraphics uses Matrix3x2fStack which
 * doesn't support pushPose/popPose like PoseStack).
 */
public final class HudFont {
    public static final HudFont INSTANCE = new HudFont();

    private float scale = 1.0f;
    private boolean shadow = true;

    private HudFont() {}

    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.5f, Math.min(3.0f, scale)); }
    public boolean hasShadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }

    /** Draw text at the given position with the current shadow setting. */
    public void draw(GuiGraphics g, String text, float x, float y, int color) {
        g.drawString(Minecraft.getInstance().font, text, (int) x, (int) y, color, shadow);
    }

    /** Get the width of text using Minecraft's font. */
    public int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    /** Get the height of a line. */
    public int height() {
        return 10;
    }
}
