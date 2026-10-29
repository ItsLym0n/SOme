package com.trevortracker.client;

import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Calls Trevor with {@code /call Trevor} (needs a Booster Cookie) to get the next quest.
 *
 * It only calls after the hunted animal has died. Trevor's cooldown is 20 seconds from
 * when he gave the quest, so the call goes out {@link TrackerConfig#callEarlySeconds}
 * before that, or straight away if the cooldown is already over.
 */
public class TrevorCaller {
	private static final Pattern QUEST = Pattern.compile("\\[NPC] Trevor: You can find your \\w+ animal near");
	private static final String MOB_DIED = "Return to the Trapper soon to get a new animal to hunt!";
	private static final String OUT_OF_TIME = "You ran out of time and the animal disappeared!";

	private static final long COOLDOWN_MS = 20_000;
	/** If Trevor doesn't answer, try again after this long, up to {@link #MAX_CALLS} calls in total. */
	private static final long RETRY_MS = 5_000;
	private static final int MAX_CALLS = 3;

	private long questStartMs;
	private boolean pending;
	private long callAtMs;
	private int calls;

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();
		long now = System.currentTimeMillis();

		if (QUEST.matcher(text).find()) {
			// New quest arrived: stop calling.
			questStartMs = now;
			pending = false;
		} else if (text.contains(MOB_DIED)) {
			int early = TrevorTrackerClient.CONFIG.callEarlySeconds;
			callAtMs = Math.max(questStartMs + COOLDOWN_MS - early * 1000L, now);
			calls = 0;
			pending = true;
		} else if (text.contains(OUT_OF_TIME)) {
			// Only call after a kill.
			pending = false;
		}
	}

	public void tick(Minecraft mc) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (!pending || !config.enabled || !config.autoCall || mc.player == null) return;

		long now = System.currentTimeMillis();
		if (now < callAtMs) return;

		String command = config.callCommand.startsWith("/") ? config.callCommand.substring(1) : config.callCommand;
		mc.player.connection.sendCommand(command);
		calls++;
		if (calls >= MAX_CALLS) {
			pending = false;
		} else {
			callAtMs = now + RETRY_MS;
		}
	}

	/** HUD line, or null when no call is coming up. */
	public String getHint() {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (!pending || !config.autoCall) return null;
		long seconds = Math.max(0, (callAtMs - System.currentTimeMillis() + 999) / 1000);
		return seconds > 0 ? "Calling Trevor in " + seconds + "s" : "Calling Trevor...";
	}
}
