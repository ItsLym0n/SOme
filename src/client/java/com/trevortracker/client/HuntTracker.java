package com.trevortracker.client;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps track of the current Trevor hunt: which area he sent you to, the rarity of
 * the animal, the animal entity once it's loaded, and the walking path to it.
 */
public class HuntTracker {
	public enum Mode {
		PATH, LINE, BOTH;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private static final Pattern QUEST = Pattern.compile(
			"\\[NPC] Trevor: You can find your (\\w+) animal near (?:the )?(.+?)\\.?$");
	private static final String MOB_DIED = "Return to the Trapper soon to get a new animal to hunt!";
	private static final String OUT_OF_TIME = "You ran out of time and the animal disappeared!";

	private static final Pattern ANY_HUNT_NAME = Pattern.compile(
			"\\b(TRACKABLE|UNTRACKABLE|UNDETECTED|ENDANGERED|ELUSIVE)\\s+(COW|PIG|SHEEP|CHICKEN|RABBIT|HORSE)\\b",
			Pattern.CASE_INSENSITIVE);
	/** Max health values Hypixel gives Trevor animals, used if no nametag can be matched. */
	private static final Set<Integer> HUNT_HEALTHS = Set.of(100, 200, 500, 1000, 2000, 5000, 10000, 30000);

	private static final int PATH_BUDGET_PER_TICK = 2500;
	private static final int REPATH_INTERVAL_TICKS = 15;

	private HuntArea area;
	private String rarity;
	private Entity target;
	private String targetName;

	private PathFinder search;
	private List<BlockPos> path = List.of();
	private int ticksSinceSearch = REPATH_INTERVAL_TICKS;

	// ----- chat -----

	public void onChat(Component message) {
		String text = ChatFormatting.stripFormatting(message.getString()).trim();

		Matcher m = QUEST.matcher(text);
		if (m.find()) {
			startHunt(m.group(1), HuntArea.fromName(m.group(2)));
			return;
		}
		if (text.contains(MOB_DIED) || text.contains(OUT_OF_TIME)) {
			clear();
		}
	}

	public void startHunt(String rarity, HuntArea area) {
		clear();
		this.rarity = rarity == null ? null : rarity.toUpperCase(Locale.ROOT);
		this.area = area;
	}

	public void clear() {
		area = null;
		rarity = null;
		target = null;
		targetName = null;
		search = null;
		path = List.of();
		ticksSinceSearch = REPATH_INTERVAL_TICKS;
	}

	// ----- tick -----

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (!TrevorTrackerClient.CONFIG.enabled || player == null || level == null) return;

		if (target == null || target.isRemoved() || !target.isAlive()) {
			target = null;
			targetName = null;
			if (level.getGameTime() % 5 == 0) findTarget(level);
		}

		BlockPos goal = getGoalBlock();
		if (goal == null) {
			search = null;
			path = List.of();
			return;
		}

		if (TrevorTrackerClient.CONFIG.mode == Mode.LINE) return;

		ticksSinceSearch++;
		if (search != null) {
			if (search.step(PATH_BUDGET_PER_TICK)) {
				path = search.getResult();
				search = null;
			}
		} else if (ticksSinceSearch >= REPATH_INTERVAL_TICKS) {
			ticksSinceSearch = 0;
			double radius = target != null ? 1.5 : 4.0;
			search = new PathFinder(level, player.blockPosition(), goal, radius);
		}
	}

	private void findTarget(ClientLevel level) {
		Pattern wanted = rarity == null ? ANY_HUNT_NAME : Pattern.compile(
				"\\b" + Pattern.quote(rarity) + "\\s+(COW|PIG|SHEEP|CHICKEN|RABBIT|HORSE)\\b",
				Pattern.CASE_INSENSITIVE);

		Entity healthMatch = null;
		for (Entity e : level.entitiesForRendering()) {
			if (!e.hasCustomName()) {
				if (healthMatch == null && e instanceof Animal animal
						&& HUNT_HEALTHS.contains(Math.round(animal.getMaxHealth()))) {
					healthMatch = e;
				}
				continue;
			}

			String name = ChatFormatting.stripFormatting(e.getCustomName().getString());
			Matcher m = wanted.matcher(name);
			if (!m.find()) continue;

			if (e instanceof Animal) {
				setTarget(e, m.group());
				return;
			}
			if (e instanceof ArmorStand) {
				// Hypixel puts the name on an invisible armor stand above the mob.
				Entity animal = nearestAnimalBelow(level, e);
				setTarget(animal != null ? animal : e, m.group());
				return;
			}
		}

		if (healthMatch != null && area != null) {
			setTarget(healthMatch, "Hunted " + healthMatch.getType().getDescription().getString());
		}
	}

	private static Entity nearestAnimalBelow(ClientLevel level, Entity stand) {
		Entity best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof Animal)) continue;
			double dx = e.getX() - stand.getX();
			double dz = e.getZ() - stand.getZ();
			double dy = stand.getY() - e.getY();
			double horiz = dx * dx + dz * dz;
			if (horiz > 9 || dy < -1 || dy > 5) continue;
			double dist = horiz + dy * dy;
			if (dist < bestDist) {
				bestDist = dist;
				best = e;
			}
		}
		return best;
	}

	private void setTarget(Entity e, String name) {
		target = e;
		targetName = capitalize(name);
		search = null;
		ticksSinceSearch = REPATH_INTERVAL_TICKS;
	}

	private static String capitalize(String s) {
		StringBuilder out = new StringBuilder();
		for (String word : s.toLowerCase(Locale.ROOT).split("\\s+")) {
			if (word.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	// ----- getters for rendering / HUD -----

	public boolean isActive() {
		return area != null || target != null;
	}

	public Entity getTarget() {
		return target;
	}

	public String getTargetName() {
		return targetName;
	}

	public HuntArea getArea() {
		return area;
	}

	public String getRarity() {
		return rarity;
	}

	public List<BlockPos> getPath() {
		return path;
	}

	public boolean isSearching() {
		return search != null;
	}

	private BlockPos getGoalBlock() {
		if (target != null) return target.blockPosition();
		if (area != null) return area.pos;
		return null;
	}

	/** Where to point: the animal (smoothly interpolated) or the area centre. */
	public Vec3 getGoalPos(float partialTick) {
		if (target != null) return target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
		if (area != null) return Vec3.atCenterOf(area.pos);
		return null;
	}
}
