package com.trevortracker.client;

import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Renews Trevor's quest through the Abiphone and accepts the [YES] prompt.
 *
 * In key-press mode every press of the action key does exactly one thing (call, open the
 * phone, click Trevor, click Call or accept). In fully automatic mode the same steps run
 * on their own with a short pause between them.
 *
 * Trevor's cooldown is 20 seconds from when he gives the quest; the call is made
 * {@link TrackerConfig#renewLeadSeconds} before that, but never before the hunt has ended.
 */
public class QuestRenewer {
	private enum Step {
		/** No hunt has ended yet, or renewing is off. */
		IDLE,
		/** Hunt over, waiting for the cooldown. */
		WAITING,
		/** Time to call Trevor. */
		READY,
		/** Right-clicked the Abiphone, waiting for its menu. */
		PHONE_OPENING,
		/** Clicked Trevor's contact, looking for a Call button. */
		CONTACT_CLICKED,
		/** Call made, waiting for Trevor to give the quest. */
		CALLED
	}

	private static final Pattern QUEST = Pattern.compile("\\[NPC] Trevor: You can find your \\w+ animal near");
	private static final String MOB_DIED = "Return to the Trapper soon to get a new animal to hunt!";
	private static final String OUT_OF_TIME = "You ran out of time and the animal disappeared!";

	private static final long COOLDOWN_MS = 20_000;
	private static final long STEP_DELAY_MS = 350;
	private static final long CALL_TIMEOUT_MS = 10_000;
	private static final long PROMPT_TIMEOUT_MS = 30_000;
	private static final int MAX_AUTO_RETRIES = 2;

	private Step step = Step.IDLE;
	private long stepTime;
	private int retries;
	private int previousSlot = -1;

	private long questStartMs;
	private long questEndMs;
	private long lastTrevorMessageMs;

	private String acceptCommand;
	private long acceptCommandMs;

	// ----- chat -----

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();
		long now = System.currentTimeMillis();

		int colon = text.indexOf(':');
		if (colon > 0 && text.substring(0, colon).contains("Trevor")) lastTrevorMessageMs = now;

		if (QUEST.matcher(text).find()) {
			questStartMs = now;
			questEndMs = 0;
			retries = 0;
			acceptCommand = null;
			setStep(Step.IDLE);
			return;
		}

		if (text.contains(MOB_DIED) || text.contains(OUT_OF_TIME)) {
			questEndMs = now;
			setStep(Step.WAITING);
			return;
		}

		// "Click an option: [YES] - [NO]", only right after Trevor talks or we called him.
		if (text.contains("[YES]") && text.contains("[NO]") && isTrevorContext(now)) {
			String command = findYesCommand(message);
			if (command != null) {
				acceptCommand = command;
				acceptCommandMs = now;
			}
		}
	}

	private boolean isTrevorContext(long now) {
		return now - lastTrevorMessageMs < 15_000 || (step == Step.CALLED && now - stepTime < CALL_TIMEOUT_MS);
	}

	private static String findYesCommand(Component message) {
		for (Component part : message.toFlatList()) {
			if (!part.getString().contains("YES")) continue;
			if (part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) return run.command();
		}
		return null;
	}

	// ----- tick -----

	public void tick(Minecraft mc, HuntTracker tracker) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		LocalPlayer player = mc.player;
		if (!config.enabled || player == null) return;
		long now = System.currentTimeMillis();

		if (acceptCommand != null && now - acceptCommandMs > PROMPT_TIMEOUT_MS) acceptCommand = null;
		if (acceptCommand != null && config.autoAccept && config.fullyAutomatic
				&& now - acceptCommandMs > STEP_DELAY_MS) {
			accept(mc);
			return;
		}

		if (!config.autoRenew) {
			if (step != Step.IDLE && step != Step.CALLED) setStep(Step.IDLE);
			return;
		}

		switch (step) {
			case IDLE -> {
			}
			case WAITING -> {
				if (now >= renewAt(config)) setStep(Step.READY);
			}
			case READY -> {
				if (tracker.isActive()) setStep(Step.IDLE);
				else if (config.fullyAutomatic) startCall(mc);
			}
			case PHONE_OPENING -> {
				if (!(mc.screen instanceof AbstractContainerScreen<?>) && now - stepTime > 3_000) {
					fail(mc, "The Abiphone menu didn't open");
				} else if (config.fullyAutomatic && now - stepTime > STEP_DELAY_MS && findSlot(mc, "Trevor") >= 0) {
					clickContact(mc);
				}
			}
			case CONTACT_CLICKED -> {
				boolean menuOpen = mc.screen instanceof AbstractContainerScreen<?>;
				if (!menuOpen || (findCallSlot(mc) < 0 && now - stepTime > 2_000)) {
					// No Call button showed up, so clicking Trevor already started the call.
					restoreSlot(mc);
					setStep(Step.CALLED);
				} else if (config.fullyAutomatic && now - stepTime > STEP_DELAY_MS && findCallSlot(mc) >= 0) {
					clickCall(mc);
				}
			}
			case CALLED -> {
				if (now - stepTime > CALL_TIMEOUT_MS) {
					restoreSlot(mc);
					if (config.fullyAutomatic && ++retries > MAX_AUTO_RETRIES) {
						TrevorTrackerClient.say(mc, "Couldn't renew the quest, stopping. Press the action key to try again.");
						setStep(Step.IDLE);
					} else {
						setStep(Step.READY);
					}
				}
			}
		}
	}

	/** Called on each press of the action key: does exactly one step. */
	public void onActionKey(Minecraft mc, HuntTracker tracker) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (mc.player == null || !config.enabled) return;

		if (acceptCommand != null && config.autoAccept) {
			accept(mc);
			return;
		}
		switch (step) {
			case PHONE_OPENING -> {
				if (findSlot(mc, "Trevor") >= 0) clickContact(mc);
			}
			case CONTACT_CLICKED -> {
				if (findCallSlot(mc) >= 0) clickCall(mc);
			}
			case CALLED -> {
			}
			default -> {
				if (tracker.isActive()) {
					TrevorTrackerClient.say(mc, "You still have an active hunt.");
				} else {
					retries = 0;
					startCall(mc);
				}
			}
		}
	}

	private long renewAt(TrackerConfig config) {
		return Math.max(questStartMs + COOLDOWN_MS - config.renewLeadSeconds * 1000L, questEndMs);
	}

	// ----- actions -----

	private void startCall(Minecraft mc) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		if (config.boosterCookie) {
			String command = config.callCommand.startsWith("/") ? config.callCommand.substring(1) : config.callCommand;
			mc.player.connection.sendCommand(command);
			setStep(Step.CALLED);
			return;
		}

		int phoneSlot = findAbiphoneInHotbar(mc.player);
		if (phoneSlot < 0) {
			TrevorTrackerClient.say(mc, "Put your Abiphone in your hotbar to call Trevor.");
			setStep(Step.IDLE);
			return;
		}
		previousSlot = mc.player.getInventory().getSelectedSlot();
		mc.player.getInventory().setSelectedSlot(phoneSlot);
		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		setStep(Step.PHONE_OPENING);
	}

	private void clickContact(Minecraft mc) {
		int slot = findSlot(mc, "Trevor");
		if (slot < 0) return;
		click(mc, slot);
		setStep(Step.CONTACT_CLICKED);
	}

	private void clickCall(Minecraft mc) {
		int slot = findCallSlot(mc);
		if (slot < 0) return;
		click(mc, slot);
		restoreSlot(mc);
		setStep(Step.CALLED);
	}

	private void accept(Minecraft mc) {
		String command = acceptCommand.startsWith("/") ? acceptCommand.substring(1) : acceptCommand;
		acceptCommand = null;
		mc.player.connection.sendCommand(command);
	}

	private void fail(Minecraft mc, String reason) {
		restoreSlot(mc);
		TrevorTrackerClient.say(mc, reason + ".");
		setStep(TrevorTrackerClient.CONFIG.fullyAutomatic ? Step.IDLE : Step.READY);
	}

	private void restoreSlot(Minecraft mc) {
		if (previousSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(previousSlot);
		previousSlot = -1;
	}

	private void setStep(Step next) {
		step = next;
		stepTime = System.currentTimeMillis();
	}

	// ----- inventory helpers -----

	private static String plainName(ItemStack stack) {
		return ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
	}

	private static int findAbiphoneInHotbar(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && plainName(stack).toLowerCase().contains("abiphone")) return i;
		}
		return -1;
	}

	/** Index in the open menu of the first slot whose item name contains {@code text}. */
	private static int findSlot(Minecraft mc, String text) {
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return -1;
		AbstractContainerMenu menu = screen.getMenu();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot.container == mc.player.getInventory()) continue;
			if (slot.hasItem() && plainName(slot.getItem()).contains(text)) return i;
		}
		return -1;
	}

	private static int findCallSlot(Minecraft mc) {
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return -1;
		AbstractContainerMenu menu = screen.getMenu();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot.container == mc.player.getInventory() || !slot.hasItem()) continue;
			String name = plainName(slot.getItem());
			if (name.equalsIgnoreCase("Call") || name.startsWith("Call ")) return i;
		}
		return -1;
	}

	private static void click(Minecraft mc, int slotIndex) {
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotIndex, 0, ContainerInput.PICKUP, mc.player);
	}

	// ----- HUD -----

	/** One line telling the player what happens next, or null when there's nothing to say. */
	public String getHint(String keyName) {
		TrackerConfig config = TrevorTrackerClient.CONFIG;
		boolean auto = config.fullyAutomatic;
		if (acceptCommand != null && config.autoAccept) {
			return auto ? "Accepting quest..." : "Press " + keyName + " to accept the quest";
		}
		if (!config.autoRenew) return null;
		return switch (step) {
			case IDLE -> null;
			case WAITING -> {
				long seconds = Math.max(0, (renewAt(config) - System.currentTimeMillis() + 999) / 1000);
				yield "Calling Trevor in " + seconds + "s";
			}
			case READY -> auto ? "Calling Trevor..." : "Press " + keyName + " to call Trevor";
			case PHONE_OPENING -> auto ? "Opening Abiphone..." : "Press " + keyName + " to pick Trevor";
			case CONTACT_CLICKED -> auto ? "Calling..." : "Press " + keyName + " to press Call";
			case CALLED -> "Waiting for Trevor...";
		};
	}
}
