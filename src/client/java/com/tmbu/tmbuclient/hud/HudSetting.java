package com.tmbu.tmbuclient.hud;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single configurable setting for a HUD element.
 * Supports: boolean toggles, enum cycling, color presets, and int cycling.
 */
public class HudSetting<T> {
    public enum Type { BOOL, ENUM, COLOR, INT }

    public final String name;
    public final Type type;
    private final Supplier<T> getter;
    private final Consumer<T> setter;

    // For ENUM type
    private T[] enumValues;

    // For COLOR type
    private int[] colorPresets;

    // For INT type
    private int min, max, step;

    private HudSetting(String name, Type type, Supplier<T> getter, Consumer<T> setter) {
        this.name = name;
        this.type = type;
        this.getter = getter;
        this.setter = setter;
    }

    public T get() { return getter.get(); }
    public void set(T value) { setter.accept(value); }

    public T[] getEnumValues() { return enumValues; }
    public int[] getColorPresets() { return colorPresets; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getStep() { return step; }

    /** Cycle to next value (for booleans and enums). */
    public void cycle() {
        if (type == Type.BOOL) {
            @SuppressWarnings("unchecked")
            Consumer<Boolean> bs = (Consumer<Boolean>) (Consumer<?>) setter;
            bs.accept(!(Boolean) getter.get());
        } else if (type == Type.ENUM && enumValues != null) {
            T current = getter.get();
            for (int i = 0; i < enumValues.length; i++) {
                if (enumValues[i] == current) {
                    setter.accept(enumValues[(i + 1) % enumValues.length]);
                    return;
                }
            }
            setter.accept(enumValues[0]);
        } else if (type == Type.INT) {
            @SuppressWarnings("unchecked")
            Supplier<Integer> ig = (Supplier<Integer>) (Supplier<?>) getter;
            @SuppressWarnings("unchecked")
            Consumer<Integer> is = (Consumer<Integer>) (Consumer<?>) setter;
            int val = ig.get() + step;
            if (val > max) val = min;
            is.accept(val);
        }
    }

    /** Get display string for current value. */
    public String displayValue() {
        T val = getter.get();
        if (type == Type.BOOL) return (Boolean) val ? "ON" : "OFF";
        if (type == Type.ENUM) return val.toString();
        if (type == Type.INT) return val.toString();
        if (type == Type.COLOR) return String.format("#%06X", ((Integer) val) & 0xFFFFFF);
        return String.valueOf(val);
    }

    // --- Builders ---

    public static HudSetting<Boolean> ofBool(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new HudSetting<>(name, Type.BOOL, getter, setter);
    }

    public static <E extends Enum<E>> HudSetting<E> ofEnum(String name, Class<E> clazz, Supplier<E> getter, Consumer<E> setter) {
        HudSetting<E> s = new HudSetting<>(name, Type.ENUM, getter, setter);
        s.enumValues = clazz.getEnumConstants();
        return s;
    }

    public static HudSetting<Integer> ofColor(String name, Supplier<Integer> getter, Consumer<Integer> setter, int... presets) {
        HudSetting<Integer> s = new HudSetting<>(name, Type.COLOR, getter, setter);
        s.colorPresets = presets;
        return s;
    }

    public static HudSetting<Integer> ofInt(String name, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max, int step) {
        HudSetting<Integer> s = new HudSetting<>(name, Type.INT, getter, setter);
        s.min = min;
        s.max = max;
        s.step = step;
        return s;
    }
}
