package com.trevortracker.client;

import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * Calls Trevor with {@code /call Trevor} (needs a Booster Cookie) to get the next quest,
 * and can accept his [YES]/[NO] prompt.
 *
 * It only calls after the hunted animal has died. Trevor's cooldown is 20 seconds from
 * when he gave the quest, so the call goes out {@link TrackerConfig#callEarlySeconds}
 * before that, or straight away if the cooldown is already over. Once Trevor answers
 * with the [YES]/[NO] prompt it stops calling.
 */
public class TrevorCaller {
	private static final Pattern QUEST = Pattern.compile("\\[NPC] Trevor: You can find your \\w+ animal near");
	private static final String MOB_DIED = "Return to the Trapper soon to get a new animal to hunt!";
	private static final String OUT_OF_TIME = "You ran out of time and the animal disappeared!";

	private static final long COOLDOWN_MS = 20_000;
	/** If Trevor doesn't answer, try again after this long, up to {@link #MAX_CALLS} calls in total. */
	private static final long RETRY_MS = 5_000;
	private static final int MAX_CALLS = 3;
	/** Short pause before clicking [YES], so it doesn't fire in the same instant the prompt arrives. */
	private static final long ACCEPT_DELAY_MS = 500;
	/** A prompt counts as Trevor's if he spoke, or we called him, this recently. */
	private static final long TREVOR_CONTEXT_MS = 15_000;

	private long questStartMs;
	private boolean pending;
	private long callAtMs;
	private int calls;
	private long lastCallMs;
	private long lastTrevorMessageMs;

	private String acceptCommand;
	private long acceptAtMs;

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();
		long now = System.currentTimeMillis();

		int colon = text.indexOf(':');
		if (colon > 0 && text.substring(0, colon).contains("Trevor")) {
			lastTrevorMessageMs = now;
			// He picked up after our call; don't call again over his answer.
			if (pending && calls > 0) pending = false;
		}

		if (QUEST.matcher(text).find()) {
			// New quest arrived: stop calling.
			questStartMs = now;
			pending = false;
			acceptCommand = null;
		} else if (text.contains("[YES]") && text.contains("[NO]") && isTrevorContext(now)) {
			// Trevor answered and is asking to accept: don't call again over his prompt.
			pending = false;
			String command = findYesCommand(message);
			if (command != null && TrevorTrackerClient.CONFIG.autoAccept) {
				acceptCommand = command;
				acceptAtMs = now + ACCEPT_DELAY_MS;
			}
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

	private boolean isTrevorContext(long now) {
		return now - lastTrevorMessageMs < TREVOR_CONTEXT_MS || now - lastCallMs < TREVOR_CONTEXT_MS;
	}

	/** The command behind the clickable [YES] in the prompt. */
	private static String findYesCommand(Component message) {
		for (Component part : message.toFlatList()) {
			if (!part.getString().contains("YES")) continue;
			if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) return run.command();
		}
		return null;
	}

	public void tick(Minecraft mc) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (!config.enabled || mc.player == null) return;
		long now = System.currentTimeMillis();

		if (acceptCommand != null && now >= acceptAtMs) {
			if (config.autoAccept) mc.player.connection.sendCommand(stripSlash(acceptCommand));
			acceptCommand = null;
		}

		if (!pending || !config.autoCall || now < callAtMs) return;

		mc.player.connection.sendCommand(stripSlash(config.callCommand));
		lastCallMs = now;
		calls++;
		if (calls >= MAX_CALLS) {
			pending = false;
		} else {
			callAtMs = now + RETRY_MS;
		}
	}

	private static String stripSlash(String command) {
		return command.startsWith("/") ? command.substring(1) : command;
	}

	/** HUD line, or null when no call is coming up. */
	public String getHint() {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (acceptCommand != null && config.autoAccept) return "Accepting quest...";
		if (!pending || !config.autoCall) return null;
		long seconds = Math.max(0, (callAtMs - System.currentTimeMillis() + 999) / 1000);
		return seconds > 0 ? "Calling Trevor in " + seconds + "s" : "Calling Trevor...";
	}
}
