package com.tmbu.tmbuclient.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Setting<T> {
	private final String name;
	private final T defaultValue;
	private T value;
	private final List<Consumer<T>> listeners = new ArrayList<>();
	private String group;
	private BooleanSupplier visibleWhen;

	protected Setting(String name, T value) {
		this.name = name;
		this.defaultValue = value;
		this.value = value;
	}

	public String getName() { return name; }
	public T getValue() { return value; }
	public T getDefault() { return defaultValue; }

	public void setValue(T value) {
		T old = this.value;
		this.value = value;
		if (old == null ? value != null : !old.equals(value)) {
			for (Consumer<T> listener : listeners) {
				listener.accept(value);
			}
		}
	}

	public void resetToDefault() { setValue(defaultValue); }

	public Setting<T> onChange(Consumer<T> listener) {
		listeners.add(listener);
		return this;
	}

	/** Assign this setting to a named group. The GUI renders a header per group. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S group(String group) {
		this.group = group;
		return (S) this;
	}

	/** Only show this setting when the supplier returns true. */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visibleWhen = condition;
		return (S) this;
	}

	public String getGroup() { return group; }
	public boolean isVisible() { return visibleWhen == null || visibleWhen.getAsBoolean(); }

	public Object serialize() { return value; }

	public void deserialize(Object raw) {
		if (raw == null) return;
		try {
			@SuppressWarnings("unchecked")
			T cast = (T) raw;
			setValue(cast);
		} catch (ClassCastException ignored) {}
	}
}
