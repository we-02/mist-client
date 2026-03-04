package com.tmbu.tmbuclient.settings;

import java.util.Arrays;

public class ModeSetting extends Setting<String> {
	private final String[] modes;

	public ModeSetting(String name, String value, String[] modes) {
		super(name, value);
		this.modes = modes == null ? new String[0] : modes.clone();
		if (this.modes.length > 0 && !Arrays.asList(this.modes).contains(value)) {
			setValue(this.modes[0]);
		}
	}

	public String[] getModes() {
		return modes.clone();
	}

	public String getMode() {
		return getValue();
	}

	public void next() {
		if (modes.length == 0) {
			return;
		}
		int idx = indexOf(getValue());
		setValue(modes[(idx + 1) % modes.length]);
	}

	private int indexOf(String mode) {
		for (int i = 0; i < modes.length; i++) {
			if (modes[i].equals(mode)) {
				return i;
			}
		}
		return 0;
	}
}

