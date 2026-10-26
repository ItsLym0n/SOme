package dev.endernode;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Settings screen with RGB sliders for the box and the route line. */
public class NodeConfigScreen extends Screen {
	private static final int ROW = 22;
	private static final int COLUMN_WIDTH = 150;
	private static final int GAP = 10;

	private final @Nullable Screen parent;
	private final NodeConfig config = EnderNodeFinder.config;

	private int leftX;
	private int rightX;
	private int top;

	public NodeConfigScreen(@Nullable Screen parent) {
		super(Component.literal("Ender Node Finder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		leftX = width / 2 - COLUMN_WIDTH - GAP / 2;
		rightX = width / 2 + GAP / 2;
		top = Math.max(36, (height - ROW * 9 - 20) / 2 + 12);

		// Box colors (left column)
		int y = top;
		addRenderableWidget(new IntSlider(leftX, y, "Box Red", 0, 255, () -> config.boxRed, v -> config.boxRed = v));
		addRenderableWidget(new IntSlider(leftX, y += ROW, "Box Green", 0, 255, () -> config.boxGreen, v -> config.boxGreen = v));
		addRenderableWidget(new IntSlider(leftX, y += ROW, "Box Blue", 0, 255, () -> config.boxBlue, v -> config.boxBlue = v));
		addRenderableWidget(new IntSlider(leftX, y += ROW, "Box Opacity", 0, 255, () -> config.boxAlpha, v -> config.boxAlpha = v));
		addRenderableWidget(new IntSlider(leftX, y += ROW, "Box Fill", 0, 255, () -> config.fillAlpha, v -> config.fillAlpha = v));
		addRenderableWidget(new IntSlider(leftX, y += ROW, "Box Thickness", 1, 10,
				() -> Math.round(config.boxWidth), v -> config.boxWidth = v));

		// Line colors (right column)
		y = top;
		addRenderableWidget(new IntSlider(rightX, y, "Line Red", 0, 255, () -> config.lineRed, v -> config.lineRed = v));
		addRenderableWidget(new IntSlider(rightX, y += ROW, "Line Green", 0, 255, () -> config.lineGreen, v -> config.lineGreen = v));
		addRenderableWidget(new IntSlider(rightX, y += ROW, "Line Blue", 0, 255, () -> config.lineBlue, v -> config.lineBlue = v));
		addRenderableWidget(new IntSlider(rightX, y += ROW, "Line Opacity", 0, 255, () -> config.lineAlpha, v -> config.lineAlpha = v));
		addRenderableWidget(new IntSlider(rightX, y += ROW, "Same Level ±", 0, 10,
				() -> config.sameLevelRange, v -> config.sameLevelRange = v));
		addRenderableWidget(new IntSlider(rightX, y += ROW, "Line Thickness", 1, 10,
				() -> Math.round(config.lineWidth), v -> config.lineWidth = v));

		// General
		y += ROW + 6;
		addRenderableWidget(new IntSlider(leftX, y, "Scan Range", 8, 96, () -> config.range, v -> config.range = v));
		addRenderableWidget(Button.builder(toggleLabel("Through Walls", config.throughWalls), b -> {
			config.throughWalls = !config.throughWalls;
			b.setMessage(toggleLabel("Through Walls", config.throughWalls));
		}).bounds(rightX, y, COLUMN_WIDTH, 20).build());

		y += ROW;
		addRenderableWidget(Button.builder(toggleLabel("Enabled", config.enabled), b -> {
			config.enabled = !config.enabled;
			b.setMessage(toggleLabel("Enabled", config.enabled));
		}).bounds(leftX, y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(rightX, y, COLUMN_WIDTH, 20).build());
	}

	private static Component toggleLabel(String name, boolean on) {
		return Component.literal(name + ": " + (on ? "§aON" : "§cOFF"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);

		// Color previews above each column
		int swatchY = top - 14;
		graphics.text(font, "Box", leftX, swatchY + 1, 0xFFFFFFFF);
		swatch(graphics, leftX + 30, swatchY, config.boxColor());
		graphics.text(font, "Line", rightX, swatchY + 1, 0xFFFFFFFF);
		swatch(graphics, rightX + 30, swatchY, config.lineColor());
	}

	private static void swatch(GuiGraphicsExtractor graphics, int x, int y, int color) {
		int w = COLUMN_WIDTH - 30;
		// Checkerboard behind the swatch so opacity is visible
		for (int i = 0; i < w; i += 5) {
			graphics.fill(x + i, y, Math.min(x + i + 5, x + w), y + 5, (i / 5) % 2 == 0 ? 0xFF999999 : 0xFF666666);
			graphics.fill(x + i, y + 5, Math.min(x + i + 5, x + w), y + 10, (i / 5) % 2 == 0 ? 0xFF666666 : 0xFF999999);
		}
		graphics.fill(x, y, x + w, y + 10, color);
		graphics.outline(x - 1, y - 1, w + 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.setScreen(parent);
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
