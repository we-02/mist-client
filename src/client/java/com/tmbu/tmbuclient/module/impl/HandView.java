package com.tmbu.tmbuclient.module.impl;

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
 * Useful for CPVP to reduce visual clutter from large items blocking your view,
 * or to reposition hands for better crosshair visibility.
 */
public class HandView extends Module {

    // ── Main Hand ────────────────────────────────────────────────────────────
    private final SliderSetting mainX     = addSetting(new SliderSetting("Main X", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainY     = addSetting(new SliderSetting("Main Y", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainZ     = addSetting(new SliderSetting("Main Z", 0, -3, 3, 0.1).group("Main Hand"));
    private final SliderSetting mainRotX  = addSetting(new SliderSetting("Main Rot X", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainRotY  = addSetting(new SliderSetting("Main Rot Y", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainRotZ  = addSetting(new SliderSetting("Main Rot Z", 0, -180, 180, 1).group("Main Hand"));
    private final SliderSetting mainScale = addSetting(new SliderSetting("Main Scale", 1, 0.1, 3, 0.1).group("Main Hand"));

    // ── Off Hand ─────────────────────────────────────────────────────────────
    private final SliderSetting offX      = addSetting(new SliderSetting("Off X", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offY      = addSetting(new SliderSetting("Off Y", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offZ      = addSetting(new SliderSetting("Off Z", 0, -3, 3, 0.1).group("Off Hand"));
    private final SliderSetting offRotX   = addSetting(new SliderSetting("Off Rot X", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offRotY   = addSetting(new SliderSetting("Off Rot Y", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offRotZ   = addSetting(new SliderSetting("Off Rot Z", 0, -180, 180, 1).group("Off Hand"));
    private final SliderSetting offScale  = addSetting(new SliderSetting("Off Scale", 1, 0.1, 3, 0.1).group("Off Hand"));

    // ── General ──────────────────────────────────────────────────────────────
    private final BooleanSetting oldSwing = addSetting(new BooleanSetting("Old Swing", false).group("General"));
    private final SliderSetting  swingSpeed = addSetting(new SliderSetting("Swing Speed", 6, 1, 20, 1).group("General"));

    /** Singleton for mixin access. */
    private static HandView instance;

    public HandView() {
        super("HandView", "Customize hand position, rotation, and scale",
              Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    /**
     * Called from the mixin to apply transforms to the PoseStack before hand rendering.
     */
    public static void applyTransforms(InteractionHand hand, PoseStack poseStack) {
        if (instance == null || !instance.isEnabled()) return;

        if (hand == InteractionHand.MAIN_HAND) {
            float sx = instance.mainScale.getValue().floatValue();
            poseStack.translate(
                instance.mainX.getValue().floatValue(),
                instance.mainY.getValue().floatValue(),
                instance.mainZ.getValue().floatValue()
            );
            poseStack.mulPose(Axis.XP.rotationDegrees(instance.mainRotX.getValue().floatValue()));
            poseStack.mulPose(Axis.YP.rotationDegrees(instance.mainRotY.getValue().floatValue()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(instance.mainRotZ.getValue().floatValue()));
            poseStack.scale(sx, sx, sx);
        } else {
            float sx = instance.offScale.getValue().floatValue();
            poseStack.translate(
                instance.offX.getValue().floatValue(),
                instance.offY.getValue().floatValue(),
                instance.offZ.getValue().floatValue()
            );
            poseStack.mulPose(Axis.XP.rotationDegrees(instance.offRotX.getValue().floatValue()));
            poseStack.mulPose(Axis.YP.rotationDegrees(instance.offRotY.getValue().floatValue()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(instance.offRotZ.getValue().floatValue()));
            poseStack.scale(sx, sx, sx);
        }
    }

    /**
     * Whether to use old 1.8-style swing animation.
     */
    public static boolean useOldSwing() {
        return instance != null && instance.isEnabled() && instance.oldSwing.getValue();
    }

    /**
     * Custom swing speed (vanilla is 6).
     */
    public static int getSwingSpeed() {
        return instance != null && instance.isEnabled()
            ? instance.swingSpeed.getValue().intValue() : 6;
    }
}
