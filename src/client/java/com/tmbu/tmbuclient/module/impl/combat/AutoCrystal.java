package com.tmbu.tmbuclient.module.impl.combat;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tmbu.tmbuclient.event.EventBus;
import com.tmbu.tmbuclient.event.events.PostKeybindsEvent;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.ModeSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import com.tmbu.tmbuclient.utils.CrystalUtils;
import com.tmbu.tmbuclient.utils.DamageUtils;
import com.tmbu.tmbuclient.utils.TimerUtils;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * AutoCrystal — rewritten from scratch.
 *
 * Place logic:  Scans valid obsidian/bedrock bases within range that the player
 *               is looking near, picks the one that deals the most damage to the
 *               nearest enemy while keeping self-damage below a threshold, then
 *               places a crystal on it.
 *
 * Break logic:  Scans all end crystals within range, picks the one that deals
 *               the most damage to the nearest enemy (again checking self-safety),
 *               and attacks it.
 *
 * Both place and break run on independent timers with independent jitter.
 */
public class AutoCrystal extends Module {

    // ── General ──────────────────────────────────────────────────────────────
    private final BooleanSetting holdToUse       = addSetting(new BooleanSetting("Hold To Use", false).group("General"));
    private final ModeSetting    mode            = addSetting(new ModeSetting("Mode", "Both",
                                                       new String[]{"Place", "Break", "Both"}).group("General"));
    private final SliderSetting  range           = addSetting(new SliderSetting("Range", 4.5, 3.0, 6.0, 0.1).group("General"));

    // ── Timing ───────────────────────────────────────────────────────────────
    private final SliderSetting  placeDelay      = addSetting(new SliderSetting("Place Delay", 80, 0, 500, 5).group("Timing"));
    private final SliderSetting  breakDelay      = addSetting(new SliderSetting("Break Delay", 50, 0, 500, 5).group("Timing"));
    private final SliderSetting  jitter          = addSetting(new SliderSetting("Jitter", 20, 0, 100, 5).group("Timing"));

    // ── Safety ───────────────────────────────────────────────────────────────
    private final SliderSetting  maxSelfDamage   = addSetting(new SliderSetting("Max Self Damage", 8.0, 0.0, 36.0, 0.5).group("Safety"));
    private final SliderSetting  minEnemyDamage  = addSetting(new SliderSetting("Min Enemy Damage", 4.0, 0.0, 36.0, 0.5).group("Safety"));
    private final BooleanSetting antiSuicide     = addSetting(new BooleanSetting("Anti Suicide", true).group("Safety"));
    private final BooleanSetting efficient       = addSetting(new BooleanSetting("Efficient", true).group("Safety"));
    private final BooleanSetting friendProtect   = addSetting(new BooleanSetting("Friend Protection", false).group("Safety"));
    private final SliderSetting  maxFriendDamage = addSetting(new SliderSetting("Max Friend Damage", 4.0, 0.0, 20.0, 0.5).group("Safety").visibleWhen(friendProtect::getValue));

    // ── Targeting ────────────────────────────────────────────────────────────
    private final BooleanSetting targetPlayers   = addSetting(new BooleanSetting("Target Players", true).group("Targeting"));
    private final BooleanSetting targetHostiles  = addSetting(new BooleanSetting("Target Hostiles", false).group("Targeting"));
    private final SliderSetting  targetRange     = addSetting(new SliderSetting("Target Range", 12.0, 3.0, 32.0, 0.5).group("Targeting"));
    private final BooleanSetting targetAboveBase = addSetting(new BooleanSetting("Target Above Base", false).group("Targeting"));

    // ── Switching ────────────────────────────────────────────────────────────
    private final BooleanSetting autoSwitch      = addSetting(new BooleanSetting("Auto Switch", true).group("Switching"));
    private final BooleanSetting switchBack      = addSetting(new BooleanSetting("Switch Back", true).group("Switching"));
    private final SliderSetting  switchBackDelay = addSetting(new SliderSetting("Switch Back Ticks", 2, 1, 10, 1).group("Switching").visibleWhen(switchBack::getValue));

    // ── Inhibitors ───────────────────────────────────────────────────────────
    private final BooleanSetting stopOnShield    = addSetting(new BooleanSetting("Stop On Shield", true).group("Inhibitors"));
    private final BooleanSetting stopOnAnchor    = addSetting(new BooleanSetting("Stop On Anchor", true).group("Inhibitors"));
    private final BooleanSetting noBreakOnSword  = addSetting(new BooleanSetting("No Break On Sword", false).group("Inhibitors"));
    private final BooleanSetting requireTotemOff = addSetting(new BooleanSetting("Require Totem Offhand", false).group("Inhibitors"));
    private final BooleanSetting onlyOnGround    = addSetting(new BooleanSetting("Only On Ground", false).group("Inhibitors"));

    // ── ESP ──────────────────────────────────────────────────────────────────
    private final BooleanSetting baseESP         = addSetting(new BooleanSetting("Base ESP", true).group("ESP"));
    private final ColorSetting   espColor        = addSetting(new ColorSetting("ESP Color", 0x60FF5000).group("ESP").visibleWhen(baseESP::getValue));
    private final BooleanSetting espThroughWalls = addSetting(new BooleanSetting("ESP Through Walls", true).group("ESP").visibleWhen(baseESP::getValue));
    public enum EspAnim { NONE, FADE, PULSE, GROW, SHRINK, SLIDE_UP, BOUNCE, SPIN, BREATHE, WAVE, FLASH }
    private final EnumSetting<EspAnim> espAnimation = addSetting(new EnumSetting<>("ESP Animation", EspAnim.FADE).group("ESP").visibleWhen(baseESP::getValue));
    private final SliderSetting  espAnimSpeed    = addSetting(new SliderSetting("ESP Anim Speed", 2.0, 0.5, 10.0, 0.5).group("ESP").visibleWhen(baseESP::getValue));

    // ── Debug ────────────────────────────────────────────────────────────────
    private final BooleanSetting debug           = addSetting(new BooleanSetting("Debug", false));

    // ── Internal state ───────────────────────────────────────────────────────
    private final TimerUtils placeTimer = new TimerUtils();
    private final TimerUtils breakTimer = new TimerUtils();
    private final Random     rng        = new Random();

    private int  pendingSwitchBackSlot = -1;
    private int  switchBackCountdown   = 0;
    private long lastPlaceMs           = 0;

    /** Simple LRU damage cache — avoids recalculating the same entity+position combo every tick. */
    private final java.util.LinkedHashMap<Long, Float> damageCache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Float> eldest) { return size() > 64; }
    };

    /** Last valid placement position for preview rendering. */
    private volatile BlockPos lastPlacePreview = null;

    private static final int  BASE_ESP_MAX    = 16;
    private static final long BASE_ESP_TTL_MS = 1200;

    private final Map<BlockPos, Long> activeBases = Collections.synchronizedMap(
        new LinkedHashMap<>(BASE_ESP_MAX, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, Long> eldest) {
                return size() > BASE_ESP_MAX;
            }
        }
    );

    /**
     * Exposed for AutoDoubleHand to check if we're actively placing.
     * True when enabled and we placed within the last placeDelay window.
     */
    public static volatile AutoCrystal activeInstance = null;

    private final Consumer<PostKeybindsEvent> preMotionHandler = e -> onPreMotion(e.client());

    public AutoCrystal() {
        super("AutoCrystal", "Crystal PvP assist — auto place & break with damage calculation",
              Category.COMBAT, GLFW.GLFW_KEY_R);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void registerEvents(EventBus bus) {
        bus.subscribe(PostKeybindsEvent.class, preMotionHandler);
    }

    @Override
    protected void unregisterEvents(EventBus bus) {
        bus.unsubscribe(PostKeybindsEvent.class, preMotionHandler);
    }

    @Override
    public void onEnable() {
        placeTimer.reset();
        breakTimer.reset();
        pendingSwitchBackSlot = -1;
        switchBackCountdown   = 0;
        lastPlaceMs           = 0;
        activeInstance        = this;
        activeBases.clear();
        damageCache.clear();
        lastPlacePreview = null;
    }

    @Override
    public void onDisable() {
        activeInstance = null;
        activeBases.clear();
        damageCache.clear();
        lastPlacePreview = null;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && pendingSwitchBackSlot != -1) {
            client.player.getInventory().setSelectedSlot(pendingSwitchBackSlot);
        }
        pendingSwitchBackSlot = -1;
        switchBackCountdown   = 0;
    }

    /**
     * Returns true if this module is actively placing (for AutoDoubleHand).
     */
    public boolean isActivelyPlacing() {
        return isEnabled() && lastPlaceMs > 0
            && (System.currentTimeMillis() - lastPlaceMs) < placeDelay.getValue().longValue() + 100;
    }

    // ── Main logic (runs on pre-motion) ──────────────────────────────────────

    private void onPreMotion(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || gameMode == null || client.level == null) return;

        // Clear damage cache each tick (positions change)
        damageCache.clear();

        // Handle pending switch-back with a tick delay
        if (pendingSwitchBackSlot != -1) {
            switchBackCountdown--;
            if (switchBackCountdown <= 0) {
                player.getInventory().setSelectedSlot(pendingSwitchBackSlot);
                dbg("Switch back to slot " + pendingSwitchBackSlot);
                pendingSwitchBackSlot = -1;
            }
            return;
        }

        if (holdToUse.getValue() && !isKeyPressed(client, getKeybind())) return;

        if (stopOnShield.getValue() && isHolding(player, Items.SHIELD)) { dbg("Suppressed — shield"); return; }
        if (stopOnAnchor.getValue() && isHolding(player, Items.RESPAWN_ANCHOR)) { dbg("Suppressed — anchor"); return; }
        if (requireTotemOff.getValue() && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) { dbg("Suppressed — no totem offhand"); return; }
        if (onlyOnGround.getValue() && !player.onGround()) { dbg("Suppressed — airborne"); return; }

        List<Entity> targets = findTargets(player);
        if (targets.isEmpty()) { dbg("No targets in range"); return; }

        String m = mode.getMode();
        boolean canBreak = "Break".equals(m) || "Both".equals(m);
        boolean canPlace = "Place".equals(m) || "Both".equals(m);

        // ── Break: only the crystal the player is actually looking at ────────
        // Only one action per tick to avoid MultiActionsF and PacketOrderI.
        if (canBreak) {
            long breakJitter = (long) (rng.nextDouble() * jitter.getValue());
            if (breakTimer.hasTimeElapsed(Math.round(breakDelay.getValue()) + breakJitter, false)) {
                if (!(noBreakOnSword.getValue() && isSword(player))) {
                    EndCrystal looked = getLookedCrystal(client, player);
                    if (looked != null && isCrystalWorthBreaking(player, looked, targets)) {
                        gameMode.attack(player, looked);
                        player.swing(InteractionHand.MAIN_HAND);
                        breakTimer.reset();
                        dbg("Break crystal at " + fmtPos(looked.position()));
                        return; // ONE action per tick — don't also place
                    }
                }
            }
        }

        // ── Place: only on the block the player is actually looking at ───────
        // Uses client.hitResult which is the vanilla ray trace result, so it
        // passes PositionPlace (face is visible) and Reach (within range).
        if (canPlace) {
            long placeJitter = (long) (rng.nextDouble() * jitter.getValue());
            if (placeTimer.hasTimeElapsed(Math.round(placeDelay.getValue()) + placeJitter, false)) {
                PlaceResult best = getLookedPlacement(client, player, targets);
                if (best != null) {
                    InteractionHand hand = getCrystalHand(player);
                    int originalSlot = player.getInventory().getSelectedSlot();

                    if (hand == null) {
                        if (!autoSwitch.getValue()) { dbg("No crystals, auto-switch off"); return; }
                        if (isProtectedItem(player.getMainHandItem())) { dbg("Auto-switch blocked"); return; }
                        int slot = findHotbarSlot(player, Items.END_CRYSTAL);
                        if (slot == -1) { dbg("No crystals in hotbar"); return; }
                        player.getInventory().setSelectedSlot(slot);
                        hand = InteractionHand.MAIN_HAND;
                    }

                    gameMode.useItemOn(player, hand, best.hit);
                    placeTimer.reset();
                    lastPlaceMs = System.currentTimeMillis();
                    dbg("Place on " + best.base + " (enemy dmg: %.1f, self dmg: %.1f)", best.enemyDamage, best.selfDamage);

                    if (baseESP.getValue()) activeBases.put(best.base, System.currentTimeMillis());

                    if (switchBack.getValue() && player.getInventory().getSelectedSlot() != originalSlot) {
                        pendingSwitchBackSlot = originalSlot;
                        switchBackCountdown = switchBackDelay.getValue().intValue();
                    }
                }
            }
        }
    }


    // ── Target finding ───────────────────────────────────────────────────────

    private List<Entity> findTargets(LocalPlayer self) {
        Level level = self.level();
        double r = targetRange.getValue();
        AABB box = self.getBoundingBox().inflate(r);
        List<Entity> result = new ArrayList<>();
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
            if (e == self) continue;
            if (targetPlayers.getValue() && e instanceof Player p && !p.isSpectator()) {
                result.add(e);
            } else if (targetHostiles.getValue() && e instanceof Enemy) {
                result.add(e);
            }
        }
        // Sort by distance — closest first
        result.sort(Comparator.comparingDouble(self::distanceToSqr));
        return result;
    }

    // ── Break logic (legit: only what the crosshair is on) ─────────────────

    /**
     * Get the end crystal the player is currently looking at via vanilla ray trace.
     * Returns null if not looking at a crystal or it's out of range.
     */
    private EndCrystal getLookedCrystal(Minecraft client, LocalPlayer player) {
        if (client.hitResult == null || client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) return null;
        if (!(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult eHit)) return null;
        if (!(eHit.getEntity() instanceof EndCrystal crystal)) return null;
        if (player.distanceToSqr(crystal) > range.getValue() * range.getValue()) return null;
        return crystal;
    }

    /**
     * Check if breaking this crystal passes our safety/damage filters.
     */
    private boolean isCrystalWorthBreaking(LocalPlayer player, EndCrystal crystal, List<Entity> targets) {
        Vec3 crystalPos = crystal.position();

        if (targetAboveBase.getValue()) {
            BlockPos base = crystal.blockPosition().below();
            if (!isEnemyAtOrAbove(player.level(), player, base.getY() + 1, targets)) return false;
        }

        float selfDmg = getCachedDamage(player, crystalPos);
        if (selfDmg > maxSelfDamage.getValue()) return false;
        if (antiSuicide.getValue() && selfDmg >= player.getHealth() + player.getAbsorptionAmount()) return false;

        float bestEnemyDmg = 0;
        for (Entity target : targets) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            float dmg = getCachedDamage(living, crystalPos);
            if (dmg > bestEnemyDmg) bestEnemyDmg = dmg;
        }
        if (bestEnemyDmg < minEnemyDamage.getValue()) return false;

        // Efficiency: don't waste crystals if self-damage >= enemy damage
        if (efficient.getValue() && selfDmg >= bestEnemyDmg) return false;

        // Friend protection
        if (friendProtect.getValue() && wouldHurtFriends(player, crystalPos)) return false;

        return true;
    }

    // ── Place logic (legit: only the block the crosshair is on) ─────────────

    private record PlaceResult(BlockPos base, BlockHitResult hit, float enemyDamage, float selfDamage) {}

    /**
     * Check if the block the player is looking at is a valid crystal base,
     * and if placing a crystal there would deal enough damage.
     * Uses client.hitResult directly — the vanilla ray trace — so the face,
     * cursor position, and reach are all valid.
     */
    private PlaceResult getLookedPlacement(Minecraft client, LocalPlayer player, List<Entity> targets) {
        if (client.hitResult == null || client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        if (!(client.hitResult instanceof BlockHitResult bhr)) return null;

        BlockPos base = bhr.getBlockPos();
        Level level = player.level();

        // Must be a valid crystal base
        if (!CrystalUtils.canPlaceCrystalClient(level, base)) return null;

        Vec3 crystalSpawn = Vec3.atBottomCenterOf(base.above());

        // Range check (vanilla interaction range is ~4.5)
        if (player.getEyePosition().distanceToSqr(crystalSpawn) > range.getValue() * range.getValue()) return null;

        if (targetAboveBase.getValue()) {
            if (!isEnemyAtOrAbove(level, player, base.getY() + 1, targets)) return null;
        }

        float selfDmg = getCachedDamage(player, crystalSpawn);
        if (selfDmg > maxSelfDamage.getValue()) { lastPlacePreview = null; return null; }
        if (antiSuicide.getValue() && selfDmg >= player.getHealth() + player.getAbsorptionAmount()) { lastPlacePreview = null; return null; }

        float bestEnemyDmg = 0;
        for (Entity target : targets) {
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            float dmg = getCachedDamage(living, crystalSpawn);
            if (dmg > bestEnemyDmg) bestEnemyDmg = dmg;
        }
        if (bestEnemyDmg < minEnemyDamage.getValue()) { lastPlacePreview = null; return null; }

        // Efficiency check
        if (efficient.getValue() && selfDmg >= bestEnemyDmg) { lastPlacePreview = null; return null; }

        // Friend protection
        if (friendProtect.getValue() && wouldHurtFriends(player, crystalSpawn)) { lastPlacePreview = null; return null; }

        // Update placement preview
        lastPlacePreview = base;

        // Use the vanilla hit result directly — cursor, face, and block are all legit
        return new PlaceResult(base, bhr, bestEnemyDmg, selfDmg);
    }

    // ── ESP rendering (fill-only, no outlines) ──────────────────────────────

    @Override
    public void onWorldRender(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        PoseStack matrices = context.matrices();
        if (matrices == null) return;

        Vec3 cam = client.gameRenderer.getMainCamera().position();
        long now = System.currentTimeMillis();

        if (espThroughWalls.getValue()) GL11.glDisable(GL11.GL_DEPTH_TEST);

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(net.minecraft.client.renderer.rendertype.RenderType.BIG_BUFFER_SIZE)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            PoseStack.Pose pose = matrices.last();

            // ── Placement preview (subtle white outline on the looked-at valid base) ──
            BlockPos preview = lastPlacePreview;
            if (baseESP.getValue() && preview != null) {
                VertexConsumer previewLines = bufferSource.getBuffer(RenderTypes.LINES);
                float px = (float)(preview.getX() - cam.x), py = (float)(preview.getY() - cam.y), pz = (float)(preview.getZ() - cam.z);
                renderBoxOutline(previewLines, pose, px, py, pz, px + 1, py + 1, pz + 1, 255, 255, 255, 80, 1.0f);
                bufferSource.endBatch(RenderTypes.LINES);
            }

            // ── Active base ESP ──────────────────────────────────────────────
            if (baseESP.getValue() && !activeBases.isEmpty()) {
                int color = espColor.getColor();
                int cr = (color >> 16) & 0xFF;
                int cg = (color >> 8)  & 0xFF;
                int cb =  color        & 0xFF;
                int baseAlpha = (color >> 24) & 0xFF;

                synchronized (activeBases) {
                    activeBases.entrySet().removeIf(e -> now - e.getValue() > BASE_ESP_TTL_MS);
                for (Map.Entry<BlockPos, Long> entry : activeBases.entrySet()) {
                    BlockPos base = entry.getKey();
                    long age = now - entry.getValue();
                    float progress = (float) age / BASE_ESP_TTL_MS; // 0→1 over lifetime
                    float animSpeed = espAnimSpeed.getValue().floatValue();

                    // Compute alpha and box modifications based on animation
                    float alpha = 1.0f;
                    float yOffset = 0;
                    float boxScale = 1.0f;

                    switch (espAnimation.getValue()) {
                        case FADE -> alpha = 1.0f - progress;
                        case PULSE -> alpha = (float)(0.4 + 0.6 * (0.5 + 0.5 * Math.sin(age / 1000.0 * animSpeed * Math.PI * 2)));
                        case GROW -> boxScale = 0.3f + 0.7f * Math.min(1.0f, progress * animSpeed);
                        case SHRINK -> boxScale = Math.max(0.01f, 1.0f - progress * animSpeed * 0.5f);
                        case SLIDE_UP -> yOffset = progress * animSpeed * 0.5f;
                        case BOUNCE -> {
                            // Bounces up and down with decreasing amplitude
                            float bounce = (float)(Math.abs(Math.sin(age / 1000.0 * animSpeed * Math.PI * 3)) * (1.0 - progress));
                            yOffset = bounce * 0.5f;
                        }
                        case SPIN -> {
                            // Box rotates by scaling X/Z with sin/cos (creates a spinning diamond effect)
                            float angle = (float)(age / 1000.0 * animSpeed * Math.PI * 2);
                            float s = (float)(0.7 + 0.3 * Math.abs(Math.sin(angle)));
                            boxScale = s;
                        }
                        case BREATHE -> {
                            // Smooth scale oscillation like breathing
                            float breath = (float)(0.85 + 0.15 * Math.sin(age / 1000.0 * animSpeed * Math.PI));
                            boxScale = breath;
                            alpha = (float)(0.6 + 0.4 * Math.sin(age / 1000.0 * animSpeed * Math.PI));
                        }
                        case WAVE -> {
                            // Rises in a wave pattern
                            yOffset = (float)(Math.sin(age / 1000.0 * animSpeed * Math.PI * 2) * 0.3 * (1.0 - progress));
                            boxScale = (float)(0.9 + 0.1 * Math.cos(age / 1000.0 * animSpeed * Math.PI * 2));
                        }
                        case FLASH -> {
                            // Rapid on/off flashing that slows down over time
                            float freq = animSpeed * 10 * (1.0f - progress * 0.8f);
                            alpha = (float)(Math.sin(age / 1000.0 * freq * Math.PI) > 0 ? 1.0 : 0.2);
                        }
                        case NONE -> {}
                    }

                    // Fade out in the last 30% regardless of animation
                    if (progress > 0.7f) {
                        alpha *= (1.0f - progress) / 0.3f;
                    }

                    int fa = Math.max(1, (int)(baseAlpha * Math.max(0, Math.min(1, alpha))));

                    // Build the box
                    double bx = base.getX() - cam.x;
                    double by = base.getY() - cam.y + yOffset;
                    double bz = base.getZ() - cam.z;

                    // Scale around center
                    double cx = bx + 0.5, cy = by + 0.5, cz = bz + 0.5;
                    double half = 0.5 * boxScale;

                    float x0 = (float)(cx - half), y0 = (float)(cy - half), z0 = (float)(cz - half);
                    float x1 = (float)(cx + half), y1 = (float)(cy + half), z1 = (float)(cz + half);

                    // Render filled box (6 faces, no outlines)
                    VertexConsumer tris = bufferSource.getBuffer(RenderTypes.debugFilledBox());
                    quad(tris, pose, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, cr,cg,cb,fa); // bottom
                    quad(tris, pose, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0, cr,cg,cb,fa); // top
                    quad(tris, pose, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, cr,cg,cb,fa); // north
                    quad(tris, pose, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, cr,cg,cb,fa); // south
                    quad(tris, pose, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, cr,cg,cb,fa); // west
                    quad(tris, pose, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, cr,cg,cb,fa); // east
                    bufferSource.endBatch(RenderTypes.debugFilledBox());
                }
            }
            } // end if baseESP && activeBases
        }

        if (espThroughWalls.getValue()) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private static void renderBoxOutline(VertexConsumer c, PoseStack.Pose pose,
                                         float x0, float y0, float z0, float x1, float y1, float z1,
                                         int r, int g, int b, int a, float w) {
        c.addVertex(pose, x0,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z0).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x1,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y0,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
        c.addVertex(pose, x0,y1,z1).setColor(r,g,b,a).setNormal(pose,0,1,0).setLineWidth(w);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             int r, int g, int b, int a) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }

    // ── Damage helpers ──────────────────────────────────────────────────────

    /** Cached crystal damage calculation. Key = entity ID XOR position hash. */
    private float getCachedDamage(net.minecraft.world.entity.LivingEntity entity, Vec3 crystalPos) {
        long key = ((long) entity.getId() << 32) ^ (Double.doubleToLongBits(crystalPos.x) * 31
            + Double.doubleToLongBits(crystalPos.y) * 17 + Double.doubleToLongBits(crystalPos.z));
        Float cached = damageCache.get(key);
        if (cached != null) return cached;
        float dmg = DamageUtils.crystalDamage(entity, crystalPos);
        damageCache.put(key, dmg);
        return dmg;
    }

    /** Check if any nearby non-target players would take too much damage. */
    private boolean wouldHurtFriends(LocalPlayer self, Vec3 crystalPos) {
        float maxFriend = maxFriendDamage.getValue().floatValue();
        for (Player p : self.level().players()) {
            if (p == self || p.isSpectator()) continue;
            // Skip entities that are targets (enemies)
            if (p.distanceTo(self) > 12) continue;
            float dmg = getCachedDamage(p, crystalPos);
            if (dmg > maxFriend) return true;
        }
        return false;
    }

    // ── Utility methods ──────────────────────────────────────────────────────

    /**
     * Returns true if any target entity has its feet at or above the given Y level,
     * OR if the target is airborne (not on ground). Airborne targets are valid
     * because they'll fall onto the crystal's explosion area.
     */
    private static boolean isEnemyAtOrAbove(Level level, LocalPlayer self, double minY, List<Entity> targets) {
        for (Entity target : targets) {
            if (target.getBoundingBox().minY >= minY) return true;
            if (!target.onGround()) return true;
        }
        return false;
    }

    private static boolean isKeyPressed(Minecraft client, int key) {
        return key >= 0 && GLFW.glfwGetKey(client.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }

    private static InteractionHand getCrystalHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.END_CRYSTAL)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.END_CRYSTAL))  return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean isHolding(LocalPlayer player, net.minecraft.world.item.Item item) {
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    private static boolean isSword(LocalPlayer player) {
        return player.getMainHandItem().is(ItemTags.SWORDS);
    }

    private static boolean isProtectedItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SHIELD) || stack.is(Items.RESPAWN_ANCHOR) || stack.is(ItemTags.SWORDS);
    }

    private static int findHotbarSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void dbg(String msg, Object... args) {
        if (debug.getValue()) System.out.println("[AutoCrystal] " + (args.length > 0 ? String.format(msg, args) : msg));
    }

    private static String fmtPos(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
