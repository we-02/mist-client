package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.AttackEntityEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * SuperKnockback — increases knockback dealt to entities by sprint-resetting
 * before each attack via packets.
 *
 * How it works: sends STOP_SPRINTING → START_SPRINTING → STOP_SPRINTING → START_SPRINTING
 * packets right before the attack. The server sees you as having just started sprinting,
 * which applies the sprint knockback bonus. This is the "W-tap" technique automated.
 */
public class SuperKnockback extends Module {

    private final SliderSetting  hurtTime     = addSetting(new SliderSetting("Hurt Time", 10, 0, 10, 1).group("General"));
    private final SliderSetting  chance       = addSetting(new SliderSetting("Chance", 100, 0, 100, 1).group("General"));
    private final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only On Ground", false).group("Conditions"));
    private final BooleanSetting onlyMoving   = addSetting(new BooleanSetting("Only Moving", true).group("Conditions"));
    private final BooleanSetting notInWater   = addSetting(new BooleanSetting("Not In Water", true).group("Conditions"));

    private final Consumer<AttackEntityEvent> attackHandler = this::onAttack;

    public SuperKnockback() {
        super("SuperKnockback", "Increases knockback dealt via sprint-reset packets",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(AttackEntityEvent.class, attackHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(AttackEntityEvent.class, attackHandler);
    }

    private void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) return;

        // Conditions
        if (onlyOnGround.getValue() && !player.onGround()) return;
        if (notInWater.getValue() && player.isInWater()) return;
        if (onlyMoving.getValue() && player.getDeltaMovement().horizontalDistanceSqr() < 0.0001) return;
        if (!player.isSprinting()) return;

        // Only on living entities with low enough hurt time
        if (!(event.target() instanceof LivingEntity living)) return;
        if (living.hurtTime > hurtTime.getValue().intValue()) return;

        // Chance check
        if (Math.random() * 100 > chance.getValue()) return;

        // Send sprint toggle packets — this makes the server think we just started sprinting
        var conn = mc.getConnection();
        conn.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        conn.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));

        // Ensure client state matches
        player.setSprinting(true);
    }
}
