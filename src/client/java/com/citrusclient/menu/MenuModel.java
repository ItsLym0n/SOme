package com.citrusclient.menu;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * What the menu shows: sections (left sidebar) hold features (rows with an on/off switch),
 * and each feature can drop down a list of options.
 */
public final class MenuModel {
	private MenuModel() {
	}

	public record Section(String name, List<Feature> features) {
	}

	public static final class Feature {
		final String name;
		final String description;
		final @Nullable Toggle toggle;
		final List<Option> options;
		boolean expanded;

		/** {@code toggle} is the switch on the feature's row; null for features without one. */
		public Feature(String name, String description, @Nullable Toggle toggle, List<Option> options) {
			this.name = name;
			this.description = description;
			this.toggle = toggle;
			this.options = options;
		}

		boolean matches(String query) {
			if (contains(name, query) || contains(description, query)) return true;
			for (Option option : options) {
				if (contains(option.name(), query)) return true;
			}
			return false;
		}

		private static boolean contains(String text, String query) {
			return text.toLowerCase().contains(query);
		}
	}

	public sealed interface Option permits Toggle, Slider, Cycle, Text, Color, Action {
		String name();
	}

	public record Toggle(String name, String tooltip, BooleanSupplier get, Consumer<Boolean> set) implements Option {
		public Toggle(String name, BooleanSupplier get, Consumer<Boolean> set) {
			this(name, "", get, set);
		}
	}

	/** Whole-number slider; {@code minLabel} replaces the number at the minimum (e.g. "OFF"). */
	public record Slider(String name, int min, int max, IntSupplier get, IntConsumer set, @Nullable String minLabel)
			implements Option {
		public Slider(String name, int min, int max, IntSupplier get, IntConsumer set) {
			this(name, min, max, get, set, null);
		}
	}

	/** Button that steps through values; {@code label} shows the current one. */
	public record Cycle(String name, Supplier<String> label, Runnable next) implements Option {
	}

	public record Text(String name, String tooltip, Supplier<String> get, Consumer<String> set) implements Option {
	}

	/**
	 * Red/green/blue (and optional opacity) sliders with a preview. {@code preview} gives the
	 * color at a position across the swatch (0-1), so rainbows show as a moving gradient.
	 */
	public record Color(String name, Channel red, Channel green, Channel blue, @Nullable Channel alpha,
			Preview preview) implements Option {
	}

	public record Channel(IntSupplier get, IntConsumer set) {
	}

	@FunctionalInterface
	public interface Preview {
		int colorAt(float position);
	}

	public record Action(String name, Runnable run) implements Option {
	}
}
