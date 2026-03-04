package com.tmbu.tmbuclient.settings;

import net.minecraft.util.Mth;

public class SliderSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public SliderSetting(String name, double value, double min, double max, double step) {
		super(name, value);
		this.min = min;
		this.max = max;
		this.step = step;
		setValue(value);
	}

	@Override
	public void setValue(Double value) {
		double clamped = Mth.clamp(value, min, max);
		double snapped = Math.round(clamped / step) * step;
		super.setValue(Mth.clamp(snapped, min, max));
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}
	public double getStep() {
		return step;
	}
}
