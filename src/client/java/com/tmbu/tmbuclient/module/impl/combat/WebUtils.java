package com.tmbu.tmbuclient.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Shared web placement logic for SelfWeb and AutoWeb. */
public final class WebUtils {
    private WebUtils() {}

    public static boolean placeWebAt(Minecraft client, LocalPlayer player, MultiPlayerGameMode gm,
                                     Level level, BlockPos pos, boolean doSwitchBack) {
        if (!level.getBlockState(pos).canBeReplaced()) return false;

        // Check for entities blocking the position
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(pos),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.player.Player))
                .isEmpty()) return false;

        // Find cobweb
        int webSlot = -1;
        InteractionHand hand = null;
        if (player.getMainHandItem().is(Items.COBWEB)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().is(Items.COBWEB)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).is(Items.COBWEB)) { webSlot = i; break; }
            }
        }
        if (hand == null && webSlot == -1) return false;

        int originalSlot = player.getInventory().getSelectedSlot();
        if (hand == null) {
            player.getInventory().setSelectedSlot(webSlot);
            hand = InteractionHand.MAIN_HAND;
        }

        BlockHitResult hit = findPlaceTarget(level, pos, player);
        if (hit == null) {
            if (doSwitchBack && player.getInventory().getSelectedSlot() != originalSlot)
                player.getInventory().setSelectedSlot(originalSlot);
            return false;
        }

        // Server rotation toward placement face
        float[] angles = calcAngles(player.getEyePosition(), hit.getLocation());
        float origYaw = player.getYRot();
        float origPitch = player.getXRot();

        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            angles[0], angles[1], player.onGround(), player.horizontalCollision));

        gm.useItemOn(player, hand, hit);

        // Restore rotation
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
            origYaw, origPitch, player.onGround(), player.horizontalCollision));

        if (doSwitchBack && player.getInventory().getSelectedSlot() != originalSlot)
            player.getInventory().setSelectedSlot(originalSlot);

        return true;
    }

    static BlockHitResult findPlaceTarget(Level level, BlockPos target, LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        double maxReachSq = 4.5 * 4.5;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = target.relative(dir);
            BlockState state = level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced()) continue;
            Vec3 cursor = new Vec3(
                neighbor.getX() + 0.5 + dir.getOpposite().getStepX() * 0.5,
                neighbor.getY() + 0.5 + dir.getOpposite().getStepY() * 0.5,
                neighbor.getZ() + 0.5 + dir.getOpposite().getStepZ() * 0.5);
            if (eyes.distanceToSqr(cursor) > maxReachSq) continue;
            return new BlockHitResult(cursor, dir.getOpposite(), neighbor, false);
        }
        return null;
    }

    static float[] calcAngles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) Math.toDegrees(-Math.atan2(dy, dist))
        };
    }
}
