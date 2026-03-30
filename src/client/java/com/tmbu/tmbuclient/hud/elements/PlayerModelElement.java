package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Displays the player's model on the HUD.
 */
public class PlayerModelElement extends HudElement {

    private boolean copyYaw = true;
    private boolean copyPitch = true;
    private int customYaw = 0;
    private int customPitch = 0;

    public PlayerModelElement() {
        super("player_model", "Player Model");
        setSize(50, 75);

        addSetting(HudSetting.ofBool("Copy Yaw",
            () -> copyYaw, v -> copyYaw = v));
        addSetting(HudSetting.ofBool("Copy Pitch",
            () -> copyPitch, v -> copyPitch = v));
    }

    public boolean isCopyYaw() { return copyYaw; }
    public void setCopyYaw(boolean v) { this.copyYaw = v; }
    public boolean isCopyPitch() { return copyPitch; }
    public void setCopyPitch(boolean v) { this.copyPitch = v; }

    @Override
    public void render(HudRenderer r) {
        double scale = getScale();
        int w = (int) (50 * scale);
        int h = (int) (75 * scale);
        setSize(w, h);

        drawBg(r.graphics, x, y, w, h);

        r.post(() -> {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            float yaw = copyYaw ? player.yBodyRot : customYaw;
            float pitch = copyPitch ? player.getXRot() : customPitch;
            r.entity(player, x, y, w, h, -yaw, -pitch);
        });
    }
}
