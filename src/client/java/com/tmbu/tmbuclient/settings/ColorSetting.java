package com.tmbu.tmbuclient.settings;

public class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, int defaultColor) {
		super(name, defaultColor);
	}

	public int getColor() { return getValue(); }

	public void setColor(int color) { setValue(color); }

	public int getDefaultColor() { return getDefault(); }

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Number n) setValue(n.intValue());
	}
}
