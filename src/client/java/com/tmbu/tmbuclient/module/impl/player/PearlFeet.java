package com.tmbu.tmbuclient.module.impl.player;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * PearlFeet / KeyFlash — throws a pearl at your feet to gain i-frames
 * before an enemy's attack connects, negating their knockback.
 *
 * Two modes:
 * - Manual: press keybind to throw (original behavior)
 * - Auto: predicts incoming attacks based on enemy behavior and throws
 *   automatically when conditions are met
 *
 * Auto prediction checks:
 * 1. Enemy is holding a sword or axe
 * 2. Enemy is within trigger range
 * 3. Enemy is closing distance (getting closer each tick)
 * 4. Enemy is facing you (look vector aimed at you)
 * 5. You're not already in i-frames (invulnerableTime == 0)
 */
public class PearlFeet extends Module {

    // ── Switching ────────────────────────────────────────────────────────────
    private final BooleanSetting autoSwitch = addSetting(new BooleanSetting("Auto Switch", true).group("Switching"));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true).group("Switching"));

    // ── Auto Prediction ──────────────────────────────────────────────────────
    private final BooleanSetting autoPearl    = addSetting(new BooleanSetting("Auto", false).group("Prediction"));
    private final SliderSetting  triggerRange = addSetting(new SliderSetting("Trigger Range", 4.0, 2.0, 6.0, 0.5)
        .group("Prediction").visibleWhen(autoPearl::getValue));
    private final SliderSetting  facingAngle  = addSetting(new SliderSetting("Facing Angle", 30, 10, 90, 5)
        .group("Prediction").visibleWhen(autoPearl::getValue));
    private final SliderSetting  cooldownSec  = addSetting(new SliderSetting("Cooldown", 3.0, 1.0, 15.0, 0.5)
        .group("Prediction").visibleWhen(autoPearl::getValue));

    /** Track previous distances to detect closing. */
    private final Map<Integer, Double> prevDistances = new HashMap<>();
    private long lastPearlTime = 0;
    private boolean manualThrown = false;
    private Integer previousSlot = null;

    public PearlFeet() {
        super("KeyFlash", "Throws pearl at feet for knockback immunity",
              Category.COMBAT, GLFW.GLFW_KEY_G);
    }

    @Override
    public void onEnable() {
        manualThrown = false;
        previousSlot = null;

        // If auto mode is off, this is a manual one-shot throw
        if (!autoPearl.getValue()) {
            // Will throw on next tick and disable
        }
    }

    @Override
    public void onDisable() {
        prevDistances.clear();
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || gameMode == null || client.level == null) return;

        if (autoPearl.getValue()) {
            tickAutoPrediction(client, player, gameMode);
        } else {
            // Manual mode — throw once and disable
            if (manualThrown) { toggle(); return; }
            throwPearl(client, player, gameMode);
            manualThrown = true;
        }
    }

    private void tickAutoPrediction(Minecraft client, LocalPlayer player, MultiPlayerGameMode gameMode) {
        // Don't throw if already in i-frames
        if (player.invulnerableTime > 0) return;

        // Cooldown between auto-pearls
        long now = System.currentTimeMillis();
        if (now - lastPearlTime < cooldownSec.getValue() * 1000) return;

        // Must have pearls
        if (getPearlHand(player) == null && findPearlSlot(player) == -1) return;

        double triggerRangeSq = triggerRange.getValue() * triggerRange.getValue();
        double facingThreshold = Math.cos(Math.toRadians(facingAngle.getValue()));

        boolean shouldThrow = false;

        for (Entity entity : client.level.getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(triggerRange.getValue() + 2), e -> e != player && e.isAlive())) {

            if (!(entity instanceof Player enemy)) continue;
            if (enemy.isSpectator()) continue;

            // 1. Must be holding a weapon
            if (!isWeapon(enemy.getMainHandItem())) continue;

            // 2. Must be within trigger range
            double distSq = player.distanceToSqr(enemy);
            if (distSq > triggerRangeSq) {
                prevDistances.put(enemy.getId(), Math.sqrt(distSq));
                continue;
            }

            double dist = Math.sqrt(distSq);

            // 3. Must be closing distance
            Double prevDist = prevDistances.get(enemy.getId());
            prevDistances.put(enemy.getId(), dist);
            if (prevDist == null || dist >= prevDist) continue; // not closing

            // 4. Must be facing us
            Vec3 enemyLook = enemy.getLookAngle();
            Vec3 toPlayer = player.position().subtract(enemy.position()).normalize();
            double dot = enemyLook.dot(toPlayer);
            if (dot < facingThreshold) continue; // not facing us

            // All conditions met
            shouldThrow = true;
            break;
        }

        if (shouldThrow) {
            throwPearl(client, player, gameMode);
            lastPearlTime = now;
        }
    }

    private void throwPearl(Minecraft client, LocalPlayer player, MultiPlayerGameMode gameMode) {
        InteractionHand hand = getPearlHand(player);
        if (hand == null && autoSwitch.getValue()) {
            int slot = findPearlSlot(player);
            if (slot == -1) return;
            previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            hand = InteractionHand.MAIN_HAND;
        }
        if (hand == null) return;

        // Snap pitch to 90 (straight down) for the throw
        float originalPitch = player.getXRot();
        player.setXRot(90.0F);

        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            player.getYRot(), 90.0F, player.onGround(), player.horizontalCollision));

        gameMode.useItem(player, hand);
        player.swing(hand);

        // Restore pitch immediately — camera never visually moves
        player.setXRot(originalPitch);
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            player.getYRot(), originalPitch, player.onGround(), player.horizontalCollision));

        if (switchBack.getValue() && previousSlot != null) {
            player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = null;
        }
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
            || stack.getItem() instanceof AxeItem;
    }

    private static InteractionHand getPearlHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.ENDER_PEARL)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.ENDER_PEARL)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static int findPearlSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}
