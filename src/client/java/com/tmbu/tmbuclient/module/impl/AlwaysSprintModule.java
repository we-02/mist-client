package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class AlwaysSprintModule extends Module {
	private final BooleanSetting onlyWhenMoving = addSetting(new BooleanSetting("Only When Moving", true));

	public AlwaysSprintModule() {
		super("AlwaysSprint", "Keeps your player sprinting.", Category.MOVEMENT, GLFW.GLFW_KEY_G);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}

		if (onlyWhenMoving.getValue() && client.player.getDeltaMovement().horizontalDistanceSqr() <= 0.0001D) {
			return;
		}

		client.player.setSprinting(true);
	}
}
