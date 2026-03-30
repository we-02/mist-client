package com.tmbu.tmbuclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Base class for draggable HUD elements.
 * Each element has a position, size, customizable colors, and renders itself on the game HUD.
 */
public abstract class HudElement {
    private final String id;
    private final String displayName;
    private int x, y;
    private int width, height;
    private boolean enabled;

    // Customizable properties
    private int textColor = 0xFFFFFFFF;
    private int bgColor = 0x80000000;
    private boolean showBackground = true;
    private int accentColor = 0xFF3D9EFF;

    protected HudElement(String id, String displayName, int defaultX, int defaultY, boolean defaultEnabled) {
        this.id = id;
        this.displayName = displayName;
        this.x = defaultX;
        this.y = defaultY;
        this.enabled = defaultEnabled;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTextColor() { return textColor; }
    public void setTextColor(int color) { this.textColor = color; }
    public int getBgColor() { return bgColor; }
    public void setBgColor(int color) { this.bgColor = color; }
    public boolean showBackground() { return showBackground; }
    public void setShowBackground(boolean show) { this.showBackground = show; }
    public int getAccentColor() { return accentColor; }
    public void setAccentColor(int color) { this.accentColor = color; }

    protected void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Draw background if enabled. Call at the start of render(). */
    protected void drawBg(GuiGraphics g, int x, int y, int w, int h) {
        if (showBackground) g.fill(x, y, x + w, y + h, bgColor);
    }

    public abstract void render(GuiGraphics graphics, Minecraft client, float delta);

    public void renderPreview(GuiGraphics graphics, Minecraft client) {
        render(graphics, client, 0);
    }
}
