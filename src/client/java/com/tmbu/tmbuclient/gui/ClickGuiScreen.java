package com.tmbu.tmbuclient.gui;

import com.tmbu.tmbuclient.config.ClickGuiConfig;
import com.tmbu.tmbuclient.module.Category;
import com.tmbu.tmbuclient.module.Module;
import com.tmbu.tmbuclient.module.ModuleManager;
import com.tmbu.tmbuclient.settings.BooleanSetting;
import com.tmbu.tmbuclient.settings.ColorSetting;
import com.tmbu.tmbuclient.settings.EnumSetting;
import com.tmbu.tmbuclient.settings.KeybindSetting;
import com.tmbu.tmbuclient.settings.ModeSetting;
import com.tmbu.tmbuclient.settings.Setting;
import com.tmbu.tmbuclient.settings.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ClickGuiScreen extends Screen {

	// ─── Layout ──────────────────────────────────────────────────────────────
	private static final int SIDEBAR_W      = 104;
	private static final int ROW_H          = 22;
	private static final int SETTING_H      = 22;
	private static final int HEADER_H       = 32;
	private static final int SEARCH_H       = 24;
	private static final int SETTINGS_COL_W = 160;
	private static final int GROUP_H        = 16;
	private static final int PANEL_W        = 500;
	private static final int PANEL_H        = 340;
	private static final int MIN_MARGIN     = 14;
	private static final int RADIUS         = 8;

	// ─── Colors ──────────────────────────────────────────────────────────────
	private static final int COL_BG          = 0xE8101014;
	private static final int COL_HEADER      = 0xFF13131A;
	private static final int COL_SIDEBAR     = 0xFF0E0E14;
	private static final int COL_MODULE      = 0xFF17171E;
	private static final int COL_MODULE_HOV  = 0xFF1F1F28;
	private static final int COL_MODULE_ON   = 0xFF152030;
	private static final int COL_CAT_HOV     = 0xFF1C1C26;
	private static final int COL_CAT_ACT     = 0xFF18243A;
	private static final int COL_SETTING     = 0xFF121218;
	private static final int COL_SETTING_HOV = 0xFF1A1A24;
	private static final int COL_SETTINGS_BG = 0xFF0F0F16;
	private static final int COL_TEXT        = 0xFFE8E8F2;
	private static final int COL_TEXT_DIM    = 0xFF88889A;
	private static final int COL_TEXT_OFF    = 0xFF484858;
	private static final int COL_SEPARATOR   = 0xFF1C1C28;
	private static final int COL_SEARCH_BG   = 0xFF14141C;

	private final ModuleManager moduleManager;

	private final Map<Module, Boolean> expanded   = new IdentityHashMap<>();
	private final Map<Module, Float>   expandAnim = new IdentityHashMap<>();
	private final Map<Module, Float>   enableAnim = new IdentityHashMap<>();

	private Module settingsPanelModule = null;
	private float  settingsPanelAnim   = 0.0F;

	private final HsvColorPicker colorPicker = new HsvColorPicker();
	private boolean colorPickerOpen = false;
	private float   colorPickerAnim = 0.0F;

	private String  searchQuery   = "";
	private boolean searchFocused = false;

	private Category           selectedCategory = Category.COMBAT;
	private final List<String> pinnedModules    = new ArrayList<>();

	private SliderSetting  draggingSlider;
	// For accurate slider dragging: store the track position and width at drag start
	private int dragSliderStartX;
	private int dragSliderTrackWidth;

	private KeybindSetting bindingSetting;

	private int     panelX, panelY, panelW, panelH;
	private boolean panelPositionLoaded;
	private boolean draggingPanel;
	private double  dragOffsetX, dragOffsetY;

	private float scrollOffset   = 0;
	private float scrollVelocity = 0;
	private int   contentHeight  = 0;

	// Settings panel scroll fields
	private float settingsScroll   = 0F;
	private float settingsScrollVelocity = 0F;

	private long  openStartNanos = -1L;
	private float lastScale      = 1.0F;

	private int accent;

	private static final Map<Category, String> CAT_ICONS = new java.util.EnumMap<>(Category.class);
	static {
		for (Category c : Category.values()) {
			String icon = switch (c.name()) {
				case "COMBAT"   -> "> ";
				case "MOVEMENT" -> "> ";
				case "RENDER"   -> "> ";
				case "MISC"     -> "> ";
				default         -> "> ";
			};
			CAT_ICONS.put(c, icon);
		}
	}

	public ClickGuiScreen(ModuleManager moduleManager) {
		super(Component.literal("Mist"));
		this.moduleManager = moduleManager;
		this.accent = moduleManager.getAccentColor();
		colorPicker.setFromColor(accent);

		ClickGuiConfig cfg = moduleManager.getClickGuiConfig();
		if (cfg != null) {
			if (cfg.panelX() != null && cfg.panelY() != null) {
				panelX = cfg.panelX(); panelY = cfg.panelY();
				panelPositionLoaded = true;
			}
			if (cfg.selectedCategory() != null) {
				try { selectedCategory = Category.valueOf(cfg.selectedCategory()); }
				catch (IllegalArgumentException ignored) {}
			}
			if (cfg.pinnedModules() != null) pinnedModules.addAll(cfg.pinnedModules());
		}
	}

	@Override
	protected void init() {
		panelW = Math.min(PANEL_W, this.width  - MIN_MARGIN * 2);
		panelH = Math.min(PANEL_H, this.height - MIN_MARGIN * 2);
		if (!panelPositionLoaded) {
			panelX = (this.width  - panelW) / 2;
			panelY = (this.height - panelH) / 2;
		}
		clampPanel();
	}

	@Override public void added() { openStartNanos = System.nanoTime(); }
	@Override public boolean isPauseScreen() { return false; }

	@Override
	public void removed() {
		moduleManager.setClickGuiConfig(new ClickGuiConfig(
			panelX, panelY, selectedCategory.name(),
			accent, new ArrayList<>(pinnedModules)
		));
		moduleManager.saveConfigNow();
	}

	// ─── Render ──────────────────────────────────────────────────────────────

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		scrollOffset  += scrollVelocity;
		scrollVelocity *= 0.82F;
		scrollOffset   = Mth.clamp(scrollOffset, 0, Math.max(0, contentHeight - moduleListH()));

		settingsScroll += settingsScrollVelocity;
		settingsScrollVelocity *= 0.82F;

		float tgt = settingsPanelModule != null ? 1.0F : 0.0F;
		settingsPanelAnim = approach(settingsPanelAnim, tgt, 0.20F);
		float cpTgt = colorPickerOpen ? 1.0F : 0.0F;
		colorPickerAnim   = approach(colorPickerAnim, cpTgt, 0.22F);

		float open  = openProgress();
		float eased = easeOutCubic(open);
		lastScale   = 0.88F + 0.12F * eased;

		g.fill(0, 0, this.width, this.height, withAlpha(0xFF000000, 0.62F * eased));

		int cx = panelX + panelW / 2;
		int cy = panelY + panelH / 2;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(lastScale, lastScale);
		g.pose().translate(-cx, -cy);

		int mx = toPanelSpaceX(mouseX, cx, lastScale);
		int my = toPanelSpaceY(mouseY, cy, lastScale);

		renderPanel(g, mx, my, eased);

		if (colorPickerAnim > 0.01F) {
			renderColorPickerPopover(g, mx, my, colorPickerAnim * eased);
		}

		g.pose().popMatrix();
	}

	private void renderPanel(GuiGraphics g, int mx, int my, float a) {
		int px = panelX, py = panelY, pw = panelW, ph = panelH;

		fillRounded(g, px, py, px + pw, py + ph, RADIUS, withAlpha(COL_BG, a));
		g.fill(px + 1, py + 1, px + pw - 1, py + 2, withAlpha(0xFF2A2A40, a));
		g.fill(px + 1, py + ph - 2, px + pw - 1, py + ph - 1, withAlpha(0xFF0A0A14, a));

		fillRounded(g, px, py, px + pw, py + HEADER_H, RADIUS, withAlpha(COL_HEADER, a));
		g.fill(px, py + HEADER_H - RADIUS, px + pw, py + HEADER_H, withAlpha(COL_HEADER, a));
		g.fill(px, py, px + pw / 3, py + HEADER_H, withAlpha(0x08FFFFFF, a));
		g.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, withAlpha(accent, a * 0.6F));

		g.drawString(font, "Mist Client", px + 10, py + 11, withAlpha(COL_TEXT, a), false);
		g.drawString(font, selectedCategory.getDisplayName(),
			px + 10 + font.width("Mist Client") + 8, py + 12, withAlpha(accent, a * 0.8F), false);

		int swatchX = px + pw - 52;
		int swatchY = py + 8;
		boolean swatchHov = isIn(mx, my, swatchX, swatchY, 16, 16);
		fillRounded(g, swatchX - 2, swatchY - 2, swatchX + 18, swatchY + 18, 4, withAlpha(0xFF222232, a));
		fillRounded(g, swatchX, swatchY, swatchX + 16, swatchY + 16, 3, withAlpha(accent, a));
		if (swatchHov) g.fill(swatchX - 1, swatchY - 1, swatchX + 17, swatchY + 17, withAlpha(0x33FFFFFF, a));

		int closeX = px + pw - 22;
		int closeY = py + 9;
		boolean closeHov = isIn(mx, my, closeX, closeY, 14, 14);
		fillRounded(g, closeX, closeY, closeX + 14, closeY + 14, 3,
			withAlpha(closeHov ? 0xFFAA2222 : 0xFF2A1A1A, a));
		g.drawString(font, "✕", closeX + 3, closeY + 3, withAlpha(0xFFFF6666, a), false);

		g.fill(px, py + HEADER_H, px + SIDEBAR_W, py + ph, withAlpha(COL_SIDEBAR, a));
		g.fill(px + SIDEBAR_W, py + HEADER_H, px + SIDEBAR_W + 1, py + ph, withAlpha(accent, a * 0.25F));

		renderSidebar(g, px, py + HEADER_H, ph - HEADER_H, mx, my, a);

		int searchX = px + SIDEBAR_W + 1;
		int searchY = py + HEADER_H;
		int searchW = panelW - SIDEBAR_W - 1 - (settingsPanelModule != null ? SETTINGS_COL_W : 0);
		renderSearchBar(g, searchX, searchY, searchW, mx, my, a);

		int listX = searchX;
		int listY = searchY + SEARCH_H;
		int listW = searchW;
		int listH = moduleListH();
		renderModuleList(g, listX, listY, listW, listH, mx, my, a);

		if (settingsPanelAnim > 0.01F) {
			int spX = px + SIDEBAR_W + 1 + searchW;
			renderSettingsPanel(g, spX, py + HEADER_H, SETTINGS_COL_W, ph - HEADER_H, mx, my, a * settingsPanelAnim);
		}
	}

	private void renderSidebar(GuiGraphics g, int x, int y, int h, int mx, int my, float a) {
		int ry = y + 10;
		for (Category cat : Category.values()) {
			List<Module> catMods = sortedModules(cat);
			boolean hov = isIn(mx, my, x + 4, ry, SIDEBAR_W - 8, ROW_H);
			boolean act = selectedCategory == cat && searchQuery.isEmpty();

			int bg = act ? withAlpha(COL_CAT_ACT, a) : hov ? withAlpha(COL_CAT_HOV, a) : 0;
			if (bg != 0) fillRounded(g, x + 4, ry, x + SIDEBAR_W - 4, ry + ROW_H, 4, bg);

			if (act) {
				g.fill(x + 2, ry + 4, x + 4, ry + ROW_H - 4, withAlpha(accent, a));
				g.fill(x + 2, ry + 4, x + 5, ry + ROW_H - 4, withAlpha(accent, a * 0.3F));
			}

			String icon = CAT_ICONS.getOrDefault(cat, "• ");
			int textCol = act ? withAlpha(COL_TEXT, a) : hov ? withAlpha(COL_TEXT, a * 0.9F) : withAlpha(COL_TEXT_DIM, a);
			g.drawString(font, icon + cat.getDisplayName(), x + 8, ry + 7, textCol, false);

			long enabled = catMods.stream().filter(Module::isEnabled).count();
			int total = catMods.size();
			if (total > 0) {
				String badge = enabled + "/" + total;
				int bw = font.width(badge) + 6;
				int bx = x + SIDEBAR_W - bw - 6;
				g.fill(bx, ry + 6, bx + bw, ry + ROW_H - 6,
					withAlpha(act ? dimColor(accent, 0.3F) : 0xFF1C1C28, a));
				g.drawString(font, badge, bx + 3, ry + 7,
					withAlpha(act ? accent : COL_TEXT_OFF, a), false);
			}
			ry += ROW_H + 3;
		}
	}

	private void renderSearchBar(GuiGraphics g, int x, int y, int w, int mx, int my, float a) {
		g.fill(x, y, x + w, y + SEARCH_H, withAlpha(COL_SEARCH_BG, a));
		g.fill(x, y + SEARCH_H - 1, x + w, y + SEARCH_H, withAlpha(COL_SEPARATOR, a));
		fillRounded(g, x + 4, y + 4, x + w - 4, y + SEARCH_H - 4, 3,
			withAlpha(searchFocused ? 0xFF1C1C2C : 0xFF161620, a));
		if (searchFocused)
			g.fill(x + 4, y + SEARCH_H - 5, x + w - 4, y + SEARCH_H - 4, withAlpha(accent, a * 0.7F));
		String display = searchQuery.isEmpty() && !searchFocused ? "⌕ Search modules..." : searchQuery + (searchFocused ? "|" : "");
		int textCol = searchQuery.isEmpty() && !searchFocused ? withAlpha(COL_TEXT_OFF, a) : withAlpha(COL_TEXT, a);
		g.drawString(font, display, x + 10, y + 8, textCol, false);
	}

	private void renderModuleList(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float a) {
		List<Module> modules = getFilteredModules();
		g.enableScissor(x, y, x + w, y + h);

		int curY = y - (int) scrollOffset;
		contentHeight = 0;

		for (int i = 0; i < modules.size(); i++) {
			Module mod = modules.get(i);
			boolean pinned = pinnedModules.contains(mod.getName());

			float enTgt = mod.isEnabled() ? 1.0F : 0.0F;
			float enCur = enableAnim.getOrDefault(mod, enTgt);
			enCur = approach(enCur, enTgt, 0.15F);
			enableAnim.put(mod, enCur);

			float exTgt = expanded.getOrDefault(mod, false) ? 1.0F : 0.0F;
			float exCur = expandAnim.getOrDefault(mod, 0.0F);
			exCur = approach(exCur, exTgt, 0.18F);
			expandAnim.put(mod, exCur);

			boolean hov = isIn(mx, my + (int) scrollOffset, x, curY + (int) scrollOffset, w, ROW_H);
			boolean on  = mod.isEnabled();
			boolean sel = settingsPanelModule == mod;

			int bgOff = hov ? COL_MODULE_HOV : COL_MODULE;
			int bg    = lerpColor(bgOff, COL_MODULE_ON, enCur);
			g.fill(x, curY, x + w, curY + ROW_H, withAlpha(bg, a));

			if (enCur > 0.01F)
				g.fill(x, curY, x + w, curY + ROW_H, withAlpha(dimColor(accent, 0.06F * enCur), a));

			int barW = (int)(3 * enCur);
			if (barW > 0) {
				g.fill(x, curY, x + barW, curY + ROW_H, withAlpha(accent, a * enCur));
				g.fill(x, curY, x + barW + 3, curY + ROW_H, withAlpha(dimColor(accent, 0.2F * enCur), a));
			}

			if (sel) g.fill(x + w - 3, curY, x + w, curY + ROW_H, withAlpha(accent, a * 0.7F));
			if (pinned) g.drawString(font, "★", x + 6, curY + 7, withAlpha(accent, a * 0.8F), false);

			int nameX = pinned ? x + 18 : x + 8;
			String name = mod.getName();
			if (!searchQuery.isEmpty()) {
				String lower = name.toLowerCase();
				int matchIdx = lower.indexOf(searchQuery.toLowerCase());
				if (matchIdx >= 0) {
					String before = name.substring(0, matchIdx);
					String match  = name.substring(matchIdx, matchIdx + searchQuery.length());
					String after  = name.substring(matchIdx + searchQuery.length());
					int bx = nameX;
					g.drawString(font, before, bx, curY + 7, withAlpha(COL_TEXT_DIM, a), false); bx += font.width(before);
					g.drawString(font, match,  bx, curY + 7, withAlpha(accent, a), false);        bx += font.width(match);
					g.drawString(font, after,  bx, curY + 7, withAlpha(COL_TEXT_DIM, a), false);
				} else {
					g.drawString(font, name, nameX, curY + 7, withAlpha(COL_TEXT_DIM, a * 0.5F), false);
				}
			} else {
				g.drawString(font, name, nameX, curY + 7, withAlpha(on ? COL_TEXT : COL_TEXT_DIM, a), false);
			}

			String kb = mod.getKeybindSetting().getDisplayName();
			g.drawString(font, kb, x + w - font.width(kb) - 8, curY + 7, withAlpha(COL_TEXT_OFF, a), false);

			if (settingsPanelModule == null) {
				String arrow = exCur > 0.5F ? "▾" : "▸";
				g.drawString(font, arrow, x + w - font.width(kb) - font.width(arrow) - 14, curY + 7,
					withAlpha(exCur > 0.5F ? accent : COL_TEXT_OFF, a), false);
			}

			curY += ROW_H;
			contentHeight += ROW_H;

			if (i < modules.size() - 1 && exCur < 0.02F)
				g.fill(x + 6, curY, x + w - 6, curY + 1, withAlpha(COL_SEPARATOR, a));

			if (settingsPanelModule == null && exCur > 0.01F) {
				String lastGrp = null;
				for (Setting<?> s : mod.getSettings()) {
					if (!s.isVisible()) continue;
					String grp = s.getGroup();
					if (grp != null && !grp.equals(lastGrp)) {
						int gh = (int)(GROUP_H * exCur);
						if (gh >= 2) {
							g.fill(x + 2, curY, x + w, curY + gh, withAlpha(0xFF0D0D14, a * exCur));
							g.fill(x + 6, curY + gh - 1, x + w - 6, curY + gh, withAlpha(accent, a * exCur * 0.3F));
							g.drawString(font, "▸ " + grp, x + 6, curY + 2, withAlpha(accent, a * exCur * 0.9F), false);
						}
						curY += gh;
						contentHeight += gh;
						lastGrp = grp;
					}
					int sh = (int)(SETTING_H * exCur);
					if (sh < 2) continue;
					boolean shov = isIn(mx, my + (int) scrollOffset, x + 2, curY + (int) scrollOffset, w - 2, sh);
					g.fill(x + 2, curY, x + w, curY + sh, withAlpha(shov ? COL_SETTING_HOV : COL_SETTING, a * exCur));
					renderSettingRow(g, s, mod, x + 2, curY, w - 2, sh, mx, my + (int) scrollOffset, a * exCur);
					curY += sh;
					contentHeight += sh;
				}
				g.fill(x + 6, curY, x + w - 6, curY + 1, withAlpha(COL_SEPARATOR, a));
			}
		}

		g.disableScissor();

		if (contentHeight > h) {
			float frac = (float) h / contentHeight;
			int barH = Math.max(20, (int)(h * frac));
			int barY = y + (int)(scrollOffset / contentHeight * h);
			int barX = x + w - 3;
			g.fill(barX, y, barX + 2, y + h, withAlpha(0xFF1A1A28, a));
			g.fill(barX, barY, barX + 2, barY + barH, withAlpha(dimColor(accent, 0.6F), a));
		}
	}

	private void renderSettingsPanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float a) {
		if (settingsPanelModule == null) return;

		// background
		g.fill(x, y, x + w, y + h, withAlpha(COL_SETTINGS_BG, a));
		g.fill(x, y, x + 1, y + h, withAlpha(accent, a * 0.3F));

		// header
		g.fill(x, y, x + w, y + HEADER_H, withAlpha(0xFF0F0F18, a));
		g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, withAlpha(accent, a * 0.4F));
		g.drawString(font, settingsPanelModule.getName(), x + 8, y + 12, withAlpha(accent, a), false);

		// close button
		int spCloseX = x + w - 16;
		int spCloseY = y + 9;
		boolean spCloseHov = isIn(mx, my, spCloseX, spCloseY, 14, 14);
		fillRounded(g, spCloseX, spCloseY, spCloseX + 14, spCloseY + 14, 3,
			withAlpha(spCloseHov ? 0xFFAA2222 : 0xFF2A1A1A, a));
		g.drawString(font, "✕", spCloseX + 3, spCloseY + 3, withAlpha(0xFFFF6666, a), false);

		// description
		String desc = settingsPanelModule.getDescription();
		int descLines = 0;
		if (desc != null && !desc.isEmpty()) {
			List<String> lines = wrapText(desc, w - 16);
			descLines = lines.size();
			int ly = y + HEADER_H + 4;
			for (String line : lines) {
				g.drawString(font, line, x + 8, ly, withAlpha(COL_TEXT_DIM, a * 0.8F), false);
				ly += 10;
			}
		}

		// separator after description
		int settingsStartY = y + HEADER_H + 4 + descLines * 10 + 4 + 1 + 4;
		g.fill(x + 4, settingsStartY - 5, x + w - 4, settingsStartY - 4, withAlpha(COL_SEPARATOR, a));

		// ---- scrollable settings area ----
		g.enableScissor(x + 2, settingsStartY, x + w - 2, y + h - 2);

		List<Setting<?>> settingList = settingsPanelModule.getSettings();

		// First pass: compute total height including group headers, skipping hidden settings
		int totalHeight = 0;
		List<Object> renderItems = new ArrayList<>(); // String = group header, Setting = setting row
		String lastGroup = null;
		for (Setting<?> s : settingList) {
			if (!s.isVisible()) continue;
			String grp = s.getGroup();
			if (grp != null && !grp.equals(lastGroup)) {
				renderItems.add(grp);
				totalHeight += GROUP_H;
				lastGroup = grp;
			}
			renderItems.add(s);
			totalHeight += computeSettingRowHeight(s, w - 18);
		}

		// Clamp scroll
		settingsScroll = Mth.clamp(settingsScroll, 0, Math.max(0, totalHeight - (y + h - settingsStartY - 2)));

		int curY = settingsStartY - (int) settingsScroll;
		int itemIndex = 0;

		for (Object item : renderItems) {
			if (item instanceof String groupName) {
				// Render group header
				g.fill(x + 2, curY, x + w, curY + GROUP_H, withAlpha(0xFF0D0D14, a));
				g.fill(x + 6, curY + GROUP_H - 1, x + w - 6, curY + GROUP_H, withAlpha(accent, a * 0.3F));
				g.drawString(font, "▸ " + groupName, x + 6, curY + 4, withAlpha(accent, a * 0.9F), false);
				curY += GROUP_H;
				itemIndex++;
				continue;
			}

			Setting<?> s = (Setting<?>) item;
			int rowHeight = computeSettingRowHeight(s, w - 18);
			int resetX = x + w - 14;

			// background
			boolean shov = isIn(mx, my, x + 2, curY, w - 2, rowHeight);
			g.fill(x + 2, curY, x + w, curY + rowHeight,
				withAlpha(shov ? COL_SETTING_HOV : COL_SETTING, a));

			// reset button (centered vertically in this row)
			int resetY = curY + (rowHeight - 12) / 2;
			boolean resetHov = isIn(mx, my, resetX, resetY + 2, 12, 12);
			g.drawString(font, "↺", resetX + 2, resetY + 2,
				withAlpha(resetHov ? accent : COL_TEXT_OFF, a), false);

			// render the setting (actual drawn height)
			renderSettingRow(g, s, settingsPanelModule, x + 2, curY, w - 18, rowHeight, mx, my, a);

			curY += rowHeight;
			itemIndex++;

			// separator between settings (skip after last)
			if (itemIndex < renderItems.size()) {
				Object next = renderItems.get(itemIndex);
				if (!(next instanceof String)) {
					g.fill(x + 6, curY - 1, x + w - 6, curY, withAlpha(COL_SEPARATOR, a * 0.5F));
				}
			}
		}

		g.disableScissor();

		// scrollbar
		if (totalHeight > (y + h - settingsStartY - 2)) {
			int barH = Math.max(20, (int)((float)(y + h - settingsStartY - 2) / totalHeight * (y + h - settingsStartY - 2)));
			int barY = settingsStartY + (int)(settingsScroll / totalHeight * (y + h - settingsStartY - 2));
			int barX = x + w - 3;
			g.fill(barX, settingsStartY, barX + 2, y + h - 2, withAlpha(0xFF1A1A28, a));
			g.fill(barX, barY, barX + 2, barY + barH, withAlpha(dimColor(accent, 0.6F), a));
		}
	}

	private int computeSettingRowHeight(Setting<?> s, int availableWidth) {
		String name = s.getName();
		int nameWidth = font.width(name);
		//int baseHeight = 10 + 4 * 2; // line height + padding top/bottom

		// If name fits on one line, use SETTING_H (which is 22)
		if (nameWidth <= availableWidth - 60) { // leave room for control
			return SETTING_H;
		}

		// Otherwise wrap text and calculate lines
		List<String> lines = wrapText(name, availableWidth - 60);
		int lineCount = lines.size();

		// For sliders, we need extra space for the slider track (bottom)
		if (s instanceof SliderSetting) {
			// name at top, slider at bottom – need at least lineCount*10 + 14
			return Math.max(SETTING_H, lineCount * 10 + 14);
		} else {
			// For other settings, name centered, control also centered – need enough vertical space
			// We'll use lineCount*10 + padding*2, but ensure at least SETTING_H
			return Math.max(SETTING_H, lineCount * 10 + 8);
		}
	}

	private void renderSettingRow(GuiGraphics g, Setting<?> s, Module mod, int x, int y, int w, int h, int mx, int my, float a) {
		if (h < 4) return;

		int uw = w; // usable width (reset button already excluded)
		int nameX = x + 6;
		int nameMaxWidth = uw - 60; // leave space for control
		String name = s.getName();

		// Wrap name if needed
		List<String> nameLines;
		if (font.width(name) <= nameMaxWidth) {
			nameLines = List.of(name);
		} else {
			nameLines = wrapText(name, nameMaxWidth);
		}

		if (s instanceof BooleanSetting bs) {
			// Draw name lines (centered vertically in the row)
			int totalNameHeight = nameLines.size() * 10;
			int nameY = y + (h - totalNameHeight) / 2;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			int pillW = 24, pillH = 10;
			int pillX = x + uw - pillW - 4;
			int pillY = y + (h - pillH) / 2;
			boolean on = bs.getValue();
			fillRounded(g, pillX, pillY, pillX + pillW, pillY + pillH, pillH / 2,
				withAlpha(on ? accent : 0xFF2A2A3A, a));
			int thumbX = on ? pillX + pillW - pillH + 1 : pillX + 1;
			fillRounded(g, thumbX, pillY + 1, thumbX + pillH - 2, pillY + pillH - 1, (pillH - 2) / 2,
				withAlpha(0xFFFFFFFF, a));
			return;
		}

		if (s instanceof SliderSetting ss) {
			// Draw name lines at top
			int nameY = y + 3;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			double frac = (ss.getValue() - ss.getMin()) / (ss.getMax() - ss.getMin());
			String val = formatSlider(ss.getValue(), ss.getStep());
			// Value at top right
			g.drawString(font, val, x + uw - font.width(val) - 4, y + 3, withAlpha(accent, a), false);

			int bx = x + 6, by = y + h - 7, bw = uw - 10;
			fillRounded(g, bx, by, bx + bw, by + 4, 2, withAlpha(0xFF222234, a));
			fillRounded(g, bx, by, bx + (int)(bw * frac), by + 4, 2, withAlpha(accent, a));
			int tx = bx + (int)(bw * frac) - 3;
			fillRounded(g, tx, by - 2, tx + 6, by + 6, 3, withAlpha(COL_TEXT, a));
			fillRounded(g, tx + 1, by - 1, tx + 5, by + 5, 2, withAlpha(accent, a));
			return;
		}

		if (s instanceof KeybindSetting ks) {
			// Draw name lines centered
			int totalNameHeight = nameLines.size() * 10;
			int nameY = y + (h - totalNameHeight) / 2;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			String label = bindingSetting == ks ? "[ Press key ]" : ks.getDisplayName();
			boolean binding = bindingSetting == ks;
			int lc = binding ? withAlpha(accent, a) : withAlpha(COL_TEXT_DIM, a);
			int labelWidth = font.width(label);
			int labelX = x + uw - labelWidth - 4;
			int labelY = y + (h - 10) / 2; // center the single line
			if (binding) {
				fillRounded(g, labelX - 4, labelY - 2, labelX + labelWidth + 4, labelY + 10, 3,
					withAlpha(dimColor(accent, 0.2F), a));
			}
			g.drawString(font, label, labelX, labelY, lc, false);
			return;
		}

		if (s instanceof ModeSetting ms) {
			// Draw name lines centered
			int totalNameHeight = nameLines.size() * 10;
			int nameY = y + (h - totalNameHeight) / 2;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			String mode = "‹ " + ms.getMode() + " ›";
			g.drawString(font, mode, x + uw - font.width(mode) - 4, y + (h - 10) / 2,
				withAlpha(COL_TEXT_DIM, a), false);
		}

		if (s instanceof EnumSetting<?> es) {
			int totalNameHeight = nameLines.size() * 10;
			int nameY = y + (h - totalNameHeight) / 2;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			String mode = "‹ " + es.getMode() + " ›";
			g.drawString(font, mode, x + uw - font.width(mode) - 4, y + (h - 10) / 2,
				withAlpha(COL_TEXT_DIM, a), false);
			return;
		}

		// ─── ColorSetting rendering ──────────────────────────────────────────────
		if (s instanceof ColorSetting cs) {
			// Draw name lines (centered vertically)
			int totalNameHeight = nameLines.size() * 10;
			int nameY = y + (h - totalNameHeight) / 2;
			for (String line : nameLines) {
				g.drawString(font, line, nameX, nameY, withAlpha(COL_TEXT, a), false);
				nameY += 10;
			}

			// Draw color swatch
			int swatchSize = 14;
			int swatchX = x + uw - swatchSize - 4;
			int swatchY = y + (h - swatchSize) / 2;
			int color = cs.getValue();

			// Background (outline)
			fillRounded(g, swatchX - 1, swatchY - 1,
						swatchX + swatchSize + 1, swatchY + swatchSize + 1,
						2, withAlpha(0xFF222232, a));
			// Actual color square
			fillRounded(g, swatchX, swatchY,
						swatchX + swatchSize, swatchY + swatchSize,
						2, withAlpha(color, a));
			return;
		}
	}

	private void renderColorPickerPopover(GuiGraphics g, int mx, int my, float a) {
		int cpW = HsvColorPicker.TOTAL_W + 4;
		int cpH = HsvColorPicker.TOTAL_H + 4;
		int swatchX = panelX + panelW - 52;
		int cpX = Mth.clamp(swatchX - cpW / 2 + 8, panelX + 4, panelX + panelW - cpW - 4);
		int cpY = panelY + HEADER_H + 4;

		fillRounded(g, cpX, cpY, cpX + cpW, cpY + cpH, RADIUS, withAlpha(0xFF14141E, a));
		g.fill(cpX, cpY, cpX + cpW, cpY + 1, withAlpha(0xFF2A2A40, a));
		g.fill(cpX, cpY + 1, cpX + 1, cpY + cpH - 1, withAlpha(0xFF2A2A40, a));
		g.fill(cpX + cpW - 1, cpY + 1, cpX + cpW, cpY + cpH - 1, withAlpha(0xFF1A1A28, a));
		g.fill(cpX, cpY + cpH - 1, cpX + cpW, cpY + cpH, withAlpha(0xFF1A1A28, a));
		colorPicker.render(g, cpX + 2, cpY + 2, a);
	}

	// ─── Input ───────────────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int cx = panelX + panelW / 2;
		int cy = panelY + panelH / 2;
		int mx = toPanelSpaceX(event.x(), cx, lastScale);
		int my = toPanelSpaceY(event.y(), cy, lastScale);
		int btn = event.button();

		// Color picker popover
		if (colorPickerOpen && colorPickerAnim > 0.1F) {
			int cpW = HsvColorPicker.TOTAL_W + 4;
			int cpH = HsvColorPicker.TOTAL_H + 4;
			int swatchX = panelX + panelW - 52;
			int cpX = Mth.clamp(swatchX - cpW / 2 + 8, panelX + 4, panelX + panelW - cpW - 4);
			int cpY = panelY + HEADER_H + 4;
			if (colorPicker.mouseClicked(mx, my, cpX + 2, cpY + 2)) {
				accent = colorPicker.getColor();
				moduleManager.setAccentColor(accent);
				return true;
			}
			if (!isIn(mx, my, cpX, cpY, cpW, cpH)) {
				colorPickerOpen = false;
				return true;
			}
			return true;
		}

		// Main panel close button
		if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && isIn(mx, my, panelX + panelW - 22, panelY + 9, 14, 14)) {
			this.onClose(); return true;
		}

		// Accent swatch
		if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && isIn(mx, my, panelX + panelW - 52, panelY + 8, 16, 16)) {
			colorPickerOpen = !colorPickerOpen; return true;
		}

		// Header drag
		if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && isIn(mx, my, panelX, panelY, panelW - 60, HEADER_H)) {
			draggingPanel = true; dragOffsetX = mx - panelX; dragOffsetY = my - panelY; return true;
		}

		// Search bar
		if (isIn(mx, my, panelX + SIDEBAR_W + 5, panelY + HEADER_H + 4, moduleColumnW() - 10, SEARCH_H - 8)) {
			searchFocused = true; return true;
		} else {
			searchFocused = false;
		}

		// Sidebar categories
		int catY = panelY + HEADER_H + 10;
		for (Category cat : Category.values()) {
			if (isIn(mx, my, panelX + 4, catY, SIDEBAR_W - 8, ROW_H)) {
				if (selectedCategory != cat || !searchQuery.isEmpty()) {
					selectedCategory = cat;
					searchQuery = "";
					scrollOffset = 0;
					scrollVelocity = 0;
					settingsPanelModule = null;
					moduleManager.requestSave();
				}
				return true;
			}
			catY += ROW_H + 3;
		}

		// Settings panel X close button — checked before anything else so it
		// always works regardless of where the panel content extends to
		if (settingsPanelModule != null) {
			int listW = panelW - SIDEBAR_W - 1 - SETTINGS_COL_W;
			int spX   = panelX + SIDEBAR_W + 1 + listW;
			int spCloseX = spX + SETTINGS_COL_W - 16;
			int spCloseY = panelY + HEADER_H + 9;
			if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && isIn(mx, my, spCloseX, spCloseY, 14, 14)) {
				settingsPanelModule = null;
				return true;
			}
		}

		// Settings panel content clicks — handle all clicks inside the settings
		// panel column without any boundary restriction (no close-on-outside)
		if (settingsPanelModule != null) {
			int listW = panelW - SIDEBAR_W - 1 - SETTINGS_COL_W;
			int spX   = panelX + SIDEBAR_W + 1 + listW;
			if (isIn(mx, my, spX, panelY + HEADER_H, SETTINGS_COL_W, panelH - HEADER_H)) {
				handleSettingsPanelClick(mx, my, spX, btn);
				return true;
			}
		}

		// Module list
		int listX = panelX + SIDEBAR_W + 1;
		int listY = panelY + HEADER_H + SEARCH_H;
		int listW = panelW - SIDEBAR_W - 1 - (settingsPanelModule != null ? SETTINGS_COL_W : 0);
		int listH = moduleListH();

		if (!isIn(mx, my, listX, listY, listW, listH))
			return super.mouseClicked(event, doubleClick);

		int curY = listY - (int) scrollOffset;
		for (Module mod : getFilteredModules()) {
			if (isIn(mx, my, listX, curY, listW, ROW_H)) {
				if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					mod.toggle();
					moduleManager.requestSave();
				} else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					// When opening a new module, reset its settings panel scroll
					if (settingsPanelModule != mod) {
						settingsScroll = 0;
						settingsScrollVelocity = 0;
					}
					settingsPanelModule = settingsPanelModule == mod ? null : mod;
					if (!searchQuery.isEmpty()) togglePin(mod);
				}
				return true;
			}
			curY += ROW_H;

			float exCur = expandAnim.getOrDefault(mod, expanded.getOrDefault(mod, false) ? 1.0F : 0.0F);
			if (settingsPanelModule == null && exCur > 0.01F) {
				String lastGrp = null;
				for (Setting<?> s : mod.getSettings()) {
					if (!s.isVisible()) continue;
					String grp = s.getGroup();
					if (grp != null && !grp.equals(lastGrp)) {
						curY += (int)(GROUP_H * exCur);
						lastGrp = grp;
					}
					int sh = (int)(SETTING_H * exCur);
					if (sh < 2) { curY += sh; continue; }
					if (isIn(mx, my, listX + 2, curY, listW - 2, sh)) {
						handleSettingClick(s, btn, mx, listX + 2, listW - 2, mod, my);
						return true;
					}
					curY += sh;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	// ─── Settings panel Y helpers ─────────────────────────────────────────────

	private int settingsPanelContentStartY() {
		List<String> descLines = wrapText(
			settingsPanelModule != null && settingsPanelModule.getDescription() != null
				? settingsPanelModule.getDescription() : "",
			SETTINGS_COL_W - 16
		);
		return panelY + HEADER_H + HEADER_H + 4 + descLines.size() * 10 + 4 + 1 + 4;
	}

	private void handleSettingsPanelClick(int mx, int my, int spX, int btn) {
		if (settingsPanelModule == null || btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

		int settingsStartY = settingsPanelContentStartY();
		int adjustedMy = my + (int) settingsScroll;

		// Build the same render items list as the render pass
		List<Setting<?>> allSettings = settingsPanelModule.getSettings();
		int availableWidth = SETTINGS_COL_W - 18;
		String lastGroup = null;

		int settingY = settingsStartY;
		for (Setting<?> s : allSettings) {
			if (!s.isVisible()) continue;

			String grp = s.getGroup();
			if (grp != null && !grp.equals(lastGroup)) {
				settingY += GROUP_H; // skip group header
				lastGroup = grp;
			}

			int rowHeight = computeSettingRowHeight(s, availableWidth);

			if (adjustedMy >= settingY && adjustedMy < settingY + rowHeight) {
				int resetX = spX + SETTINGS_COL_W - 14;
				int resetY = settingY - (int) settingsScroll + (rowHeight - 12) / 2;
				if (isIn(mx, my, resetX, resetY + 2, 12, 12)) {
					s.resetToDefault();
					moduleManager.requestSave();
					return;
				}
				handleSettingClick(s, btn, mx, spX + 2, SETTINGS_COL_W - 4, settingsPanelModule, adjustedMy);
				return;
			}
			settingY += rowHeight;
		}
	}
	
	private void handleSettingClick(Setting<?> s, int btn, int mx, int x, int w, Module mod, int adjustedMy) {
		if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
		if (s instanceof BooleanSetting bs)      { bs.toggle(); moduleManager.requestSave(); }
		else if (s instanceof SliderSetting ss)  {
			draggingSlider = ss;
			// Store the exact track position and width for accurate dragging
			dragSliderStartX = x + 6; // left edge of track
			dragSliderTrackWidth = w - 28; // track width (w includes reset area)
		}
		else if (s instanceof KeybindSetting ks) { bindingSetting = ks; }
		else if (s instanceof ModeSetting ms)    { ms.next(); moduleManager.requestSave(); }
		else if (s instanceof EnumSetting<?> es) { es.next(); moduleManager.requestSave(); }
		// ─── ColorSetting click handling ────────────────────────────────────────
		else if (s instanceof ColorSetting cs) {
			Minecraft.getInstance().setScreen(new ColorPickerScreen(cs.getValue(),
				newColor -> {
					cs.setValue(newColor);
					moduleManager.requestSave();
				}));
		}
	}

	private void togglePin(Module mod) {
		String name = mod.getName();
		if (pinnedModules.contains(name)) pinnedModules.remove(name);
		else pinnedModules.add(name);
		moduleManager.setPinnedModules(pinnedModules);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		int cx = panelX + panelW / 2;
		int cy = panelY + panelH / 2;
		int mx = toPanelSpaceX(event.x(), cx, lastScale);
		int my = toPanelSpaceY(event.y(), cy, lastScale);

		if (colorPickerOpen) {
			int cpW = HsvColorPicker.TOTAL_W + 4;
			int swatchX = panelX + panelW - 52;
			int cpX = Mth.clamp(swatchX - cpW / 2 + 8, panelX + 4, panelX + panelW - cpW - 4);
			int cpY = panelY + HEADER_H + 4;
			if (colorPicker.mouseDragged(mx, my, cpX + 2, cpY + 2)) {
				accent = colorPicker.getColor();
				moduleManager.setAccentColor(accent);
				return true;
			}
		}

		if (draggingPanel) {
			panelX = (int) Math.round(mx - dragOffsetX);
			panelY = (int) Math.round(my - dragOffsetY);
			clampPanel(); return true;
		}
		if (draggingSlider != null) {
			// Use stored track start and width for precise value mapping
			double frac = Mth.clamp((mx - dragSliderStartX) / (double) dragSliderTrackWidth, 0.0, 1.0);
			draggingSlider.setValue(draggingSlider.getMin() + (draggingSlider.getMax() - draggingSlider.getMin()) * frac);
			moduleManager.requestSave();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		colorPicker.mouseReleased();
		if (draggingPanel) moduleManager.requestSave();
		draggingPanel  = false;
		draggingSlider = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		int cx = panelX + panelW / 2;
		int cy = panelY + panelH / 2;
		int mx = toPanelSpaceX(x, cx, lastScale);
		int my = toPanelSpaceY(y, cy, lastScale);

		// Settings panel scroll
		if (settingsPanelModule != null) {
			int listW = panelW - SIDEBAR_W - 1 - SETTINGS_COL_W;
			int spX = panelX + SIDEBAR_W + 1 + listW;
			if (isIn(mx, my, spX, panelY + HEADER_H, SETTINGS_COL_W, panelH - HEADER_H)) {
				settingsScrollVelocity -= (float) vertical * 15.0F;
				return true;
			}
		}

		// Module list scroll
		if (isIn(mx, my, panelX + SIDEBAR_W + 1, panelY + HEADER_H, moduleColumnW(), panelH - HEADER_H)) {
			scrollVelocity -= (float) vertical * 10.0F;
			return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (bindingSetting != null) {
			int key = event.key();
			bindingSetting.setValue(key == GLFW.GLFW_KEY_ESCAPE ? -1 : key);
			bindingSetting = null;
			moduleManager.requestSave();
			return true;
		}
		if (searchFocused) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; searchQuery = ""; return true; }
			if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1); return true;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (searchFocused) {
			int cp = event.codepoint();
			if (cp >= 32 && Character.isBmpCodePoint(cp) && searchQuery.length() < 32) {
				searchQuery += (char) cp;
				return true;
			}
		}
		return super.charTyped(event);
	}

	// ─── Helpers ─────────────────────────────────────────────────────────────

	private int moduleColumnW() {
		int base = panelW - SIDEBAR_W - 1;
		if (settingsPanelAnim > 0.01F) base -= (int)(SETTINGS_COL_W * settingsPanelAnim);
		return Math.max(80, base);
	}

	private int moduleListH() { return panelH - HEADER_H - SEARCH_H; }

	private List<Module> getFilteredModules() {
		if (!searchQuery.isEmpty()) {
			String q = searchQuery.toLowerCase();
			return moduleManager.getModules().stream()
				.filter(m -> m.getName().toLowerCase().contains(q))
				.toList();
		}
		return sortedModules(selectedCategory);
	}

	private List<Module> sortedModules(Category cat) {
		List<Module> mods = new ArrayList<>(moduleManager.getByCategory(cat));
		mods.sort((a, b) -> {
			boolean ap = pinnedModules.contains(a.getName());
			boolean bp = pinnedModules.contains(b.getName());
			if (ap != bp) return ap ? -1 : 1;
			return a.getName().compareTo(b.getName());
		});
		return mods;
	}

	private static String formatSlider(double val, double step) {
		if (step >= 1.0) return String.valueOf((int) Math.round(val));
		if (step >= 0.1) return String.format("%.1f", val);
		return String.format("%.2f", val);
	}

	private List<String> wrapText(String text, int maxW) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isEmpty()) return lines;
		String[] words = text.split(" ");
		StringBuilder cur = new StringBuilder();
		for (String word : words) {
			String test = cur.isEmpty() ? word : cur + " " + word;
			if (font.width(test) > maxW && !cur.isEmpty()) {
				lines.add(cur.toString()); cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(test);
			}
		}
		if (!cur.isEmpty()) lines.add(cur.toString());
		return lines;
	}

	private void clampPanel() {
		panelX = Mth.clamp(panelX, MIN_MARGIN, Math.max(MIN_MARGIN, this.width  - panelW - MIN_MARGIN));
		panelY = Mth.clamp(panelY, MIN_MARGIN, Math.max(MIN_MARGIN, this.height - panelH - MIN_MARGIN));
	}

	private float openProgress() {
		if (openStartNanos <= 0) return 1.0F;
		return Mth.clamp((System.nanoTime() - openStartNanos) / 180_000_000.0F, 0.0F, 1.0F);
	}

	// ─── Drawing utilities ────────────────────────────────────────────────────

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

	private static int lerpColor(int ca, int cb, float t) {
		int ar = (ca >> 16) & 0xFF, ag = (ca >> 8) & 0xFF, ab = ca & 0xFF, aa = (ca >> 24) & 0xFF;
		int br = (cb >> 16) & 0xFF, bg = (cb >> 8) & 0xFF, bb = cb & 0xFF, ba = (cb >> 24) & 0xFF;
		return (((int)(aa + (ba - aa) * t)) << 24) | (((int)(ar + (br - ar) * t)) << 16)
			 | (((int)(ag + (bg - ag) * t)) << 8)  |  ((int)(ab + (bb - ab) * t));
	}

	private static int dimColor(int argb, float factor) {
		int r = (int)(((argb >> 16) & 0xFF) * factor);
		int g = (int)(((argb >>  8) & 0xFF) * factor);
		int b = (int)((argb & 0xFF) * factor);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int withAlpha(int argb, float mul) {
		mul = Mth.clamp(mul, 0, 1);
		int a = (argb >>> 24) & 0xFF;
		return ((int)(a * mul) << 24) | (argb & 0x00FFFFFF);
	}

	private static float easeOutCubic(float t) { float inv = 1 - t; return 1 - inv * inv * inv; }
	private static float approach(float cur, float tgt, float speed) { return cur + (tgt - cur) * speed; }

	private static double toPanelSpace(double v, int center, float scale) {
		return scale < 0.0001F ? v : center + (v - center) / scale;
	}
	private static int toPanelSpaceX(double x, int cx, float s) { return (int) Math.round(toPanelSpace(x, cx, s)); }
	private static int toPanelSpaceY(double y, int cy, float s) { return (int) Math.round(toPanelSpace(y, cy, s)); }
	private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}