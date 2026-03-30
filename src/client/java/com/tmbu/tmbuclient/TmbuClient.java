package com.tmbu.tmbuclient;

import com.tmbu.tmbuclient.command.CommandManager;
import com.tmbu.tmbuclient.gui.ClickGuiScreen;
import com.tmbu.tmbuclient.gui.ToastManager;
import com.tmbu.tmbuclient.hud.*;
import com.tmbu.tmbuclient.hud.elements.*;
import com.tmbu.tmbuclient.module.ModuleManager;
import com.tmbu.tmbuclient.module.ModuleRegistry;
import com.tmbu.tmbuclient.module.Modules;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class TmbuClient {
	public static final TmbuClient INSTANCE = new TmbuClient();
	private final ModuleManager moduleManager = new ModuleManager();
	private KeyMapping clickGuiKey;
	private KeyMapping hudEditorKey;

	private TmbuClient() {}

	public void initialize() {
		Modules.register();
		ModuleRegistry.registerAll(moduleManager);
		moduleManager.loadConfig();

		// Initialize command system
		CommandManager.INSTANCE.init();

		// Register HUD elements with Meteor-style anchor positioning
		HudManager hud = HudManager.INSTANCE;

		// Top left: watermark, fps, ping, coords
		hud.add(new WatermarkElement(), 4, 4, XAnchor.Left, YAnchor.Top);
		hud.add(new FpsElement(), 4, 16, XAnchor.Left, YAnchor.Top);
		hud.add(new PingElement(), 4, 28, XAnchor.Left, YAnchor.Top);
		hud.add(new CoordsElement(), 4, 40, XAnchor.Left, YAnchor.Top);

		// Top right: active modules
		hud.add(new ActiveModulesElement(), -4, 4, XAnchor.Right, YAnchor.Top);

		// Bottom left: totems, armor
		hud.add(new TotemCountElement(), 4, -40, XAnchor.Left, YAnchor.Bottom);
		hud.add(new ArmorElement(), 4, -20, XAnchor.Left, YAnchor.Bottom);

		// Bottom right: player model
		hud.add(new PlayerModelElement(), -4, -4, XAnchor.Right, YAnchor.Bottom);

		// Bottom right: potion timers (above player model)
		hud.add(new PotionTimersElement(), -4, -84, XAnchor.Right, YAnchor.Bottom);

		hud.load();

		registerKeybinds();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		WorldRenderEvents.END_MAIN.register(moduleManager::worldRender);

		//noinspection deprecation
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((guiGraphics, tickDeltaManager) -> {
			int accent = moduleManager.getAccentColor();
			ToastManager.INSTANCE.render(guiGraphics, accent);
			HudManager.INSTANCE.render(guiGraphics, tickDeltaManager.getGameTimeDeltaPartialTick(false));
		});
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	private void registerKeybinds() {
		clickGuiKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.mistclient.clickgui", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));
		hudEditorKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.mistclient.hudeditor", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_COMMA, KeyMapping.Category.MISC));
	}

	private void onClientTick(Minecraft client) {
		while (clickGuiKey.consumeClick()) {
			client.setScreen(new ClickGuiScreen(moduleManager));
		}
		while (hudEditorKey.consumeClick()) {
			client.setScreen(new HudEditorScreen());
		}
		moduleManager.tick(client);
		HudManager.INSTANCE.tick();
		ToastManager.INSTANCE.tick();
	}
}
