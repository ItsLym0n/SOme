package com.citrusclient;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Settings for the Misc section. Saved to config/citrusclient.json. */
public class CitrusConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(CitrusClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("citrusclient.json");

	/** Show the crosshair in third-person back view (F5 once). */
	public boolean thirdPersonCrosshair = true;

	public static CitrusConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				CitrusConfig config = GSON.fromJson(reader, CitrusConfig.class);
				if (config != null) return config;
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		CitrusConfig config = new CitrusConfig();
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
			LOGGER.warn("Could not save {}", FILE, e);
		}
	}
}
