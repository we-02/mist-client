package com.tmbu.tmbuclient.hud.elements;

import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.hud.*;
import com.tmbu.tmbuclient.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays active modules in a sorted list with rainbow/flat coloring.
 */
public class ActiveModulesElement extends HudElement {

    public enum ColorMode { Flat, Rainbow }
    public enum Sort { Biggest, Smallest, Alphabetical }

    private ColorMode colorMode = ColorMode.Rainbow;
    private Sort sort = Sort.Biggest;
    private int flatColor = 0xFFFF5555;
    private boolean outlines = false;
    private int outlineWidth = 2;
    private double rainbowSpeed = 0.05;
    private double rainbowSpread = 0.01;
    private float rainbowSaturation = 1.0f;
    private float rainbowBrightness = 1.0f;

    private final List<Module> modules = new ArrayList<>();
    private double rainbowHue;

    public ActiveModulesElement() {
        super("active_modules", "Active Modules");

        addSetting(HudSetting.ofEnum("Color Mode", ColorMode.class,
            () -> colorMode, v -> colorMode = v));
        addSetting(HudSetting.ofEnum("Sort", Sort.class,
            () -> sort, v -> sort = v));
        addSetting(HudSetting.ofColor("Flat Color",
            () -> flatColor, v -> flatColor = v,
            0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF));
        addSetting(HudSetting.ofBool("Outlines",
            () -> outlines, v -> outlines = v));
        addSetting(HudSetting.ofInt("Outline Width",
            () -> outlineWidth, v -> outlineWidth = v, 1, 4, 1));
    }

    // Getters for serialization
    public ColorMode getColorMode() { return colorMode; }
    public void setColorMode(ColorMode m) { this.colorMode = m; }
    public Sort getSort() { return sort; }
    public void setSort(Sort s) { this.sort = s; }
    public int getFlatColor() { return flatColor; }
    public void setFlatColor(int c) { this.flatColor = c; }
    public boolean hasOutlines() { return outlines; }
    public void setOutlines(boolean o) { this.outlines = o; }
    public int getOutlineWidth() { return outlineWidth; }
    public void setOutlineWidth(int w) { this.outlineWidth = w; }

    @Override
    public void tick(HudRenderer renderer) {
        modules.clear();
        var mgr = TmbuClient.INSTANCE.getModuleManager();
        for (Module m : mgr.getModules()) {
            if (m.isEnabled()) modules.add(m);
        }

        if (modules.isEmpty()) {
            if (isInEditor()) {
                setSize(renderer.textWidth("Active Modules", hasShadow(), getScale()),
                        renderer.textHeight(hasShadow(), getScale()));
            }
            return;
        }

        boolean shadow = hasShadow();
        double scale = getScale();
        modules.sort((a, b) -> switch (sort) {
            case Biggest -> Double.compare(
                renderer.textWidth(b.getName(), shadow, scale),
                renderer.textWidth(a.getName(), shadow, scale));
            case Smallest -> Double.compare(
                renderer.textWidth(a.getName(), shadow, scale),
                renderer.textWidth(b.getName(), shadow, scale));
            case Alphabetical -> a.getName().compareTo(b.getName());
        });

        double maxW = 0;
        for (Module m : modules) {
            maxW = Math.max(maxW, renderer.textWidth(m.getName(), shadow, scale));
        }
        setSize(maxW, modules.size() * renderer.textHeight(shadow, scale));
    }

    @Override
    public void render(HudRenderer r) {
        boolean shadow = hasShadow();
        double scale = getScale();

        if (modules.isEmpty()) {
            if (isInEditor()) {
                r.text("Active Modules", x, y, 0xFFFFFFFF, shadow, scale);
            }
            return;
        }

        rainbowHue += rainbowSpeed * r.delta;
        if (rainbowHue > 1) rainbowHue -= 1;

        double currentHue = rainbowHue;
        double lineH = r.textHeight(shadow, scale);

        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            String name = module.getName();
            double textW = r.textWidth(name, shadow, scale);
            double offset = alignX(textW, Alignment.Auto);
            double drawX = x + offset;
            double drawY = y + i * lineH;

            int color;
            if (colorMode == ColorMode.Rainbow) {
                currentHue += rainbowSpread;
                int rgb = java.awt.Color.HSBtoRGB(
                    (float) (currentHue % 1.0), rainbowSaturation, rainbowBrightness);
                color = 0xFF000000 | (rgb & 0x00FFFFFF);
            } else {
                color = flatColor;
            }

            if (showBackground()) {
                r.quad(drawX - 2, drawY, textW + 4, lineH, getBgColor());
            }

            if (outlines) {
                int ow = outlineWidth;
                r.quad(drawX - 2 - ow, drawY, ow, lineH, color);
                r.quad(drawX + textW + 2, drawY, ow, lineH, color);
                if (i == 0) r.quad(drawX - 2 - ow, drawY - ow, textW + 4 + 2 * ow, ow, color);
                if (i == modules.size() - 1) r.quad(drawX - 2 - ow, drawY + lineH, textW + 4 + 2 * ow, ow, color);
            }

            r.text(name, drawX, drawY, color, shadow, scale);
        }
    }
}
