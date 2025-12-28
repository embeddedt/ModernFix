package org.embeddedt.modernfix.dynresources;

import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.duck.IModelHoldingBlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Optimized blockstate->model dispatch map that stores the models directly on the block state objects using a helper
 * field. This relies on the fact that Minecraft should only have one model map in flight at a time.
 */
public record BlockStateModelMap(Map<BlockState, BlockStateModel> modelMap,
                                 BlockStateModel fallbackModel) implements Map<BlockState, BlockStateModel> {

    @Override
    public int size() {
        return Block.BLOCK_STATE_REGISTRY.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object o) {
        return o instanceof BlockState;
    }

    @Override
    public boolean containsValue(Object o) {
        return modelMap.containsValue(o);
    }

    @Override
    public BlockStateModel get(Object o) {
        if (o instanceof IModelHoldingBlockState modelHolder) {
            BlockStateModel model = modelHolder.mfix$getModel();

            if(model != null) {
                return model;
            }

            model = modelMap.getOrDefault(o, fallbackModel);
            modelHolder.mfix$setModel(model);
            return model;
        } else {
            return modelMap.getOrDefault(o, fallbackModel);
        }
    }

    @Override
    public @Nullable BlockStateModel put(BlockState blockState, BlockStateModel blockStateModel) {
        var oldModel = modelMap.put(blockState, blockStateModel);
        ((IModelHoldingBlockState)blockState).mfix$setModel(null);
        return oldModel;
    }

    @Override
    public BlockStateModel remove(Object o) {
        var old = modelMap.remove(o);
        if (o instanceof IModelHoldingBlockState holder) {
            holder.mfix$setModel(null);
        }
        return old;
    }

    @Override
    public void putAll(@NotNull Map<? extends BlockState, ? extends BlockStateModel> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        modelMap.clear();
        resetCache();
    }

    @Override
    public @NotNull Set<BlockState> keySet() {
        return modelMap.keySet();
    }

    @Override
    public @NotNull Collection<BlockStateModel> values() {
        return modelMap.values();
    }

    @Override
    public @NotNull Set<Entry<BlockState, BlockStateModel>> entrySet() {
        return modelMap.entrySet();
    }

    public static void resetCache() {
        for (var state : Block.BLOCK_STATE_REGISTRY) {
            ((IModelHoldingBlockState) state).mfix$setModel(null);
        }
    }
}
