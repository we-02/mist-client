package com.tmbu.tmbuclient.gui;

public class Toast {
	public enum State { SLIDE_IN, VISIBLE, SLIDE_OUT, DEAD }

	public final String message;
	public final int color;
	public final boolean enabled;

	public State state = State.SLIDE_IN;
	public float slideAnim = 0.0F; // 0 = off screen, 1 = fully visible
	public long visibleStartMs = -1L;

	private static final long VISIBLE_MS = 2500L;

	public Toast(String message, int color, boolean enabled) {
		this.message = message;
		this.color = color;
		this.enabled = enabled;
	}

	/** Call every frame with delta in seconds. Returns true while still alive. */
	public boolean tick(float delta) {
		switch (state) {
			case SLIDE_IN -> {
				slideAnim = Math.min(1.0F, slideAnim + delta * 6.0F);
				if (slideAnim >= 1.0F) {
					state = State.VISIBLE;
					visibleStartMs = System.currentTimeMillis();
				}
			}
			case VISIBLE -> {
				if (System.currentTimeMillis() - visibleStartMs >= VISIBLE_MS) {
					state = State.SLIDE_OUT;
				}
			}
			case SLIDE_OUT -> {
				slideAnim = Math.max(0.0F, slideAnim - delta * 5.0F);
				if (slideAnim <= 0.0F) {
					state = State.DEAD;
				}
			}
			case DEAD -> { return false; }
		}
		return true;
	}

	public float alpha() {
		return switch (state) {
			case SLIDE_IN  -> slideAnim;
			case VISIBLE   -> 1.0F;
			case SLIDE_OUT -> slideAnim;
			case DEAD      -> 0.0F;
		};
	}

	/** X offset: 0 = fully visible, positive = off to the right */
	public float xOffset(int toastWidth) {
		float eased = easeOutCubic(slideAnim);
		return (1.0F - eased) * (toastWidth + 16);
	}

	private static float easeOutCubic(float t) {
		float inv = 1 - t;
		return 1 - inv * inv * inv;
	}
}