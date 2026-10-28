package com.trevortracker.client;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
		top = Math.max(32, (height - ROW * 10) / 2 + 14);

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

		// Quest renewing
		y += ROW + 6;
		addRenderableWidget(Button.builder(toggleLabel("Auto Renew", config.autoRenew), b -> {
			config.autoRenew = !config.autoRenew;
			b.setMessage(toggleLabel("Auto Renew", config.autoRenew));
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20)
				.tooltip(Tooltip.create(Component.literal("Call Trevor through the Abiphone when the cooldown is almost over")))
				.build());
		addRenderableWidget(Button.builder(cookieLabel(), b -> {
			config.boosterCookie = !config.boosterCookie;
			b.setMessage(cookieLabel());
		}).bounds(columnX[1], y, COLUMN_WIDTH, 20)
				.tooltip(Tooltip.create(Component.literal(
						"ON: call with the command below.\nOFF: open the Abiphone, click Trevor, then Call")))
				.build());
		addRenderableWidget(Button.builder(automationLabel(), b -> {
			config.fullyAutomatic = !config.fullyAutomatic;
			b.setMessage(automationLabel());
		}).bounds(columnX[2], y, COLUMN_WIDTH, 20)
				.tooltip(Tooltip.create(Component.literal(
						"Key Press: each press of the action key does one step.\n"
								+ "Fully Auto: the mod does every step by itself. This is a macro under Hypixel's rules and can get you banned.")))
				.build());

		y += ROW;
		addRenderableWidget(Button.builder(toggleLabel("Auto Accept", config.autoAccept), b -> {
			config.autoAccept = !config.autoAccept;
			b.setMessage(toggleLabel("Auto Accept", config.autoAccept));
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20)
				.tooltip(Tooltip.create(Component.literal("Accept Trevor's [YES] prompt")))
				.build());
		addRenderableWidget(new IntSlider(columnX[1], y, "Call Early (s)", 0, 10,
				() -> config.renewLeadSeconds, v -> config.renewLeadSeconds = v));
		EditBox commandBox = new EditBox(font, columnX[2], y, COLUMN_WIDTH, 20, Component.literal("Call command"));
		commandBox.setMaxLength(64);
		commandBox.setValue(config.callCommand);
		commandBox.setResponder(v -> config.callCommand = v.trim());
		commandBox.setTooltip(Tooltip.create(Component.literal("Command used to call Trevor with a Booster Cookie")));
		addRenderableWidget(commandBox);

		y += ROW + 6;
		addRenderableWidget(Button.builder(toggleLabel("Enabled", config.enabled), b -> {
			config.enabled = !config.enabled;
			b.setMessage(toggleLabel("Enabled", config.enabled));
		}).bounds(columnX[0], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Keybinds..."),
						b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
				.bounds(columnX[1], y, COLUMN_WIDTH, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(columnX[2], y, COLUMN_WIDTH, 20).build());
	}

	private Component modeLabel() {
		return Component.literal("Guide: " + config.mode);
	}

	private Component cookieLabel() {
		return Component.literal("Booster Cookie: " + (config.boosterCookie ? "§aYES" : "§cNO"));
	}

	private Component automationLabel() {
		return Component.literal("Mode: " + (config.fullyAutomatic ? "§cFully Auto" : "§aKey Press"));
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
		swatch(graphics, columnX[0], swatchY, config.pathColor());
		graphics.text(font, "Tracer Line", columnX[1], labelY, 0xFFFFFFFF);
		swatch(graphics, columnX[1], swatchY, config.tracerColor());
		graphics.text(font, "Mob Outline", columnX[2], labelY, 0xFFFFFFFF);
		swatch(graphics, columnX[2], swatchY, config.outlineColor());
	}

	private static void swatch(GuiGraphicsExtractor graphics, int x, int y, int color) {
		int w = COLUMN_WIDTH;
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
