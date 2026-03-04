package com.tmbu.tmbuclient.module.impl;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import com.tmbu.tmbuclient.utils.TimerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class PearlFeet extends Module {
    private final BooleanSetting autoSwitch = addSetting(new BooleanSetting("Auto Switch", true));
    private final BooleanSetting switchBack = addSetting(new BooleanSetting("Switch Back", true));
    private final SliderSetting throwDelay = addSetting(new SliderSetting("Throw Delay", 0, 0, 200, 5));
    private final TimerUtils timer = new TimerUtils();
    private boolean thrown = false;
    private Integer previousSlot = null;

    public PearlFeet() {
        super("KeyFlash", "Throws pearl at feet to gain knockback immunity", Category.COMBAT, GLFW.GLFW_KEY_G);
    }

    @Override
    public void onEnable() {
        thrown = false;
        previousSlot = null;
        timer.reset();
    }

    @Override
    public void onTick(Minecraft client) {
        if (thrown) {
            toggle();
            return;
        }

        LocalPlayer player = client.player;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || gameMode == null) return;

        if (!timer.hasTimeElapsed(throwDelay.getValue().longValue(), false)) return;

        InteractionHand hand = getPearlHand(player);
        if (hand == null && autoSwitch.getValue()) {
            int slot = findPearlSlot(player);
            if (slot == -1) {
                toggle();
                return;
            }
            previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            hand = InteractionHand.MAIN_HAND;
        }

        if (hand == null) {
            toggle();
            return;
        }

        // Temporarily force client pitch down for the throw calculation
        float originalPitch = player.getXRot();
        player.setXRot(90.0F);

        // Send fake packet so server also calculates throw downward
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                player.getYRot(),
                90.0F,
                player.onGround(),
                player.horizontalCollision
        ));

        gameMode.useItem(player, hand);
        player.swing(hand);

        // Restore client pitch immediately - camera never visually moves
        // because this all happens in one tick before rendering
        player.setXRot(originalPitch);

        // Restore server pitch
        client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                player.getYRot(),
                originalPitch,
                player.onGround(),
                player.horizontalCollision
        ));

        thrown = true;

        if (switchBack.getValue() && previousSlot != null) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    private InteractionHand getPearlHand(LocalPlayer player) {
        if (player.getMainHandItem().is(Items.ENDER_PEARL)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(Items.ENDER_PEARL)) return InteractionHand.OFF_HAND;
        return null;
    }

    private int findPearlSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}