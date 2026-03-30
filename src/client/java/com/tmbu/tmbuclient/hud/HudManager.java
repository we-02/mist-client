package com.tmbu.tmbuclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages all HUD elements — registration, rendering, and config persistence.
 */
public final class HudManager {
    public static final HudManager INSTANCE = new HudManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mistclient_hud.json";

    private final List<HudElement> elements = new ArrayList<>();

    private HudManager() {}

    public void register(HudElement element) {
        elements.add(element);
    }

    public List<HudElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /** Render all enabled elements on the game HUD. */
    public void render(GuiGraphics graphics, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        for (HudElement element : elements) {
            if (element.isEnabled()) {
                element.render(graphics, client, delta);
            }
        }
    }

    /** Save element positions and enabled states to config. */
    public void save() {
        JsonObject root = new JsonObject();
        for (HudElement el : elements) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", el.getX());
            obj.addProperty("y", el.getY());
            obj.addProperty("enabled", el.isEnabled());
            obj.addProperty("textColor", el.getTextColor());
            obj.addProperty("bgColor", el.getBgColor());
            obj.addProperty("showBg", el.showBackground());
            obj.addProperty("accentColor", el.getAccentColor());
            root.add(el.getId(), obj);
        }
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[mistclient] Failed to save HUD config: " + e.getMessage());
        }
    }

    /** Load element positions and enabled states from config. */
    public void load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            if (!Files.exists(path)) return;
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (HudElement el : elements) {
                JsonElement entry = root.get(el.getId());
                if (entry != null && entry.isJsonObject()) {
                    JsonObject obj = entry.getAsJsonObject();
                    if (obj.has("x")) el.setX(obj.get("x").getAsInt());
                    if (obj.has("y")) el.setY(obj.get("y").getAsInt());
                    if (obj.has("enabled")) el.setEnabled(obj.get("enabled").getAsBoolean());
                    if (obj.has("textColor")) el.setTextColor(obj.get("textColor").getAsInt());
                    if (obj.has("bgColor")) el.setBgColor(obj.get("bgColor").getAsInt());
                    if (obj.has("showBg")) el.setShowBackground(obj.get("showBg").getAsBoolean());
                    if (obj.has("accentColor")) el.setAccentColor(obj.get("accentColor").getAsInt());
                }
            }
        } catch (Exception e) {
            System.err.println("[mistclient] Failed to load HUD config: " + e.getMessage());
        }
    }
}
