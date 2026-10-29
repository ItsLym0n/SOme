package com.trevortracker.client;

import java.util.regex.Matcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Warps toward the hunted animal with a travel scroll when Trevor gives a quest:
 * {@code /warp glowing} for the cave and gorge areas, {@code /warp desert} for the rest
 * (see {@link HuntArea#warp}).
 *
 * Where you are is taken from the previous quest's area, since that's where you just
 * hunted. It only warps when the new area is in the other set, and never between
 * Desert Mountain and Mushroom Gorge, which are right next to each other. The first
 * quest after logging in has no previous area, so it doesn't warp.
 */
public class TrevorWarper {
	/** Wait a moment after Trevor's message so the warp doesn't land on top of other commands. */
	private static final long WARP_DELAY_MS = 1_000;

	private HuntArea lastQuestArea;
	private String pendingWarp;
	private long warpAtMs;

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();
		Matcher m = HuntTracker.QUEST.matcher(text);
		if (!m.find()) return;

		HuntArea target = HuntArea.fromName(m.group(2));
		if (target == null) return;
		HuntArea previous = lastQuestArea;
		lastQuestArea = target;

		if (shouldWarp(previous, target)) {
			pendingWarp = target.warp;
			warpAtMs = System.currentTimeMillis() + WARP_DELAY_MS;
		}
	}

	static boolean shouldWarp(HuntArea from, HuntArea to) {
		if (from == null) return false; // first quest: we don't know where you are
		if (from.warp.equals(to.warp)) return false; // same set: walk
		return !isMountainGorge(from, to);
	}

	/** Desert Mountain and Mushroom Gorge are next to each other, so never warp between them. */
	private static boolean isMountainGorge(HuntArea a, HuntArea b) {
		return (a == HuntArea.DESERT_MOUNTAIN && b == HuntArea.MUSHROOM_GORGE)
				|| (a == HuntArea.MUSHROOM_GORGE && b == HuntArea.DESERT_MOUNTAIN);
	}

	public void tick(Minecraft mc) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (pendingWarp == null || mc.player == null) return;
		if (!config.enabled || !config.autoWarp) {
			pendingWarp = null;
			return;
		}
		if (System.currentTimeMillis() < warpAtMs) return;

		mc.player.connection.sendCommand(pendingWarp);
		pendingWarp = null;
	}

	/** HUD line, or null when no warp is coming up. */
	public String getHint() {
		if (pendingWarp == null || !TrevorTrackerClient.CONFIG.autoWarp) return null;
		return "Warping: /" + pendingWarp;
	}
}
