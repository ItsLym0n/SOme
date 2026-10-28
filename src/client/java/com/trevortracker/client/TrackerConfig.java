package com.trevortracker.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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

	// Quest renewing through the Abiphone
	public boolean autoRenew = false;
	/** Off = one action per key press. On = the mod calls and accepts by itself (macro, ban risk). */
	public boolean fullyAutomatic = false;
	/** With a Booster Cookie the call is a command; without one the mod goes through the Abiphone menu. */
	public boolean boosterCookie = true;
	public String callCommand = "call trevor";
	/** Seconds before Trevor's cooldown ends to start calling. */
	public int renewLeadSeconds = 3;
	public boolean autoAccept = false;

	public int pathColor() {
		return argb(pathAlpha, pathRed, pathGreen, pathBlue);
	}

	public int tracerColor() {
		return argb(tracerAlpha, tracerRed, tracerGreen, tracerBlue);
	}

	public int outlineColor() {
		return argb(outlineAlpha, outlineRed, outlineGreen, outlineBlue);
	}

	/** Glow outlines are always fully opaque, so this has no alpha. */
	public int glowColor() {
		return argb(0, outlineRed, outlineGreen, outlineBlue);
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
					if (config.callCommand == null || config.callCommand.isBlank()) config.callCommand = "call trevor";
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
