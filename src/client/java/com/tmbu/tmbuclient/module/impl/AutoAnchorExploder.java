package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.lwjgl.glfw.GLFW;

public class AutoAnchorExploder extends Module {

    private final BooleanSetting autoCharge = addSetting(new BooleanSetting("Auto Charge", true));
    private final BooleanSetting autoDetonate = addSetting(new BooleanSetting("Auto Detonate", true));
    private final BooleanSetting requireHold = addSetting(new BooleanSetting("Require Hold", false));
    private final SliderSetting chargeDelay = addSetting(new SliderSetting("Charge Delay", 100, 0, 500, 10));
    private final BooleanSetting switchItems = addSetting(new BooleanSetting("Auto Switch Items", true));
    private final SliderSetting minCharges = addSetting(new SliderSetting("Min Charges", 1, 1, 4, 1));
    private final SliderSetting maxCharges = addSetting(new SliderSetting("Max Charges", 4, 1, 4, 1));

    private boolean wasHolding = false;
    private long lastChargeTime = 0;
    private BlockPos currentAnchorPos = null;
    private boolean isCharging = false;
    private Integer preSwitchHotbarSlot = null;

    public AutoAnchorExploder() {
        super("AutoAnchor", "Automatically charges and detonates respawn anchors", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetState();
    }

    private void resetState() {
        wasHolding = false;
        currentAnchorPos = null;
        isCharging = false;
        lastChargeTime = 0;
        preSwitchHotbarSlot = null;
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode interactionManager = client.gameMode;
        Level world = client.level;

        if (player == null || interactionManager == null || world == null) return;

        boolean active = !requireHold.getValue() || isHoldingRelevantItem(player);
        if (!active) {
            if (wasHolding) resetState();
            wasHolding = false;
            return;
        }
        wasHolding = true;

        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) client.hitResult;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.is(Blocks.RESPAWN_ANCHOR)) {
                handleAnchor(player, interactionManager, pos, state, hit);
            }
        }
    }

    private void handleAnchor(LocalPlayer player, MultiPlayerGameMode interactionManager, BlockPos pos, BlockState state, BlockHitResult hit) {
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        int min = (int) Math.round(minCharges.getValue());
        int max = (int) Math.round(maxCharges.getValue());
        long delay = (long) Math.round(chargeDelay.getValue());

        // DETONATE only if this is the anchor we are "owning"
        if (autoDetonate.getValue() && currentAnchorPos != null && currentAnchorPos.equals(pos) && charges >= min && charges <= max) {
            isCharging = false;  // done charging
            currentAnchorPos = null; // release ownership
            attemptDetonate(player, interactionManager, pos, hit);
            return;
        }

        // CHARGING logic
        if (autoCharge.getValue() && charges < max) {
            if (hasGlowstone(player) && (currentAnchorPos == null || !currentAnchorPos.equals(pos))) {
                currentAnchorPos = pos;
                isCharging = true;
            }

            if (isCharging && currentAnchorPos.equals(pos)) {
                if (System.currentTimeMillis() - lastChargeTime >= delay) {
                    attemptCharge(player, interactionManager, pos, hit);
                    lastChargeTime = System.currentTimeMillis();
                }
            }
        }

        // stop charging if max reached
        if (isCharging && currentAnchorPos.equals(pos) && charges >= max) {
            isCharging = false;
            currentAnchorPos = null;
        }
    }



    private void attemptCharge(LocalPlayer player, MultiPlayerGameMode interactionManager, BlockPos pos, BlockHitResult hit) {
        InteractionHand hand = getGlowstoneHand(player);

        if (hand == null && switchItems.getValue()) {
            int slot = findGlowstoneHotbarSlot(player);
            if (slot != -1) {
                if (preSwitchHotbarSlot == null) {
                    int currentSlot = getSelectedHotbarSlot(player);
                    if (currentSlot != -1 && currentSlot != slot) {
                        preSwitchHotbarSlot = currentSlot;
                    }
                }
                player.getInventory().setSelectedSlot(slot);
                hand = InteractionHand.MAIN_HAND;
            }
        }

        if (hand != null) {
            interactionManager.useItemOn(player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false));
        }
    }

    private void attemptDetonate(LocalPlayer player, MultiPlayerGameMode interactionManager, BlockPos pos, BlockHitResult hit) {
        // If we previously switched to glowstone, restore the original slot
        if (preSwitchHotbarSlot != null && player.getMainHandItem().is(Items.GLOWSTONE)) {
            if (preSwitchHotbarSlot >= 0 && preSwitchHotbarSlot < 9) {
                player.getInventory().setSelectedSlot(preSwitchHotbarSlot);
            }
            preSwitchHotbarSlot = null;
        }

        // Always use MAIN_HAND for detonation
        interactionManager.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false));
    }


    // private int getNonGlowstoneHotbarSlot(LocalPlayer player) {
    //     for (int i = 0; i < 9; i++) {
    //         ItemStack stack = player.getInventory().getItem(i);
    //         if (!stack.is(Items.GLOWSTONE)) {
    //             return i;
    //         }
    //     }
    //     return -1; // fallback if all slots are glowstone
    // }

    private int getSelectedHotbarSlot(LocalPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i) == mainHand) {
                return i;
            }
        }
        return -1;
    }

    private boolean isHoldingRelevantItem(LocalPlayer player) {
        return player.getMainHandItem().is(Items.RESPAWN_ANCHOR)
                || player.getOffhandItem().is(Items.RESPAWN_ANCHOR)
                || player.getMainHandItem().is(Items.GLOWSTONE)
                || player.getOffhandItem().is(Items.GLOWSTONE);
    }

    private boolean hasGlowstone(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.GLOWSTONE)) return true;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.GLOWSTONE)) return true;
        }
        return false;
    }

    private InteractionHand getGlowstoneHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.GLOWSTONE)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.GLOWSTONE)) return InteractionHand.OFF_HAND;
        return null;
    }

    private int findGlowstoneHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.GLOWSTONE)) return i;
        }
        return -1;
    }
}
