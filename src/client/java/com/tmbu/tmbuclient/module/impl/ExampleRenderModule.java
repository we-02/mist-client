package com.tmbu.tmbuclient.module.impl;

import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import org.lwjgl.glfw.GLFW;

public class ExampleRenderModule extends Module {
	private final BooleanSetting dotCrosshair = addSetting(new BooleanSetting("Dot Crosshair", true));
	private final SliderSetting scale = addSetting(new SliderSetting("Scale", 1.0D, 0.5D, 3.0D, 0.1D));

	public ExampleRenderModule() {
		super("ExampleRender", "Example render module scaffold.", Category.RENDER, GLFW.GLFW_KEY_J);
	}

	public boolean isDotCrosshair() {
		return dotCrosshair.getValue();
	}

	public double getScale() {
		return scale.getValue();
	}
}
