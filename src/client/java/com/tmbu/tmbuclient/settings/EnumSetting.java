package com.tmbu.tmbuclient.settings;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] constants;

    public EnumSetting(String name, E defaultValue) {
        super(name, defaultValue);
        this.constants = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E next() {
        int ordinal = (getValue().ordinal() + 1) % constants.length;
        setValue(constants[ordinal]);
        return getValue();
    }

    public E previous() {
        int ordinal = (getValue().ordinal() - 1 + constants.length) % constants.length;
        setValue(constants[ordinal]);
        return getValue();
    }

    public String getMode() {
        return getValue().name();
    }
}