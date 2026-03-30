package com.tmbu.tmbuclient.module.impl.combat;

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
 * Timing safety:
 * - Cooldown between shield placements prevents MultiPlace flags
 * - Slot switch + placement always split across two ticks (PacketOrderE safe)
 * - Busy flag prevents AutoAnchor from detonating mid-placement
 * - Re-validates world state on deferred tick to handle fast block updates
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

    private static final double MAX_REACH_SQ = 4.5 * 4.5;
    /** Minimum ticks between shield placements to avoid MultiPlace. */
    private static final int PLACE_COOLDOWN_TICKS = 2;

    private int preSwitchSlot = -1;
    private BlockPos pendingPlaceShield = null;
    private BlockPos pendingPlaceAnchor = null;
    /** Ticks since last shield placement — prevents rapid-fire placements. */
    private int ticksSinceLastPlace = PLACE_COOLDOWN_TICKS;
    /** True while we're in the middle of a slot-switch → deferred-place sequence. */
    private boolean busy = false;

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
        ticksSinceLastPlace = PLACE_COOLDOWN_TICKS;
        busy = false;
    }

    @Override
    public void onDisable() {
        restoreSlot();
        pendingPlaceShield = null;
        pendingPlaceAnchor = null;
        busy = false;
    }

    /**
     * Returns true if SafeAnchor is in the middle of a placement sequence.
     * AutoAnchor should NOT detonate while this is true.
     */
    public boolean isBusy() {
        return busy;
    }

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gameMode = client.gameMode;
        Level level = client.level;
        if (player == null || gameMode == null || level == null) return;

        ticksSinceLastPlace++;

        // Execute deferred placement from previous tick's slot switch
        if (pendingPlaceShield != null) {
            BlockPos shield = pendingPlaceShield;
            BlockPos anchor = pendingPlaceAnchor;
            pendingPlaceShield = null;
            pendingPlaceAnchor = null;

            boolean placed = false;
            if (level.getBlockState(shield).canBeReplaced()) {
                placed = executePlacement(player, gameMode, level, shield, anchor);
            } else {
                dbg("Deferred placement: shield pos no longer replaceable");
            }

            if (!placed) {
                dbg("Deferred placement failed, restoring slot");
                restoreSlot();
            }
            // Mark not busy — AutoAnchor can proceed next tick
            busy = false;
            return;
        }

        // Restore slot from previous tick (only if not busy)
        if (!busy) {
            restoreSlot();
        }

        // Standalone mode — only trigger if not called by AutoAnchor
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
     * @return true if a shield will be placed (caller should wait for isBusy() == false)
     */
    public boolean placeShield(BlockPos anchorPos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        Level level = mc.level;
        if (player == null || gameMode == null || level == null) return false;

        // Don't place if we're already busy with a previous placement
        if (busy) {
            dbg("Already busy with a placement");
            return true; // tell caller to wait
        }

        // Cooldown — prevent rapid-fire placements (MultiPlace)
        if (ticksSinceLastPlace < PLACE_COOLDOWN_TICKS) {
            dbg("Placement cooldown (%d/%d)", ticksSinceLastPlace, PLACE_COOLDOWN_TICKS);
            return false;
        }

        if (hasAdjacentGlowstone(level, anchorPos)) {
            dbg("Already has glowstone neighbor");
            return false;
        }

        if (level.getBlockState(anchorPos.below()).isAir()) {
            dbg("Anchor has air below, skipping shield");
            return false;
        }

        float selfDamage = DamageUtils.anchorDamage(player, Vec3.atCenterOf(anchorPos));
        if (selfDamage < damageThreshold.getValue()) {
            dbg("Damage %.1f below threshold", selfDamage);
            return false;
        }

        BlockPos shieldPos = findShieldPosition(level, player, anchorPos);
        if (shieldPos == null) { dbg("No valid shield position"); return false; }
        if (!level.getBlockState(shieldPos).canBeReplaced()) { dbg("Shield pos occupied"); return false; }

        BlockHitResult placeHit = findPlaceTarget(level, shieldPos, anchorPos, player);
        if (placeHit == null) {
            dbg("No valid placement target — skipping shield");
            return false;
        }

        // Already holding glowstone — place immediately (one action this tick)
        // Must sneak to prevent charging the anchor when clicking near it
        InteractionHand hand = getGlowstoneHand(player);
        if (hand != null) {
            boolean wasSneaking = player.isShiftKeyDown();
            player.setShiftKeyDown(true);
            gameMode.useItemOn(player, hand, placeHit);
            player.setShiftKeyDown(wasSneaking);
            ticksSinceLastPlace = 0;
            dbg("Placed shield at %s (immediate)", shieldPos);
            return true;
        }

        // Need slot switch — split across two ticks
        if (!autoSwitch.getValue()) return false;
        int slot = findGlowstoneSlot(player);
        if (slot == -1) { dbg("No glowstone in hotbar"); return false; }

        if (switchBack.getValue()) preSwitchSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        dbg("Switched to slot %d, deferring placement", slot);

        pendingPlaceShield = shieldPos;
        pendingPlaceAnchor = anchorPos;
        busy = true;
        return true;
    }

    private boolean executePlacement(LocalPlayer player, MultiPlayerGameMode gameMode,
                                     Level level, BlockPos shieldPos, BlockPos anchorPos) {
        InteractionHand hand = getGlowstoneHand(player);
        if (hand == null) { dbg("No glowstone in hand"); return false; }

        BlockHitResult placeHit = findPlaceTarget(level, shieldPos, anchorPos, player);
        if (placeHit == null) { dbg("No valid placement target on deferred tick"); return false; }

        boolean wasSneaking2 = player.isShiftKeyDown();
        player.setShiftKeyDown(true);
        gameMode.useItemOn(player, hand, placeHit);
        player.setShiftKeyDown(wasSneaking2);
        ticksSinceLastPlace = 0;
        dbg("Placed shield at %s (deferred)", shieldPos);
        return true;
    }

    private BlockHitResult findPlaceTarget(Level level, BlockPos shieldPos, BlockPos anchorPos, LocalPlayer player) {
        Vec3 eyePos = player.getEyePosition();

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = shieldPos.relative(dir);
            if (neighbor.equals(anchorPos)) continue;
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir() || neighborState.canBeReplaced()) continue;

            Vec3 cursor = computeFaceCursor(neighbor, dir.getOpposite());
            if (eyePos.distanceToSqr(cursor) > MAX_REACH_SQ) continue;

            return new BlockHitResult(cursor, dir.getOpposite(), neighbor, false);
        }

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

        // Candidates between player and anchor (blocks explosion rays toward player)
        BlockPos[] cardinals = { anchorPos.north(), anchorPos.south(), anchorPos.east(), anchorPos.west() };
        BlockPos[] diagonals = {
            anchorPos.offset(1, 0, 1), anchorPos.offset(1, 0, -1),
            anchorPos.offset(-1, 0, 1), anchorPos.offset(-1, 0, -1)
        };

        BlockPos[] primary   = isDiagonal ? diagonals : cardinals;
        BlockPos[] secondary = isDiagonal ? cardinals : diagonals;

        // Sort candidates by how well they block rays from anchor to player
        // (prefer positions that are between the anchor and the player)
        BlockPos best = pickBestShield(level, playerEyes, anchorCenter, primary, anchorPos, player);
        return best != null ? best : pickBestShield(level, playerEyes, anchorCenter, secondary, anchorPos, player);
    }

    private BlockPos pickBestShield(Level level, Vec3 playerEyes, Vec3 anchorCenter,
                                    BlockPos[] candidates, BlockPos anchorPos, LocalPlayer player) {
        BlockPos best = null;
        double bestScore = -1;

        Vec3 dirToPlayer = playerEyes.subtract(anchorCenter).normalize();

        for (BlockPos c : candidates) {
            if (!level.getBlockState(c).canBeReplaced()) continue;
            if (findPlaceTarget(level, c, anchorPos, player) == null) continue;

            // Skip if entity is blocking
            net.minecraft.world.phys.AABB blockBox = new net.minecraft.world.phys.AABB(c);
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, blockBox,
                    e -> e.isAlive() && !e.isSpectator()).isEmpty()) continue;

            // Score: how well does this position block rays from anchor to player?
            // Higher dot product = more directly between anchor and player
            Vec3 dirToCandidate = Vec3.atCenterOf(c).subtract(anchorCenter).normalize();
            double dot = dirToPlayer.dot(dirToCandidate);

            if (dot > bestScore) {
                bestScore = dot;
                best = c;
            }
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
