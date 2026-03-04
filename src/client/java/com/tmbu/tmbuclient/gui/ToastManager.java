package com.tmbu.tmbuclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ToastManager {
	public static final ToastManager INSTANCE = new ToastManager();

	private static final int TOAST_W      = 180;
	private static final int TOAST_H      = 28;
	private static final int TOAST_GAP    = 4;
	private static final int TOAST_MARGIN = 8;
	private static final int RADIUS       = 4;

	private final List<Toast> toasts = new ArrayList<>();
	private long lastTickMs = -1L;

	private ToastManager() {}

	public void push(String moduleName, boolean enabled, int accentColor) {
		String msg = moduleName + (enabled ? " enabled" : " disabled");
		int color = enabled ? accentColor : 0xFFFF5555;
		toasts.add(new Toast(msg, color, enabled));
	}

	/** Call every client tick from TmbuClient */
	public void tick() {
		long now = System.currentTimeMillis();
		float delta = lastTickMs < 0 ? 0.05F : (now - lastTickMs) / 1000.0F;
		lastTickMs = now;

		Iterator<Toast> it = toasts.iterator();
		while (it.hasNext()) {
			Toast t = it.next();
			if (!t.tick(delta)) it.remove();
		}
	}

	/** Call from HUD render event */
	public void render(GuiGraphics g, int accentColor) {
		if (toasts.isEmpty()) return;

		Minecraft mc = Minecraft.getInstance();
		int screenW  = mc.getWindow().getGuiScaledWidth();
		int screenH  = mc.getWindow().getGuiScaledHeight();

		int totalToasts = toasts.size();
		for (int i = 0; i < totalToasts; i++) {
			Toast toast = toasts.get(totalToasts - 1 - i); // newest at bottom
			float xOff  = toast.xOffset(TOAST_W);
			float alpha = toast.alpha();

			int tx = (int)(screenW - TOAST_W - TOAST_MARGIN + xOff);
			int ty = screenH - TOAST_MARGIN - TOAST_H - i * (TOAST_H + TOAST_GAP);

			renderToast(g, toast, tx, ty, alpha, accentColor);
		}
	}

	private void renderToast(GuiGraphics g, Toast toast, int x, int y, float alpha, int accentColor) {
		Minecraft mc = Minecraft.getInstance();

		// Background
		fillRounded(g, x, y, x + TOAST_W, y + TOAST_H, RADIUS, withAlpha(0xFF18181E, alpha));

		// Left accent bar
		g.fill(x, y + 2, x + 3, y + TOAST_H - 2, withAlpha(toast.color, alpha));

		// Subtle top highlight
		g.fill(x + 3, y, x + TOAST_W, y + 1, withAlpha(0xFF2A2A38, alpha));

		// Split message into name + state (last word)
		int lastSpace = toast.message.lastIndexOf(' ');
		String name  = lastSpace >= 0 ? toast.message.substring(0, lastSpace) : toast.message;
		String state = lastSpace >= 0 ? toast.message.substring(lastSpace + 1) : "";

		g.drawString(mc.font, name,  x + 10, y + 6,  withAlpha(0xFFE8E8F0, alpha), false);
		g.drawString(mc.font, state, x + 10, y + 16, withAlpha(toast.color,  alpha), false);
	}

	private static void fillRounded(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
		if (x2 - x1 < r * 2 || y2 - y1 < r * 2) { g.fill(x1, y1, x2, y2, color); return; }
		g.fill(x1 + r, y1,     x2 - r, y2,     color);
		g.fill(x1,     y1 + r, x1 + r, y2 - r, color);
		g.fill(x2 - r, y1 + r, x2,     y2 - r, color);
		for (int i = 0; i < r; i++) {
			int len = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - (double)(r - 1 - i) * (r - 1 - i))));
			g.fill(x1 + len, y1 + i,     x1 + r, y1 + i + 1,     color);
			g.fill(x2 - r,   y1 + i,     x2 - len, y1 + i + 1,   color);
			g.fill(x1 + len, y2 - i - 1, x1 + r, y2 - i,         color);
			g.fill(x2 - r,   y2 - i - 1, x2 - len, y2 - i,       color);
		}
	}

	private static int withAlpha(int argb, float mul) {
		mul = Mth.clamp(mul, 0, 1);
		int a = (argb >>> 24) & 0xFF;
		return ((int)(a * mul) << 24) | (argb & 0x00FFFFFF);
	}
}