package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
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
     * Initially, the cache should be invalid.
     */
    @Test
    @Order(1)
    public void testCacheNullInitially() {
        BlockState stoneBlock = Blocks.STONE.defaultBlockState();
        assertTrue(((IBlockState)stoneBlock).isCacheInvalid());
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
}
