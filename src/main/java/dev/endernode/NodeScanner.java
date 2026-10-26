package dev.endernode;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Finds ender nodes (purple terracotta) around the player and picks which one to walk to. */
public final class NodeScanner {
	private static final Predicate<BlockState> IS_NODE = state -> state.is(Blocks.PURPLE_TERRACOTTA);

	private NodeScanner() {
	}

	/** Returns every node within {@code range} blocks of {@code center}. */
	public static List<BlockPos> scan(ClientLevel level, BlockPos center, int range) {
		List<BlockPos> found = new ArrayList<>();
		long rangeSq = (long) range * range;

		int minChunkX = (center.getX() - range) >> 4;
		int maxChunkX = (center.getX() + range) >> 4;
		int minChunkZ = (center.getZ() - range) >> 4;
		int maxChunkZ = (center.getZ() + range) >> 4;
		int minY = Math.max(level.getMinY(), center.getY() - range);
		int maxY = Math.min(level.getMaxY(), center.getY() + range);

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
					int index = level.getSectionIndexFromSectionY(sectionY);
					if (index < 0 || index >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					// Skip whole 16x16x16 sections that can't contain the block
					if (section.hasOnlyAir() || !section.maybeHas(IS_NODE)) {
						continue;
					}
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								if (!IS_NODE.test(section.getBlockState(x, y, z))) {
									continue;
								}
								pos.set((cx << 4) + x, (sectionY << 4) + y, (cz << 4) + z);
								if (pos.distSqr(center) <= rangeSq) {
									found.add(pos.immutable());
								}
							}
						}
					}
				}
			}
		}
		return found;
	}

	/**
	 * Picks the node to walk to: the nearest one within {@code sameLevelRange} blocks
	 * up/down of {@code feet}, or the nearest overall if none are on your level.
	 */
	public static BlockPos pickTarget(List<BlockPos> nodes, BlockPos feet, int sameLevelRange) {
		BlockPos sameLevel = null;
		BlockPos any = null;
		double sameLevelDist = Double.MAX_VALUE;
		double anyDist = Double.MAX_VALUE;
		for (BlockPos node : nodes) {
			double dist = node.distSqr(feet);
			if (dist < anyDist) {
				anyDist = dist;
				any = node;
			}
			if (Math.abs(node.getY() - feet.getY()) <= sameLevelRange && dist < sameLevelDist) {
				sameLevelDist = dist;
				sameLevel = node;
			}
		}
		return sameLevel != null ? sameLevel : any;
	}
}
