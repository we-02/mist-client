package com.tmbu.tmbuclient.module.impl.player;

import com.tmbu.tmbuclient.module.impl.combat.AutoCrystal;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ModeSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import com.tmbu.tmbuclient.utils.CrystalUtils;
import com.tmbu.tmbuclient.utils.DamageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class AutoDoubleHand extends Module {
    public enum AnchorMode {
        ALWAYS("Always"),
        CRITICAL("Critical");

        private final String name;
        AnchorMode(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final BooleanSetting stopOnCrystal;
    private final BooleanSetting checkShield;
    private final BooleanSetting onPop;
    private final BooleanSetting onHealth;
    private final BooleanSetting predict;
    private final SliderSetting health;
    private final BooleanSetting onGround;
    private final BooleanSetting checkPlayers;
    private final SliderSetting distance;
    private final BooleanSetting predictCrystals;
    private final BooleanSetting checkAim;
    private final BooleanSetting checkItems;
    private final SliderSetting activatesAbove;
    private final BooleanSetting reduceStrictness;
    private final BooleanSetting includeAnchor;
    private final ModeSetting anchorMode;

    private boolean belowHealth = false;
    private boolean offhandHasNoTotem = false;
    private int previousSlot = -1; // for restoring after totem switch
    private int restoreCountdown = 0;

    public AutoDoubleHand() {
        super("AutoDoubleHand", "Automatically switches to your totem when you're about to pop", Category.COMBAT, -1);

        stopOnCrystal  = addSetting(new BooleanSetting("Stop On Crystal", true).group("General"));
        checkShield    = addSetting(new BooleanSetting("Check Shield", true).group("General"));
        onPop          = addSetting(new BooleanSetting("On Pop", false).group("Triggers"));
        onHealth       = addSetting(new BooleanSetting("On Health", false).group("Triggers"));
        predict        = addSetting(new BooleanSetting("Predict Damage", true).group("Triggers"));
        health         = addSetting(new SliderSetting("Health", 2, 1, 20, 1).group("Triggers").visibleWhen(onHealth::getValue));
        onGround       = addSetting(new BooleanSetting("On Ground", false).group("Conditions"));
        checkPlayers   = addSetting(new BooleanSetting("Check Players", true).group("Conditions"));
        distance       = addSetting(new SliderSetting("Distance", 5, 1, 10, 0.1).group("Conditions").visibleWhen(checkPlayers::getValue));
        predictCrystals = addSetting(new BooleanSetting("Predict Crystals", false).group("Prediction"));
        checkAim       = addSetting(new BooleanSetting("Check Aim", true).group("Prediction").visibleWhen(predictCrystals::getValue));
        checkItems     = addSetting(new BooleanSetting("Check Items", true).group("Prediction").visibleWhen(predictCrystals::getValue));
        activatesAbove = addSetting(new SliderSetting("Activates Above", 0.2, 0, 4, 0.1).group("Conditions"));
        reduceStrictness = addSetting(new BooleanSetting("Reduce Strictness", true).group("General"));
        includeAnchor  = addSetting(new BooleanSetting("Include Anchor", true).group("Anchors"));
        anchorMode     = addSetting(new ModeSetting("Anchor Mode", AnchorMode.CRITICAL.name(),
                new String[]{"ALWAYS", "CRITICAL"}).group("Anchors").visibleWhen(includeAnchor::getValue));
    }

    @Override
    public void onEnable() {
        belowHealth = false;
        offhandHasNoTotem = false;
        previousSlot = -1;
        restoreCountdown = 0;
    }

    private boolean isHoldingShield(LocalPlayer player) {
        return player.getMainHandItem().is(Items.SHIELD)
            || player.getOffhandItem().is(Items.SHIELD);
    }

    /**
     * Returns true if AutoCrystal is actively placing crystals right now.
     */
    private boolean isAutoCrystalPlacing() {
        AutoCrystal ac = AutoCrystal.activeInstance;
        return ac != null && ac.isActivelyPlacing();
    }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        // Restore previous slot after a brief delay (let the danger pass)
        if (restoreCountdown > 0) {
            restoreCountdown--;
            if (restoreCountdown == 0 && previousSlot != -1) {
                // Only restore if we're still on the totem slot and danger has passed
                if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    player.getInventory().setSelectedSlot(previousSlot);
                }
                previousSlot = -1;
            }
        }

        // Don't switch slots while AutoTotemRefill has the inventory locked
        if (manager != null) {
            AutoTotemRefill refill = manager.getModule(AutoTotemRefill.class);
            if (refill != null && refill.isEnabled() && refill.isLocking()) return;
        }

        // Stop while AutoCrystal is actively placing crystals.
        if (stopOnCrystal.getValue() && isAutoCrystalPlacing()) return;

        if (reduceStrictness.getValue() && isElytraEquipped(client)) {
            if (hasTotemInOffhand(client)) return;
        }

        if (checkShield.getValue() && isHoldingShield(player)) return;

        // OnPop: switch if offhand has no totem
        if (onPop.getValue()) {
            if (player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING && !offhandHasNoTotem) {
                offhandHasNoTotem = true;
                int slot = findItemInHotbar(client, Items.TOTEM_OF_UNDYING);
                safeSelectSlot(client, slot);
            }
            if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                offhandHasNoTotem = false;
            }
        }

        // OnHealth: switch when health drops below threshold
        if (onHealth.getValue()) {
            if (player.getHealth() <= health.getValue() && !belowHealth) {
                belowHealth = true;
                int slot = findItemInHotbar(client, Items.TOTEM_OF_UNDYING);
                safeSelectSlot(client, slot);
            }
            if (player.getHealth() > health.getValue()) {
                belowHealth = false;
            }
        }

        if (!predict.getValue()) return;

        double squaredDistance = distance.getValue() * distance.getValue();

        // Charged anchors
        if (includeAnchor.getValue()) {
            List<BlockPos> chargedAnchors = new ArrayList<>();
            BlockPos.betweenClosedStream(
                    player.blockPosition().offset(-6, -6, -6),
                    player.blockPosition().offset(6, 6, 6)
            ).forEach(pos -> {
                BlockState state = client.level.getBlockState(pos);
                if (state.getBlock() == Blocks.RESPAWN_ANCHOR) {
                    Integer charges = state.getValue(RespawnAnchorBlock.CHARGE);
                    if (charges > 0) {
                        chargedAnchors.add(new BlockPos(pos));
                    }
                }
            });

            for (BlockPos anchorPos : chargedAnchors) {
                Vec3 anchorVec = Vec3.atCenterOf(anchorPos);
                float damage = DamageUtils.anchorDamage(player, anchorVec);
                if (damage >= player.getHealth() + player.getAbsorptionAmount()) {
                    int slot = findItemInHotbar(client, Items.TOTEM_OF_UNDYING);
                    String mode = anchorMode.getMode();
                    if ("ALWAYS".equals(mode)) {
                        safeSelectSlot(client, slot);
                        return;
                    } else if ("CRITICAL".equals(mode)) {
                        if (player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                            safeSelectSlot(client, slot);
                            return;
                        }
                    }
                }
            }
        }

        if (onGround.getValue() && !player.onGround()) return;

        if (checkPlayers.getValue()) {
            boolean anyNearby = client.level.players().stream()
                    .filter(e -> e != player)
                    .anyMatch(p -> player.distanceToSqr(p) <= squaredDistance);
            if (!anyNearby) return;
        }

        double above = activatesAbove.getValue();
        for (int i = 1; i <= (int) Math.floor(above); i++) {
            if (!client.level.getBlockState(player.blockPosition().below(i)).isAir())
                return;
        }

        // Get existing crystals
        List<EndCrystal> crystals = nearbyCrystals(client);
        List<Vec3> crystalPositions = new ArrayList<>();
        crystals.forEach(e -> crystalPositions.add(new Vec3(e.getX(), e.getY(), e.getZ())));

        // Predict future crystal placements
        if (predictCrystals.getValue()) {
            Stream<BlockPos> stream = BlockPos.betweenClosedStream(
                    player.blockPosition().offset(-6, -8, -6),
                    player.blockPosition().offset(6, 2, 6)
            ).filter(pos -> {
                BlockState state = client.level.getBlockState(pos);
                return (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) &&
                        CrystalUtils.canPlaceCrystalClient(client.level, pos);
            });

            if (checkAim.getValue()) {
                if (checkItems.getValue()) {
                    stream = stream.filter(pos -> arePeopleAimingAtBlockAndHoldingCrystals(client, pos));
                } else {
                    stream = stream.filter(pos -> arePeopleAimingAtBlock(client, pos));
                }
            }
            stream.forEach(pos -> crystalPositions.add(Vec3.atBottomCenterOf(pos).add(0, 1, 0)));
        }

        // Check damage from each crystal
        for (Vec3 crys : crystalPositions) {
            if (Math.abs(player.getY() - crys.y) > 3.0) continue;
            double damage = DamageUtils.crystalDamage(player, crys);
            if (damage >= player.getHealth() + player.getAbsorptionAmount()) {
                int slot = findItemInHotbar(client, Items.TOTEM_OF_UNDYING);
                safeSelectSlot(client, slot);
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private int findItemInHotbar(Minecraft client, net.minecraft.world.item.Item item) {
        if (client.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void safeSelectSlot(Minecraft client, int slot) {
        if (client.player == null) return;
        if (slot >= 0 && slot <= 8) {
            int current = client.player.getInventory().getSelectedSlot();
            if (current != slot) {
                previousSlot = current;
                restoreCountdown = 10; // restore after 0.5 seconds if danger passes
            }
            client.player.getInventory().setSelectedSlot(slot);
        }
    }

    private boolean isElytraEquipped(Minecraft client) {
        return client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private boolean hasTotemInOffhand(Minecraft client) {
        return client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private List<EndCrystal> nearbyCrystals(Minecraft client) {
        Vec3 pos = client.player.position();
        AABB box = new AABB(pos.x - 6, pos.y - 6, pos.z - 6, pos.x + 6, pos.y + 6, pos.z + 6);
        return client.level.getEntitiesOfClass(EndCrystal.class, box, e -> true);
    }

    private boolean arePeopleAimingAtBlock(Minecraft client, BlockPos block) {
        for (Player player : client.level.players()) {
            if (player == client.player) continue;
            Vec3 eyes = player.getEyePosition();
            Vec3 look = player.getLookAngle().scale(4.5);
            Vec3 end = eyes.add(look);
            BlockHitResult result = client.level.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (result != null && result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(block)) return true;
        }
        return false;
    }

    private boolean arePeopleAimingAtBlockAndHoldingCrystals(Minecraft client, BlockPos block) {
        for (Player player : client.level.players()) {
            if (player == client.player) continue;
            if (!player.isHolding(Items.END_CRYSTAL)) continue;
            Vec3 eyes = player.getEyePosition();
            Vec3 look = player.getLookAngle().scale(4.5);
            Vec3 end = eyes.add(look);
            BlockHitResult result = client.level.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (result != null && result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(block)) return true;
        }
        return false;
    }
}