package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class CoordsElement extends HudElement {
    public CoordsElement() {
        super("coords", "Coordinates", 4, 46, false);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        if (client.player == null) { setSize(60, 12); return; }
        String text = String.format("%.0f, %.0f, %.0f",
            client.player.getX(), client.player.getY(), client.player.getZ());
        var hf = HudFont.INSTANCE;
        int w = hf.width(text) + 6;
        int h = hf.height() + 2;
        setSize(w, h);
        drawBg(g, getX(), getY(), w, h);
        hf.draw(g, text, getX() + 3, getY() + 1, getTextColor());
    }
}
