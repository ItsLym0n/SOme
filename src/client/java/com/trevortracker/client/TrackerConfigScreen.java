package com.trevortracker.client;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/** Settings screen: color, opacity and thickness sliders for the path, tracer line and mob outline. */
public class TrackerConfigScreen extends Screen {
	private static final int ROW = 22;
	private static final int COLUMN_WIDTH = 124;
	private static final int GAP = 8;

	private final @Nullable Screen parent;
	private final TrackerConfig config = TrevorTrackerClient.CONFIG;

	private int[] columnX;
	private int top;

	public TrackerConfigScreen(@Nullable Screen parent) {
		super(Component.literal("Trevor Tracker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int totalWidth = COLUMN_WIDTH * 3 + GAP * 2;
		int left = width / 2 - totalWidth / 2;
		columnX = new int[]{left, left + COLUMN_WIDTH + GAP, left + (COLUMN_WIDTH + GAP) * 2};
		top = Math.max(32, (height - ROW * 9) / 2 + 14);

		// Walking path
		int x = columnX[0];
		int y = top;
		addRenderableWidget(new IntSlider(x, y, "Red", 0, 255, () -> config.pathRed, v -> config.pathRed = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Green", 0, 255, () -> config.pathGreen, v -> config.pathGreen = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Blue", 0, 255, () -> config.pathBlue, v -> config.pathBlue = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Opacity", 0, 255, () -> config.pathAlpha, v -> config.pathAlpha = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Thickness", 1, 20,
				() -> Math.round(config.pathWidth), v -> config.pathWidth = v));

		// Tracer line
		x = columnX[1];
		y = top;
		addRenderableWidget(new IntSlider(x, y, "Red", 0, 255, () -> config.tracerRed, v -> config.tracerRed = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Green", 0, 255, () -> config.tracerGreen, v -> config.tracerGreen = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Blue", 0, 255, () -> config.tracerBlue, v -> config.tracerBlue = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Opacity", 0, 255, () -> config.tracerAlpha, v -> config.tracerAlpha = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Thickness", 1, 20,
				() -> Math.round(config.tracerWidth), v -> config.tracerWidth = v));

		// Mob outline
		x = columnX[2];
		y = top;
		addRenderableWidget(new IntSlider(x, y, "Red", 0, 255, () -> config.outlineRed, v -> config.outlineRed = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Green", 0, 255, () -> config.outlineGreen, v -> config.outlineGreen = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Blue", 0, 255, () -> config.outlineBlue, v -> config.outlineBlue = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Box Opacity", 0, 255, () -> config.outlineAlpha, v -> config.outlineAlpha = v));
		addRenderableWidget(new IntSlider(x, y += ROW, "Box Thickness", 1, 10,
				() -> Math.round(config.outlineWidth), v -> config.outlineWidth = v));

		// Rainbow toggle under each column
		y += ROW;
		addRenderableWidget(Button.builder(rainbowLabel(config.pathRainbow), b -> {
			config.pathRainbow = !config.pathRainbow;
			b.setMessage(rainbowLabel(config.pathRainbow));
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(rainbowLabel(config.tracerRainbow), b -> {
			config.tracerRainbow = !config.tracerRainbow;
			b.setMessage(rainbowLabel(config.tracerRainbow));
		}).bounds(columnX[1], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(rainbowLabel(config.outlineRainbow), b -> {
			config.outlineRainbow = !config.outlineRainbow;
			b.setMessage(rainbowLabel(config.outlineRainbow));
		}).bounds(columnX[2], y, COLUMN_WIDTH, 20).build());

		// Toggles
		y += ROW + 6;
		addRenderableWidget(Button.builder(modeLabel(), b -> {
			config.mode = config.mode.next();
			b.setMessage(modeLabel());
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(toggleLabel("Glow (thru walls)", config.glow), b -> {
			config.glow = !config.glow;
			b.setMessage(toggleLabel("Glow (thru walls)", config.glow));
		}).bounds(columnX[1], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(toggleLabel("Box", config.box), b -> {
			config.box = !config.box;
			b.setMessage(toggleLabel("Box", config.box));
		}).bounds(columnX[2], y, COLUMN_WIDTH, 20).build());

		y += ROW;
		addRenderableWidget(Button.builder(toggleLabel("Enabled", config.enabled), b -> {
			config.enabled = !config.enabled;
			b.setMessage(toggleLabel("Enabled", config.enabled));
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(new IntSlider(columnX[1], y, "Rainbow Speed", 1, 10,
				() -> config.rainbowSpeed, v -> config.rainbowSpeed = v));
		addRenderableWidget(Button.builder(Component.literal("Keybinds..."),
						b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
				.bounds(columnX[2], y, COLUMN_WIDTH, 20).build());

		y += ROW;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(columnX[1], y, COLUMN_WIDTH, 20).build());
	}

	private Component modeLabel() {
		return Component.literal("Guide: " + config.mode);
	}

	private static Component rainbowLabel(boolean on) {
		return Component.literal("Rainbow: " + (on ? "§aON" : "§cOFF"));
	}

	private static Component toggleLabel(String name, boolean on) {
		return Component.literal(name + ": " + (on ? "§aON" : "§cOFF"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);

		// Column titles with a color preview
		int labelY = top - 26;
		int swatchY = top - 14;
		graphics.text(font, "Walking Path", columnX[0], labelY, 0xFFFFFFFF);
		swatch(graphics, columnX[0], swatchY, config::pathColor);
		graphics.text(font, "Tracer Line", columnX[1], labelY, 0xFFFFFFFF);
		swatch(graphics, columnX[1], swatchY, config::tracerColor);
		graphics.text(font, "Mob Outline", columnX[2], labelY, 0xFFFFFFFF);
		swatch(graphics, columnX[2], swatchY, config::outlineColor);
	}

	/** {@code colorAt} gets the position across the swatch (0-1), so a rainbow previews as a moving gradient. */
	private static void swatch(GuiGraphicsExtractor graphics, int x, int y, FloatToIntFunction colorAt) {
		int w = COLUMN_WIDTH;
		// Checkerboard behind the swatch so opacity is visible
		for (int i = 0; i < w; i += 5) {
			graphics.fill(x + i, y, Math.min(x + i + 5, x + w), y + 5, (i / 5) % 2 == 0 ? 0xFF999999 : 0xFF666666);
			graphics.fill(x + i, y + 5, Math.min(x + i + 5, x + w), y + 10, (i / 5) % 2 == 0 ? 0xFF666666 : 0xFF999999);
		}
		for (int i = 0; i < w; i += 2) {
			graphics.fill(x + i, y, Math.min(x + i + 2, x + w), y + 10, colorAt.apply((float) i / w));
		}
		graphics.outline(x - 1, y - 1, w + 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.setScreen(parent);
	}

	@FunctionalInterface
	private interface FloatToIntFunction {
		int apply(float value);
	}

	/** Slider for a whole-number setting. */
	private static class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min;
		private final int max;
		private final Consumer<Integer> setter;

		IntSlider(int x, int y, String label, int min, int max, IntSupplier getter, Consumer<Integer> setter) {
			super(x, y, COLUMN_WIDTH, 20, Component.empty(), (Mth.clamp(getter.getAsInt(), min, max) - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.setter = setter;
			updateMessage();
		}

		private int intValue() {
			return min + (int) Math.round(value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + intValue()));
		}

		@Override
		protected void applyValue() {
			setter.accept(intValue());
		}
	}
}
