package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IRegistryDelegate;
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
    @Shadow(remap = false) @Final private static Map<IRegistryDelegate<Block>, Predicate<RenderType>> blockRenderChecks;

    private static Map<IRegistryDelegate<Block>, Predicate<RenderType>> renderCheckOverrides = new ConcurrentHashMap<>();

    private static Predicate<RenderType> DEFAULT_PREDICATE = type -> type == RenderType.solid();

    @Shadow(remap = false) protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow(remap = false) protected abstract Predicate<RenderType> getExistingRenderCheck(Block block);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(this);
    }

    /**
     * @author embeddedt
     * @reason handle layer changes dynamically
     */
    @Overwrite(remap = false)
    private void refreshLayerHacks() {
        renderCheckOverrides.clear();
        if(blockRenderChecks.isEmpty()) {
            for(Block block : ForgeRegistries.BLOCKS.getValues()) {
                Predicate<RenderType> original = this.getExistingRenderCheck(block);
                if(original == null)
                    original = DEFAULT_PREDICATE;
                blockRenderChecks.put(block.delegate, original);
                updateBlockPredicate(block);
            }
        }
    }

    private void updateBlockPredicate(Block block) {
        ItemBlockRenderTypes.setRenderLayer(block, type -> this.useOverrideIfPresent(block.delegate, type));
    }

    private boolean useOverrideIfPresent(IRegistryDelegate<Block> delegate, RenderType type) {
        Predicate<RenderType> override = renderCheckOverrides.get(delegate);
        if(override == null)
            override = blockRenderChecks.get(delegate);
        return override.test(type);
    }

    @Override
    public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState modelState, ModelBakery bakery) {
        if(!(location instanceof ModelResourceLocation))
            return originalModel;
        if(!(originalModel instanceof AbstractCTMBakedModel || originalModel instanceof WeightedBakedModel || originalModel instanceof MultiPartBakedModel))
            return originalModel;
        /* we construct a new ResourceLocation because an MRL is coming in */
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(location.getNamespace(), location.getPath()));
        if(block == null || block == Blocks.AIR || renderCheckOverrides.containsKey(block.delegate))
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
                renderCheckOverrides.put(block.delegate, newPredicate);
                updateBlockPredicate(block);
                return originalModel;
            }
        }
        return originalModel;
    }
}
