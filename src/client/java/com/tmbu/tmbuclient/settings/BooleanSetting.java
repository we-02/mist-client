package com.tmbu.tmbuclient.settings;

public class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String name, boolean value) {
		super(name, value);
	}

	public void toggle() {
		setValue(!getValue());
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Boolean b) setValue(b);
	}
}
