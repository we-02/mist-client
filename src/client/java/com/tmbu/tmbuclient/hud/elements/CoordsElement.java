package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;

public class CoordsElement extends HudElement {
    private int labelColor = 0xFFFFFFFF;
    private int valueColor = 0xFFAAAAAA;

    public CoordsElement() {
        super("coords", "Coordinates");

        addSetting(HudSetting.ofColor("Label Color",
            () -> labelColor, v -> labelColor = v,
            0xFFFFFFFF, 0xFF3D9EFF, 0xFF55FF55, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00));
        addSetting(HudSetting.ofColor("Value Color",
            () -> valueColor, v -> valueColor = v,
            0xFFAAAAAA, 0xFFFFFFFF, 0xFF3D9EFF, 0xFF55FF55));
    }

    public int getLabelColor() { return labelColor; }
    public void setLabelColor(int c) { this.labelColor = c; }
    public int getValueColor() { return valueColor; }
    public void setValueColor(int c) { this.valueColor = c; }

    @Override
    public void render(HudRenderer r) {
        Minecraft mc = Minecraft.getInstance();
        String label = "Pos: ";
        String value;
        if (mc.player != null) {
            value = String.format("%.0f, %.0f, %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        } else {
            value = "0, 0, 0";
        }

        boolean shadow = hasShadow();
        double scale = getScale();

        double lw = r.textWidth(label, shadow, scale);
        double vw = r.textWidth(value, shadow, scale);
        double h = r.textHeight(shadow, scale);

        setSize(lw + vw, h);
        drawBg(r.graphics, x, y, getWidth(), getHeight());

        r.text(label, x, y, labelColor, shadow, scale);
        r.text(value, x + lw, y, valueColor, shadow, scale);
    }
}
