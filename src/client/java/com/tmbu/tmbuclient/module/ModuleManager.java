package com.tmbu.tmbuclient.module;

import com.tmbu.tmbuclient.config.ClickGuiConfig;
import com.tmbu.tmbuclient.config.TmbuConfigManager;
import com.tmbu.tmbuclient.gui.ToastManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final Map<Module, Boolean> lastKeyStates = new IdentityHashMap<>();
	private final TmbuConfigManager configManager = new TmbuConfigManager();
	private ClickGuiConfig clickGuiConfig;
	private boolean saveRequested;
	private long lastSaveMs;
	private boolean suppressToggleNotifications;

	/**
	 * External toggle listeners. Modules (like ChatNotifier) register themselves
	 * here instead of being hardcoded via instanceof checks.
	 */
	private final List<BiConsumer<Module, Boolean>> toggleListeners = new ArrayList<>();

	public void register(Module... modules) {
		for (Module module : modules) {
			this.modules.add(module);
			module.manager = this;
			this.lastKeyStates.put(module, false);
		}
		this.modules.sort(Comparator.comparing(Module::getName));
	}

	/**
	 * Add a listener that fires whenever any module is toggled.
	 * Used by modules like ChatNotifier to react without the manager knowing about them.
	 */
	public void addToggleListener(BiConsumer<Module, Boolean> listener) {
		toggleListeners.add(listener);
	}

	public void removeToggleListener(BiConsumer<Module, Boolean> listener) {
		toggleListeners.remove(listener);
	}

	public List<Module> getModules() { return modules; }

	public List<Module> getByCategory(Category category) {
		return modules.stream().filter(m -> m.getCategory() == category).toList();
	}

	/**
	 * Find a module by its class type. Useful for inter-module communication
	 * without hardcoding references in the manager.
	 */
	@SuppressWarnings("unchecked")
	public <T extends Module> T getModule(Class<T> type) {
		for (Module m : modules) {
			if (type.isInstance(m)) return (T) m;
		}
		return null;
	}

	/**
	 * Find a module by name.
	 */
	public Module getModuleByName(String name) {
		for (Module m : modules) {
			if (m.getName().equalsIgnoreCase(name)) return m;
		}
		return null;
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

		if (!suppressToggleNotifications) {
			// Toast notification
			ToastManager.INSTANCE.push(module.getName(), enabled, getAccentColor());

			// Notify external listeners (e.g. ChatNotifier)
			for (BiConsumer<Module, Boolean> listener : toggleListeners) {
				listener.accept(module, enabled);
			}
		}
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
