package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
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
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IBlockStateModelLoader;
import org.embeddedt.modernfix.dynamicresources.ModelBakeryHelpers;
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
import java.util.function.BiConsumer;

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
            try {
                // Only filter states if we are in a world and not in the loading overlay
                filteredStates = (Minecraft.getInstance().getOverlay() == null && Minecraft.getInstance().level != null) ? ModelBakeryHelpers.getBlockStatesForMRL(optionalBlock.get().getStateDefinition(), location) : null;
            } catch(RuntimeException e) {
                ModernFix.LOGGER.error("Exception filtering states on {}", location, e);
                filteredStates = null;
            }
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
}
