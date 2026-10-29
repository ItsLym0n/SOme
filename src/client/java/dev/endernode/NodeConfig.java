package dev.endernode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** All user settings. Saved to config/endernodefinder.json. */
public class NodeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("endernodefinder.json");

	private static final int CURRENT_VERSION = 2;

	/** Bumped when defaults change, so old config files get upgraded once (0 = file predates it). */
	public int configVersion;

	public boolean enabled = true;
	public boolean throughWalls = true;

	// Box outline color (0-255)
	public int boxRed = 170;
	public int boxGreen = 0;
	public int boxBlue = 255;
	public int boxAlpha = 255;
	public float boxWidth = 3.0f;
	// Translucent fill inside the box (0 = no fill)
	public int fillAlpha = 50;

	// Route line color (0-255)
	public int lineRed = 255;
	public int lineGreen = 255;
	public int lineBlue = 255;
	public int lineAlpha = 255;
	public float lineWidth = 6.0f;

	/** How far around you to look for nodes, in blocks. */
	public int range = 32;
	/** Nodes more than this many blocks up/down from your feet get a small extra cost when picking a target. */
	public int sameLevelRange = 2;
	/** Nodes this close to each other count as a group (0 = off). Groups are visited first. */
	public int groupRadius = 6;
	/** How many nodes it takes to make a group. */
	public int groupSize = 3;

	public int boxColor() {
		return argb(boxAlpha, boxRed, boxGreen, boxBlue);
	}

	public int fillColor() {
		return argb(fillAlpha, boxRed, boxGreen, boxBlue);
	}

	public int lineColor() {
		return argb(lineAlpha, lineRed, lineGreen, lineBlue);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	public static NodeConfig load() {
		if (Files.exists(FILE)) {
			NodeConfig config = null;
			try (Reader reader = Files.newBufferedReader(FILE)) {
				config = GSON.fromJson(reader, NodeConfig.class);
			} catch (Exception e) {
				EnderNodeFinder.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
			if (config != null) {
				config.upgrade();
				return config;
			}
		}
		NodeConfig config = new NodeConfig();
		config.configVersion = CURRENT_VERSION;
		config.save();
		return config;
	}

	private void upgrade() {
		if (configVersion < 2) {
			lineWidth = Math.max(lineWidth, 6.0f);
		}
		if (configVersion < CURRENT_VERSION) {
			configVersion = CURRENT_VERSION;
			save();
		}
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			EnderNodeFinder.LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
