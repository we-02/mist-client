package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class WatermarkElement extends HudElement {
    public WatermarkElement() {
        super("watermark", "Watermark", 4, 4, true);
        setAccentColor(0xFF3D9EFF);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        String text = "Mist Client";
        var hf = HudFont.INSTANCE;
        int w = hf.width(text) + 6;
        int h = hf.height() + 2;
        setSize(w, h);
        drawBg(g, getX(), getY(), w, h);
        hf.draw(g, text, getX() + 3, getY() + 1, getAccentColor());
    }
}
