package com.tmbu.tmbuclient.module.impl.combat;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PostKeybindsEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.Random;
import java.util.function.Consumer;

/**
 * TriggerBot — auto-attacks when crosshair is on a valid entity.
 *
 * Runs on PostKeybindsEvent — right after Minecraft.handleKeybinds() in
 * Minecraft.tick(). This is the exact same timing as vanilla attacks:
 * - After GameRenderer.pick() updates crosshairPickEntity
 * - After handleKeybinds() processes vanilla key presses
 * - BEFORE tickEntities() → LocalPlayer.tick() → sendPosition() → flying packet
 *
 * This means INTERACT_ENTITY + ANIMATION are sent before the flying packet,
 * and the sprint slowdown from player.attack() is applied before movement
 * calculation, matching vanilla behavior exactly.
 */
public class TriggerBot extends Module {

    private final SliderSetting  cooldownMin       = addSetting(new SliderSetting("Cooldown Min", 0.9, 0.5, 1.0, 0.05).group("Timing"));
    private final SliderSetting  cooldownMax       = addSetting(new SliderSetting("Cooldown Max", 1.0, 0.5, 1.5, 0.05).group("Timing"));
    private final SliderSetting  range             = addSetting(new SliderSetting("Range", 3.0, 1.0, 6.0, 0.1).group("General"));
    private final BooleanSetting targetPlayers     = addSetting(new BooleanSetting("Players", true).group("Targeting"));
    private final BooleanSetting targetHostiles    = addSetting(new BooleanSetting("Hostiles", false).group("Targeting"));
    private final BooleanSetting targetInvisible   = addSetting(new BooleanSetting("Invisible", false).group("Targeting"));
    private final BooleanSetting requireWeapon     = addSetting(new BooleanSetting("Require Weapon", false).group("Conditions"));
    private final BooleanSetting stopOnShield      = addSetting(new BooleanSetting("Stop On Shield", false).group("Conditions"));
    private final BooleanSetting stopOnUse         = addSetting(new BooleanSetting("Stop While Using", true).group("Conditions"));

    private final Random rng = new Random();
    private float nextCooldownThreshold;

    private final Consumer<PostKeybindsEvent> handler = e -> onPostKeybinds(e.client());

    public TriggerBot() {
        super("TriggerBot", "Auto-attacks when crosshair is on a valid entity",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onEnable() {
        rollNextThreshold();
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(PostKeybindsEvent.class, handler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(PostKeybindsEvent.class, handler);
    }

    private void onPostKeybinds(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || client.level == null) return;
        if (client.screen != null) return;

        if (stopOnShield.getValue() && (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem
            || player.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem)) return;
        if (stopOnUse.getValue() && player.isUsingItem()) return;
        if (requireWeapon.getValue() && !isWeapon(player)) return;

        float cooldown = player.getAttackStrengthScale(0);
        if (cooldown < nextCooldownThreshold) return;

        if (client.crosshairPickEntity == null) return;
        Entity target = client.crosshairPickEntity;
        if (!isValidTarget(player, target)) return;

        double dist = player.distanceTo(target);
        if (dist > range.getValue()) return;

        // Attack at vanilla timing — same as startAttack() in handleKeybinds()
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        rollNextThreshold();
    }

    private void rollNextThreshold() {
        float min = cooldownMin.getValue().floatValue();
        float max = cooldownMax.getValue().floatValue();
        nextCooldownThreshold = max <= min ? min : min + rng.nextFloat() * (max - min);
    }

    private boolean isValidTarget(LocalPlayer player, Entity entity) {
        if (entity == player || !entity.isAlive()) return false;
        if (!targetInvisible.getValue() && entity.isInvisible()) return false;
        if (entity instanceof Player p) {
            if (p.isSpectator()) return false;
            return targetPlayers.getValue();
        }
        if (entity instanceof Enemy) return targetHostiles.getValue();
        return false;
    }

    private static boolean isWeapon(LocalPlayer player) {
        var stack = player.getMainHandItem();
        return stack.is(net.minecraft.tags.ItemTags.SWORDS)
            || stack.getItem() instanceof net.minecraft.world.item.AxeItem
            || stack.getItem() instanceof net.minecraft.world.item.TridentItem;
    }
}
