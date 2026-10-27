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

/** Finds ender nodes (purple terracotta) around the player. */
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
}
