package com.citrusclient.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.citrusclient.CitrusClient;
import com.citrusclient.menu.MenuModel.Action;
import com.citrusclient.menu.MenuModel.Color;
import com.citrusclient.menu.MenuModel.Cycle;
import com.citrusclient.menu.MenuModel.Feature;
import com.citrusclient.menu.MenuModel.Option;
import com.citrusclient.menu.MenuModel.Section;
import com.citrusclient.menu.MenuModel.Slider;
import com.citrusclient.menu.MenuModel.Text;
import com.citrusclient.menu.MenuModel.Toggle;
import com.trevortracker.client.TrevorTrackerClient;
import dev.endernode.EnderNodeFinder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * SkyHanni-style settings: sections in a sidebar on the left, the selected section's
 * features on the right. Each feature row has an on/off switch and an arrow that drops
 * down its options. The search box at the top searches every section.
 */
public class CitrusScreen extends Screen {
	private static final int HEADER_HEIGHT = 26;
	private static final int SIDEBAR_WIDTH = 96;
	private static final int FEATURE_HEIGHT = 30;
	private static final int OPTION_HEIGHT = 22;
	private static final int COLOR_LABEL_HEIGHT = 14;
	private static final int INDENT = 12;
	private static final int SCROLL_STEP = 20;

	private static final int PANEL_COLOR = 0xE0101418;
	private static final int SIDEBAR_COLOR = 0xE0181E24;
	private static final int FEATURE_COLOR = 0x80000000;
	private static final int ACCENT = 0xFFFFB627; // citrus orange
	private static final int GRAY = 0xFFA0A0A0;

	/** Remembered while the game is running, so the menu reopens where you left it. */
	private static String lastSection = CitrusClient.HUNTING;
	private static final Set<String> EXPANDED = new HashSet<>();

	private final @Nullable Screen parent;
	private final List<Section> sections = MenuSections.build();
	private String section;
	private int scroll;
	private boolean dirty;

	private EditBox search;
	/** Row backgrounds (drawn under the buttons) and text (drawn over them), collected while laying out the rows. */
	private final List<Consumer<GuiGraphicsExtractor>> backgroundDraws = new ArrayList<>();
	private final List<Consumer<GuiGraphicsExtractor>> draws = new ArrayList<>();

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int contentX;
	private int contentWidth;
	private int contentTop;
	private int contentBottom;
	private int contentHeight;

	public CitrusScreen(@Nullable Screen parent, @Nullable String section) {
		super(Component.literal(CitrusClient.DISPLAY_NAME));
		this.parent = parent;
		this.section = section != null ? section : lastSection;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 20, 480);
		panelHeight = Math.max(160, height - 20);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentX = panelX + SIDEBAR_WIDTH + 8;
		contentWidth = panelWidth - SIDEBAR_WIDTH - 14;
		contentTop = panelY + HEADER_HEIGHT + 4;
		contentBottom = panelY + panelHeight - 4;

		String query = search != null ? search.getValue() : "";
		search = new EditBox(font, panelX + panelWidth - 146, panelY + 5, 140, 16, Component.literal("Search"));
		search.setHint(Component.literal("Search..."));
		search.setValue(query);
		search.setResponder(text -> {
			scroll = 0;
			dirty = true;
		});
		rebuild();
	}

	@Override
	public void tick() {
		super.tick();
		if (dirty) {
			dirty = false;
			rebuild();
		}
	}

	// ----- layout -----

	private void rebuild() {
		clearWidgets();
		backgroundDraws.clear();
		draws.clear();
		addRenderableWidget(search);
		buildSidebar();

		List<Row> rows = new ArrayList<>();
		String query = search.getValue().trim().toLowerCase();
		for (Section s : sections) {
			if (query.isEmpty() && !s.name().equals(section)) continue;
			for (Feature feature : s.features()) {
				if (!query.isEmpty() && !feature.matches(query)) continue;
				addFeatureRows(rows, s, feature, !query.isEmpty());
			}
		}

		contentHeight = 0;
		for (Row row : rows) contentHeight += row.height;
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (contentBottom - contentTop)));

		int y = contentTop - scroll;
		for (Row row : rows) {
			// Only rows that fit completely; the rest appear as you scroll.
			if (y >= contentTop && y + row.height <= contentBottom) row.build.accept(y);
			y += row.height;
		}

		if (rows.isEmpty()) {
			draws.add(g -> g.centeredText(font, "Nothing matches \"" + search.getValue() + "\"",
					contentX + contentWidth / 2, contentTop + 20, GRAY));
		}
	}

	private void buildSidebar() {
		int x = panelX + 6;
		int y = panelY + HEADER_HEIGHT + 6;
		for (Section s : sections) {
			boolean selected = s.name().equals(section) && search.getValue().isBlank();
			Button button = Button.builder(Component.literal((selected ? "§6» §f" : "§7") + s.name()), b -> {
				section = s.name();
				lastSection = section;
				search.setValue("");
				scroll = 0;
				dirty = true;
			}).bounds(x, y, SIDEBAR_WIDTH - 12, 20).build();
			addRenderableWidget(button);
			y += 24;
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, panelY + panelHeight - 26, SIDEBAR_WIDTH - 12, 20).build());
	}

	private record Row(int height, IntConsumer build) {
	}

	private void addFeatureRows(List<Row> rows, Section s, Feature feature, boolean searching) {
		String key = s.name() + "/" + feature.name;
		boolean expanded = EXPANDED.contains(key) || (searching && !feature.options.isEmpty());

		rows.add(new Row(FEATURE_HEIGHT, y -> {
			int right = contentX + contentWidth;
			backgroundDraws.add(g -> {
				g.fill(contentX, y + 1, right, y + FEATURE_HEIGHT - 1, FEATURE_COLOR);
				g.fill(contentX, y + 1, contentX + 2, y + FEATURE_HEIGHT - 1, ACCENT);
			});
			draws.add(g -> {
				g.text(font, feature.name, contentX + 7, y + 5, 0xFFFFFFFF, true);
				int textWidth = contentWidth - 7 - 70;
				String desc = (searching ? "[" + s.name() + "] " : "") + feature.description;
				g.text(font, font.plainSubstrByWidth(desc, textWidth), contentX + 7, y + 17, GRAY, false);
			});

			int bx = right - 44;
			if (feature.toggle != null) {
				Toggle toggle = feature.toggle;
				addRenderableWidget(Button.builder(switchLabel(toggle.get().getAsBoolean()), b -> {
					boolean on = !toggle.get().getAsBoolean();
					toggle.set().accept(on);
					b.setMessage(switchLabel(on));
				}).bounds(bx, y + 7, 40, 16).build());
			}
			if (!feature.options.isEmpty()) {
				addRenderableWidget(Button.builder(Component.literal(expanded ? "▼" : "▶"), b -> {
					if (!EXPANDED.remove(key)) EXPANDED.add(key);
					dirty = true;
				}).bounds(bx - 20, y + 7, 16, 16).build());
			}
		}));

		if (!expanded) return;
		for (Option option : feature.options) addOptionRows(rows, option);
	}

	private void addOptionRows(List<Row> rows, Option option) {
		int x = contentX + INDENT;
		int w = contentWidth - INDENT - 4;
		switch (option) {
			case Toggle t -> rows.add(new Row(OPTION_HEIGHT, y -> {
				Button button = Button.builder(toggleLabel(t.name(), t.get().getAsBoolean()), b -> {
					boolean on = !t.get().getAsBoolean();
					t.set().accept(on);
					b.setMessage(toggleLabel(t.name(), on));
				}).bounds(x, y + 1, w, 20).build();
				if (!t.tooltip().isEmpty()) button.setTooltip(Tooltip.create(Component.literal(t.tooltip())));
				addRenderableWidget(button);
			}));
			case Slider s -> rows.add(new Row(OPTION_HEIGHT, y ->
					addRenderableWidget(new IntSlider(x, y + 1, w, s.name(), s.min(), s.max(), s.get(), s.set(), s.minLabel()))));
			case Cycle c -> rows.add(new Row(OPTION_HEIGHT, y ->
					addRenderableWidget(Button.builder(Component.literal(c.name() + ": §e" + c.label().get()), b -> {
						c.next().run();
						b.setMessage(Component.literal(c.name() + ": §e" + c.label().get()));
					}).bounds(x, y + 1, w, 20).build())));
			case Text t -> rows.add(new Row(OPTION_HEIGHT, y -> {
				int labelWidth = Math.min(80, w / 3);
				draws.add(g -> g.text(font, t.name(), x, y + 7, 0xFFFFFFFF, true));
				EditBox box = new EditBox(font, x + labelWidth, y + 2, w - labelWidth, 18, Component.literal(t.name()));
				box.setMaxLength(64);
				box.setValue(t.get().get());
				box.setResponder(t.set());
				if (!t.tooltip().isEmpty()) box.setTooltip(Tooltip.create(Component.literal(t.tooltip())));
				addRenderableWidget(box);
			}));
			case Color c -> {
				rows.add(new Row(COLOR_LABEL_HEIGHT, y -> draws.add(g -> {
					g.text(font, c.name(), x, y + 3, 0xFFFFFFFF, true);
					int swatchX = x + Math.max(font.width(c.name()) + 8, w / 3);
					swatch(g, swatchX, y + 2, x + w - swatchX, c.preview());
				})));
				int half = (w - 4) / 2;
				rows.add(new Row(OPTION_HEIGHT, y -> {
					addRenderableWidget(new IntSlider(x, y + 1, half, "Red", 0, 255, c.red().get(), c.red().set(), null));
					addRenderableWidget(new IntSlider(x + half + 4, y + 1, half, "Green", 0, 255, c.green().get(), c.green().set(), null));
				}));
				rows.add(new Row(OPTION_HEIGHT, y -> {
					addRenderableWidget(new IntSlider(x, y + 1, half, "Blue", 0, 255, c.blue().get(), c.blue().set(), null));
					if (c.alpha() != null) {
						addRenderableWidget(new IntSlider(x + half + 4, y + 1, half, "Opacity", 0, 255,
								c.alpha().get(), c.alpha().set(), null));
					}
				}));
			}
			case Action a -> rows.add(new Row(OPTION_HEIGHT, y ->
					addRenderableWidget(Button.builder(Component.literal(a.name()), b -> a.run().run())
							.bounds(x, y + 1, w, 20).build())));
		}
	}

	private static Component switchLabel(boolean on) {
		return Component.literal(on ? "§aON" : "§cOFF");
	}

	private static Component toggleLabel(String name, boolean on) {
		return Component.literal(name + ": " + (on ? "§aON" : "§cOFF"));
	}

	// ----- drawing -----

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
		graphics.fill(panelX, panelY + HEADER_HEIGHT, panelX + SIDEBAR_WIDTH, panelY + panelHeight, SIDEBAR_COLOR);
		graphics.fill(panelX, panelY + HEADER_HEIGHT - 1, panelX + panelWidth, panelY + HEADER_HEIGHT, ACCENT);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF2A323A);
		for (Consumer<GuiGraphicsExtractor> draw : backgroundDraws) draw.accept(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(font, "§6Citrus §fClient", panelX + 8, panelY + 9, 0xFFFFFFFF, true);
		graphics.text(font, "v" + CitrusClient.VERSION, panelX + 8 + font.width("Citrus Client") + 6, panelY + 9, GRAY, false);
		for (Consumer<GuiGraphicsExtractor> draw : draws) draw.accept(graphics);

		// Scrollbar
		int visible = contentBottom - contentTop;
		if (contentHeight > visible) {
			int barHeight = Math.max(16, visible * visible / contentHeight);
			int barY = contentTop + (visible - barHeight) * scroll / (contentHeight - visible);
			int barX = panelX + panelWidth - 4;
			graphics.fill(barX, contentTop, barX + 2, contentBottom, 0x40FFFFFF);
			graphics.fill(barX, barY, barX + 2, barY + barHeight, ACCENT);
		}
	}

	private static void swatch(GuiGraphicsExtractor graphics, int x, int y, int w, MenuModel.Preview preview) {
		// Checkerboard behind the swatch so opacity is visible
		for (int i = 0; i < w; i += 5) {
			graphics.fill(x + i, y, Math.min(x + i + 5, x + w), y + 5, (i / 5) % 2 == 0 ? 0xFF999999 : 0xFF666666);
			graphics.fill(x + i, y + 5, Math.min(x + i + 5, x + w), y + 10, (i / 5) % 2 == 0 ? 0xFF666666 : 0xFF999999);
		}
		for (int i = 0; i < w; i += 2) {
			graphics.fill(x + i, y, Math.min(x + i + 2, x + w), y + 10, preview.colorAt((float) i / w));
		}
		graphics.outline(x - 1, y - 1, w + 2, 12, 0xFFFFFFFF);
	}

	// ----- input -----

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= contentX && mouseX <= contentX + contentWidth && mouseY >= contentTop && mouseY <= contentBottom) {
			scroll -= (int) Math.round(scrollY * SCROLL_STEP);
			rebuild();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		TrevorTrackerClient.CONFIG.save();
		EnderNodeFinder.config.save();
		minecraft.setScreen(parent);
	}

	/** Slider for a whole-number setting. */
	private static class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final IntConsumer setter;
		private final @Nullable String minLabel;

		IntSlider(int x, int y, int width, String label, int min, int max, IntSupplier getter, IntConsumer setter,
				@Nullable String minLabel) {
			super(x, y, width, 20, Component.empty(),
					(Mth.clamp(getter.getAsInt(), min, max) - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.minLabel = minLabel;
			updateMessage();
		}

		private int intValue() {
			return min + (int) Math.round(value * (max - min));
		}

		@Override
		protected void updateMessage() {
			int v = intValue();
			setMessage(Component.literal(label + ": " + (v == min && minLabel != null ? minLabel : v)));
		}

		@Override
		protected void applyValue() {
			setter.accept(intValue());
		}
	}
}
