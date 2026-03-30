package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.hud.Alignment;
import com.tmbu.tmbuclient.hud.HudElement;
import com.tmbu.tmbuclient.hud.HudRenderer;
import com.tmbu.tmbuclient.hud.HudSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays active potion effects with timers.
 * Adapted from Meteor Client's PotionTimersHud.
 */
public class PotionTimersElement extends HudElement {

    public enum ColorMode { Effect, Flat, Rainbow }

    private ColorMode colorMode = ColorMode.Effect;
    private int flatColor = 0xFFFF5555;
    private boolean showAmbient = true;

    private final List<EffectEntry> effects = new ArrayList<>();
    private double rainbowHue;

    public PotionTimersElement() {
        super("potion_timers", "Potion Timers");

        addSetting(HudSetting.ofEnum("Color Mode", ColorMode.class,
            () -> colorMode, v -> colorMode = v));
        addSetting(HudSetting.ofColor("Flat Color",
            () -> flatColor, v -> flatColor = v,
            0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF));
        addSetting(HudSetting.ofBool("Show Ambient",
            () -> showAmbient, v -> showAmbient = v));
    }

    public ColorMode getColorMode() { return colorMode; }
    public void setColorMode(ColorMode m) { this.colorMode = m; }
    public int getFlatColor() { return flatColor; }
    public void setFlatColor(int c) { this.flatColor = c; }
    public boolean isShowAmbient() { return showAmbient; }
    public void setShowAmbient(boolean v) { this.showAmbient = v; }

    @Override
    public void tick(HudRenderer renderer) {
        Minecraft mc = Minecraft.getInstance();
        effects.clear();

        if (mc.player == null) {
            if (isInEditor()) {
                setSize(renderer.textWidth("Potion Timers 0:00", hasShadow(), getScale()),
                        renderer.textHeight(hasShadow(), getScale()));
            }
            return;
        }

        boolean shadow = hasShadow();
        double scale = getScale();
        double maxW = 0;
        double totalH = 0;

        for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            if (!showAmbient && inst.isAmbient()) continue;

            String name = inst.getEffect().value().getDescriptionId();
            // Clean up the translation key to a readable name
            String displayName = formatEffectName(name);
            int amp = inst.getAmplifier() + 1;
            String duration = MobEffectUtil.formatDuration(inst, 1, mc.level.tickRateManager().tickrate()).getString();
            String text = displayName + " " + amp + " (" + duration + ")";
            int color = inst.getEffect().value().getColor();

            effects.add(new EffectEntry(text, color));
            maxW = Math.max(maxW, renderer.textWidth(text, shadow, scale));
            totalH += renderer.textHeight(shadow, scale);
        }

        if (effects.isEmpty() && isInEditor()) {
            setSize(renderer.textWidth("Potion Timers 0:00", shadow, scale),
                    renderer.textHeight(shadow, scale));
        } else {
            setSize(maxW, totalH);
        }
    }

    @Override
    public void render(HudRenderer r) {
        boolean shadow = hasShadow();
        double scale = getScale();

        drawBg(r.graphics, x, y, getWidth(), getHeight());

        if (effects.isEmpty()) {
            if (isInEditor()) {
                r.text("Potion Timers 0:00", x, y, 0xFFFFFFFF, shadow, scale);
            }
            return;
        }

        rainbowHue += 0.05 * r.delta;
        if (rainbowHue > 1) rainbowHue -= 1;
        double localHue = rainbowHue;

        double lineH = r.textHeight(shadow, scale);
        for (int i = 0; i < effects.size(); i++) {
            EffectEntry e = effects.get(i);
            double textW = r.textWidth(e.text, shadow, scale);
            double offset = alignX(textW, Alignment.Auto);
            double drawX = x + offset;
            double drawY = y + i * lineH;

            int color = switch (colorMode) {
                case Effect -> 0xFF000000 | (e.effectColor & 0x00FFFFFF);
                case Flat -> flatColor;
                case Rainbow -> {
                    localHue += 0.01;
                    int rgb = java.awt.Color.HSBtoRGB((float) (localHue % 1.0), 1.0f, 1.0f);
                    yield 0xFF000000 | (rgb & 0x00FFFFFF);
                }
            };

            r.text(e.text, drawX, drawY, color, shadow, scale);
        }
    }

    private static String formatEffectName(String translationKey) {
        // "effect.minecraft.speed" -> "Speed"
        String raw = translationKey;
        int lastDot = raw.lastIndexOf('.');
        if (lastDot >= 0) raw = raw.substring(lastDot + 1);
        // Convert snake_case to Title Case
        StringBuilder sb = new StringBuilder();
        for (String part : raw.split("_")) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private record EffectEntry(String text, int effectColor) {}
}
