package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

/**
 * Ambience — customize sky color, time, and brightness.
 *
 * Purely client-side rendering changes. No packets sent.
 */
public class Ambience extends Module {

    // ── Sky ──────────────────────────────────────────────────────────────────
    private final BooleanSetting customSky   = addSetting(new BooleanSetting("Custom Sky", false).group("Sky"));
    private final ColorSetting   skyColor    = addSetting(new ColorSetting("Sky Color", 0xFF007DFF).group("Sky").visibleWhen(customSky::getValue));

    // ── Time ─────────────────────────────────────────────────────────────────
    private final BooleanSetting customTime  = addSetting(new BooleanSetting("Custom Time", false).group("Time"));
    private final SliderSetting  time        = addSetting(new SliderSetting("Time", 6000, 0, 24000, 100).group("Time").visibleWhen(customTime::getValue));

    // ── Fullbright ───────────────────────────────────────────────────────────
    private final BooleanSetting fullbright  = addSetting(new BooleanSetting("Fullbright", false).group("World"));

    private static Ambience instance;

    public Ambience() {
        super("Ambience", "Customize sky, time, and brightness",
              Category.RENDER, GLFW.GLFW_KEY_UNKNOWN);
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!fullbright.getValue()) return;
        LocalPlayer player = client.player;
        if (player == null) return;

        // Apply a client-side night vision effect for fullbright.
        // Duration of 400 ticks (20 seconds) — refreshed every tick so it never expires.
        // Ambient=true hides particles, showIcon=false hides the HUD icon.
        if (!player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && fullbright.getValue()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // ── Static accessors for mixins ──────────────────────────────────────────

    /** Whether fullbright is active — checked by tick to maintain night vision. */
    public static boolean isFullbright() {
        return instance != null && instance.isEnabled() && instance.fullbright.getValue();
    }

    public static boolean hasSkyOverride() {
        return instance != null && instance.isEnabled() && instance.customSky.getValue();
    }

    public static int getSkyColor() {
        return instance != null ? instance.skyColor.getColor() : 0xFF007DFF;
    }

    public static boolean hasTimeOverride() {
        return instance != null && instance.isEnabled() && instance.customTime.getValue();
    }

    public static long getTimeOverride() {
        return instance != null ? instance.time.getValue().longValue() : 6000;
    }
}
