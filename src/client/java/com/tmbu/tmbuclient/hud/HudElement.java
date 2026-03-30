package com.tmbu.tmbuclient.hud;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for HUD elements with anchor-based positioning.
 * Inspired by Meteor Client's HUD architecture.
 */
public abstract class HudElement {
    private final String id;
    private final String displayName;
    private boolean active;
    private final List<HudSetting<?>> settings = new ArrayList<>();

    public final HudBox box;
    public boolean autoAnchors = true;
    public int x, y;

    // Visual settings
    private boolean showBackground = false;
    private int bgColor = 0x40000000;
    private boolean shadow = true;
    private double scale = 1.0;
    private boolean customScale = false;

    protected HudElement(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.active = true;
        this.box = new HudBox(this);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public void toggle() { active = !active; }

    public boolean showBackground() { return showBackground; }
    public void setShowBackground(boolean show) { this.showBackground = show; }
    public int getBgColor() { return bgColor; }
    public void setBgColor(int color) { this.bgColor = color; }
    public boolean hasShadow() { return shadow; }
    public void setShadow(boolean shadow) { this.shadow = shadow; }
    public double getScale() { return customScale ? scale : 1.0; }
    public void setScale(double scale) { this.scale = scale; }
    public boolean hasCustomScale() { return customScale; }
    public void setCustomScale(boolean custom) { this.customScale = custom; }

    public void setSize(double width, double height) {
        box.setSize(width, height);
    }

    public void setPos(int px, int py) {
        if (autoAnchors) {
            box.setPos(px, py);
            box.xAnchor = XAnchor.Left;
            box.yAnchor = YAnchor.Top;
            box.updateAnchors();
        } else {
            box.setPos(box.x + (px - this.x), box.y + (py - this.y));
        }
        updatePos();
    }

    public void move(int dx, int dy) {
        box.move(dx, dy);
        updatePos();
    }

    public void updatePos() {
        x = box.getRenderX();
        y = box.getRenderY();
    }

    protected double alignX(double contentWidth, Alignment alignment) {
        return box.alignX(getWidth(), contentWidth, alignment);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return box.width; }
    public int getHeight() { return box.height; }
    public int getX2() { return x + box.width; }
    public int getY2() { return y + box.height; }

    /** Draw background quad if enabled. */
    protected void drawBg(GuiGraphics g, int bx, int by, int w, int h) {
        if (showBackground) g.fill(bx, by, bx + w, by + h, bgColor);
    }

    /** Called each tick for updating data. */
    public void tick(HudRenderer renderer) {}

    /** Render the element. */
    public abstract void render(HudRenderer renderer);

    /** Render preview in editor (defaults to normal render). */
    public void renderPreview(HudRenderer renderer) {
        render(renderer);
    }

    public boolean isInEditor() {
        return HudEditorScreen.isOpen();
    }

    /** Register a setting for this element. Call in constructor. */
    protected <T> HudSetting<T> addSetting(HudSetting<T> setting) {
        settings.add(setting);
        return setting;
    }

    /** Get all custom settings for the editor panel. */
    public List<HudSetting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }
}
