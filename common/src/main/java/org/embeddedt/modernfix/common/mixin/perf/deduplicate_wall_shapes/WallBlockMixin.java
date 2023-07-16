package org.embeddedt.modernfix.common.mixin.perf.deduplicate_wall_shapes;

import com.google.common.collect.ImmutableMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Mixin(WallBlock.class)
public abstract class WallBlockMixin extends Block {
    private static Map<ImmutableMap<Property<?>, Comparable<?>>, VoxelShape> CACHE_BY_PROPERTIES = new HashMap<>();
    private static StateDefinition<Block, BlockState> CACHED_DEFINITION = null;
    private static float[] CACHED_FLOATS = null;

    public WallBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "makeShapes", at = @At("HEAD"), cancellable = true)
    private synchronized void useCachedShapeMap(float f1, float f2, float f3, float f4, float f5, float f6, CallbackInfoReturnable<Map<BlockState, VoxelShape>> cir) {
        if(CACHED_DEFINITION != null) {
            // check if this state container's properties exactly match the one we used for the cache
            if(CACHED_DEFINITION.getProperties().equals(this.stateDefinition.getProperties()) && Arrays.equals(CACHED_FLOATS, new float[] { f1, f2, f3, f4, f5, f6 })) {
                ImmutableMap.Builder<BlockState, VoxelShape> builder = ImmutableMap.builder();
                for(BlockState state : this.stateDefinition.getPossibleStates()) {
                    builder.put(state, CACHE_BY_PROPERTIES.get(state.getValues()));
                }
                cir.setReturnValue(builder.build());
            }
        }
    }

    @Inject(method = "makeShapes", at = @At("RETURN"))
    private synchronized void storeCachedShapesByProperty(float f1, float f2, float f3, float f4, float f5, float f6, CallbackInfoReturnable<Map<BlockState, VoxelShape>> cir) {
        if(CACHE_BY_PROPERTIES.size() == 0) {
            Map<BlockState, VoxelShape> shapeMap = cir.getReturnValue();
            for(Map.Entry<BlockState, VoxelShape> entry : shapeMap.entrySet()) {
                CACHE_BY_PROPERTIES.put(entry.getKey().getValues(), entry.getValue());
            }
            CACHED_FLOATS = new float[] { f1, f2, f3, f4, f5, f6 };
            CACHED_DEFINITION = this.stateDefinition;
        }
    }
}
