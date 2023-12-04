package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.api.helpers.ModelHelpers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;
import team.chisel.ctm.client.util.CTMPackReloadListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Mixin(CTMPackReloadListener.class)
@RequiresMod("ctm")
@ClientOnlyMixin
public abstract class CTMPackReloadListenerMixin implements ModernFixClientIntegration {
    /* caches the original render checks */
    @Shadow(remap = false) @Final private static Map<Holder.Reference<Block>, Predicate<RenderType>> blockRenderChecks;

    private static Map<Holder.Reference<Block>, Predicate<RenderType>> renderCheckOverrides = new ConcurrentHashMap<>();

    private static ChunkRenderTypeSet DEFAULT_TYPE_SET = ChunkRenderTypeSet.of(RenderType.solid());

    @Shadow(remap = false) protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow(remap = false) protected abstract ChunkRenderTypeSet getExistingRenderCheck(Block block);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(this);
    }

    /**
     * @author embeddedt
     * @reason handle layer changes dynamically
     */
    @Overwrite(remap = false)
    @SuppressWarnings("removal")
    private void refreshLayerHacks() {
        renderCheckOverrides.clear();
        if(blockRenderChecks.isEmpty()) {
            for(Block block : BuiltInRegistries.BLOCK) {
                Holder.Reference<Block> holder = block.builtInRegistryHolder();
                ChunkRenderTypeSet original = this.getExistingRenderCheck(block);
                if(original == null)
                    original = DEFAULT_TYPE_SET;
                blockRenderChecks.put(holder, original::contains);
                ItemBlockRenderTypes.setRenderLayer(block, type -> this.useOverrideIfPresent(holder, type));
            }
        }
    }

    private boolean useOverrideIfPresent(Holder.Reference<Block> delegate, RenderType type) {
        Predicate<RenderType> override = renderCheckOverrides.get(delegate);
        if(override == null)
            override = blockRenderChecks.get(delegate);
        return override.test(type);
    }

    @Override
    public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState modelState, ModelBakery bakery) {
        if(!(location instanceof ModelResourceLocation))
            return originalModel;
        if(true) throw new UnsupportedOperationException("not ported yet");
        /*
        if(!(originalModel instanceof AbstractCTMBakedModel || originalModel instanceof WeightedBakedModel || originalModel instanceof MultiPartBakedModel))
            return originalModel;
        */
        /* we construct a new ResourceLocation because an MRL is coming in */
        Block block = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(location.getNamespace(), location.getPath())).orElse(null);
        Holder.Reference<Block> delegate = block != null ? block.builtInRegistryHolder() : null;
        if(block == null || block == Blocks.AIR || renderCheckOverrides.containsKey(delegate))
            return originalModel;
        /* find all states that match this MRL */
        ImmutableList<BlockState> allStates;
        try {
            allStates = ModelHelpers.getBlockStateForLocation(block.getStateDefinition(), (ModelResourceLocation)location);
        } catch(RuntimeException e) {
            ModernFix.LOGGER.error("Couldn't get state for MRL " + location, e);
            return originalModel;
        }
        for(BlockState state : allStates) {
            Predicate<RenderType> newPredicate = this.getLayerCheck(state, originalModel);
            if(newPredicate != null) {
                renderCheckOverrides.put(delegate, newPredicate);
                return originalModel;
            }
        }
        return originalModel;
    }
}
