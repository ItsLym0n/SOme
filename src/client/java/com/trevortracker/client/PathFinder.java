package com.trevortracker.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A* search over the blocks around the player, run a few thousand nodes per tick
 * so it never freezes the game. It finds a route you can actually walk: it steps
 * up one block, drops up to three, swims through water and avoids lava and cactus.
 *
 * If the goal can't be reached (for example it's in chunks that aren't loaded yet),
 * it returns the route to the closest point it found.
 */
public class PathFinder {
	private static final int[][] DIRS = {
			{1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	private static final int MAX_DROP = 3;
	private static final int MAX_NODES = 40_000;

	private static final class Node implements Comparable<Node> {
		final BlockPos pos;
		Node parent;
		double g;
		double f;
		boolean closed;

		Node(BlockPos pos) {
			this.pos = pos;
		}

		@Override
		public int compareTo(Node o) {
			return Double.compare(f, o.f);
		}
	}

	private final Level level;
	private final BlockPos goal;
	private final double goalRadius;
	private final Map<Long, Node> nodes = new HashMap<>();
	private final PriorityQueue<Node> open = new PriorityQueue<>();
	private Node best;
	private double bestH = Double.MAX_VALUE;
	private int expanded;
	private List<BlockPos> result;

	public PathFinder(Level level, BlockPos start, BlockPos goal, double goalRadius) {
		this.level = level;
		this.goal = goal;
		this.goalRadius = goalRadius;

		Node s = new Node(findStandable(start));
		s.g = 0;
		s.f = heuristic(s.pos);
		nodes.put(s.pos.asLong(), s);
		open.add(s);
		best = s;
		bestH = s.f;
	}

	public boolean isDone() {
		return result != null;
	}

	public List<BlockPos> getResult() {
		return result;
	}

	/** Runs up to {@code budget} node expansions. Returns true once the search has finished. */
	public boolean step(int budget) {
		if (result != null) return true;

		for (int i = 0; i < budget; i++) {
			Node cur = open.poll();
			if (cur == null || expanded >= MAX_NODES) {
				result = build(best);
				return true;
			}
			if (cur.closed) continue;
			cur.closed = true;
			expanded++;

			double h = heuristic(cur.pos);
			if (h < bestH) {
				bestH = h;
				best = cur;
			}
			if (reachedGoal(cur.pos)) {
				result = build(cur);
				return true;
			}

			expand(cur);
		}
		return false;
	}

	private void expand(Node cur) {
		BlockPos p = cur.pos;
		for (int[] d : DIRS) {
			boolean diagonal = d[0] != 0 && d[1] != 0;
			if (diagonal) {
				// Don't cut corners through walls.
				if (!isOpenColumn(p.offset(d[0], 0, 0)) || !isOpenColumn(p.offset(0, 0, d[1]))) continue;
			}

			BlockPos side = p.offset(d[0], 0, d[1]);
			BlockPos next = null;
			double extra = 0;

			if (isStandable(side)) {
				next = side;
			} else if (isOpenColumn(side)) {
				// Gap in front: look for ground below.
				for (int dy = 1; dy <= MAX_DROP; dy++) {
					BlockPos below = side.below(dy);
					if (!isPassable(below.above())) break;
					if (isStandable(below)) {
						next = below;
						extra = 0.3 * dy;
						break;
					}
				}
			} else if (!diagonal && isPassable(p.above(2)) && isStandable(side.above())) {
				// Wall in front: step or jump up one block.
				next = side.above();
				extra = 0.6;
			}

			if (next == null) continue;

			double cost = (diagonal ? 1.414 : 1.0) + extra;
			if (isWater(next)) cost += 1.5;
			relax(cur, next, cur.g + cost);
		}
	}

	private void relax(Node from, BlockPos pos, double g) {
		long key = pos.asLong();
		Node n = nodes.get(key);
		if (n == null) {
			n = new Node(pos);
			nodes.put(key, n);
		} else if (n.closed || g >= n.g) {
			return;
		}
		n.parent = from;
		n.g = g;
		n.f = g + heuristic(pos) * 1.2;
		open.add(n);
	}

	private boolean reachedGoal(BlockPos p) {
		double dx = p.getX() + 0.5 - (goal.getX() + 0.5);
		double dz = p.getZ() + 0.5 - (goal.getZ() + 0.5);
		double dy = p.getY() - goal.getY();
		return dx * dx + dz * dz <= goalRadius * goalRadius && Math.abs(dy) <= 3;
	}

	private double heuristic(BlockPos p) {
		double dx = p.getX() - goal.getX();
		double dy = p.getY() - goal.getY();
		double dz = p.getZ() - goal.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static List<BlockPos> build(Node end) {
		List<BlockPos> path = new ArrayList<>();
		for (Node n = end; n != null; n = n.parent) path.add(n.pos);
		Collections.reverse(path);
		return path;
	}

	/** The player may be mid-air or on a slab; snap down to the nearest block we can stand on. */
	private BlockPos findStandable(BlockPos start) {
		for (int dy = 0; dy <= 4; dy++) {
			if (isStandable(start.below(dy))) return start.below(dy);
		}
		return start;
	}

	// ----- block checks -----

	private boolean isLoaded(BlockPos p) {
		return level.hasChunk(p.getX() >> 4, p.getZ() >> 4);
	}

	private boolean isDangerous(BlockState state, BlockPos p) {
		return state.is(Blocks.CACTUS) || state.is(Blocks.FIRE) || state.is(Blocks.MAGMA_BLOCK)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB)
				|| level.getFluidState(p).is(FluidTags.LAVA);
	}

	private boolean isPassable(BlockPos p) {
		if (!isLoaded(p)) return false;
		BlockState state = level.getBlockState(p);
		if (isDangerous(state, p)) return false;
		return state.getCollisionShape(level, p).isEmpty();
	}

	private boolean isOpenColumn(BlockPos p) {
		return isPassable(p) && isPassable(p.above());
	}

	private boolean isWater(BlockPos p) {
		return level.getFluidState(p).is(FluidTags.WATER);
	}

	/** Feet and head fit at {@code p}, and there is ground (or water) to stand in. */
	private boolean isStandable(BlockPos p) {
		if (!isOpenColumn(p)) return false;
		if (isWater(p)) return true;

		BlockPos floorPos = p.below();
		if (!isLoaded(floorPos)) return false;
		BlockState floor = level.getBlockState(floorPos);
		if (isDangerous(floor, floorPos)) return false;
		VoxelShape shape = floor.getCollisionShape(level, floorPos);
		if (shape.isEmpty()) return false;
		// Fences and walls are 1.5 blocks tall; you can't walk along them normally.
		return shape.max(Direction.Axis.Y) <= 1.0;
	}
}
