package com.tmbu.tmbuclient.utils;

public final class TimerUtils {
	private long lastMs = System.currentTimeMillis();

	public void reset() {
		lastMs = System.currentTimeMillis();
	}

	public boolean hasTimeElapsed(long ms, boolean reset) {
		if (ms <= 0) {
			if (reset) {
				this.lastMs = System.currentTimeMillis();
			}
			return true;
		}
		long now = System.currentTimeMillis();
		boolean elapsed = now - lastMs >= ms;
		if (elapsed && reset) {
			lastMs = now;
		}
		return elapsed;
	}
}

