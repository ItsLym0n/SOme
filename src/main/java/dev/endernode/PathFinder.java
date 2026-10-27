package dev.endernode;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* over standable block positions: walks on solid ground, steps up 1 block,
 * drops down up to 3 blocks, and does not cut corners.
 */
public final class PathFinder {
	/** Stop once within this distance of the node (you can mine it from there). */
	private static final double REACH = 3.0;
	private static final int MAX_DROP = 3;
	private static final int MAX_EXPANSIONS = 4000;
	/** How many of the closest nodes get a real walking path computed when picking a target. */
	private static final int MAX_CANDIDATES = 6;
	/** Extra walking cost per block of height beyond the "same level" range. */
	private static final double LEVEL_PENALTY = 2.0;
	/** Extra cost per block still left to go when a node can't be fully reached. */
	private static final double UNREACHED_PENALTY = 3.0;

	private static final int[][] DIRECTIONS = {
			{1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};

	private final Level level;
	private final Long2ByteOpenHashMap passableCache = new Long2ByteOpenHashMap();
	private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

	private PathFinder(Level level) {
		this.level = level;
	}

	/** The node to walk to and the walking path (feet positions) towards it. */
	public record Route(BlockPos target, List<BlockPos> path) {
	}

	private record Search(List<BlockPos> path, double cost, double remaining) {
	}

	/**
	 * Picks the node with the shortest walk. Nodes more than {@code sameLevelRange} blocks
	 * above or below you cost a little extra per block, so a node on your level wins a
	 * close race, but a node just down the stairs beats one far away on your level.
	 * {@code keep} (the current target, may be null) gets {@code keepBonus} blocks of head
	 * start so the path only switches when another node is clearly better.
	 * Returns null if there are no nodes.
	 */
	public static @Nullable Route findBest(Level level, BlockPos start, List<BlockPos> nodes, int sameLevelRange,
			@Nullable BlockPos keep, double keepBonus) {
		if (nodes.isEmpty()) {
			return null;
		}
		// Only path to the few closest-looking nodes
		List<BlockPos> candidates = new ArrayList<>(nodes);
		candidates.sort(Comparator.comparingDouble(node -> estimate(start, node, sameLevelRange)));
		candidates = new ArrayList<>(candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size())));
		if (keep != null) {
			// Always path to the current target first so it's never dropped by the shortlist
			candidates.remove(keep);
			candidates.add(0, keep);
		}

		PathFinder finder = new PathFinder(level);
		Route best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos node : candidates) {
			double bonus = node.equals(keep) ? keepBonus : 0;
			if (estimate(start, node, sameLevelRange) - bonus >= bestScore) {
				continue; // can't beat what we already have, even walking in a straight line
			}
			Search search = finder.search(start, node);
			double score = search.cost() + search.remaining() * UNREACHED_PENALTY
					+ levelPenalty(start, node, sameLevelRange) - bonus;
			if (score < bestScore) {
				bestScore = score;
				best = new Route(node, search.path());
			}
		}
		return best;
	}

	/** Lowest possible score for a node: straight-line walk plus the level penalty. */
	private static double estimate(BlockPos start, BlockPos node, int sameLevelRange) {
		double straight = Math.sqrt(Vec3.atCenterOf(node).distanceToSqr(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5));
		return Math.max(0, straight - REACH) + levelPenalty(start, node, sameLevelRange);
	}

	private static double levelPenalty(BlockPos start, BlockPos node, int sameLevelRange) {
		return Math.max(0, Math.abs(node.getY() - start.getY()) - sameLevelRange) * LEVEL_PENALTY;
	}

	private Search search(BlockPos start, BlockPos target) {
		start = findGround(start);
		Vec3 goal = Vec3.atCenterOf(target);

		Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
		PriorityQueue<Node> open = new PriorityQueue<>();
		Node first = new Node(start, null, 0, heuristic(start, goal));
		nodes.put(start.asLong(), first);
		open.add(first);
		Node best = first;

		int expansions = 0;
		while (!open.isEmpty() && expansions++ < MAX_EXPANSIONS) {
			Node current = open.poll();
			if (current.closed) {
				continue;
			}
			current.closed = true;
			if (current.h < best.h) {
				best = current;
			}
			if (current.h <= 0) {
				break;
			}
			for (int[] dir : DIRECTIONS) {
				BlockPos next = step(current.pos, dir[0], dir[1]);
				if (next == null) {
					continue;
				}
				double cost = current.g + (dir[0] != 0 && dir[1] != 0 ? 1.414 : 1.0)
						+ Math.abs(next.getY() - current.pos.getY()) * 0.5;
				Node known = nodes.get(next.asLong());
				if (known == null) {
					Node node = new Node(next, current, cost, heuristic(next, goal));
					nodes.put(next.asLong(), node);
					open.add(node);
				} else if (!known.closed && cost < known.g) {
					// Re-add with the better cost; the stale entry is skipped when polled
					known.closed = true;
					Node node = new Node(next, current, cost, known.h);
					nodes.put(next.asLong(), node);
					open.add(node);
				}
			}
		}

		List<BlockPos> path = new ArrayList<>();
		for (Node n = best; n != null; n = n.parent) {
			path.add(n.pos);
		}
		Collections.reverse(path);
		return new Search(path, best.g, best.h);
	}

	/** Where you end up walking one block in direction (dx, dz) from {@code from}, or null if you can't. */
	private BlockPos step(BlockPos from, int dx, int dz) {
		BlockPos to = from.offset(dx, 0, dz);
		boolean diagonal = dx != 0 && dz != 0;
		if (diagonal && (!clear(from.offset(dx, 0, 0)) || !clear(from.offset(0, 0, dz)))) {
			return null; // would clip a corner
		}
		if (standable(to)) {
			return to;
		}
		// Step up one block (needs headroom to jump)
		if (!diagonal && passable(from.above(2)) && standable(to.above())) {
			return to.above();
		}
		// Walk off an edge and drop
		if (clear(to)) {
			for (int d = 1; d <= MAX_DROP; d++) {
				BlockPos below = to.below(d);
				if (!passable(below)) {
					break;
				}
				if (standable(below)) {
					return below;
				}
			}
		}
		return null;
	}

	/** If you're in the air (jumping, falling), use the ground below you as the start. */
	private BlockPos findGround(BlockPos pos) {
		for (int d = 0; d <= MAX_DROP; d++) {
			if (standable(pos.below(d))) {
				return pos.below(d);
			}
		}
		return pos;
	}

	private static double heuristic(BlockPos pos, Vec3 goal) {
		// Distance from roughly chest height to the node, minus mining reach
		double dist = Math.sqrt(goal.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
		return Math.max(0, dist - REACH);
	}

	/** Feet and head fit here. */
	private boolean clear(BlockPos feet) {
		return passable(feet) && passable(feet.above());
	}

	/** You can stand here: feet and head fit, and there's solid ground below. */
	private boolean standable(BlockPos feet) {
		return clear(feet) && !passable(feet.below()) && !isLava(feet.below());
	}

	private boolean passable(BlockPos pos) {
		long key = pos.asLong();
		byte cached = passableCache.getOrDefault(key, (byte) -1);
		if (cached >= 0) {
			return cached == 1;
		}
		mutable.set(pos);
		BlockState state = level.getBlockState(mutable);
		boolean result = state.getCollisionShape(level, mutable).isEmpty() && !state.getFluidState().is(FluidTags.LAVA);
		passableCache.put(key, (byte) (result ? 1 : 0));
		return result;
	}

	private boolean isLava(BlockPos pos) {
		return level.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
	}

	private static final class Node implements Comparable<Node> {
		final BlockPos pos;
		final Node parent;
		final double g;
		final double h;
		boolean closed;

		Node(BlockPos pos, Node parent, double g, double h) {
			this.pos = pos;
			this.parent = parent;
			this.g = g;
			this.h = h;
		}

		@Override
		public int compareTo(Node other) {
			return Double.compare(g + h, other.g + other.h);
		}
	}
}
