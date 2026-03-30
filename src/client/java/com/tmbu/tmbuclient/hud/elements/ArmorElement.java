package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorElement extends HudElement {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public ArmorElement() {
        super("armor", "Armor", 4, 60, true);
    }

    @Override
    public void render(GuiGraphics g, Minecraft client, float delta) {
        if (client.player == null) { setSize(70, 48); return; }

        int x = getX(), y = getY();
        int maxW = 70;

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack stack = client.player.getItemBySlot(ARMOR_SLOTS[i]);
            int rowY = y + i * 12;

            if (stack.isEmpty()) {
                drawBg(g, x, rowY, maxW, 11);
                g.drawString(client.font, "Empty", x + 3, rowY + 1, 0x40888888, false);
            } else {
                drawBg(g, x, rowY, maxW, 11);
                String name = stack.getHoverName().getString();
                if (name.length() > 8) name = name.substring(0, 7) + "..";

                if (stack.isDamageableItem()) {
                    int maxDmg = stack.getMaxDamage();
                    int dmg = stack.getDamageValue();
                    float pct = 1.0f - (float) dmg / maxDmg;
                    int barW = (int)((maxW - 4) * pct);
                    int barColor = pct > 0.5f ? 0xFF55FF55 : pct > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;

                    g.fill(x + 2, rowY + 9, x + maxW - 2, rowY + 11, 0xFF222222);
                    g.fill(x + 2, rowY + 9, x + 2 + barW, rowY + 11, barColor);
                }

                g.drawString(client.font, name, x + 3, rowY + 1, getTextColor(), false);
            }
        }

        setSize(maxW, ARMOR_SLOTS.length * 12);
    }
}
