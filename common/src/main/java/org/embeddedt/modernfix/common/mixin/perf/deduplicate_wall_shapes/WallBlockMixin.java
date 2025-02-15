package org.embeddedt.modernfix.common.mixin.perf.deduplicate_wall_shapes;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;

/**
 * Most wall blocks use the default set of vanilla properties, and the default sizes for their shapes. This means
 * there is no need to reconstruct a separate VoxelShape instance for each wall, we can just repurpose the
 * same shape instances. To do this we can cache a mapping between a state (represented only as its prop->value map)
 * and the desired shape, and generate the BlockState->VoxelShape map from this for each block.
 */
@Mixin(WallBlock.class)
public abstract class WallBlockMixin extends Block {
    private static Map<ImmutableList<Float>, Pair<Map<Map<Property<?>, Comparable<?>>, VoxelShape>, StateDefinition<Block, BlockState>>> CACHE_BY_SHAPE_VALS = new HashMap<>();

    public WallBlockMixin(Properties properties) {
        super(properties);
    }

    // TODO reimplement for 1.21.5
}
