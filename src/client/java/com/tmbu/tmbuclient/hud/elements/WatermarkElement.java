package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;

public class WatermarkElement extends HudElement {
    private int accentColor = 0xFF3D9EFF;
    private int versionColor = 0xFFAAAAAA;

    public WatermarkElement() {
        super("watermark", "Watermark");

        addSetting(HudSetting.ofColor("Accent Color",
            () -> accentColor, v -> accentColor = v,
            0xFF3D9EFF, 0xFF00E5FF, 0xFFAA55FF, 0xFFFF55AA, 0xFF55FF7A, 0xFFFF5555, 0xFFFFAA00));
        addSetting(HudSetting.ofColor("Version Color",
            () -> versionColor, v -> versionColor = v,
            0xFFAAAAAA, 0xFFFFFFFF, 0xFF888888, 0xFF555555));
    }

    public int getAccentColor() { return accentColor; }
    public void setAccentColor(int c) { this.accentColor = c; }
    public int getVersionColor() { return versionColor; }
    public void setVersionColor(int c) { this.versionColor = c; }

    @Override
    public void render(HudRenderer r) {
        String name = "Mist";
        String ver = " v1.0";
        boolean shadow = hasShadow();
        double scale = getScale();

        double nameW = r.textWidth(name, shadow, scale);
        double verW = r.textWidth(ver, shadow, scale);
        double h = r.textHeight(shadow, scale);

        setSize(nameW + verW, h);
        drawBg(r.graphics, x, y, getWidth(), getHeight());

        r.text(name, x, y, accentColor, shadow, scale);
        r.text(ver, x + nameW, y, versionColor, shadow, scale);
    }
}
