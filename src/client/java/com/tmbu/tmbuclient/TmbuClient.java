package com.tmbu.tmbuclient;

import com.tmbu.tmbuclient.gui.ClickGuiScreen;
import com.tmbu.tmbuclient.gui.ToastManager;
import com.tmbu.tmbuclient.module.ModuleManager;
import com.tmbu.tmbuclient.module.impl.AlwaysSprintModule;
import com.tmbu.tmbuclient.module.impl.ChatNotifierModule;
import com.tmbu.tmbuclient.module.impl.AutoAnchorExploder;
import com.tmbu.tmbuclient.module.impl.AutoCrystal;
import com.tmbu.tmbuclient.module.impl.AutoTotemHover;
import com.tmbu.tmbuclient.module.impl.EspModule;
import com.tmbu.tmbuclient.module.impl.NametagsModule;
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

	private TmbuClient() {}

	public void initialize() {
		registerModules();
		moduleManager.loadConfig();
		registerKeybinds();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		WorldRenderEvents.END_MAIN.register(moduleManager::worldRender);
		//noinspection deprecation
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((guiGraphics, tickDeltaManager) -> {
			int accent = moduleManager.getAccentColor();
			ToastManager.INSTANCE.render(guiGraphics, accent);
		});
	}

	private void registerModules() {
		moduleManager.register(
			new AutoAnchorExploder(),
			new AutoCrystal(),
			new AlwaysSprintModule(),
			new EspModule(),
			new NametagsModule(),
			new ChatNotifierModule(),
			new AutoTotemHover(),
			new com.tmbu.tmbuclient.module.impl.PearlFeet(),
			new com.tmbu.tmbuclient.module.impl.AutoDoubleHand(),
			new com.tmbu.tmbuclient.module.impl.ModuleList()
		);
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	private void registerKeybinds() {
		clickGuiKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.tmbuclient.clickgui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyMapping.Category.MISC
			)
		);
	}

	private void onClientTick(Minecraft client) {
		while (clickGuiKey.consumeClick()) {
			client.setScreen(new ClickGuiScreen(moduleManager));
		}
		moduleManager.tick(client);
		ToastManager.INSTANCE.tick();
	}
}