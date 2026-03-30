package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.AttackEntityEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * KeepSprint — prevents the sprint slowdown when you hit an entity.
 *
 * Vanilla multiplies your velocity by 0.6 when you attack while sprinting.
 * This module saves your velocity before the attack and restores it after,
 * keeping you at full speed.
 */
public class KeepSprint extends Module {

    private final SliderSetting motionPercent = addSetting(
        new SliderSetting("Motion", 100, 0, 100, 1).group("General"));

    private boolean wasSprinting = false;
    private Vec3 savedVelocity = Vec3.ZERO;

    private final Consumer<AttackEntityEvent> attackHandler = this::onPreAttack;

    public KeepSprint() {
        super("KeepSprint", "Prevents sprint reset when hitting entities",
              Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        // Priority -10 = runs BEFORE SuperKnockback (which is default 0)
        bus.subscribe(AttackEntityEvent.class, -10, attackHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(AttackEntityEvent.class, attackHandler);
    }

    /**
     * Save sprint state before the attack. The actual restoration happens
     * in onTick — we check if sprint was lost and restore it.
     */
    private void onPreAttack(AttackEntityEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        wasSprinting = player.isSprinting();
        savedVelocity = player.getDeltaMovement();
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        // If we were sprinting before the attack but aren't now, restore it
        if (wasSprinting && !player.isSprinting()) {
            double factor = motionPercent.getValue() / 100.0;

            player.setSprinting(true);

            if (factor > 0) {
                Vec3 current = player.getDeltaMovement();
                double rx = savedVelocity.x * factor + current.x * (1 - factor);
                double rz = savedVelocity.z * factor + current.z * (1 - factor);
                player.setDeltaMovement(rx, current.y, rz);
            }

            wasSprinting = false;
        }
    }
}
