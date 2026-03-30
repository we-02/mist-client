package com.tmbu.tmbuclient.module.impl.render;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ModeSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModuleList extends Module {

    private final ModeSetting    sortMode;
    private final BooleanSetting showDescriptions;
    private final SliderSetting  animationDuration;

    private final Map<Module, Long> enableTimes  = new ConcurrentHashMap<>();
    private final Map<Module, Long> disableTimes = new ConcurrentHashMap<>();
    private Set<Module> lastEnabled = new HashSet<>();

    public ModuleList() {
        super("ModuleList", "Shows enabled modules on screen", Category.RENDER, -1);

        sortMode          = addSetting(new ModeSetting("Sort", "Alphabetical", new String[]{"Alphabetical", "Length"}));
        showDescriptions  = addSetting(new BooleanSetting("Show Descriptions", false));
        animationDuration = addSetting(new SliderSetting("Animation Duration", 300, 0, 1000, 50));

        // Register the HUD callback HERE in the constructor — once, permanently.
        // DO NOT register in onEnable(). Fabric's event bus has no unregister;
        // calling register() in onEnable() stacks a new listener every toggle.
        // The isEnabled() check inside the callback handles the on/off state.
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    @Override
    public void onEnable() {
        // Reset animation state so modules don't appear with stale timings
        lastEnabled.clear();
        enableTimes.clear();
        disableTimes.clear();
    }

    @Override
    public void onDisable() {
        // isEnabled() guard in the callback handles this — nothing needed here
    }

    private void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!isEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (manager == null) return;

        long now      = System.currentTimeMillis();
        long duration = animationDuration.getValue().longValue();

        // Snapshot currently enabled modules (excluding self)
        Set<Module> currentEnabled = new HashSet<>();
        for (Module mod : manager.getModules()) {
            if (mod.isEnabled() && mod != this) {
                currentEnabled.add(mod);
            }
        }

        // Newly enabled → fade in
        for (Module mod : currentEnabled) {
            if (!lastEnabled.contains(mod)) {
                enableTimes.put(mod, now);
                disableTimes.remove(mod);
            }
        }

        // Newly disabled → fade out
        for (Module mod : lastEnabled) {
            if (!currentEnabled.contains(mod)) {
                disableTimes.put(mod, now);
                enableTimes.remove(mod);
            }
        }

        // Clean up finished fade-outs
        disableTimes.entrySet().removeIf(e -> (now - e.getValue()) >= duration);

        // Build render list
        List<ModuleRender> toRender = new ArrayList<>();

        for (Module mod : currentEnabled) {
            float alpha = 1.0f;
            Long start = enableTimes.get(mod);
            if (start != null) {
                long elapsed = now - start;
                alpha = duration == 0 ? 1.0f : Mth.clamp((float) elapsed / duration, 0.0f, 1.0f);
                if (elapsed >= duration) enableTimes.remove(mod);
            }
            toRender.add(new ModuleRender(mod, alpha));
        }

        for (Map.Entry<Module, Long> entry : disableTimes.entrySet()) {
            long elapsed = now - entry.getValue();
            float alpha  = duration == 0 ? 0.0f : 1.0f - Mth.clamp((float) elapsed / duration, 0.0f, 1.0f);
            toRender.add(new ModuleRender(entry.getKey(), alpha));
        }

        toRender.removeIf(r -> r.alpha <= 0.001f);
        if (toRender.isEmpty()) { lastEnabled = currentEnabled; return; }

        // Sort
        String sort = sortMode.getMode();
        if ("Alphabetical".equals(sort)) {
            toRender.sort(Comparator.comparing(r -> r.module.getName()));
        } else {
            // Length — longest name first
            toRender.sort((a, b) -> Integer.compare(
                    client.font.width(b.module.getName()),
                    client.font.width(a.module.getName())));
        }

        int accent  = manager.getAccentColor();
        int padding = 4;
        int screenW = client.getWindow().getGuiScaledWidth();
        int y       = 10;

        for (ModuleRender mr : toRender) {
            // Clamp alpha to [1, 255] — drawString silently skips alpha == 0
            int alphaInt = Mth.clamp((int) (mr.alpha * 255), 1, 255);

            String name  = mr.module.getName();
            int nameW    = client.font.width(name);
            int textX    = screenW - nameW - padding - 10;
            int nameColor = (accent & 0x00FFFFFF) | (alphaInt << 24);

            graphics.drawString(client.font, name, textX, y + padding, nameColor, false);

            int rowHeight = 10 + padding * 2;

            if (showDescriptions.getValue()) {
                String desc = mr.module.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    // Use a muted grey with the same alpha
                    int descColor = 0x88889A | (alphaInt << 24);
                    int descX     = screenW - client.font.width(desc) - padding - 10;
                    graphics.drawString(client.font, desc, descX, y + padding + 10, descColor, false);
                    rowHeight += 10;
                }
            }

            y += rowHeight + 2;
        }

        lastEnabled = currentEnabled;
    }

    private static class ModuleRender {
        final Module module;
        final float  alpha;
        ModuleRender(Module module, float alpha) {
            this.module = module;
            this.alpha  = alpha;
        }
    }
}