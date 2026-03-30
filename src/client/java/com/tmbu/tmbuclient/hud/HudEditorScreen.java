package com.tmbu.tmbuclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * HUD Editor — drag to move, right-click to toggle, middle-click for settings.
 * Elements snap to screen edges, center, and other elements.
 */
public class HudEditorScreen extends Screen {
    private static final int BORDER_COLOR = 0x80FFFFFF;
    private static final int BORDER_ACTIVE = 0xFF3D9EFF;
    private static final int BORDER_DISABLED = 0x40FF5555;
    private static final int SNAP_LINE_COLOR = 0x603D9EFF;
    private static final int BG_COLOR = 0x30000000;
    private static final int PANEL_BG = 0xE8101018;
    private static final int PANEL_W = 140;
    private static final int SNAP_DIST = 6;

    private HudElement dragging = null;
    private int dragOffX, dragOffY;
    private int snapLineX = -1, snapLineY = -1;
    private HudElement settingsTarget = null; // element whose settings panel is open

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x44000000);

        if (snapLineX >= 0) g.fill(snapLineX, 0, snapLineX + 1, this.height, SNAP_LINE_COLOR);
        if (snapLineY >= 0) g.fill(0, snapLineY, this.width, snapLineY + 1, SNAP_LINE_COLOR);
        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x15FFFFFF);
        g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x15FFFFFF);

        Minecraft client = Minecraft.getInstance();

        for (HudElement el : HudManager.INSTANCE.getElements()) {
            el.renderPreview(g, client);
            int x = el.getX(), y = el.getY();
            int w = Math.max(el.getWidth(), 20), h = Math.max(el.getHeight(), 10);
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            int borderCol = !el.isEnabled() ? BORDER_DISABLED
                : (hovered || dragging == el || settingsTarget == el) ? BORDER_ACTIVE : BORDER_COLOR;

            g.fill(x - 1, y - 1, x + w + 1, y, borderCol);
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, borderCol);
            g.fill(x - 1, y, x, y + h, borderCol);
            g.fill(x + w, y, x + w + 1, y + h, borderCol);
            g.fill(x, y, x + w, y + h, BG_COLOR);

            if (!el.isEnabled()) {
                g.drawString(this.font, el.getDisplayName() + " (OFF)", x + 2, y + 2, 0x80FF5555, false);
            }
        }

        // Settings panel
        if (settingsTarget != null) {
            renderSettingsPanel(g, mouseX, mouseY);
        }

        String help = "Drag=Move | Right=Toggle | Middle=Settings";
        g.drawString(this.font, help, (this.width - this.font.width(help)) / 2, 4, 0xFFAAAAAA, true);
    }

    private void renderSettingsPanel(GuiGraphics g, int mx, int my) {
        HudElement el = settingsTarget;
        int px = Math.min(el.getX() + el.getWidth() + 4, this.width - PANEL_W - 4);
        int py = el.getY();
        int ph = 110;

        g.fill(px, py, px + PANEL_W, py + ph, PANEL_BG);
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF3D9EFF);

        int y = py + 4;
        g.drawString(font, el.getDisplayName(), px + 4, y, 0xFF3D9EFF, false); y += 12;
        g.fill(px + 4, y, px + PANEL_W - 4, y + 1, 0xFF222233); y += 4;

        // Background toggle
        boolean bgHov = isIn(mx, my, px + 4, y, PANEL_W - 8, 12);
        g.drawString(font, "Background: " + (el.showBackground() ? "ON" : "OFF"), px + 4, y,
            bgHov ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        y += 14;

        // Text color presets
        g.drawString(font, "Text Color:", px + 4, y, 0xFF888888, false); y += 11;
        int[] textPresets = { 0xFFFFFFFF, 0xFF3D9EFF, 0xFF55FF55, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00 };
        for (int i = 0; i < textPresets.length; i++) {
            int sx = px + 4 + i * 18;
            g.fill(sx, y, sx + 14, y + 14, textPresets[i]);
            if (el.getTextColor() == textPresets[i]) {
                g.fill(sx - 1, y - 1, sx + 15, y, 0xFFFFFFFF);
                g.fill(sx - 1, y + 14, sx + 15, y + 15, 0xFFFFFFFF);
                g.fill(sx - 1, y, sx, y + 14, 0xFFFFFFFF);
                g.fill(sx + 14, y, sx + 15, y + 14, 0xFFFFFFFF);
            }
        }
        y += 18;

        // Accent color presets
        g.drawString(font, "Accent:", px + 4, y, 0xFF888888, false); y += 11;
        int[] accentPresets = { 0xFF3D9EFF, 0xFF00E5FF, 0xFFAA55FF, 0xFFFF55AA, 0xFF55FF7A, 0xFFFF5555, 0xFFFFAA00 };
        for (int i = 0; i < accentPresets.length; i++) {
            int sx = px + 4 + i * 18;
            g.fill(sx, y, sx + 14, y + 14, accentPresets[i]);
            if (el.getAccentColor() == accentPresets[i]) {
                g.fill(sx - 1, y - 1, sx + 15, y, 0xFFFFFFFF);
                g.fill(sx - 1, y + 14, sx + 15, y + 15, 0xFFFFFFFF);
                g.fill(sx - 1, y, sx, y + 14, 0xFFFFFFFF);
                g.fill(sx + 14, y, sx + 15, y + 14, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), btn = event.button();

        // Handle settings panel clicks
        if (settingsTarget != null && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (handleSettingsPanelClick(mx, my)) return true;
        }

        for (int i = HudManager.INSTANCE.getElements().size() - 1; i >= 0; i--) {
            HudElement el = HudManager.INSTANCE.getElements().get(i);
            int w = Math.max(el.getWidth(), 20), h = Math.max(el.getHeight(), 10);

            if (mx >= el.getX() && mx <= el.getX() + w && my >= el.getY() && my <= el.getY() + h) {
                if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    dragging = el;
                    dragOffX = mx - el.getX();
                    dragOffY = my - el.getY();
                    settingsTarget = null;
                    return true;
                } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    el.setEnabled(!el.isEnabled());
                    return true;
                } else if (btn == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    settingsTarget = settingsTarget == el ? null : el;
                    return true;
                }
            }
        }

        // Click outside settings panel closes it
        settingsTarget = null;
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleSettingsPanelClick(int mx, int my) {
        HudElement el = settingsTarget;
        int px = Math.min(el.getX() + el.getWidth() + 4, this.width - PANEL_W - 4);
        int py = el.getY();

        if (!isIn(mx, my, px, py, PANEL_W, 110)) return false;

        int y = py + 4 + 12 + 4; // after title + separator

        // Background toggle
        if (isIn(mx, my, px + 4, y, PANEL_W - 8, 12)) {
            el.setShowBackground(!el.showBackground());
            return true;
        }
        y += 14 + 11; // skip label

        // Text color presets
        int[] textPresets = { 0xFFFFFFFF, 0xFF3D9EFF, 0xFF55FF55, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00 };
        for (int i = 0; i < textPresets.length; i++) {
            int sx = px + 4 + i * 18;
            if (isIn(mx, my, sx, y, 14, 14)) {
                el.setTextColor(textPresets[i]);
                return true;
            }
        }
        y += 18 + 11; // skip swatches + label

        // Accent color presets
        int[] accentPresets = { 0xFF3D9EFF, 0xFF00E5FF, 0xFFAA55FF, 0xFFFF55AA, 0xFF55FF7A, 0xFFFF5555, 0xFFFFAA00 };
        for (int i = 0; i < accentPresets.length; i++) {
            int sx = px + 4 + i * 18;
            if (isIn(mx, my, sx, y, 14, 14)) {
                el.setAccentColor(accentPresets[i]);
                return true;
            }
        }

        return true; // consumed click inside panel
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(event, dx, dy);

        int rawX = (int) event.x() - dragOffX;
        int rawY = (int) event.y() - dragOffY;
        int w = Math.max(dragging.getWidth(), 10), h = Math.max(dragging.getHeight(), 10);

        rawX = Mth.clamp(rawX, 0, this.width - w);
        rawY = Mth.clamp(rawY, 0, this.height - h);

        snapLineX = snapLineY = -1;
        int snappedX = rawX, snappedY = rawY;

        // X snapping
        int centerX = rawX + w / 2, rightX = rawX + w;
        if (Math.abs(rawX) < SNAP_DIST) { snappedX = 0; snapLineX = 0; }
        else if (Math.abs(rightX - this.width) < SNAP_DIST) { snappedX = this.width - w; snapLineX = this.width - 1; }
        else if (Math.abs(centerX - this.width / 2) < SNAP_DIST) { snappedX = this.width / 2 - w / 2; snapLineX = this.width / 2; }
        else {
            for (HudElement other : HudManager.INSTANCE.getElements()) {
                if (other == dragging) continue;
                int ox = other.getX(), ow = Math.max(other.getWidth(), 10);
                if (Math.abs(rawX - ox) < SNAP_DIST) { snappedX = ox; snapLineX = ox; break; }
                if (Math.abs(rightX - (ox + ow)) < SNAP_DIST) { snappedX = ox + ow - w; snapLineX = ox + ow; break; }
                if (Math.abs(rawX - (ox + ow)) < SNAP_DIST) { snappedX = ox + ow; snapLineX = ox + ow; break; }
            }
        }

        // Y snapping
        int centerY = rawY + h / 2, bottomY = rawY + h;
        if (Math.abs(rawY) < SNAP_DIST) { snappedY = 0; snapLineY = 0; }
        else if (Math.abs(bottomY - this.height) < SNAP_DIST) { snappedY = this.height - h; snapLineY = this.height - 1; }
        else if (Math.abs(centerY - this.height / 2) < SNAP_DIST) { snappedY = this.height / 2 - h / 2; snapLineY = this.height / 2; }
        else {
            for (HudElement other : HudManager.INSTANCE.getElements()) {
                if (other == dragging) continue;
                int oy = other.getY(), oh = Math.max(other.getHeight(), 10);
                if (Math.abs(rawY - oy) < SNAP_DIST) { snappedY = oy; snapLineY = oy; break; }
                if (Math.abs(bottomY - (oy + oh)) < SNAP_DIST) { snappedY = oy + oh - h; snapLineY = oy + oh; break; }
                if (Math.abs(rawY - (oy + oh)) < SNAP_DIST) { snappedY = oy + oh; snapLineY = oy + oh; break; }
            }
        }

        dragging.setX(snappedX);
        dragging.setY(snappedY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        snapLineX = snapLineY = -1;
        return super.mouseReleased(event);
    }

    @Override
    public void removed() {
        HudManager.INSTANCE.save();
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
