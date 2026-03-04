package com.tmbu.tmbuclient.module;

import com.tmbu.tmbuclient.settings.KeybindSetting;
import com.tmbu.tmbuclient.settings.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final KeybindSetting keybindSetting;
	protected ModuleManager manager;

	protected Module(String name, String description, Category category, int defaultKey) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.keybindSetting = addSetting(new KeybindSetting("Keybind", defaultKey));
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}

		if (manager != null) {
			manager.onModuleToggled(this, enabled);
		}
	}

	public int getKeybind() {
		return keybindSetting.getValue();
	}

	public KeybindSetting getKeybindSetting() {
		return keybindSetting;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onTick(Minecraft client) {
	}

	public void onWorldRender(WorldRenderContext context) {
	}

	protected <T extends Setting<?>> T addSetting(T setting) {
		settings.add(setting);
		return setting;
	}
}
