package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * TriggerBot — automatically attacks when your crosshair is on a valid entity
 * and the 1.9+ attack cooldown is ready.
 *
 * Timing is based on the raw attack strength ticker vs the weapon's cooldown
 * period (like LiquidBounce's KillAura). No CPS — attacks exactly when the
 * cooldown bar fills to the configured threshold, with optional randomization
 * to avoid detection.
 *
 * Does NOT manually reset the attack strength ticker — gameMode.attack()
 * already calls player.attack() which resets it internally.
 */
public class TriggerBot extends Module {

    // ── General ──────────────────────────────────────────────────────────────
    private final SliderSetting  cooldownMin       = addSetting(new SliderSetting("Cooldown Min", 0.9, 0.5, 1.0, 0.05)
        .group("Timing"));
    private final SliderSetting  cooldownMax       = addSetting(new SliderSetting("Cooldown Max", 1.0, 0.5, 1.5, 0.05)
        .group("Timing"));
    private final SliderSetting  range             = addSetting(new SliderSetting("Range", 3.0, 1.0, 6.0, 0.1)
        .group("General"));

    // ── Targeting ────────────────────────────────────────────────────────────
    private final BooleanSetting targetPlayers     = addSetting(new BooleanSetting("Players", true).group("Targeting"));
    private final BooleanSetting targetHostiles    = addSetting(new BooleanSetting("Hostiles", false).group("Targeting"));
    private final BooleanSetting targetInvisible   = addSetting(new BooleanSetting("Invisible", false).group("Targeting"));

    // ── Conditions ───────────────────────────────────────────────────────────
    private final BooleanSetting requireWeapon     = addSetting(new BooleanSetting("Require Weapon", false).group("Conditions"));
    private final BooleanSetting stopOnShield      = addSetting(new BooleanSetting("Stop On Shield", false).group("Conditions"));
    private final BooleanSetting stopOnUse         = addSetting(new BooleanSetting("Stop While Using", true).group("Conditions"));

    private final Random rng = new Random();
    private float nextCooldownThreshold;

    public TriggerBot() {
        super("TriggerBot", "Auto-attacks when crosshair is on a valid entity",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onEnable() {
        rollNextThreshold();
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || client.level == null) return;
        if (client.screen != null) return;

        // Conditions
        if (stopOnShield.getValue() && (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem
            || player.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem)) return;
        if (stopOnUse.getValue() && player.isUsingItem()) return;
        if (requireWeapon.getValue() && !isWeapon(player)) return;

        // Check cooldown using raw tick-based calculation (same approach as LiquidBounce)
        // getAttackStrengthScale(0.5f) gives the progress at mid-tick which is more
        // representative than 0 (start of tick). The value ranges 0→1 where 1 = full.
        float cooldown = player.getAttackStrengthScale(0.5f);
        if (cooldown < nextCooldownThreshold) return;

        // Check if crosshair is on a valid entity
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return;
        if (!(client.hitResult instanceof EntityHitResult eHit)) return;

        Entity target = eHit.getEntity();
        if (!isValidTarget(player, target)) return;

        // Range check
        if (player.distanceToSqr(target) > range.getValue() * range.getValue()) return;

        // Attack — gameMode.attack() internally calls player.attack() which
        // resets the attack strength ticker. Do NOT call resetAttackStrengthTicker()
        // manually — that double-resets and breaks the timing.
        client.gameMode.attack(player, target);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        // Roll a new randomized threshold for the next attack
        rollNextThreshold();
    }

    /**
     * Randomize the cooldown threshold between min and max for each attack.
     * This mimics human variance and avoids anti-cheat pattern detection.
     */
    private void rollNextThreshold() {
        float min = cooldownMin.getValue().floatValue();
        float max = cooldownMax.getValue().floatValue();
        if (max <= min) {
            nextCooldownThreshold = min;
        } else {
            nextCooldownThreshold = min + rng.nextFloat() * (max - min);
        }
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
