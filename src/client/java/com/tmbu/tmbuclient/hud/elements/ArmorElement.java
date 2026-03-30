package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Displays armor items with durability bars.
 */
public class ArmorElement extends HudElement {
    public enum Orientation { Horizontal, Vertical }

    private Orientation orientation = Orientation.Horizontal;
    private boolean flipOrder = true;

    public ArmorElement() {
        super("armor", "Armor");

        addSetting(HudSetting.ofEnum("Orientation", Orientation.class,
            () -> orientation, v -> orientation = v));
        addSetting(HudSetting.ofBool("Flip Order",
            () -> flipOrder, v -> flipOrder = v));
    }

    public Orientation getOrientation() { return orientation; }
    public void setOrientation(Orientation o) { this.orientation = o; }
    public boolean isFlipOrder() { return flipOrder; }
    public void setFlipOrder(boolean f) { this.flipOrder = f; }

    @Override
    public void render(HudRenderer r) {
        float scale = (float) getScale();
        int itemSize = (int) (16 * scale);
        int gap = (int) (2 * scale);

        if (orientation == Orientation.Horizontal) {
            setSize((itemSize + gap) * 4, itemSize);
        } else {
            setSize(itemSize, (itemSize + gap) * 4);
        }

        drawBg(r.graphics, x, y, getWidth(), getHeight());

        r.post(() -> {
            Minecraft mc = Minecraft.getInstance();
            EquipmentSlot[] order = flipOrder
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

            for (int i = 0; i < order.length; i++) {
                ItemStack stack = getItem(mc, order[i]);
                int ix, iy;
                if (orientation == Orientation.Horizontal) {
                    ix = x + i * (itemSize + gap);
                    iy = y;
                } else {
                    ix = x;
                    iy = y + i * (itemSize + gap);
                }
                r.item(stack, ix, iy, scale, stack.isDamageableItem());
            }
        });
    }

    private ItemStack getItem(Minecraft mc, EquipmentSlot slot) {
        if (mc.player == null || isInEditor()) {
            return switch (slot) {
                case HEAD -> Items.NETHERITE_HELMET.getDefaultInstance();
                case CHEST -> Items.NETHERITE_CHESTPLATE.getDefaultInstance();
                case LEGS -> Items.NETHERITE_LEGGINGS.getDefaultInstance();
                case FEET -> Items.NETHERITE_BOOTS.getDefaultInstance();
                default -> ItemStack.EMPTY;
            };
        }
        return mc.player.getItemBySlot(slot);
    }
}
