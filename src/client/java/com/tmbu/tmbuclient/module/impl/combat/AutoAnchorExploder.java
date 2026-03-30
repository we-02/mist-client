package com.tmbu.tmbuclient.module.impl.combat;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PreMotionEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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
 * Timing safety for Grim:
 * - One action per tick via action queue (avoids PacketOrderE + MultiPlace)
 * - Global interaction cooldown prevents back-to-back useItemOn calls
 * - Checks SafeAnchor.isBusy() before detonating
 * - Clears stale queue when switching to a different anchor
 * - Validates block state before every interaction
 */
public class AutoAnchorExploder extends Module {

    private final BooleanSetting autoCharge  = addSetting(new BooleanSetting("Auto Charge", true).group("General"));
    private final BooleanSetting autoDetonate = addSetting(new BooleanSetting("Auto Detonate", true).group("General"));
    private final BooleanSetting packetDetonate = addSetting(new BooleanSetting("Packet Detonate", false).group("General"));
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

    /**
     * Global interaction cooldown — ticks since last useItemOn call.
     * Prevents sending two block interactions too close together which
     * triggers MultiPlace even across charge→detonate transitions.
     */
    private int ticksSinceLastInteraction = 0;
    private static final int MIN_INTERACTION_GAP = 2;

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
        ticksSinceLastInteraction = MIN_INTERACTION_GAP;
    }

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gm = client.gameMode;
        Level world = client.level;
        if (player == null || gm == null || world == null) return;

        ticksSinceLastInteraction++;

        // Wait for SafeAnchor to finish its placement sequence
        SafeAnchor safeAnchor = getSafeAnchor();
        if (safeAnchor != null && safeAnchor.isEnabled() && safeAnchor.isBusy()) {
            return;
        }

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

        // ── Packet detonate: try to detonate stored anchor without looking at it ──
        if (packetDetonate.getValue() && autoDetonate.getValue() && currentAnchorPos != null && !isCharging) {
            BlockState storedState = world.getBlockState(currentAnchorPos);
            if (storedState.is(Blocks.RESPAWN_ANCHOR)) {
                int charges = storedState.getValue(RespawnAnchorBlock.CHARGE);
                int min = (int) Math.round(minCharges.getValue());
                int max = (int) Math.round(maxCharges.getValue());
                if (charges >= min && charges <= max && ticksSinceLastInteraction >= MIN_INTERACTION_GAP) {
                    BlockPos detonatePos = currentAnchorPos;
                    isCharging = false;
                    currentAnchorPos = null;

                    SafeAnchor sa = getSafeAnchor();
                    if (sa != null && sa.isEnabled()) {
                        boolean shieldQueued = sa.placeShield(detonatePos);
                        if (shieldQueued) {
                            queueDetonation(player, gm, detonatePos);
                            return;
                        }
                    }

                    ensureNotHoldingGlowstone(player);
                    if (!player.getMainHandItem().is(Items.GLOWSTONE)) {
                        packetDetonateAnchor(player, gm, detonatePos);
                    }
                    return;
                }
            } else {
                // Anchor was broken/removed
                currentAnchorPos = null;
            }
        }

        // ── Normal flow: requires looking at an anchor ──
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) client.hitResult;
        BlockPos pos = hit.getBlockPos();

        // Re-read block state fresh — don't rely on stale data
        BlockState state = world.getBlockState(pos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) return;

        // If we switched to a different anchor, clear any stale queue
        if (currentAnchorPos != null && !currentAnchorPos.equals(pos) && !actionQueue.isEmpty()) {
            actionQueue.clear();
            isCharging = false;
        }

        handleAnchor(client, player, gm, world, pos, state, hit);
    }

    private void handleAnchor(Minecraft client, LocalPlayer player, MultiPlayerGameMode gm,
                              Level world, BlockPos pos, BlockState state, BlockHitResult hit) {
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        int min = (int) Math.round(minCharges.getValue());
        int max = (int) Math.round(maxCharges.getValue());
        long delay = (long) Math.round(chargeDelay.getValue());

        // ── DETONATE ─────────────────────────────────────────────────────────
        if (autoDetonate.getValue() && currentAnchorPos != null
            && currentAnchorPos.equals(pos) && charges >= min && charges <= max
            && !isCharging) {

            // Don't detonate too quickly after the last interaction (charge/shield)
            if (ticksSinceLastInteraction < MIN_INTERACTION_GAP) return;

            isCharging = false;
            currentAnchorPos = null;

            // Try SafeAnchor shield first
            SafeAnchor safeAnchor = getSafeAnchor();
            if (safeAnchor != null && safeAnchor.isEnabled()) {
                boolean shieldQueued = safeAnchor.placeShield(pos);
                if (shieldQueued) {
                    queueDetonation(player, gm, pos);
                    return;
                }
            }

            // No shield — ensure we're NOT holding glowstone, then detonate
            if (player.getMainHandItem().is(Items.GLOWSTONE)) {
                // Need to switch away from glowstone first (separate tick)
                ensureNotHoldingGlowstone(player);
                if (player.getMainHandItem().is(Items.GLOWSTONE)) {
                    // All slots are glowstone — can't detonate
                    return;
                }
                // Switched slot this tick — queue detonate for next tick
                actionQueue.add(() -> {
                    if (!player.getMainHandItem().is(Items.GLOWSTONE)) {
                        if (packetDetonate.getValue()) {
                            packetDetonateAnchor(player, gm, pos);
                        } else {
                            gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
                            ticksSinceLastInteraction = 0;
                        }
                    }
                });
            } else {
                if (packetDetonate.getValue()) {
                    packetDetonateAnchor(player, gm, pos);
                } else {
                    gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
                    ticksSinceLastInteraction = 0;
                }
            }
            return;
        }

        // ── CHARGING ─────────────────────────────────────────────────────────
        if (autoCharge.getValue() && charges < max) {
            if (hasGlowstone(player) && (currentAnchorPos == null || !currentAnchorPos.equals(pos))) {
                currentAnchorPos = pos;
                isCharging = true;
            }

            if (isCharging && currentAnchorPos != null && currentAnchorPos.equals(pos)) {
                if (System.currentTimeMillis() - lastChargeTime >= delay
                    && ticksSinceLastInteraction >= MIN_INTERACTION_GAP) {

                    InteractionHand hand = getGlowstoneHand(player);
                    if (hand == null && switchItems.getValue()) {
                        int slot = findGlowstoneSlot(player);
                        if (slot != -1) {
                            if (preSwitchHotbarSlot == null) {
                                int cur = player.getInventory().getSelectedSlot();
                                if (cur != slot) preSwitchHotbarSlot = cur;
                            }
                            // Tick N: switch slot (no interaction packet)
                            player.getInventory().setSelectedSlot(slot);
                            // Tick N+1: charge (one interaction packet)
                            final BlockPos chargePos = pos;
                            actionQueue.add(() -> {
                                // Re-validate: is it still an anchor? Still needs charges?
                                BlockState freshState = player.level().getBlockState(chargePos);
                                if (freshState.is(Blocks.RESPAWN_ANCHOR)
                                    && freshState.getValue(RespawnAnchorBlock.CHARGE) < max) {
                                    gm.useItemOn(player, InteractionHand.MAIN_HAND,
                                        new BlockHitResult(hit.getLocation(), hit.getDirection(), chargePos, false));
                                    ticksSinceLastInteraction = 0;
                                }
                            });
                            lastChargeTime = System.currentTimeMillis();
                            return;
                        }
                    }
                    if (hand != null || player.getMainHandItem().is(Items.GLOWSTONE)) {
                        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
                        gm.useItemOn(player, useHand,
                            new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false));
                        lastChargeTime = System.currentTimeMillis();
                        ticksSinceLastInteraction = 0;
                    }
                }
            }
        }

        // Check if charging is complete
        if (isCharging && currentAnchorPos != null && currentAnchorPos.equals(pos) && charges >= max) {
            isCharging = false;
            // Don't clear currentAnchorPos — we need it for the detonate check
        }
    }

    /**
     * Queue the detonation sequence after SafeAnchor has placed its shield.
     * SafeAnchor's deferred placement will execute on the next tick,
     * so we queue our actions after that with proper spacing.
     */
    private void queueDetonation(LocalPlayer player, MultiPlayerGameMode gm, BlockPos pos) {
        // Tick +1: SafeAnchor's deferred placement runs (handled by SafeAnchor)
        // We wait by checking isBusy() in onPreMotion — but also add spacers
        // in case the busy check doesn't catch it.
        actionQueue.add(() -> {}); // spacer — let SafeAnchor finish

        // Tick +2: restore slot to non-glowstone
        actionQueue.add(() -> {
            ensureNotHoldingGlowstone(player);
            if (preSwitchHotbarSlot != null) {
                player.getInventory().setSelectedSlot(preSwitchHotbarSlot);
                preSwitchHotbarSlot = null;
            }
        });

        // Tick +3: detonate (guaranteed not holding glowstone)
        actionQueue.add(() -> {
            if (!player.getMainHandItem().is(Items.GLOWSTONE)) {
                // Re-validate: is the anchor still charged?
                BlockState freshState = player.level().getBlockState(pos);
                if (freshState.is(Blocks.RESPAWN_ANCHOR)
                    && freshState.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                    if (packetDetonate.getValue()) {
                        packetDetonateAnchor(player, gm, pos);
                    } else {
                        gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
                        ticksSinceLastInteraction = 0;
                    }
                }
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
     * Detonate an anchor using packet rotation — sends a server-side rotation
     * toward the anchor, interacts, then restores the original rotation.
     * This allows detonation without physically looking at the anchor.
     * The camera never moves visually since it all happens in one tick.
     */
    private void packetDetonateAnchor(LocalPlayer player, MultiPlayerGameMode gm, BlockPos pos) {
        if (player.getMainHandItem().is(Items.GLOWSTONE)) return;

        Vec3 anchorCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 eyes = player.getEyePosition();
        double dx = anchorCenter.x - eyes.x;
        double dy = anchorCenter.y - eyes.y;
        double dz = anchorCenter.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));

        float origYaw = player.getYRot();
        float origPitch = player.getXRot();

        // Send rotation toward anchor
        Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(
            yaw, pitch, player.onGround(), player.horizontalCollision));

        // Interact with anchor
        gm.useItemOn(player, InteractionHand.MAIN_HAND, anchorHit(pos));
        ticksSinceLastInteraction = 0;

        // Restore original rotation
        Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(
            origYaw, origPitch, player.onGround(), player.horizontalCollision));
    }

    /**
     * If the player is holding glowstone, switch to the saved slot or find any
     * non-glowstone hotbar slot. MUST be called before detonation.
     */
    private void ensureNotHoldingGlowstone(LocalPlayer player) {
        if (!player.getMainHandItem().is(Items.GLOWSTONE)) return;

        if (preSwitchHotbarSlot != null && preSwitchHotbarSlot >= 0 && preSwitchHotbarSlot <= 8) {
            player.getInventory().setSelectedSlot(preSwitchHotbarSlot);
            preSwitchHotbarSlot = null;
            return;
        }

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
