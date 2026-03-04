	package com.tmbu.tmbuclient.module.impl;

	import com.tmbu.tmbuclient.module.Category;
	import com.tmbu.tmbuclient.module.Module;
	import org.lwjgl.glfw.GLFW;

	public class ChatNotifierModule extends Module {
		public ChatNotifierModule() {
			super("ChatNotifier", "Shows module toggle messages in chat.", Category.MISC, GLFW.GLFW_KEY_H);
		}
	}
