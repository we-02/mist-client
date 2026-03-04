package com.tmbu.tmbuclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class HsvColorPicker {
	private static final int SV_SIZE   = 100;
	private static final int HUE_H     = 10;
	private static final int HUE_GAP   = 6;
	private static final int PREVIEW_H = 16;
	private static final int PAD       = 8;
	private static final int SWATCH_S  = 10;

	public static final int TOTAL_W = SV_SIZE + PAD * 2;
	public static final int TOTAL_H = PAD + SV_SIZE + HUE_GAP + HUE_H + HUE_GAP + PREVIEW_H + HUE_GAP + SWATCH_S + PAD;

	// Preset swatches: [name, color]
	private static final int[] PRESETS = {
		0xFF3D9EFF, // Blue
		0xFF00E5FF, // Cyan
		0xFFAA55FF, // Purple
		0xFFFF55AA, // Pink
		0xFF55FF7A, // Green
		0xFFFF5555, // Red
		0xFFFFAA00, // Orange
		0xFFFFFF55, // Yellow
	};

	private float hue = 0.6F;        // 0-1
	private float saturation = 0.8F; // 0-1
	private float value = 1.0F;      // 0-1

	private boolean draggingSV  = false;
	private boolean draggingHue = false;
	private int lastDrawnHueInt = -1; // cache key

	// Cached SV pixel colors (drawn at 2px resolution)
	private final int[][] svCache = new int[SV_SIZE / 2][SV_SIZE / 2];

	public HsvColorPicker() {}

	public int getColor() {
		return hsvToRgb(hue, saturation, value) | 0xFF000000;
	}

	public void setFromColor(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255.0F;
		float g = ((argb >>  8) & 0xFF) / 255.0F;
		float b = (argb & 0xFF)         / 255.0F;
		float[] hsv = rgbToHsv(r, g, b);
		hue        = hsv[0];
		saturation = hsv[1];
		value      = hsv[2];
		lastDrawnHueInt = -1; // invalidate cache
	}

	public void render(GuiGraphics g, int px, int py, float alpha) {
		int svX = px + PAD;
		int svY = py + PAD;

		// Rebuild SV cache if hue changed
		int hueInt = (int)(hue * 360);
		if (hueInt != lastDrawnHueInt) {
			for (int xi = 0; xi < SV_SIZE / 2; xi++) {
				for (int yi = 0; yi < SV_SIZE / 2; yi++) {
					float s = xi / (float)(SV_SIZE / 2 - 1);
					float v = 1.0F - yi / (float)(SV_SIZE / 2 - 1);
					svCache[xi][yi] = hsvToRgb(hue, s, v) | 0xFF000000;
				}
			}
			lastDrawnHueInt = hueInt;
		}

		// Draw SV square at 2px resolution
		for (int xi = 0; xi < SV_SIZE / 2; xi++) {
			for (int yi = 0; yi < SV_SIZE / 2; yi++) {
				int col = withAlpha(svCache[xi][yi], alpha);
				g.fill(svX + xi * 2, svY + yi * 2, svX + xi * 2 + 2, svY + yi * 2 + 2, col);
			}
		}

		// SV crosshair
		int crossX = svX + (int)(saturation * (SV_SIZE - 1));
		int crossY = svY + (int)((1.0F - value) * (SV_SIZE - 1));
		g.fill(crossX - 3, crossY, crossX + 4, crossY + 1, withAlpha(0xFFFFFFFF, alpha));
		g.fill(crossX, crossY - 3, crossX + 1, crossY + 4, withAlpha(0xFFFFFFFF, alpha));
		g.fill(crossX - 2, crossY - 2, crossX + 3, crossY + 3, withAlpha(0x44000000, alpha));

		// Hue bar
		int hueY = svY + SV_SIZE + HUE_GAP;
		for (int xi = 0; xi < SV_SIZE; xi += 2) {
			float h = xi / (float)(SV_SIZE - 1);
			int col = withAlpha(hsvToRgb(h, 1.0F, 1.0F) | 0xFF000000, alpha);
			g.fill(svX + xi, hueY, svX + xi + 2, hueY + HUE_H, col);
		}
		// Hue thumb
		int thumbX = svX + (int)(hue * (SV_SIZE - 1));
		g.fill(thumbX - 1, hueY - 1, thumbX + 2, hueY + HUE_H + 1, withAlpha(0xFFFFFFFF, alpha));

		// Color preview
		int previewY = hueY + HUE_H + HUE_GAP;
		int previewColor = getColor();
		g.fill(svX, previewY, svX + SV_SIZE, previewY + PREVIEW_H, withAlpha(previewColor, alpha));
		g.fill(svX, previewY, svX + SV_SIZE, previewY + 1, withAlpha(0xFF2A2A38, alpha));
		g.fill(svX, previewY + PREVIEW_H - 1, svX + SV_SIZE, previewY + PREVIEW_H, withAlpha(0xFF2A2A38, alpha));

		// Subtle dark overlay on preview edges
		g.fill(svX, previewY, svX + SV_SIZE, previewY + 1, withAlpha(0x88000000, alpha));
		g.fill(svX, previewY + PREVIEW_H - 1, svX + SV_SIZE, previewY + PREVIEW_H, withAlpha(0x88000000, alpha));

		// Preset swatches
		int swatchY = previewY + PREVIEW_H + HUE_GAP;
		int totalSwatchW = PRESETS.length * (SWATCH_S + 2) - 2;
		int swatchStartX = svX + (SV_SIZE - totalSwatchW) / 2;
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = swatchStartX + i * (SWATCH_S + 2);
			g.fill(sx, swatchY, sx + SWATCH_S, swatchY + SWATCH_S, withAlpha(PRESETS[i] | 0xFF000000, alpha));
			// Selected indicator
			if (Math.abs(hsvToRgb(hue, saturation, value) - (PRESETS[i] & 0xFFFFFF)) < 0x111111) {
				g.fill(sx - 1, swatchY - 1, sx + SWATCH_S + 1, swatchY + SWATCH_S + 1, withAlpha(0xFFFFFFFF, alpha));
				g.fill(sx, swatchY, sx + SWATCH_S, swatchY + SWATCH_S, withAlpha(PRESETS[i] | 0xFF000000, alpha));
			}
		}
	}

	/** Returns true if the event was consumed */
	public boolean mouseClicked(double mouseX, double mouseY, int px, int py) {
		int svX = px + PAD;
		int svY = py + PAD;
		int hueY = svY + SV_SIZE + HUE_GAP;
		int previewY = hueY + HUE_H + HUE_GAP;
		int swatchY = previewY + PREVIEW_H + HUE_GAP;

		// SV square
		if (isIn(mouseX, mouseY, svX, svY, SV_SIZE, SV_SIZE)) {
			draggingSV = true;
			updateSV(mouseX, mouseY, svX, svY);
			return true;
		}

		// Hue bar
		if (isIn(mouseX, mouseY, svX, hueY, SV_SIZE, HUE_H)) {
			draggingHue = true;
			updateHue(mouseX, svX);
			return true;
		}

		// Swatches
		int totalSwatchW = PRESETS.length * (SWATCH_S + 2) - 2;
		int swatchStartX = svX + (SV_SIZE - totalSwatchW) / 2;
		for (int i = 0; i < PRESETS.length; i++) {
			int sx = swatchStartX + i * (SWATCH_S + 2);
			if (isIn(mouseX, mouseY, sx, swatchY, SWATCH_S, SWATCH_S)) {
				setFromColor(PRESETS[i]);
				return true;
			}
		}

		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int px, int py) {
		int svX = px + PAD;
		int svY = py + PAD;
		if (draggingSV) { updateSV(mouseX, mouseY, svX, svY); return true; }
		if (draggingHue) { updateHue(mouseX, svX); return true; }
		return false;
	}

	public void mouseReleased() {
		draggingSV  = false;
		draggingHue = false;
	}

	private void updateSV(double mouseX, double mouseY, int svX, int svY) {
		saturation = (float) Mth.clamp((mouseX - svX) / (SV_SIZE - 1), 0.0, 1.0);
		value      = 1.0F - (float) Mth.clamp((mouseY - svY) / (SV_SIZE - 1), 0.0, 1.0);
	}

	private void updateHue(double mouseX, int svX) {
		hue = (float) Mth.clamp((mouseX - svX) / (SV_SIZE - 1), 0.0, 1.0);
		lastDrawnHueInt = -1; // invalidate cache
	}

	// ─── Color math ──────────────────────────────────────────────────────────

	public static int hsvToRgb(float h, float s, float v) {
		if (s == 0) {
			int gray = (int)(v * 255);
			return (gray << 16) | (gray << 8) | gray;
		}
		h = h * 6.0F;
		int i = (int) h;
		float f = h - i;
		float p = v * (1 - s);
		float q = v * (1 - s * f);
		float t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
	}

	private static float[] rgbToHsv(float r, float g, float b) {
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h = 0, s = max == 0 ? 0 : delta / max, v = max;
		if (delta != 0) {
			if (max == r)      h = (g - b) / delta % 6;
			else if (max == g) h = (b - r) / delta + 2;
			else               h = (r - g) / delta + 4;
			h /= 6.0F;
			if (h < 0) h += 1.0F;
		}
		return new float[]{ h, s, v };
	}

	private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static int withAlpha(int argb, float mul) {
		mul = Mth.clamp(mul, 0, 1);
		int a = (argb >>> 24) & 0xFF;
		return ((int)(a * mul) << 24) | (argb & 0x00FFFFFF);
	}
}