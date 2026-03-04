package com.tmbu.tmbuclient.module;

import com.tmbu.tmbuclient.config.ClickGuiConfig;
import com.tmbu.tmbuclient.config.TmbuConfigManager;
import com.tmbu.tmbuclient.gui.ToastManager;
import com.tmbu.tmbuclient.module.impl.ChatNotifierModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final Map<Module, Boolean> lastKeyStates = new IdentityHashMap<>();
	private final TmbuConfigManager configManager = new TmbuConfigManager();
	private ClickGuiConfig clickGuiConfig;
	private boolean saveRequested;
	private long lastSaveMs;
	private boolean suppressToggleNotifications;
	private ChatNotifierModule chatNotifier;

	public void register(Module... modules) {
		for (Module module : modules) {
			this.modules.add(module);
			module.manager = this;
			this.lastKeyStates.put(module, false);
			if (module instanceof ChatNotifierModule cn) this.chatNotifier = cn;
		}
		this.modules.sort(Comparator.comparing(Module::getName));
	}

	public List<Module> getModules() { return modules; }

	public List<Module> getByCategory(Category category) {
		return modules.stream().filter(m -> m.getCategory() == category).toList();
	}

	public int getAccentColor() {
		if (clickGuiConfig != null && clickGuiConfig.accentColor() != null) {
			return clickGuiConfig.accentColor();
		}
		return 0xFF3D9EFF;
	}

	public void setAccentColor(int color) {
		if (clickGuiConfig == null) clickGuiConfig = ClickGuiConfig.defaults();
		clickGuiConfig = new ClickGuiConfig(
			clickGuiConfig.panelX(),
			clickGuiConfig.panelY(),
			clickGuiConfig.selectedCategory(),
			color,
			clickGuiConfig.pinnedModules()
		);
		requestSave();
	}

	public List<String> getPinnedModules() {
		if (clickGuiConfig == null || clickGuiConfig.pinnedModules() == null) return new ArrayList<>();
		return clickGuiConfig.pinnedModules();
	}

	public void setPinnedModules(List<String> pinned) {
		if (clickGuiConfig == null) clickGuiConfig = ClickGuiConfig.defaults();
		clickGuiConfig = new ClickGuiConfig(
			clickGuiConfig.panelX(),
			clickGuiConfig.panelY(),
			clickGuiConfig.selectedCategory(),
			clickGuiConfig.accentColor(),
			new ArrayList<>(pinned)
		);
		requestSave();
	}

	public void loadConfig() {
		suppressToggleNotifications = true;
		try {
			configManager.load(modules, config -> this.clickGuiConfig = config);
		} finally {
			suppressToggleNotifications = false;
		}
	}

	public void saveConfigNow() {
		configManager.save(modules, clickGuiConfig);
		lastSaveMs = System.currentTimeMillis();
		saveRequested = false;
	}

	public void requestSave() { saveRequested = true; }

	public ClickGuiConfig getClickGuiConfig() { return clickGuiConfig; }

	public void setClickGuiConfig(ClickGuiConfig clickGuiConfig) {
		this.clickGuiConfig = clickGuiConfig;
	}

	public void tick(Minecraft client) {
		handleKeybinds(client);
		for (Module module : modules) {
			if (module.isEnabled()) module.onTick(client);
		}
		if (saveRequested) {
			long now = System.currentTimeMillis();
			if (now - lastSaveMs >= 250L) saveConfigNow();
		}
	}

	public void worldRender(WorldRenderContext context) {
		for (Module module : modules) {
			if (module.isEnabled()) module.onWorldRender(context);
		}
	}

	void onModuleToggled(Module module, boolean enabled) {
		requestSave();

		// Toast notification
		if (!suppressToggleNotifications) {
			ToastManager.INSTANCE.push(module.getName(), enabled, getAccentColor());
		}

		// Chat notification (existing behavior)
		if (suppressToggleNotifications || chatNotifier == null || !chatNotifier.isEnabled()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		String state = enabled ? "enabled" : "disabled";
		client.player.displayClientMessage(Component.literal("[TMBU] " + module.getName() + " " + state), false);
	}

	private void handleKeybinds(Minecraft client) {
		if (client.screen != null) return;
		long handle = client.getWindow().handle();
		for (Module module : modules) {
			int key = module.getKeybind();
			if (key < 0) continue;
			boolean pressed    = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
			boolean wasPressed = lastKeyStates.getOrDefault(module, false);
			if (pressed && !wasPressed) module.toggle();
			lastKeyStates.put(module, pressed);
		}
	}
}