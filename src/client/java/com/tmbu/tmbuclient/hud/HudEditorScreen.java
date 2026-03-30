package com.tmbu.tmbuclient.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD Editor with Meteor-style interaction:
 * - Left-click + drag to move elements
 * - Left-click (no drag) to toggle active/inactive
 * - Right-click to open element settings panel
 * - Drag on empty space to box-select
 * - Arrow keys to nudge selection (Ctrl for 10px)
 * - Snapping to edges, center, and other elements
 */
public class HudEditorScreen extends Screen {
    private static boolean open = false;

    // Colors
    private static final int INACTIVE_BG = 0x32C81919;
    private static final int INACTIVE_OL = 0xC8C81919;
    private static final int HOVER_BG = 0x32C8C8C8;
    private static final int HOVER_OL = 0xC8C8C8C8;
    private static final int SELECTION_BG = 0x19E1E1E1;
    private static final int SELECTION_OL = 0x64E1E1E1;
    private static final int SPLIT_LINE = 0x4BFFFFFF;
    private static final int SNAP_LINE = 0x603D9EFF;

    // Settings panel
    private static final int PANEL_BG = 0xE8101018;
    private static final int PANEL_W = 150;

    // State
    private boolean pressed;
    private int clickX, clickY;
    private boolean moved, dragging;
    private final List<HudElement> selection = new ArrayList<>();
    private HudElement settingsTarget;
    private int snapLineX = -1, snapLineY = -1;

    // Grab offset: distance from mouse to each selected element's position at drag start
    private int grabOffX, grabOffY;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        open = true;
    }

    @Override
    public void removed() {
        open = false;
        HudManager.INSTANCE.save();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public static boolean isOpen() { return open; }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Dim background
        g.fill(0, 0, width, height, 0x44000000);

        // Split lines (thirds)
        int w3 = width / 3, h3 = height / 3;
        drawDashedLine(g, w3, 0, w3, height, SPLIT_LINE);
        drawDashedLine(g, w3 * 2, 0, w3 * 2, height, SPLIT_LINE);
        drawDashedLine(g, 0, h3, width, h3, SPLIT_LINE);
        drawDashedLine(g, 0, h3 * 2, width, h3 * 2, SPLIT_LINE);

        // Snap lines
        if (snapLineX >= 0) g.fill(snapLineX, 0, snapLineX + 1, height, SNAP_LINE);
        if (snapLineY >= 0) g.fill(0, snapLineY, width, snapLineY + 1, SNAP_LINE);

        // Render all elements
        HudRenderer r = HudRenderer.INSTANCE;
        r.begin(g, delta);
        for (HudElement el : HudManager.INSTANCE) {
            el.updatePos();
            el.render(r);
        }
        r.end();

        // Inactive overlays
        for (HudElement el : HudManager.INSTANCE) {
            if (!el.isActive()) renderElementBox(g, el, INACTIVE_BG, INACTIVE_OL);
        }

        // Selection overlays
        for (HudElement el : selection) {
            renderElementBox(g, el, HOVER_BG, HOVER_OL);
        }

        // Hover overlay
        if (!pressed) {
            HudElement hovered = getHovered(mouseX, mouseY);
            if (hovered != null && !selection.contains(hovered)) {
                renderElementBox(g, hovered, HOVER_BG, HOVER_OL);
            }
        }

        // Selection rectangle
        if (pressed && !dragging && moved) {
            int x1 = Math.min(clickX, mouseX), y1 = Math.min(clickY, mouseY);
            int x2 = Math.max(clickX, mouseX), y2 = Math.max(clickY, mouseY);
            renderBox(g, x1, y1, x2 - x1, y2 - y1, SELECTION_BG, SELECTION_OL);
        }

        // Settings panel
        if (settingsTarget != null) {
            renderSettingsPanel(g, mouseX, mouseY);
        }

        // Help text
        String help = "Drag=Move | Click=Toggle | Right=Settings | Arrows=Nudge";
        g.drawString(font, help, (width - font.width(help)) / 2, 4, 0xFFAAAAAA, true);
    }

    private void renderElementBox(GuiGraphics g, HudElement el, int bg, int ol) {
        renderBox(g, el.getX(), el.getY(), el.getWidth(), el.getHeight(), bg, ol);
    }

    private void renderBox(GuiGraphics g, int bx, int by, int bw, int bh, int bg, int ol) {
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, bg);
        g.fill(bx, by, bx + bw, by + 1, ol);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, ol);
        g.fill(bx, by + 1, bx + 1, by + bh - 1, ol);
        g.fill(bx + bw - 1, by + 1, bx + bw, by + bh - 1, ol);
    }

    private void drawDashedLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        boolean horiz = y1 == y2;
        int len = horiz ? Math.abs(x2 - x1) : Math.abs(y2 - y1);
        int dashLen = 6, gapLen = 4;
        for (int i = 0; i < len; i += dashLen + gapLen) {
            int end = Math.min(i + dashLen, len);
            if (horiz) g.fill(x1 + i, y1, x1 + end, y1 + 1, color);
            else g.fill(x1, y1 + i, x1 + 1, y1 + end, color);
        }
    }

    // --- Settings Panel ---

    private int panelHeight(HudElement el) {
        // title(12) + sep(5) + active(13) + bg(13) + shadow(13) + per-setting(13 each, or 18 for color)
        int h = 12 + 5 + 13 + 13 + 13;
        for (HudSetting<?> s : el.getSettings()) {
            h += (s.type == HudSetting.Type.COLOR) ? 18 : 13;
        }
        return h + 6; // padding
    }

    private void renderSettingsPanel(GuiGraphics g, int mx, int my) {
        HudElement el = settingsTarget;
        int ph = panelHeight(el);
        int px = Math.min(el.getX() + el.getWidth() + 6, width - PANEL_W - 4);
        int py = Math.max(Math.min(el.getY(), height - ph - 4), 4);

        g.fill(px, py, px + PANEL_W, py + ph, PANEL_BG);
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF3D9EFF);

        int ly = py + 4;
        g.drawString(font, el.getDisplayName(), px + 4, ly, 0xFF3D9EFF, false); ly += 12;
        g.fill(px + 4, ly, px + PANEL_W - 4, ly + 1, 0xFF222233); ly += 5;

        // Base settings: Active, Background, Shadow
        ly = renderToggleRow(g, mx, my, px, ly, "Active", el.isActive());
        ly = renderToggleRow(g, mx, my, px, ly, "Background", el.showBackground());
        ly = renderToggleRow(g, mx, my, px, ly, "Shadow", el.hasShadow());

        // Element-specific settings
        for (HudSetting<?> s : el.getSettings()) {
            switch (s.type) {
                case BOOL, ENUM, INT -> {
                    boolean hov = isIn(mx, my, px + 4, ly, PANEL_W - 8, 10);
                    String display = s.name + ": " + s.displayValue();
                    g.drawString(font, display, px + 4, ly, hov ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                    ly += 13;
                }
                case COLOR -> {
                    g.drawString(font, s.name + ":", px + 4, ly, 0xFF888888, false);
                    ly += 9;
                    int[] presets = s.getColorPresets();
                    if (presets != null) {
                        @SuppressWarnings("unchecked")
                        int currentColor = ((HudSetting<Integer>) s).get();
                        for (int i = 0; i < presets.length; i++) {
                            int sx = px + 4 + i * 18;
                            g.fill(sx, ly, sx + 14, ly + 8, presets[i]);
                            if (currentColor == presets[i]) {
                                // Selection border
                                g.fill(sx - 1, ly - 1, sx + 15, ly, 0xFFFFFFFF);
                                g.fill(sx - 1, ly + 8, sx + 15, ly + 9, 0xFFFFFFFF);
                                g.fill(sx - 1, ly, sx, ly + 8, 0xFFFFFFFF);
                                g.fill(sx + 14, ly, sx + 15, ly + 8, 0xFFFFFFFF);
                            }
                        }
                    }
                    ly += 9;
                }
            }
        }
    }

    private int renderToggleRow(GuiGraphics g, int mx, int my, int px, int ly, String label, boolean value) {
        boolean hov = isIn(mx, my, px + 4, ly, PANEL_W - 8, 10);
        g.drawString(font, label + ": " + (value ? "ON" : "OFF"), px + 4, ly,
            hov ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        return ly + 13;
    }

    private boolean handleSettingsPanelClick(int mx, int my) {
        HudElement el = settingsTarget;
        int ph = panelHeight(el);
        int px = Math.min(el.getX() + el.getWidth() + 6, width - PANEL_W - 4);
        int py = Math.max(Math.min(el.getY(), height - ph - 4), 4);

        if (!isIn(mx, my, px, py, PANEL_W, ph)) return false;

        int ly = py + 4 + 12 + 5; // after title + separator

        // Active toggle
        if (isIn(mx, my, px + 4, ly, PANEL_W - 8, 10)) { el.toggle(); return true; }
        ly += 13;

        // Background toggle
        if (isIn(mx, my, px + 4, ly, PANEL_W - 8, 10)) { el.setShowBackground(!el.showBackground()); return true; }
        ly += 13;

        // Shadow toggle
        if (isIn(mx, my, px + 4, ly, PANEL_W - 8, 10)) { el.setShadow(!el.hasShadow()); return true; }
        ly += 13;

        // Element-specific settings
        for (HudSetting<?> s : el.getSettings()) {
            switch (s.type) {
                case BOOL, ENUM, INT -> {
                    if (isIn(mx, my, px + 4, ly, PANEL_W - 8, 10)) { s.cycle(); return true; }
                    ly += 13;
                }
                case COLOR -> {
                    ly += 9; // label
                    int[] presets = s.getColorPresets();
                    if (presets != null) {
                        for (int i = 0; i < presets.length; i++) {
                            int sx = px + 4 + i * 18;
                            if (isIn(mx, my, sx, ly, 14, 8)) {
                                @SuppressWarnings("unchecked")
                                HudSetting<Integer> cs = (HudSetting<Integer>) s;
                                cs.set(presets[i]);
                                return true;
                            }
                        }
                    }
                    ly += 9;
                }
            }
        }

        return true; // consumed click inside panel
    }

    // --- Mouse Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), btn = event.button();

        // Right-click: settings panel
        if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            HudElement hovered = getHovered(mx, my);
            settingsTarget = (settingsTarget == hovered) ? null : hovered;
            return true;
        }

        // Left-click
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Settings panel click
            if (settingsTarget != null && handleSettingsPanelClick(mx, my)) return true;

            pressed = true;
            moved = false;
            clickX = mx;
            clickY = my;

            HudElement hovered = getHovered(mx, my);
            dragging = hovered != null;
            if (dragging) {
                if (!selection.contains(hovered)) {
                    selection.clear();
                    selection.add(hovered);
                }
                // Record grab offset from mouse to primary element
                HudElement primary = selection.get(0);
                grabOffX = mx - primary.getX();
                grabOffY = my - primary.getY();
            } else {
                selection.clear();
                settingsTarget = null;
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = (int) event.x(), my = (int) event.y();

        if (!pressed) return super.mouseDragged(event, dx, dy);

        if (Math.abs(mx - clickX) > 2 || Math.abs(my - clickY) > 2) moved = true;

        if (dragging && !selection.isEmpty()) {
            snapLineX = snapLineY = -1;

            HudElement primary = selection.get(0);
            int pw = primary.getWidth(), ph = primary.getHeight();
            int snap = HudManager.INSTANCE.snappingRange;

            // Target position = mouse minus grab offset (where the element WANTS to be)
            int targetX = mx - grabOffX;
            int targetY = my - grabOffY;

            // Snapped position starts as the target
            int snappedX = targetX;
            int snappedY = targetY;

            // X snapping — snap the target, not the delta
            int cx = targetX + pw / 2;
            if (Math.abs(targetX) < snap) { snappedX = 0; snapLineX = 0; }
            else if (Math.abs(targetX + pw - width) < snap) { snappedX = width - pw; snapLineX = width - 1; }
            else if (Math.abs(cx - width / 2) < snap) { snappedX = width / 2 - pw / 2; snapLineX = width / 2; }
            else {
                for (HudElement other : HudManager.INSTANCE) {
                    if (selection.contains(other)) continue;
                    int ox = other.getX(), ow = other.getWidth();
                    if (Math.abs(targetX - ox) < snap) { snappedX = ox; snapLineX = ox; break; }
                    if (Math.abs(targetX + pw - (ox + ow)) < snap) { snappedX = ox + ow - pw; snapLineX = ox + ow; break; }
                    if (Math.abs(targetX - (ox + ow)) < snap) { snappedX = ox + ow; snapLineX = ox + ow; break; }
                }
            }

            // Y snapping
            int cy = targetY + ph / 2;
            if (Math.abs(targetY) < snap) { snappedY = 0; snapLineY = 0; }
            else if (Math.abs(targetY + ph - height) < snap) { snappedY = height - ph; snapLineY = height - 1; }
            else if (Math.abs(cy - height / 2) < snap) { snappedY = height / 2 - ph / 2; snapLineY = height / 2; }
            else {
                for (HudElement other : HudManager.INSTANCE) {
                    if (selection.contains(other)) continue;
                    int oy = other.getY(), oh = other.getHeight();
                    if (Math.abs(targetY - oy) < snap) { snappedY = oy; snapLineY = oy; break; }
                    if (Math.abs(targetY + ph - (oy + oh)) < snap) { snappedY = oy + oh - ph; snapLineY = oy + oh; break; }
                    if (Math.abs(targetY - (oy + oh)) < snap) { snappedY = oy + oh; snapLineY = oy + oh; break; }
                }
            }

            // Move all selected elements by the difference from current to snapped
            int moveX = snappedX - primary.getX();
            int moveY = snappedY - primary.getY();
            for (HudElement el : selection) el.move(moveX, moveY);
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = (int) event.x(), my = (int) event.y();

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!moved && dragging) {
                // Click without drag = toggle
                HudElement hovered = getHovered(mx, my);
                if (hovered != null) hovered.toggle();
            } else if (!dragging && moved) {
                // Box selection
                fillSelection(mx, my);
            }

            pressed = false;
            moved = false;
            dragging = false;
            snapLineX = snapLineY = -1;
        }

        return super.mouseReleased(event);
    }

    // --- Keyboard Input ---

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        int modifiers = event.modifiers();

        if (!pressed && !selection.isEmpty()) {
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            int pixels = ctrl ? 10 : 1;
            int ddx = 0, ddy = 0;

            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> ddy = -pixels;
                case GLFW.GLFW_KEY_DOWN -> ddy = pixels;
                case GLFW.GLFW_KEY_LEFT -> ddx = -pixels;
                case GLFW.GLFW_KEY_RIGHT -> ddx = pixels;
            }

            if (ddx != 0 || ddy != 0) {
                for (HudElement el : selection) el.move(ddx, ddy);
                return true;
            }
        }

        // Delete key disables selection
        if (keyCode == GLFW.GLFW_KEY_DELETE && !selection.isEmpty()) {
            for (HudElement el : selection) el.setActive(false);
            selection.clear();
            return true;
        }

        return super.keyPressed(event);
    }

    // --- Helpers ---

    private HudElement getHovered(int mx, int my) {
        List<HudElement> elements = HudManager.INSTANCE.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement el = elements.get(i);
            int ew = Math.max(el.getWidth(), 20), eh = Math.max(el.getHeight(), 10);
            if (mx >= el.getX() && mx <= el.getX() + ew && my >= el.getY() && my <= el.getY() + eh) {
                return el;
            }
        }
        return null;
    }

    private void fillSelection(int mx, int my) {
        int x1 = Math.min(clickX, mx), y1 = Math.min(clickY, my);
        int x2 = Math.max(clickX, mx), y2 = Math.max(clickY, my);
        selection.clear();
        for (HudElement el : HudManager.INSTANCE) {
            if (el.getX() <= x2 && el.getX2() >= x1 && el.getY() <= y2 && el.getY2() >= y1) {
                selection.add(el);
            }
        }
    }

    private static boolean isIn(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }
}
