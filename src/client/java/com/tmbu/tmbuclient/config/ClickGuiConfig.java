package com.tmbu.tmbuclient.config;

import java.util.ArrayList;
import java.util.List;

public record ClickGuiConfig(
	Integer panelX,
	Integer panelY,
	String selectedCategory,
	Integer accentColor,
	List<String> pinnedModules
) {
	public ClickGuiConfig {
		if (pinnedModules == null) pinnedModules = new ArrayList<>();
	}

	/** Convenience constructor for backward compat */
	public static ClickGuiConfig defaults() {
		return new ClickGuiConfig(null, null, null, 0xFF3D9EFF, new ArrayList<>());
	}
}