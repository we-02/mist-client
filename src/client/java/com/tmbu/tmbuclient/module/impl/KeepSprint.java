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
 * This module saves your velocity before the attack and restores a portion
 * of it after, keeping you moving faster.
 *
 * Grim's Simulation check compares predicted vs actual movement. At 100%
 * motion restore, the server expects 0.6x but sees 1.0x — instant flag.
 * The default 80% is a safe middle ground that feels responsive but stays
 * within Grim's tolerance (movement threshold + 0.03 uncertainty).
 *
 * The sprint STATE is always restored (so you don't stop sprinting), but
 * the velocity is only partially restored based on the Motion setting.
 */
public class KeepSprint extends Module {

    private final SliderSetting motionPercent = addSetting(
        new SliderSetting("Motion", 80, 0, 100, 1).group("General"));

    private boolean wasSprinting = false;
    private Vec3 savedVelocity = Vec3.ZERO;

    private final Consumer<AttackEntityEvent> attackHandler = this::onPreAttack;

    public KeepSprint() {
        super("KeepSprint", "Prevents sprint reset when hitting entities",
              Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(AttackEntityEvent.class, -10, attackHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(AttackEntityEvent.class, attackHandler);
    }

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

        if (wasSprinting && !player.isSprinting()) {
            // Always restore sprint state — this is safe, the server
            // tracks sprint via packets not prediction
            player.setSprinting(true);

            double factor = motionPercent.getValue() / 100.0;
            if (factor > 0) {
                Vec3 current = player.getDeltaMovement();
                // Blend between the post-attack velocity (0.6x) and the saved velocity
                // factor=0.8 means: 80% of saved + 20% of slowed = ~0.92x original
                // This stays within Grim's movement threshold tolerance
                double rx = savedVelocity.x * factor + current.x * (1 - factor);
                double rz = savedVelocity.z * factor + current.z * (1 - factor);
                player.setDeltaMovement(rx, current.y, rz);
            }

            wasSprinting = false;
        }
    }
}
