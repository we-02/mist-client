package com.tmbu.tmbuclient.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class CrystalUtils {
    private CrystalUtils() {}

    /**
     * Checks if a crystal can be placed on the obsidian/bedrock block at the given position.
     * This is a client‑side prediction that mimics the server's placement rules.
     *
     * @param level the world
     * @param pos   the position of the obsidian/bedrock block
     * @return true if a crystal can be placed
     */
    public static boolean canPlaceCrystalClient(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
            return false;
        }

        BlockPos above = pos.above();
        // The block above must be air
        if (!level.getBlockState(above).isAir()) {
            return false;
        }

        // Check for any entities in the 1×2 area where the crystal would be
        AABB box = new AABB(above).expandTowards(0, 1, 0).inflate(0.01);
        return level.getEntitiesOfClass(Entity.class, box,
                e -> !(e instanceof Player) || !((Player) e).isSpectator()).isEmpty();
    }
}