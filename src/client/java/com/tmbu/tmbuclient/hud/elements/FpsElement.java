package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;

public class FpsElement extends HudElement {
    public enum ValueColor { Dynamic, White, Gray }

    private int labelColor = 0xFFFFFFFF;
    private ValueColor valueColor = ValueColor.Dynamic;

    public FpsElement() {
        super("fps", "FPS");

        addSetting(HudSetting.ofColor("Label Color",
            () -> labelColor, v -> labelColor = v,
            0xFFFFFFFF, 0xFF3D9EFF, 0xFF55FF55, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00));
        addSetting(HudSetting.ofEnum("Value Color", ValueColor.class,
            () -> valueColor, v -> valueColor = v));
    }

    public int getLabelColor() { return labelColor; }
    public void setLabelColor(int c) { this.labelColor = c; }
    public ValueColor getValueColor() { return valueColor; }
    public void setValueColor(ValueColor v) { this.valueColor = v; }

    @Override
    public void render(HudRenderer r) {
        int fps = Minecraft.getInstance().getFps();
        String label = "FPS: ";
        String value = String.valueOf(fps);
        boolean shadow = hasShadow();
        double scale = getScale();

        int vc = switch (valueColor) {
            case Dynamic -> fps >= 60 ? 0xFF55FF55 : fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555;
            case White -> 0xFFFFFFFF;
            case Gray -> 0xFFAAAAAA;
        };

        double lw = r.textWidth(label, shadow, scale);
        double vw = r.textWidth(value, shadow, scale);
        double h = r.textHeight(shadow, scale);

        setSize(lw + vw, h);
        drawBg(r.graphics, x, y, getWidth(), getHeight());

        r.text(label, x, y, labelColor, shadow, scale);
        r.text(value, x + lw, y, vc, shadow, scale);
    }
}
