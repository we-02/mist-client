package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class FpsElement extends HudElement {
    public FpsElement() {
        super("fps", "FPS", 4, 18, true);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        int fps = client.getFps();
        int color = fps >= 60 ? 0xFF55FF55 : fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555;
        String text = fps + " FPS";
        var hf = HudFont.INSTANCE;
        int w = hf.width(text) + 6;
        int h = hf.height() + 2;
        setSize(w, h);
        drawBg(g, getX(), getY(), w, h);
        hf.draw(g, text, getX() + 3, getY() + 1, color);
    }
}
