package com.trevortracker.client;

import net.minecraft.core.BlockPos;

/**
 * The places Trevor can send you to in the Mushroom Desert.
 * Coordinates are a rough centre of each area.
 */
public enum HuntArea {
	OASIS("Oasis", new BlockPos(126, 77, -456)),
	MUSHROOM_GORGE("Mushroom Gorge", new BlockPos(300, 80, -509)),
	OVERGROWN_MUSHROOM_CAVE("Overgrown Mushroom Cave", new BlockPos(242, 60, -389)),
	DESERT_SETTLEMENT("Desert Settlement", new BlockPos(184, 86, -384)),
	GLOWING_MUSHROOM_CAVE("Glowing Mushroom Cave", new BlockPos(199, 50, -512)),
	DESERT_MOUNTAIN("Desert Mountain", new BlockPos(255, 148, -518));

	public final String displayName;
	public final BlockPos pos;

	HuntArea(String displayName, BlockPos pos) {
		this.displayName = displayName;
		this.pos = pos;
	}

	/** Matches the location text from Trevor's chat message, e.g. "Desert Settlement". */
	public static HuntArea fromName(String text) {
		String t = text.trim().toLowerCase();
		for (HuntArea area : values()) {
			if (t.contains(area.displayName.toLowerCase())) return area;
		}
		return null;
	}
}
