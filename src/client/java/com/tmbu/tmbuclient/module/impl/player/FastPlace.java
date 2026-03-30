package com.tmbu.tmbuclient.module.impl.player;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.lwjgl.glfw.GLFW;

/**
 * FastPlace — removes or reduces the 4-tick delay between block placements.
 *
 * Vanilla has a rightClickDelay of 4 ticks between uses. This module sets it
 * to 0 (or a custom value) so you can place blocks/use items every tick.
 *
 * Grim-safe: the delay is purely client-side. The server processes one
 * placement per tick regardless. No extra packets are sent.
 */
public class FastPlace extends Module {

    private final SliderSetting  delay       = addSetting(new SliderSetting("Delay", 0, 0, 4, 1).group("General"));
    private final BooleanSetting blocks      = addSetting(new BooleanSetting("Blocks", true).group("Apply To"));
    private final BooleanSetting projectiles = addSetting(new BooleanSetting("Projectiles", true).group("Apply To"));
    private final BooleanSetting all         = addSetting(new BooleanSetting("All Items", false).group("Apply To"));

    private static FastPlace instance;

    public FastPlace() {
        super("FastPlace", "Removes block placement delay",
              Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    /**
     * Called from mixin to get the modified right-click delay.
     * Returns -1 if the module shouldn't modify the delay.
     */
    public static int getModifiedDelay() {
        if (instance == null || !instance.isEnabled()) return -1;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;

        var mainItem = mc.player.getMainHandItem().getItem();
        var offItem = mc.player.getOffhandItem().getItem();

        if (instance.all.getValue()) {
            return instance.delay.getValue().intValue();
        }

        boolean applies = false;
        if (instance.blocks.getValue() && (mainItem instanceof BlockItem || offItem instanceof BlockItem)) {
            applies = true;
        }
        if (instance.projectiles.getValue() && (mainItem instanceof ProjectileWeaponItem || offItem instanceof ProjectileWeaponItem)) {
            applies = true;
        }

        return applies ? instance.delay.getValue().intValue() : -1;
    }
}
