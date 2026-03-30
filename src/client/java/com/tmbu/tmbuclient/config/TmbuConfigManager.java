package com.tmbu.tmbuclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class TmbuConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "mistclient.json";
	private static final int VERSION = 1;

	public Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public void load(List<Module> modules, ConfigSink sink) {
		Path path = getConfigPath();
		if (!Files.exists(path)) {
			sink.setClickGuiConfig(ClickGuiConfig.defaults());
			return;
		}

		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) return;

			JsonObject root = parsed.getAsJsonObject();

			// ─── ClickGui config ─────────────────────────────────────────────
			JsonObject clickGui = root.getAsJsonObject("clickGui");
			if (clickGui != null) {
				Integer panelX           = clickGui.has("panelX")           ? clickGui.get("panelX").getAsInt()           : null;
				Integer panelY           = clickGui.has("panelY")           ? clickGui.get("panelY").getAsInt()           : null;
				String  selectedCategory = clickGui.has("selectedCategory") ? clickGui.get("selectedCategory").getAsString() : null;
				Integer accentColor      = clickGui.has("accentColor")      ? clickGui.get("accentColor").getAsInt()      : 0xFF3D9EFF;

				List<String> pinnedModules = new ArrayList<>();
				if (clickGui.has("pinnedModules")) {
					JsonArray arr = clickGui.getAsJsonArray("pinnedModules");
					for (JsonElement el : arr) pinnedModules.add(el.getAsString());
				}

				sink.setClickGuiConfig(new ClickGuiConfig(panelX, panelY, selectedCategory, accentColor, pinnedModules));
			} else {
				sink.setClickGuiConfig(ClickGuiConfig.defaults());
			}

			// ─── Modules ─────────────────────────────────────────────────────
			JsonObject moduleRoot = root.getAsJsonObject("modules");
			if (moduleRoot == null) return;

			for (Module module : modules) {
				JsonObject moduleJson = moduleRoot.getAsJsonObject(module.getName());
				if (moduleJson == null) continue;

				if (moduleJson.has("enabled")) {
					module.setEnabled(moduleJson.get("enabled").getAsBoolean());
				}

				JsonObject settingsJson = moduleJson.getAsJsonObject("settings");
				if (settingsJson == null) continue;

				for (Setting<?> setting : module.getSettings()) {
					JsonElement value = settingsJson.get(setting.getName());
					if (value == null || !value.isJsonPrimitive()) continue;

					// Use the setting's own deserialize method — no type checks needed
					Object raw = jsonPrimitiveToObject(value);
					setting.deserialize(raw);
				}
			}
		} catch (Exception e) {
			System.err.println("[mistclient] Failed to load config: " + e.getMessage());
		}
	}

	public void save(List<Module> modules, ClickGuiConfig clickGuiConfig) {
		Path path = getConfigPath();
		Path tmp  = path.resolveSibling(path.getFileName() + ".tmp");

		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);

		// ─── ClickGui config ─────────────────────────────────────────────────
		if (clickGuiConfig != null) {
			JsonObject clickGui = new JsonObject();
			if (clickGuiConfig.panelX()           != null) clickGui.addProperty("panelX",           clickGuiConfig.panelX());
			if (clickGuiConfig.panelY()           != null) clickGui.addProperty("panelY",           clickGuiConfig.panelY());
			if (clickGuiConfig.selectedCategory() != null) clickGui.addProperty("selectedCategory", clickGuiConfig.selectedCategory());
			if (clickGuiConfig.accentColor()      != null) clickGui.addProperty("accentColor",      clickGuiConfig.accentColor());

			JsonArray pinnedArr = new JsonArray();
			if (clickGuiConfig.pinnedModules() != null) {
				for (String name : clickGuiConfig.pinnedModules()) pinnedArr.add(name);
			}
			clickGui.add("pinnedModules", pinnedArr);
			root.add("clickGui", clickGui);
		}

		// ─── Modules ─────────────────────────────────────────────────────────
		JsonObject moduleRoot = new JsonObject();
		for (Module module : modules) {
			JsonObject moduleJson = new JsonObject();
			moduleJson.addProperty("enabled", module.isEnabled());

			JsonObject settingsJson = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				// Use the setting's own serialize method — no type checks needed
				Object v = setting.serialize();
				if      (v instanceof Boolean b) settingsJson.addProperty(setting.getName(), b);
				else if (v instanceof Number  n) settingsJson.addProperty(setting.getName(), n);
				else if (v instanceof String  s) settingsJson.addProperty(setting.getName(), s);
			}
			moduleJson.add("settings", settingsJson);
			moduleRoot.add(module.getName(), moduleJson);
		}
		root.add("modules", moduleRoot);

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			try {
				Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
			} catch (IOException ex) {
				System.err.println("[mistclient] Failed to save config: " + ex.getMessage());
			}
		}
	}

	/**
	 * Convert a JsonElement primitive to a plain Java object for setting deserialization.
	 */
	private static Object jsonPrimitiveToObject(JsonElement element) {
		if (!element.isJsonPrimitive()) return null;
		var prim = element.getAsJsonPrimitive();
		if (prim.isBoolean()) return prim.getAsBoolean();
		if (prim.isNumber())  return prim.getAsNumber();
		if (prim.isString())  return prim.getAsString();
		return null;
	}

	public interface ConfigSink {
		void setClickGuiConfig(ClickGuiConfig config);
	}
}
