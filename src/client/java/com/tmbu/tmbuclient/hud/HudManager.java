package com.tmbu.tmbuclient.hud;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class HudManager implements Iterable<HudElement> {
    public static final HudManager INSTANCE = new HudManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mistclient_hud.json";
    private final List<HudElement> elements = new ArrayList<>();
    public boolean active = true;
    public int snappingRange = 6;
    public int border = 4;
    private HudManager() {}
    public void register(HudElement el) { elements.add(el); }
    public void add(HudElement el, int x, int y, XAnchor xa, YAnchor ya) {
        el.box.setPos(x, y);
        if (xa != null && ya != null) { el.box.xAnchor = xa; el.box.yAnchor = ya; }
        else el.box.updateAnchors();
        el.updatePos();
        elements.add(el);
    }
    public void remove(HudElement el) { elements.remove(el); }
    public List<HudElement> getElements() { return Collections.unmodifiableList(elements); }
    public void tick() {
        for (HudElement el : elements) {
            if (el.isActive() || el.isInEditor()) el.tick(HudRenderer.INSTANCE);
        }
    }
    public void render(GuiGraphics g, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!active && !HudEditorScreen.isOpen()) return;
        HudRenderer r = HudRenderer.INSTANCE;
        r.begin(g, delta);
        for (HudElement el : elements) {
            el.updatePos();
            if (el.isActive() || el.isInEditor()) el.render(r);
        }
        r.end();
    }
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("active", active);
        JsonArray arr = new JsonArray();
        for (HudElement el : elements) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", el.getId());
            obj.addProperty("active", el.isActive());
            obj.add("box", el.box.toJson());
            obj.addProperty("autoAnchors", el.autoAnchors);
            obj.addProperty("showBg", el.showBackground());
            obj.addProperty("bgColor", el.getBgColor());
            obj.addProperty("shadow", el.hasShadow());
            obj.addProperty("customScale", el.hasCustomScale());
            obj.addProperty("scale", el.getScale());
            arr.add(obj);
        }
        root.add("elements", arr);
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[mistclient] Failed to save HUD config: " + e.getMessage());
        }
    }
    public void load() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("active")) active = root.get("active").getAsBoolean();
            if (root.has("elements") && root.get("elements").isJsonArray()) {
                for (JsonElement je : root.getAsJsonArray("elements")) {
                    JsonObject obj = je.getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : "";
                    HudElement el = findById(id);
                    if (el == null) continue;
                    if (obj.has("active")) el.setActive(obj.get("active").getAsBoolean());
                    if (obj.has("box")) el.box.fromJson(obj.getAsJsonObject("box"));
                    if (obj.has("autoAnchors")) el.autoAnchors = obj.get("autoAnchors").getAsBoolean();
                    if (obj.has("showBg")) el.setShowBackground(obj.get("showBg").getAsBoolean());
                    if (obj.has("bgColor")) el.setBgColor(obj.get("bgColor").getAsInt());
                    if (obj.has("shadow")) el.setShadow(obj.get("shadow").getAsBoolean());
                    if (obj.has("customScale")) el.setCustomScale(obj.get("customScale").getAsBoolean());
                    if (obj.has("scale")) el.setScale(obj.get("scale").getAsDouble());
                    el.updatePos();
                }
            }
        } catch (Exception e) {
            System.err.println("[mistclient] Failed to load HUD config: " + e.getMessage());
        }
    }
    private HudElement findById(String id) {
        for (HudElement el : elements) if (el.getId().equals(id)) return el;
        return null;
    }
    @Override
    public Iterator<HudElement> iterator() { return elements.iterator(); }
}
