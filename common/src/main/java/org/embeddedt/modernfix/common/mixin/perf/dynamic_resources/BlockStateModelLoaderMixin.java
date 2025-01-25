package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ReferenceObjectImmutablePair;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IBlockStateModelLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@Mixin(BlockStateModelLoader.class)
@ClientOnlyMixin
public abstract class BlockStateModelLoaderMixin implements IBlockStateModelLoader {
    @Shadow protected abstract void loadBlockStateDefinitions(ResourceLocation resourceLocation, StateDefinition<Block, BlockState> stateDefinition);

    @Shadow @Mutable @Final private Object2IntMap<BlockState> modelGroups;

    private ImmutableList<BlockState> filteredStates;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeModelGroupsSynchronized(Map map, ProfilerFiller profilerFiller, UnbakedModel unbakedModel, BlockColors blockColors, BiConsumer biConsumer, CallbackInfo ci) {
        this.modelGroups = Object2IntMaps.synchronize(this.modelGroups);
    }

    @Override
    public void loadSpecificBlock(ModelResourceLocation location) {
        var optionalBlock = BuiltInRegistries.BLOCK.getOptional(location.id());
        if(optionalBlock.isPresent()) {
            // embeddedt note - filtering is currently disabled as it's quite inefficient to do vs. just loading
            // the extra models and letting LRU deal with it
            /*
            try {
                // Only filter states if we are in a world and not in the loading overlay
                filteredStates = (Minecraft.getInstance().getOverlay() == null && Minecraft.getInstance().level != null) ? ModelBakeryHelpers.getBlockStatesForMRL(optionalBlock.get().getStateDefinition(), location) : null;
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Exception filtering states on {}", location, e);
                filteredStates = null;
            }
             */
            try {
                this.loadBlockStateDefinitions(location.id(), optionalBlock.get().getStateDefinition());
            } finally {
                filteredStates = null;
            }
        }
    }

    @Redirect(method = "loadAllBlockStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/DefaultedRegistry;iterator()Ljava/util/Iterator;"))
    private Iterator<?> skipIteratingBlocks(DefaultedRegistry instance) {
        return Collections.emptyIterator();
    }

    @Redirect(method = "loadBlockStateDefinitions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> getFilteredStates(StateDefinition<Block, BlockState> instance) {
        return this.filteredStates != null ? this.filteredStates : instance.getPossibleStates();
    }

    // Add some caching around key hot paths

    private final Cache<ReferenceObjectImmutablePair<BlockStateModelLoader.LoadedJson, ResourceLocation>, BlockModelDefinition> cachedBlockModelDefs = CacheBuilder.newBuilder()
            .maximumSize(100)
            .build();

    private static final Cache<Pair<StateDefinition<Block, BlockState>, String>, Predicate<BlockState>> cachedBlockStatePredicates = CacheBuilder.newBuilder()
            .maximumSize(100)
            .build();

    @WrapOperation(method = "loadBlockStateDefinitions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedJson;parse(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/block/model/BlockModelDefinition$Context;)Lnet/minecraft/client/renderer/block/model/BlockModelDefinition;"))
    private BlockModelDefinition avoidMultipleParses(BlockStateModelLoader.LoadedJson instance, ResourceLocation blockStateId, BlockModelDefinition.Context context, Operation<BlockModelDefinition> original) {
        try {
            return cachedBlockModelDefs.get(ReferenceObjectImmutablePair.of(instance, blockStateId), () -> original.call(instance, blockStateId, context));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @WrapMethod(method = "predicate")
    private static Predicate<BlockState> memoizePredicate(StateDefinition<Block, BlockState> stateDefentition, String properties, Operation<Predicate<BlockState>> original) {
        try {
            return cachedBlockStatePredicates.get(Pair.of(stateDefentition, properties), () -> original.call(stateDefentition, properties));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
