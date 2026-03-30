package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

/**
 * AimAssist — smoothly pulls your crosshair toward nearby entities.
 * Looks completely legit — no snapping, no impossible rotations.
 * Works by adding a small rotation delta each tick toward the target.
 */
public class AimAssist extends Module {

    // ── Targeting ────────────────────────────────────────────────────────────
    private final SliderSetting  range        = addSetting(new SliderSetting("Range", 4.2, 1.0, 8.0, 0.1).group("Targeting"));
    private final SliderSetting  fov          = addSetting(new SliderSetting("FOV", 60.0, 10.0, 180.0, 5.0).group("Targeting"));
    private final BooleanSetting targetPlayers = addSetting(new BooleanSetting("Players", true).group("Targeting"));
    private final BooleanSetting targetHostiles = addSetting(new BooleanSetting("Hostiles", false).group("Targeting"));
    private final BooleanSetting invisibles   = addSetting(new BooleanSetting("Invisibles", false).group("Targeting"));

    public enum Priority { CLOSEST, CROSSHAIR, HEALTH }
    private final EnumSetting<Priority> priority = addSetting(new EnumSetting<>("Priority", Priority.CROSSHAIR).group("Targeting"));

    // ── Smoothing ────────────────────────────────────────────────────────────
    public enum SmoothMode { LINEAR, INTERPOLATION, SIGMOID }
    private final EnumSetting<SmoothMode> smoothMode = addSetting(new EnumSetting<>("Smooth Mode", SmoothMode.INTERPOLATION).group("Smoothing"));
    private final SliderSetting  speed       = addSetting(new SliderSetting("Speed", 3.0, 0.5, 20.0, 0.5).group("Smoothing"));
    private final SliderSetting  yawSpeed    = addSetting(new SliderSetting("Yaw Speed", 1.0, 0.1, 3.0, 0.1).group("Smoothing"));
    private final SliderSetting  pitchSpeed  = addSetting(new SliderSetting("Pitch Speed", 1.0, 0.1, 3.0, 0.1).group("Smoothing"));

    // ── Axis ─────────────────────────────────────────────────────────────────
    private final BooleanSetting horizontal  = addSetting(new BooleanSetting("Horizontal", true).group("Axis"));
    private final BooleanSetting vertical    = addSetting(new BooleanSetting("Vertical", true).group("Axis"));

    // ── Conditions ───────────────────────────────────────────────────────────
    private final BooleanSetting onlyOnClick = addSetting(new BooleanSetting("Only On Click", false).group("Conditions"));
    private final BooleanSetting stopOnShield = addSetting(new BooleanSetting("Stop On Shield", false).group("Conditions"));
    private final BooleanSetting requireWeapon = addSetting(new BooleanSetting("Require Weapon", false).group("Conditions"));

    public AimAssist() {
        super("AimAssist", "Smoothly pulls crosshair toward nearby entities",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        if (client.screen != null) return; // don't aim while in menus

        // Conditions
        if (onlyOnClick.getValue()) {
            if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
                return;
        }
        if (stopOnShield.getValue() && (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem
            || player.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem)) return;
        if (requireWeapon.getValue() && !isHoldingWeapon(player)) return;

        // Find target
        Entity target = findTarget(player);
        if (target == null) return;

        // Calculate target rotation
        Vec3 eyes = player.getEyePosition();
        Vec3 targetPos = getAimPoint(target);
        double dx = targetPos.x - eyes.x;
        double dy = targetPos.y - eyes.y;
        double dz = targetPos.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));

        // Current rotation
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        // Calculate deltas (wrapped to -180..180)
        float deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        // Apply smoothing
        float smoothedYaw = smooth(deltaYaw, speed.getValue().floatValue() * yawSpeed.getValue().floatValue());
        float smoothedPitch = smooth(deltaPitch, speed.getValue().floatValue() * pitchSpeed.getValue().floatValue());

        // Apply axis filters
        if (horizontal.getValue()) player.setYRot(currentYaw + smoothedYaw);
        if (vertical.getValue()) player.setXRot(Mth.clamp(currentPitch + smoothedPitch, -90, 90));
    }

    private float smooth(float delta, float spd) {
        if (Math.abs(delta) < 0.1f) return 0; // dead zone to prevent jitter

        return switch (smoothMode.getValue()) {
            case LINEAR -> {
                // Constant speed, capped at the remaining delta
                float step = Math.min(Math.abs(delta), spd);
                yield Math.signum(delta) * step;
            }
            case INTERPOLATION -> {
                // Lerp: move a fraction of the remaining distance each tick
                yield delta * (spd * 0.05f);
            }
            case SIGMOID -> {
                // S-curve: slow start, fast middle, slow end
                float normalized = Math.abs(delta) / 180.0f; // 0-1
                float sigmoid = (float)(1.0 / (1.0 + Math.exp(-12.0 * (normalized - 0.3))));
                float step = sigmoid * spd;
                yield Math.signum(delta) * Math.min(step, Math.abs(delta));
            }
        };
    }

    private Entity findTarget(LocalPlayer player) {
        double r = range.getValue();
        AABB searchBox = player.getBoundingBox().inflate(r);
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxFov = fov.getValue();

        List<Entity> candidates = player.level().getEntitiesOfClass(Entity.class, searchBox, e -> {
            if (e == player || !e.isAlive()) return false;
            if (!invisibles.getValue() && e.isInvisible()) return false;
            if (e instanceof Player p && p.isSpectator()) return false;

            boolean isPlayer = e instanceof Player;
            boolean isHostile = e instanceof Enemy;
            if (isPlayer && !targetPlayers.getValue()) return false;
            if (isHostile && !targetHostiles.getValue()) return false;
            if (!isPlayer && !isHostile) return false;

            // FOV check
            Vec3 toEntity = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eyes).normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(toEntity)))));
            return angle <= maxFov * 0.5;
        });

        if (candidates.isEmpty()) return null;

        return switch (priority.getValue()) {
            case CLOSEST -> candidates.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
            case CROSSHAIR -> candidates.stream()
                .min(Comparator.comparingDouble(e -> {
                    Vec3 toE = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eyes).normalize();
                    return Math.acos(Math.max(-1, Math.min(1, look.dot(toE))));
                })).orElse(null);
            case HEALTH -> candidates.stream()
                .filter(e -> e instanceof LivingEntity)
                .min(Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth()))
                .orElse(null);
        };
    }

    /** Aim at the center of the target's body (not feet, not head top). */
    private Vec3 getAimPoint(Entity target) {
        return target.position().add(0, target.getBbHeight() * 0.45, 0);
    }

    private static boolean isHoldingWeapon(LocalPlayer player) {
        var stack = player.getMainHandItem();
        return stack.is(net.minecraft.tags.ItemTags.SWORDS)
            || stack.getItem() instanceof net.minecraft.world.item.AxeItem
            || stack.getItem() instanceof net.minecraft.world.item.TridentItem;
    }
}
