package com.tmbu.tmbuclient.settings;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind setting that supports both keyboard keys and mouse buttons.
 * Keyboard keys are stored as positive GLFW key codes.
 * Mouse buttons are stored as negative values: -(button + 1).
 * -1 = unbound (NONE).
 */
public class KeybindSetting extends Setting<Integer> {

	public KeybindSetting(String name, int value) {
		super(name, value);
	}

	/** Set from a keyboard key code. */
	public void setKey(int glfwKey) {
		setValue(glfwKey);
	}

	/** Set from a mouse button (0=left, 1=right, 2=middle, 3+). */
	public void setMouseButton(int button) {
		// Encode mouse buttons as negative: -(button + 2) to avoid collision with -1 (NONE)
		setValue(-(button + 2));
	}

	/** Clear the binding. */
	public void clear() {
		setValue(-1);
	}

	/** Whether this is bound to anything. */
	public boolean isBound() {
		return getValue() != -1;
	}

	/** Whether this is a mouse button binding. */
	public boolean isMouseButton() {
		return getValue() <= -2;
	}

	/** Get the GLFW mouse button index (only valid if isMouseButton()). */
	public int getMouseButtonIndex() {
		return -(getValue() + 2);
	}

	/** Check if this keybind is currently pressed. */
	public boolean isPressed(long windowHandle) {
		int val = getValue();
		if (val == -1) return false;

		if (val <= -2) {
			// Mouse button
			int button = -(val + 2);
			return GLFW.glfwGetMouseButton(windowHandle, button) == GLFW.GLFW_PRESS;
		} else {
			// Keyboard key
			return GLFW.glfwGetKey(windowHandle, val) == GLFW.GLFW_PRESS;
		}
	}

	public String getDisplayName() {
		int val = getValue();
		if (val == -1) return "NONE";

		if (val <= -2) {
			int button = -(val + 2);
			return switch (button) {
				case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "Mouse Left";
				case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "Mouse Right";
				case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "Mouse Middle";
				default -> "Mouse " + (button + 1);
			};
		}

		return InputConstants.Type.KEYSYM.getOrCreate(val).getDisplayName().getString();
	}

	@Override
	public void deserialize(Object raw) {
		if (raw instanceof Number n) setValue(n.intValue());
	}
}
