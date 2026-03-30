package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class PingElement extends HudElement {
    public PingElement() {
        super("ping", "Ping", 4, 32, true);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        int ping = 0;
        if (client.player != null && client.getConnection() != null) {
            var info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        int color = ping < 80 ? 0xFF55FF55 : ping < 150 ? 0xFFFFFF55 : 0xFFFF5555;
        String text = ping + "ms";
        var hf = HudFont.INSTANCE;
        int w = hf.width(text) + 6;
        int h = hf.height() + 2;
        setSize(w, h);
        drawBg(g, getX(), getY(), w, h);
        hf.draw(g, text, getX() + 3, getY() + 1, color);
    }
}
