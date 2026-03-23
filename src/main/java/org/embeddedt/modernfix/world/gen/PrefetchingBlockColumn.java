package org.embeddedt.modernfix.world.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Wraps a BlockColumn and prefetches all block states for a column into an array.
 *
 * <p>Writes bypass {@link ChunkAccess#setBlockState} and go directly to the section,
 * skipping heightmap and light updates that are unnecessary during the surface building
 * stage (which runs before {@code INITIALIZE_LIGHT}).
 */
public class PrefetchingBlockColumn implements BlockColumn {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState VOID_AIR = Blocks.VOID_AIR.defaultBlockState();

    private final BlockState[] states;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private ChunkAccess chunk;
    private LevelChunkSection[] sections;
    private int minBuildHeight;
    private int localX, localZ;

    public PrefetchingBlockColumn(int height) {
        this.states = new BlockState[height];
    }

    public int getExpectedHeight() {
        return this.states.length;
    }

    /**
     * Prefetch all block states for the column at the given local XZ coordinates.
     * Must be called before any getBlock/setBlock calls for this column.
     */
    public void prefetch(ChunkAccess chunk, int localX, int localZ) {
        if (chunk.getHeight() != this.states.length) {
            throw new IllegalStateException();
        }
        this.chunk = chunk;
        this.sections = chunk.getSections();
        this.minBuildHeight = chunk.getMinBuildHeight();
        this.localX = localX;
        this.localZ = localZ;
        var sections = this.sections;
        var states = this.states;
        int offset = 0;
        for (LevelChunkSection section : sections) {
            if (section.hasOnlyAir()) {
                for (int y = 0; y < 16; y++) {
                    states[offset + y] = AIR;
                }
            } else {
                var container = section.getStates();
                for (int y = 0; y < 16; y++) {
                    states[offset + y] = container.get(localX, y, localZ);
                }
            }
            offset += 16;
        }
    }

    /**
     * Clear cached references to allow GC of the chunk between uses.
     */
    public void dispose() {
        this.chunk = null;
        this.sections = null;
    }

    @Override
    public BlockState getBlock(int y) {
        int idx = y - minBuildHeight;
        if (idx >= 0 && idx < states.length) {
            return states[idx];
        }
        return VOID_AIR;
    }

    private void markPostprocessing(int y) {
        cursor.set(
                SectionPos.sectionToBlockCoord(chunk.getPos().x, localX),
                y,
                SectionPos.sectionToBlockCoord(chunk.getPos().z, localZ)
        );
        chunk.markPosForPostprocessing(cursor);
    }

    private void updateHeightmap(Heightmap.Types type, int y, BlockState newState) {
        chunk.getOrCreateHeightmapUnprimed(type).update(localX, y, localZ, newState);
    }

    @Override
    public void setBlock(int y, BlockState state) {
        int idx = y - minBuildHeight;
        var states = this.states;
        if (idx >= 0 && idx < states.length) {
            BlockState oldState = states[idx];
            if (oldState == state) {
                return;
            }
            states[idx] = state;
            // Write directly to the section, bypassing ProtoChunk.setBlockState which
            // does expensive heightmap updates that are not needed during surface building.
            int sectionIdx = idx >> 4;
            sections[sectionIdx].setBlockState(localX, y & 15, localZ, state, false);
            if (!state.getFluidState().isEmpty()) {
                markPostprocessing(y);
            }
            // Update heightmaps if the air/motion-blocking properties changed.
            if (oldState.isAir() != state.isAir()) {
                updateHeightmap(Heightmap.Types.WORLD_SURFACE_WG, y, state);
            }
            if (oldState.blocksMotion() != state.blocksMotion()) {
                updateHeightmap(Heightmap.Types.OCEAN_FLOOR_WG, y, state);
            }
        }
    }
}
