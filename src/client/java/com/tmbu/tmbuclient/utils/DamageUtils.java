package com.tmbu.tmbuclient.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
//import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class DamageUtils {
    private DamageUtils() {}

    // -------------------------------------------------------------------------
    // Public API used by AutoDoubleHand
    // -------------------------------------------------------------------------

    public static float crystalDamage(LivingEntity target, Vec3 crystalPos) {
        return explosionDamage(target, crystalPos, 12f, HIT_FACTORY);
    }

    public static float anchorDamage(LivingEntity target, Vec3 anchorPos) {
        // For anchors we treat the anchor block itself as air (it doesn't block explosion rays)
        BlockPos anchorBlock = BlockPos.containing(anchorPos);
        RaycastFactory factory = (level, start, end) -> {
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) return null;
            // If we hit the anchor block itself, treat it as no hit
            if (hit.getBlockPos().equals(anchorBlock)) return null;
            // Otherwise apply blast resistance filter
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.getBlock().getExplosionResistance() < 600) return null;
            return hit;
        };
        return explosionDamage(target, anchorPos, 10f, factory);
    }

    // -------------------------------------------------------------------------
    // Explosion damage calculation core
    // -------------------------------------------------------------------------

    private static float explosionDamage(LivingEntity target, Vec3 explosionPos,
                                         float power, RaycastFactory raycastFactory) {
        if (target == null) return 0f;
        if (target instanceof Player player && player.isCreative()) return 0f;

        Vec3 targetPos = target.position();
        AABB targetBox = target.getBoundingBox();

        double distance = Math.sqrt(distanceSq(targetPos.x, targetPos.y, targetPos.z,
                explosionPos.x, explosionPos.y, explosionPos.z));
        if (distance > power) return 0f;

        float exposure = getExposure(target.level(), explosionPos, targetBox, raycastFactory);
        double impact = (1.0 - (distance / power)) * exposure;
        float rawDamage = (float) ((int) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0));

        return calculateReductions(rawDamage, target, target.damageSources().explosion(null, null));
    }

    // -------------------------------------------------------------------------
    // Exposure calculation (copied from vanilla explosion logic)
    // -------------------------------------------------------------------------

    private static float getExposure(Level level, Vec3 source, AABB box,
                                     RaycastFactory raycastFactory) {
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1.0 / (xDiff * 2.0 + 1.0);
        double yStep = 1.0 / (yDiff * 2.0 + 1.0);
        double zStep = 1.0 / (zDiff * 2.0 + 1.0);

        if (xStep <= 0 || yStep <= 0 || zStep <= 0) return 0f;

        int hits = 0;
        int misses = 0;

        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) * 0.5;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) * 0.5;

        xStep *= xDiff;
        yStep *= yDiff;
        zStep *= zDiff;

        double startX = box.minX + xOffset;
        double startY = box.minY;
        double startZ = box.minZ + zOffset;
        double endX = box.maxX + xOffset;
        double endY = box.maxY;
        double endZ = box.maxZ + zOffset;

        for (double x = startX; x <= endX; x += xStep) {
            for (double y = startY; y <= endY; y += yStep) {
                for (double z = startZ; z <= endZ; z += zStep) {
                    Vec3 start = new Vec3(x, y, z);
                    BlockHitResult result = raycastFactory.apply(level, start, source);
                    if (result == null) misses++;
                    hits++;
                }
            }
        }

        return (float) misses / hits;
    }

    // -------------------------------------------------------------------------
    // Damage reduction (armor, enchantments, effects, difficulty)
    // -------------------------------------------------------------------------

    private static float calculateReductions(float damage, LivingEntity entity, DamageSource source) {
        // Difficulty scaling (explosion damage is scaled by difficulty)
        switch (entity.level().getDifficulty()) {
            case PEACEFUL:
                return 0f; // No damage in peaceful
            case EASY:
                damage = Math.min(damage / 2.0f + 1.0f, damage);
                break;
            case HARD:
                damage *= 1.5f;
                break;
            default: // NORMAL
                break;
        }

        // Armor reduction (assume explosion damage does not bypass armor)
        float armor = entity.getArmorValue();
        float toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, source, armor, toughness);

        // Resistance effect
        MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int level = resistance.getAmplifier() + 1;
            damage *= (1.0f - (level * 0.2f));
        }

        // Protection enchantments are ignored (overestimates damage – safe for totem switching)

        return Math.max(damage, 0);
    }

    // -------------------------------------------------------------------------
    // Raycast factory interface and default implementation
    // -------------------------------------------------------------------------

    @FunctionalInterface
    public interface RaycastFactory {
        BlockHitResult apply(Level level, Vec3 start, Vec3 end);
    }

    /**
     * Default raycast that returns null if the block's blast resistance is < 600.
     */
    public static final RaycastFactory HIT_FACTORY = (level, start, end) -> {
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return null;
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.getBlock().getExplosionResistance() < 600) return null;
        return hit;
    };

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    private static double distanceSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}