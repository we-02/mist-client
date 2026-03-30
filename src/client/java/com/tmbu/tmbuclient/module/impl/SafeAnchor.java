package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PreMotionEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import com.tmbu.tmbuclient.utils.DamageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
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
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * SafeAnchor — places a glowstone block between you and a charged respawn anchor
 * before detonation to reduce self-damage.
 *
 * Edge cases handled:
 * - Validates placement target exists BEFORE switching slots
 * - Restores slot immediately if deferred placement fails
 * - Checks reach distance to placement target
 * - Re-validates everything on the deferred tick
 */
public class SafeAnchor extends Module {

    private final SliderSetting  damageThreshold = addSetting(new SliderSetting("Damage Threshold", 4.0, 0.0, 20.0, 0.5)
        .group("Safety"));
    private final BooleanSetting onlyWhenHolding = addSetting(new BooleanSetting("Only When Holding", true)
        .group("Safety"));
    private final BooleanSetting autoSwitch      = addSetting(new BooleanSetting("Auto Switch", true)
        .group("Switching"));
    private final BooleanSetting switchBack      = addSetting(new BooleanSetting("Switch Back", true)
        .group("Switching").visibleWhen(autoSwitch::getValue));
    private final BooleanSetting debug           = addSetting(new BooleanSetting("Debug", false));

    private static final double MAX_REACH_SQ = 4.5 * 4.5; // vanilla interaction range squared

    private int preSwitchSlot = -1;
    private BlockPos pendingPlaceShield = null;
    private BlockPos pendingPlaceAnchor = null;

    private final Consumer<PreMotionEvent> preMotionHandler = e -> onPreMotion(e.client());

    public SafeAnchor() {
        super("SafeAnchor", "Places glowstone between you and anchors to reduce explosion damage",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(PreMotionEvent.class, 10, preMotionHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(PreMotionEvent.class, preMotionHandler);
    }

    @Override
    public void onEnable() {
        preSwitchSlot = -1;
        pendingPlaceShield = null;
        pendingPlaceAnchor = null;
    }

    @Override
    public void onDisable() {
        restoreSlot();
        pendingPlaceShield = null;
        pendingPlaceAnchor = null;
    }

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gameMode = client.gameMode;
        Level level = client.level;
        if (player == null || gameMode == null || level == null) return;

        // Execute deferred placement from previous tick's slot switch
        if (pendingPlaceShield != null) {
            BlockPos shield = pendingPlaceShield;
            BlockPos anchor = pendingPlaceAnchor;
            pendingPlaceShield = null;
            pendingPlaceAnchor = null;

            // Re-validate everything — world state may have changed since last tick
            boolean placed = false;
            if (level.getBlockState(shield).canBeReplaced()) {
                placed = executePlacement(player, gameMode, level, shield, anchor);
            } else {
                dbg("Deferred placement: shield pos no longer replaceable");
            }

            if (!placed) {
                // Placement failed — restore slot immediately so we don't
                // accidentally charge the anchor with glowstone
                dbg("Deferred placement failed, restoring slot");
                restoreSlot();
            }
            return;
        }

        // Restore slot from previous tick
        restoreSlot();

        // Standalone mode
        if (onlyWhenHolding.getValue() && !isHoldingRelevant(player)) return;

        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult hit = (BlockHitResult) client.hitResult;
        BlockPos anchorPos = hit.getBlockPos();
        BlockState state = level.getBlockState(anchorPos);

        if (!state.is(Blocks.RESPAWN_ANCHOR)) return;
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charges <= 0) return;

        float selfDamage = DamageUtils.anchorDamage(player, Vec3.atCenterOf(anchorPos));
        if (selfDamage >= damageThreshold.getValue()) {
            placeShield(anchorPos);
        }
    }

    /**
     * Called by AutoAnchor before detonation.
     * @return true if a shield will be placed (caller should wait)
     */
    public boolean placeShield(BlockPos anchorPos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        Level level = mc.level;
        if (player == null || gameMode == null || level == null) return false;

        if (hasAdjacentGlowstone(level, anchorPos)) {
            dbg("Already has glowstone neighbor");
            return false;
        }

        // Don't bother if the anchor is floating (air below) — placement will fail
        if (level.getBlockState(anchorPos.below()).isAir()) {
            dbg("Anchor has air below, skipping shield");
            return false;
        }

        // Skip if the player's body is blocking the shield position
        // (player standing between anchor and shield spot prevents placement)
        if (isPlayerBlockingShield(player, anchorPos, level)) {
            dbg("Player is blocking shield position, skipping");
            return false;
        }

        float selfDamage = DamageUtils.anchorDamage(player, Vec3.atCenterOf(anchorPos));
        if (selfDamage < damageThreshold.getValue()) {
            dbg("Damage %.1f below threshold", selfDamage);
            return false;
        }

        // Find shield position
        BlockPos shieldPos = findShieldPosition(level, player, anchorPos);
        if (shieldPos == null) { dbg("No valid shield position"); return false; }
        if (!level.getBlockState(shieldPos).canBeReplaced()) { dbg("Shield pos occupied"); return false; }

        // CRITICAL: Validate placement target exists BEFORE switching slots
        // This prevents the edge case where we switch to glowstone but can't place
        BlockHitResult placeHit = findPlaceTarget(level, shieldPos, anchorPos, player);
        if (placeHit == null) {
            dbg("No valid placement target — skipping shield to avoid accidental charge");
            return false;
        }

        // Already holding glowstone — place immediately
        InteractionHand hand = getGlowstoneHand(player);
        if (hand != null) {
            gameMode.useItemOn(player, hand, placeHit);
            dbg("Placed shield at %s (immediate)", shieldPos);
            return true;
        }

        // Need slot switch — validate we have glowstone first
        if (!autoSwitch.getValue()) return false;
        int slot = findGlowstoneSlot(player);
        if (slot == -1) { dbg("No glowstone in hotbar"); return false; }

        // Switch and defer placement
        if (switchBack.getValue()) preSwitchSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        dbg("Switched to slot %d, deferring placement", slot);

        pendingPlaceShield = shieldPos;
        pendingPlaceAnchor = anchorPos;
        return true;
    }

    private boolean executePlacement(LocalPlayer player, MultiPlayerGameMode gameMode,
                                     Level level, BlockPos shieldPos, BlockPos anchorPos) {
        InteractionHand hand = getGlowstoneHand(player);
        if (hand == null) { dbg("No glowstone in hand"); return false; }

        // Re-validate placement target (world may have changed)
        BlockHitResult placeHit = findPlaceTarget(level, shieldPos, anchorPos, player);
        if (placeHit == null) { dbg("No valid placement target on deferred tick"); return false; }

        gameMode.useItemOn(player, hand, placeHit);
        dbg("Placed shield at %s (deferred)", shieldPos);
        return true;
    }

    /**
     * Find a solid block adjacent to shieldPos (NOT the anchor) to click against.
     * Also checks that the click target is within reach distance.
     */
    private BlockHitResult findPlaceTarget(Level level, BlockPos shieldPos, BlockPos anchorPos, LocalPlayer player) {
        Vec3 eyePos = player.getEyePosition();

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = shieldPos.relative(dir);
            if (neighbor.equals(anchorPos)) continue;
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir() || neighborState.canBeReplaced()) continue;

            // Check reach
            Vec3 cursor = computeFaceCursor(neighbor, dir.getOpposite());
            if (eyePos.distanceToSqr(cursor) > MAX_REACH_SQ) continue;

            return new BlockHitResult(cursor, dir.getOpposite(), neighbor, false);
        }

        // Fallback: ground below
        BlockPos below = shieldPos.below();
        if (!below.equals(anchorPos) && !level.getBlockState(below).isAir()
            && !level.getBlockState(below).canBeReplaced()) {
            Vec3 cursor = computeFaceCursor(below, Direction.UP);
            if (eyePos.distanceToSqr(cursor) <= MAX_REACH_SQ) {
                return new BlockHitResult(cursor, Direction.UP, below, false);
            }
        }
        return null;
    }

    private static Vec3 computeFaceCursor(BlockPos blockPos, Direction face) {
        return new Vec3(
            blockPos.getX() + 0.5 + face.getStepX() * 0.5,
            blockPos.getY() + 0.5 + face.getStepY() * 0.5,
            blockPos.getZ() + 0.5 + face.getStepZ() * 0.5
        );
    }

    private void restoreSlot() {
        if (preSwitchSlot != -1) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.getInventory().setSelectedSlot(preSwitchSlot);
            preSwitchSlot = -1;
        }
    }

    private BlockPos findShieldPosition(Level level, LocalPlayer player, BlockPos anchorPos) {
        Vec3 playerEyes = player.getEyePosition();
        Vec3 anchorCenter = Vec3.atCenterOf(anchorPos);
        double dx = playerEyes.x - anchorCenter.x;
        double dz = playerEyes.z - anchorCenter.z;
        boolean isDiagonal = Math.abs(Math.abs(dx) - Math.abs(dz)) < Math.max(Math.abs(dx), Math.abs(dz)) * 0.6;

        BlockPos[] cardinals = { anchorPos.north(), anchorPos.south(), anchorPos.east(), anchorPos.west() };
        BlockPos[] diagonals = {
            anchorPos.offset(1, 0, 1), anchorPos.offset(1, 0, -1),
            anchorPos.offset(-1, 0, 1), anchorPos.offset(-1, 0, -1)
        };

        BlockPos[] primary   = isDiagonal ? diagonals : cardinals;
        BlockPos[] secondary = isDiagonal ? cardinals : diagonals;

        // Only consider positions that have a valid placement target
        // This prevents finding a shield position we can't actually place at
        BlockPos best = pickPlaceable(level, playerEyes, primary, anchorPos, player);
        return best != null ? best : pickPlaceable(level, playerEyes, secondary, anchorPos, player);
    }

    /**
     * Pick the closest candidate that is replaceable AND has a valid placement target.
     */
    private BlockPos pickPlaceable(Level level, Vec3 eyes, BlockPos[] candidates,
                                   BlockPos anchorPos, LocalPlayer player) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos c : candidates) {
            if (!level.getBlockState(c).canBeReplaced()) continue;
            // Must have a solid neighbor (not the anchor) to click against, within reach
            if (findPlaceTarget(level, c, anchorPos, player) == null) continue;
            double d = eyes.distanceToSqr(Vec3.atCenterOf(c));
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private static boolean hasAdjacentGlowstone(Level level, BlockPos pos) {
        for (Direction dir : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST })
            if (level.getBlockState(pos.relative(dir)).is(Blocks.GLOWSTONE)) return true;
        for (int[] d : new int[][]{ {1,0,1}, {1,0,-1}, {-1,0,1}, {-1,0,-1} })
            if (level.getBlockState(pos.offset(d[0], d[1], d[2])).is(Blocks.GLOWSTONE)) return true;
        return false;
    }

    private static boolean isPlayerBlockingShield(LocalPlayer player, BlockPos anchorPos, Level level) {
        net.minecraft.world.phys.AABB playerBox = player.getBoundingBox();

        // Check all horizontal candidates (cardinal + diagonal)
        BlockPos[] candidates = {
            anchorPos.north(), anchorPos.south(), anchorPos.east(), anchorPos.west(),
            anchorPos.offset(1, 0, 1), anchorPos.offset(1, 0, -1),
            anchorPos.offset(-1, 0, 1), anchorPos.offset(-1, 0, -1)
        };

        // Count how many valid positions the player is blocking
        int validCount = 0;
        int blockedCount = 0;
        for (BlockPos c : candidates) {
            if (!level.getBlockState(c).canBeReplaced()) continue;
            validCount++;
            net.minecraft.world.phys.AABB blockBox = new net.minecraft.world.phys.AABB(c);
            if (playerBox.intersects(blockBox)) blockedCount++;
        }

        // If the player is blocking ALL valid positions, skip
        return validCount > 0 && blockedCount >= validCount;
    }

    private static boolean isHoldingRelevant(LocalPlayer p) {
        return p.getMainHandItem().is(Items.RESPAWN_ANCHOR) || p.getOffhandItem().is(Items.RESPAWN_ANCHOR)
            || p.getMainHandItem().is(Items.GLOWSTONE) || p.getOffhandItem().is(Items.GLOWSTONE);
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

    private void dbg(String fmt, Object... args) {
        if (debug.getValue()) System.out.println("[SafeAnchor] " + String.format(fmt, args));
    }
}
