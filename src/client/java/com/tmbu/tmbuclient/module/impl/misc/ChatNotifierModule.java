package com.tmbu.tmbuclient.module.impl.misc;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;

public class ChatNotifierModule extends Module {

	private final BiConsumer<Module, Boolean> toggleListener = (mod, enabled) -> {
		if (mod == this) return; // don't notify about ourselves
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		String state = enabled ? "enabled" : "disabled";
		client.player.displayClientMessage(
			Component.literal("[Mist] " + mod.getName() + " " + state), false);
	};

	public ChatNotifierModule() {
		super("ChatNotifier", "Shows module toggle messages in chat.", Category.MISC, GLFW.GLFW_KEY_H);
	}

	@Override
	public void onEnable() {
		if (manager != null) {
			manager.addToggleListener(toggleListener);
		}
	}

	@Override
	public void onDisable() {
		if (manager != null) {
			manager.removeToggleListener(toggleListener);
		}
	}
}
