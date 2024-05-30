package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.resources.model.BlockStateModelLoader;
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
import java.util.function.BiConsumer;

@Mixin(BlockStateModelLoader.class)
@ClientOnlyMixin
public abstract class BlockStateModelLoaderMixin implements IBlockStateModelLoader {
    @Shadow protected abstract void loadBlockStateDefinitions(ResourceLocation resourceLocation, StateDefinition<Block, BlockState> stateDefinition);

    @Shadow @Mutable @Final private Object2IntMap<BlockState> modelGroups;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeModelGroupsSynchronized(Map map, ProfilerFiller profilerFiller, UnbakedModel unbakedModel, BlockColors blockColors, BiConsumer biConsumer, CallbackInfo ci) {
        this.modelGroups = Object2IntMaps.synchronize(this.modelGroups);
    }

    @Override
    public void loadSpecificBlock(ResourceLocation location) {
        var optionalBlock = BuiltInRegistries.BLOCK.getOptional(location);
        if(optionalBlock.isPresent()) {
            this.loadBlockStateDefinitions(location, optionalBlock.get().getStateDefinition());
        }
    }

    @Redirect(method = "loadAllBlockStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/DefaultedRegistry;iterator()Ljava/util/Iterator;"))
    private Iterator<?> skipIteratingBlocks(DefaultedRegistry instance) {
        return Collections.emptyIterator();
    }
}
