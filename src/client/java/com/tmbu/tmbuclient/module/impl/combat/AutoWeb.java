package com.tmbu.tmbuclient.module.impl.combat;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

/**
 * AutoWeb — finds the nearest player and places a cobweb at their feet.
 * One placement per tick, with server rotation for anti-cheat.
 */
public class AutoWeb extends Module {

    private final SliderSetting  range      = addSetting(new SliderSetting("Range", 4.0, 2.0, 6.0, 0.5).group("General"));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true).group("General"));
    private final SliderSetting  delay      = addSetting(new SliderSetting("Delay Ticks", 2, 0, 20, 1).group("Timing"));

    private int tickCounter = 0;

    public AutoWeb() {
        super("AutoWeb", "Webs the nearest player",
              Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN);
    }

    @Override public void onEnable() { tickCounter = 0; }

    @Override
    public void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode gm = client.gameMode;
        Level level = client.level;
        if (player == null || gm == null || level == null) return;

        if (tickCounter++ < delay.getValue().intValue()) return;

        double r = range.getValue();
        List<Player> targets = level.getEntitiesOfClass(Player.class,
            player.getBoundingBox().inflate(r + 1),
            p -> p != player && p.isAlive() && !p.isSpectator() && player.distanceTo(p) <= r);

        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(player::distanceTo));

        for (Player target : targets) {
            BlockPos feet = target.blockPosition();
            if (!level.getBlockState(feet).canBeReplaced()) continue;

            if (WebUtils.placeWebAt(client, player, gm, level, feet, switchBack.getValue())) {
                tickCounter = 0;
                return;
            }
        }
    }
}
