package com.trevortracker.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/** All user settings. Saved to config/trevortracker.json. */
public class TrackerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("trevortracker.json");

	public boolean enabled = true;
	public HuntTracker.Mode mode = HuntTracker.Mode.PATH;

	// Walking path (0-255)
	public int pathRed = 0;
	public int pathGreen = 229;
	public int pathBlue = 255;
	public int pathAlpha = 255;
	public float pathWidth = 3.0f;

	// Straight tracer line (0-255)
	public int tracerRed = 255;
	public int tracerGreen = 208;
	public int tracerBlue = 0;
	public int tracerAlpha = 255;
	public float tracerWidth = 2.5f;

	// Mob outline: the glow (seen through walls) and the box around the mob share this color
	public int outlineRed = 255;
	public int outlineGreen = 59;
	public int outlineBlue = 59;
	public int outlineAlpha = 255;
	public float outlineWidth = 2.5f;
	public boolean glow = true;
	public boolean box = true;
	/** Draw the path, tracer line and box on top of blocks so they show through walls. */
	public boolean linesThroughWalls = true;

	// Moving rainbow colors (replace the RGB sliders while on; opacity still applies)
	public boolean pathRainbow = false;
	public boolean tracerRainbow = false;
	public boolean outlineRainbow = false;
	/** 1 = slow, 10 = fast. */
	public int rainbowSpeed = 3;

	/**
	 * Colors take an {@code offset} (0-1) along the line, so a rainbow line shows every hue
	 * at once and the colors flow along it. Solid colors ignore the offset.
	 */
	public int pathColor(float offset) {
		return pathRainbow ? rainbow(pathAlpha, offset) : argb(pathAlpha, pathRed, pathGreen, pathBlue);
	}

	public int tracerColor(float offset) {
		return tracerRainbow ? rainbow(tracerAlpha, offset) : argb(tracerAlpha, tracerRed, tracerGreen, tracerBlue);
	}

	public int outlineColor(float offset) {
		return outlineRainbow ? rainbow(outlineAlpha, offset) : argb(outlineAlpha, outlineRed, outlineGreen, outlineBlue);
	}

	/** Glow outlines are always fully opaque, so this has no alpha. */
	public int glowColor() {
		return outlineColor(0) & 0xFFFFFF;
	}

	/** Hue cycles over time; {@code offset} shifts it so different parts show different colors. */
	private int rainbow(int alpha, float offset) {
		float cycleMs = 12_000f / Math.max(1, rainbowSpeed);
		float hue = (System.currentTimeMillis() % (long) cycleMs) / cycleMs - offset;
		return Mth.hsvToArgb(hue - (float) Math.floor(hue), 1.0f, 1.0f, alpha);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	public static TrackerConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				TrackerConfig config = GSON.fromJson(reader, TrackerConfig.class);
				if (config != null) {
					if (config.mode == null) config.mode = HuntTracker.Mode.PATH;
					return config;
				}
			} catch (Exception e) {
				TrevorTrackerClient.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		TrackerConfig config = new TrackerConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			TrevorTrackerClient.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
