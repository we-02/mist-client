package com.tmbu.tmbuclient.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.SliderSetting;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

/**
 * HandView — customize the position, rotation, and scale of your held items.
 *
 * Transform order matches Meteor: rotate → scale → translate.
 * This ensures rotation happens around the hand's local origin (not world origin),
 * scale applies uniformly, and translate shifts the final result.
 */
public class HandView extends Module {

    // ── Main Hand ────────────────────────────────────────────────────────────
    private final SliderSetting mainPosX   = addSetting(new SliderSetting("Main Pos X", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainPosY   = addSetting(new SliderSetting("Main Pos Y", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainPosZ   = addSetting(new SliderSetting("Main Pos Z", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainRotX   = addSetting(new SliderSetting("Main Rot X", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainRotY   = addSetting(new SliderSetting("Main Rot Y", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainRotZ   = addSetting(new SliderSetting("Main Rot Z", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainScaleX = addSetting(new SliderSetting("Main Scale X", 1, 0.1, 5, 0.1).group("Main Hand"));
    private final SliderSetting mainScaleY = addSetting(new SliderSetting("Main Scale Y", 1, 0.1, 5, 0.1).group("Main Hand"));
    private final SliderSetting mainScaleZ = addSetting(new SliderSetting("Main Scale Z", 1, 0.1, 5, 0.1).group("Main Hand"));

    // ── Off Hand ─────────────────────────────────────────────────────────────
    private final SliderSetting offPosX    = addSetting(new SliderSetting("Off Pos X", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offPosY    = addSetting(new SliderSetting("Off Pos Y", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offPosZ    = addSetting(new SliderSetting("Off Pos Z", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offRotX    = addSetting(new SliderSetting("Off Rot X", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offRotY    = addSetting(new SliderSetting("Off Rot Y", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offRotZ    = addSetting(new SliderSetting("Off Rot Z", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offScaleX  = addSetting(new SliderSetting("Off Scale X", 1, 0.1, 5, 0.1).group("Off Hand"));
    private final SliderSetting offScaleY  = addSetting(new SliderSetting("Off Scale Y", 1, 0.1, 5, 0.1).group("Off Hand"));
    private final SliderSetting offScaleZ  = addSetting(new SliderSetting("Off Scale Z", 1, 0.1, 5, 0.1).group("Off Hand"));

    // ── General ──────────────────────────────────────────────────────────────
    private final BooleanSetting oldSwing  = addSetting(new BooleanSetting("Old Swing", false).group("General"));
    private final SliderSetting swingSpeed = addSetting(new SliderSetting("Swing Speed", 6, 0.5, 20, 0.5).group("General"));

    private static HandView instance;

    public HandView() {
        super("HandView", "Customize hand position, rotation, and scale",
              Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    /**
     * Called from mixin at the renderItem call — AFTER all vanilla transforms.
     * Order: rotate → scale → translate (matches Meteor).
     */
    public static void applyTransforms(InteractionHand hand, PoseStack ps) {
        if (instance == null || !instance.isEnabled()) return;

        if (hand == InteractionHand.MAIN_HAND) {
            // Rotate
            ps.mulPose(Axis.XP.rotationDegrees(instance.mainRotX.getValue().floatValue()));
            ps.mulPose(Axis.YP.rotationDegrees(instance.mainRotY.getValue().floatValue()));
            ps.mulPose(Axis.ZP.rotationDegrees(instance.mainRotZ.getValue().floatValue()));
            // Scale
            ps.scale(instance.mainScaleX.getValue().floatValue(),
                     instance.mainScaleY.getValue().floatValue(),
                     instance.mainScaleZ.getValue().floatValue());
            // Translate
            ps.translate(instance.mainPosX.getValue().floatValue(),
                         instance.mainPosY.getValue().floatValue(),
                         instance.mainPosZ.getValue().floatValue());
        } else {
            ps.mulPose(Axis.XP.rotationDegrees(instance.offRotX.getValue().floatValue()));
            ps.mulPose(Axis.YP.rotationDegrees(instance.offRotY.getValue().floatValue()));
            ps.mulPose(Axis.ZP.rotationDegrees(instance.offRotZ.getValue().floatValue()));
            ps.scale(instance.offScaleX.getValue().floatValue(),
                     instance.offScaleY.getValue().floatValue(),
                     instance.offScaleZ.getValue().floatValue());
            ps.translate(instance.offPosX.getValue().floatValue(),
                         instance.offPosY.getValue().floatValue(),
                         instance.offPosZ.getValue().floatValue());
        }
    }

    public static boolean useOldSwing() {
        return instance != null && instance.isEnabled() && instance.oldSwing.getValue();
    }

    public static int getSwingSpeed() {
        return instance != null && instance.isEnabled()
            ? Math.round(instance.swingSpeed.getValue().floatValue()) : 6;
    }
}
