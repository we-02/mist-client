package com.tmbu.tmbuclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ColorPickerScreen extends Screen {
    private final HsvColorPicker picker = new HsvColorPicker();
    private final Consumer<Integer> onClose;
    private final int initialColor;

    public ColorPickerScreen(int initialColor, Consumer<Integer> onClose) {
        super(Component.literal("Pick a Color"));
        this.initialColor = initialColor;
        this.onClose = onClose;
    }

    @Override
    protected void init() {
        picker.setFromColor(initialColor);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Draw a plain dim overlay instead of renderBackground to avoid
        // "Can only blur once per frame" crash when opened on top of ClickGuiScreen
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int px = (width - HsvColorPicker.TOTAL_W) / 2;
        int py = (height - HsvColorPicker.TOTAL_H) / 2;

        // Panel background
        graphics.fill(px - 8, py - 8, px + HsvColorPicker.TOTAL_W + 8, py + HsvColorPicker.TOTAL_H + 8, 0xFF14141E);
        graphics.fill(px - 8, py - 8, px + HsvColorPicker.TOTAL_W + 8, py - 7, 0xFF2A2A40);
        graphics.fill(px - 8, py - 8, px - 7, py + HsvColorPicker.TOTAL_H + 8, 0xFF2A2A40);

        picker.render(graphics, px, py, 1.0f);

        // "Press Escape to close" hint
        String hint = "Press Escape to close";
        graphics.drawString(this.font, hint,
            (this.width - this.font.width(hint)) / 2,
            py + HsvColorPicker.TOTAL_H + 16,
            0xFF888899, false);

        // Don't call super.render — it would call renderBackground again
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int px = (width - HsvColorPicker.TOTAL_W) / 2;
        int py = (height - HsvColorPicker.TOTAL_H) / 2;
        if (picker.mouseClicked(event.x(), event.y(), px, py)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int px = (width - HsvColorPicker.TOTAL_W) / 2;
        int py = (height - HsvColorPicker.TOTAL_H) / 2;
        if (picker.mouseDragged(event.x(), event.y(), px, py)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        picker.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        onClose.accept(picker.getColor());
        super.onClose();
    }
}