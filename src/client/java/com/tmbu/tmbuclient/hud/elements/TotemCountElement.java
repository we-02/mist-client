package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

public class TotemCountElement extends HudElement {
    public TotemCountElement() {
        super("totems", "Totem Count", 4, 110, true);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        if (client.player == null) { setSize(60, 12); return; }

        int count = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) count++;
        }
        if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count++;

        int color = count >= 3 ? 0xFF55FF55 : count >= 1 ? 0xFFFFFF55 : 0xFFFF5555;
        String text = "Totems: " + count;
        var hf = HudFont.INSTANCE;
        int w = hf.width(text) + 6;
        int h = hf.height() + 2;
        setSize(w, h);
        drawBg(g, getX(), getY(), w, h);
        hf.draw(g, text, getX() + 3, getY() + 1, color);
    }
}
