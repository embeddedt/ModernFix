package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.embeddedt.modernfix.duck.IBlockState;
import org.embeddedt.modernfix.testing.util.BootstrapMinecraft;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@BootstrapMinecraft
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockStateCacheTest {
    @BeforeEach
    public void rebuildCache() {
        Blocks.rebuildCache();
    }

    /**
     * Initially, the cache should be invalid, and null.
     */
    @Test
    @Order(1)
    public void testCacheNullInitially() {
        BlockState stoneBlock = Blocks.STONE.defaultBlockState();
        assertTrue(((IBlockState)stoneBlock).isCacheInvalid());
        assertNull(stoneBlock.cache);
    }

    /**
     * When an API that needs the cache is called, it should be built and the invalid flag
     * becomes false.
     */
    @Test
    @Order(2)
    public void testCacheBuiltByRequest() {
        BlockState stoneBlock = Blocks.STONE.defaultBlockState();
        stoneBlock.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        assertFalse(((IBlockState)stoneBlock).isCacheInvalid());
        assertNotNull(stoneBlock.cache);
    }

    /**
     * When a second rebuild occurs, the invalid flag should be set to true, but the old cache
     * is not set to null, in order to prevent NPEs if a second thread is accessing the cache
     * when this takes place.
     */
    @Test
    @Order(3)
    public void testCacheInvalidatedByLateRebuild() {
        BlockState stoneBlock = Blocks.STONE.defaultBlockState();
        stoneBlock.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        Blocks.rebuildCache();
        assertTrue(((IBlockState)stoneBlock).isCacheInvalid());
        assertNotNull(stoneBlock.cache);
    }

    /**
     * Tests that the fluidState and isRandomlyTicking caching fields added by Mojang to blockstates are correctly
     * handled by the dynamic cache system.
     */
    @Test
    @Order(4)
    public void testExtraFieldCachingCorrect() {
        Block[] blocksToCheck = new Block[] { Blocks.WATER, Blocks.FARMLAND };
        for(Block block : blocksToCheck) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                // check that the fluid states match
                assertEquals(block.getFluidState(state), state.getFluidState(), "mismatched fluid state on " + state);
                // check that random ticking flag matches
                assertEquals(block.isRandomlyTicking(state), state.isRandomlyTicking(), "mismatched random tick state on " + state);
            }
        }
    }
}
