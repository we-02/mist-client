package com.tmbu.tmbuclient.settings;

import com.mojang.blaze3d.platform.InputConstants;

public class KeybindSetting extends Setting<Integer> {
	public KeybindSetting(String name, int value) {
		super(name, value);
	}

	public String getDisplayName() {
		int key = getValue();
		if (key < 0) {
			return "NONE";
		}
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}
}
