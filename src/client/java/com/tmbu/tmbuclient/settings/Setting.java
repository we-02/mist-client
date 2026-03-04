package com.tmbu.tmbuclient.settings;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	private T value;

	protected Setting(String name, T value) {
		this.name = name;
		this.defaultValue = value;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public T getValue() {
		return value;
	}

	public T getDefault() {
		return defaultValue;
	}

	public void setValue(T value) {
		this.value = value;
	}

	public void resetToDefault() {
		setValue(defaultValue);
	}
}