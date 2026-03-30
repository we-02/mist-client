package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;

public class TotemCountElement extends HudElement {
    public enum ValueColor { Dynamic, White, Gray }

    private int labelColor = 0xFFFFFFFF;
    private ValueColor valueColor = ValueColor.Dynamic;

    public TotemCountElement() {
        super("totems", "Totem Count");

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
        Minecraft mc = Minecraft.getInstance();
        int count = 0;
        if (mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) count++;
            }
            if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) count++;
        }

        String label = "Totems: ";
        String value = String.valueOf(count);
        boolean shadow = hasShadow();
        double scale = getScale();

        int vc = switch (valueColor) {
            case Dynamic -> count >= 3 ? 0xFF55FF55 : count >= 1 ? 0xFFFFFF55 : 0xFFFF5555;
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
