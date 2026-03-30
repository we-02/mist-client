package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PreMotionEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * AutoAnchor — charges and detonates respawn anchors.
 *
 * All packet-sending actions are queued and executed one-per-tick to avoid
 * Grim's PacketOrderE (slot switch during interaction) and MultiPlace
 * (multiple placements in one tick).
 */
public class AutoAnchorExploder extends Module {

    private final BooleanSetting autoCharge  = addSetting(new BooleanSetting("Auto Charge", true).group("General"));
    private final BooleanSetting autoDetonate = addSetting(new BooleanSetting("Auto Detonate", true).group("General"));
    private final BooleanSetting requireHold = addSetting(new BooleanSetting("Require Hold", false).group("General"));
    private final SliderSetting  chargeDelay = addSetting(new SliderSetting("Charge Delay", 100, 0, 500, 10).group("Timing"));
    private final BooleanSetting switchItems = addSetting(new BooleanSetting("Auto Switch Items", true).group("Switching"));
    private final SliderSetting  minCharges  = addSetting(new SliderSetting("Min Charges", 1, 1, 4, 1).group("Charges"));
    private final SliderSetting  maxCharges  = addSetting(new SliderSetting("Max Charges", 4, 1, 4, 1).group("Charges"));

    private boolean wasHolding = false;
    private long lastChargeTime = 0;
    private BlockPos currentAnchorPos = null;
    private boolean isCharging = false;
    private Integer preSwitchHotbarSlot = null;

    /** One action per tick queue. Each entry is a Runnable that sends exactly one packet. */
    private final ArrayDeque<Runnable> actionQueue = new ArrayDeque<>();

    private final Consumer<PreMotionEvent> preMotionHandler = e -> onPreMotion(e.client());

    public AutoAnchorExploder() {
        super("AutoAnchor", "Automatically charges and detonates respawn anchors", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override public void onEnable() { super.onEnable(); resetState(); }
    @Override public void onDisable() { super.onDisable(); resetState(); }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(PreMotionEvent.class, 20, preMotionHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(PreMotionEvent.class, preMotionHandler);
    }

    private void resetState() {
        wasHolding = false;
        currentAnchorPos = null;
        isCharging = false;
        lastChargeTime = 0;
        preSwitchHotbarSlot = null;
        actionQueue.clear();
    }

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gm = client.gameMode;
        Level world = client.level;
        if (player == null || gm == null || world == null) return;

        // Execute one queued action per tick (avoids PacketOrderE + MultiPlace)
        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        boolean active = !requireHold.getValue() || isHoldingRelevant(player);
        if (!active) {
            if (wasHolding) resetState();
            wasHolding = false;
            return;
        }
        wasHolding = true;

        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) client.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) return;

        handleAnchor(client, player, gm, pos, state, hit);
    }

    private void handleAnchor(Minecraft client, LocalPlayer player, MultiPlayerGameMode gm,
                              BlockPos pos, BlockState state, BlockHitResult hit) {
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        int min = (int) Math.round(minCharges.getValue());
        int max = (int) Math.round(maxCharges.getValue());
        long delay = (long) Math.round(chargeDelay.getValue());

        // ── DETONATE ─────────────────────────────────────────────────────────
        if (autoDetonate.getValue() && currentAnchorPos != null
            && currentAnchorPos.equals(pos) && charges >= min && charges <= max) {
            isCharging = false;
            currentAnchorPos = null;

            // Queue: SafeAnchor shield → slot restore → detonate
            // Each step is a separate tick
            SafeAnchor safeAnchor = getSafeAnchor();
            if (safeAnchor != null && safeAnchor.isEnabled()) {
                boolean shieldQueued = safeAnchor.placeShield(pos);
                if (shieldQueued) {
                    // SafeAnchor may have queued a slot switch this tick.
                    // Its placement happens next tick (deferred).
                    // We queue our detonate actions after that.
                    // Tick N: SafeAnchor slot switch (already happened)
                    // Tick N+1: SafeAnchor placement (its pending)
                    // Tick N+2: our slot restore (queued below)
                    // Tick N+3: our detonate (queued below)
                    queueDetonation(player, gm, pos, hit);
                    return;
                }
            }

            // No shield — ensure we're NOT holding glowstone, then detonate
            ensureNotHoldingGlowstone(player);
            if (player.getMainHandItem().is(Items.GLOWSTONE)) {
                // Still glowstone (no other slot to switch to) — queue restore + detonate
                if (preSwitchHotbarSlot != null) {
                    final int restoreSlot = preSwitchHotbarSlot;
                    preSwitchHotbarSlot = null;
                    actionQueue.add(() -> player.getInventory().setSelectedSlot(restoreSlot));
                    actionQueue.add(() -> gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos)));
                }
                // else: can't detonate without a non-glowstone slot
            } else {
                gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
            }
            return;
        }

        // ── CHARGING ─────────────────────────────────────────────────────────
        if (autoCharge.getValue() && charges < max) {
            if (hasGlowstone(player) && (currentAnchorPos == null || !currentAnchorPos.equals(pos))) {
                currentAnchorPos = pos;
                isCharging = true;
            }

            if (isCharging && currentAnchorPos.equals(pos)) {
                if (System.currentTimeMillis() - lastChargeTime >= delay) {
                    // Split slot switch and charge into separate ticks
                    InteractionHand hand = getGlowstoneHand(player);
                    if (hand == null && switchItems.getValue()) {
                        int slot = findGlowstoneSlot(player);
                        if (slot != -1) {
                            if (preSwitchHotbarSlot == null) {
                                int cur = player.getInventory().getSelectedSlot();
                                if (cur != slot) preSwitchHotbarSlot = cur;
                            }
                            // Tick N: switch slot
                            player.getInventory().setSelectedSlot(slot);
                            // Tick N+1: charge
                            actionQueue.add(() -> gm.useItemOn(player, InteractionHand.MAIN_HAND,
                                new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false)));
                            lastChargeTime = System.currentTimeMillis();
                            return;
                        }
                    }
                    if (hand != null || player.getMainHandItem().is(Items.GLOWSTONE)) {
                        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
                        gm.useItemOn(player, useHand,
                            new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false));
                        lastChargeTime = System.currentTimeMillis();
                    }
                }
            }
        }

        if (isCharging && currentAnchorPos.equals(pos) && charges >= max) {
            isCharging = false;
            currentAnchorPos = null;
        }
    }

    /**
     * Queue the detonation sequence after SafeAnchor has placed its shield.
     * SafeAnchor's deferred placement will execute on the next tick,
     * so we queue our actions after that.
     */
    private void queueDetonation(LocalPlayer player, MultiPlayerGameMode gm,
                                 BlockPos pos, BlockHitResult hit) {
        // Tick +1: SafeAnchor's deferred placement runs (handled by SafeAnchor)
        // Tick +2: empty tick (let server process the placement)
        actionQueue.add(() -> {}); // spacer

        // Tick +3: ALWAYS restore slot to non-glowstone before detonating
        // Detonating with glowstone in hand charges the anchor instead!
        actionQueue.add(() -> {
            ensureNotHoldingGlowstone(player);
            if (preSwitchHotbarSlot != null) {
                player.getInventory().setSelectedSlot(preSwitchHotbarSlot);
                preSwitchHotbarSlot = null;
            }
        });

        // Tick +4: detonate (now guaranteed to not be holding glowstone)
        actionQueue.add(() -> {
            // Final safety check — if somehow still holding glowstone, don't detonate
            if (!player.getMainHandItem().is(Items.GLOWSTONE)) {
                gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
            }
        });
    }

    /** Build a BlockHitResult that directly targets the anchor block. */
    private static BlockHitResult anchorHit(BlockPos pos) {
        return new BlockHitResult(
            new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            Direction.UP, pos, false);
    }

    /**
     * If the player is holding glowstone, switch to the saved slot or find any
     * non-glowstone hotbar slot. MUST be called before detonation to prevent
     * accidentally charging the anchor.
     */
    private void ensureNotHoldingGlowstone(LocalPlayer player) {
        if (!player.getMainHandItem().is(Items.GLOWSTONE)) return;

        // Try saved slot first
        if (preSwitchHotbarSlot != null && preSwitchHotbarSlot >= 0 && preSwitchHotbarSlot <= 8) {
            player.getInventory().setSelectedSlot(preSwitchHotbarSlot);
            preSwitchHotbarSlot = null;
            return;
        }

        // Find any non-glowstone slot
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getItem(i).is(Items.GLOWSTONE)) {
                player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private SafeAnchor getSafeAnchor() {
        return manager == null ? null : manager.getModule(SafeAnchor.class);
    }

    private static boolean isHoldingRelevant(LocalPlayer p) {
        return p.getMainHandItem().is(Items.RESPAWN_ANCHOR) || p.getOffhandItem().is(Items.RESPAWN_ANCHOR)
            || p.getMainHandItem().is(Items.GLOWSTONE) || p.getOffhandItem().is(Items.GLOWSTONE);
    }

    private static boolean hasGlowstone(LocalPlayer p) {
        if (p.getOffhandItem().is(Items.GLOWSTONE)) return true;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.GLOWSTONE)) return true;
        return false;
    }

    private static InteractionHand getGlowstoneHand(LocalPlayer p) {
        if (p.getMainHandItem().is(Items.GLOWSTONE)) return InteractionHand.MAIN_HAND;
        if (p.getOffhandItem().is(Items.GLOWSTONE)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static int findGlowstoneSlot(LocalPlayer p) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(Items.GLOWSTONE)) return i;
        return -1;
    }
}
