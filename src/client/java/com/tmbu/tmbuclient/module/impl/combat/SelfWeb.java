package com.tmbu.tmbuclient.module.impl.combat;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * SelfWeb — places a cobweb at your feet, auto-disables after placing.
 */
public class SelfWeb extends Module {

    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true));
    private boolean placed = false;

    public SelfWeb() {
        super("SelfWeb", "Places a cobweb at your feet",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override public void onEnable() { placed = false; }

    @Override
    public void onTick(Minecraft client) {
        if (placed) { toggle(); return; }
        LocalPlayer player = client.player;
        MultiPlayerGameMode gm = client.gameMode;
        Level level = client.level;
        if (player == null || gm == null || level == null) { toggle(); return; }

        BlockPos feet = player.blockPosition();
        if (!level.getBlockState(feet).canBeReplaced()) { toggle(); return; }

        if (WebUtils.placeWebAt(client, player, gm, level, feet, switchBack.getValue())) {
            placed = true;
        } else {
            toggle();
        }
    }
}
