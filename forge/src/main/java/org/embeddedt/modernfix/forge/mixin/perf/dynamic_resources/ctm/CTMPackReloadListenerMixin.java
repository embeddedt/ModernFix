package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
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
import org.embeddedt.modernfix.forge.dynresources.ModernFixCTMPredicate;
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
import java.util.function.Predicate;

@Mixin(CTMPackReloadListener.class)
@RequiresMod("ctm")
@ClientOnlyMixin
public abstract class CTMPackReloadListenerMixin implements ModernFixClientIntegration {
    /* caches the original render checks */
    @Shadow(remap = false) @Final private static Map<IRegistryDelegate<Block>, Predicate<RenderType>> blockRenderChecks;

    private static volatile Map<IRegistryDelegate<Block>, ModernFixCTMPredicate> mfixBouncerPredicates = null;

    private static Predicate<RenderType> DEFAULT_PREDICATE = type -> type == RenderType.solid();

    @Shadow protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow protected abstract Predicate<RenderType> getExistingRenderCheck(Block block);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(this);
    }

    private static void initMap() {
        if(mfixBouncerPredicates == null) {
            synchronized (CTMPackReloadListener.class) {
                if(mfixBouncerPredicates == null) {
                    Map<IRegistryDelegate<Block>, ModernFixCTMPredicate> map = new Reference2ReferenceOpenHashMap<>();
                    for(Block registeredBlock : ForgeRegistries.BLOCKS.getValues()) {
                        map.put(registeredBlock.delegate, new ModernFixCTMPredicate());
                    }
                    mfixBouncerPredicates = map;
                }
            }
        }
    }

    /**
     * @author embeddedt
     * @reason handle layer changes dynamically
     */
    @Overwrite(remap = false)
    private void refreshLayerHacks() {
        // Make sure predicate map exists
        initMap();
        mfixBouncerPredicates.values().forEach(bouncer -> bouncer.ctmOverride = null);
    }

    private static ModernFixCTMPredicate getPredicateForBlock(Block block) {
        initMap();
        ModernFixCTMPredicate predicate = mfixBouncerPredicates.get(block.delegate);
        if(predicate == null) {
            throw new NullPointerException("ModernFix CTM predicate missing for block: " + block.getRegistryName());
        }
        return predicate;
    }

    private void updateBlockPredicate(Block block, Predicate<RenderType> override) {
        Predicate<RenderType> original = this.getExistingRenderCheck(block);
        if(original == null) {
            original = DEFAULT_PREDICATE;
        }
        ModernFixCTMPredicate bouncer = getPredicateForBlock(block);
        if(original != bouncer) {
            // Give the bouncer the original predicate for correct behavior
            bouncer.defaultPredicate = original;
            synchronized (ItemBlockRenderTypes.class) {
                blockRenderChecks.put(block.delegate, original);
            }
        }
        bouncer.ctmOverride = override;
        ItemBlockRenderTypes.setRenderLayer(block, bouncer);
    }

    @Override
    public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState modelState, ModelBakery bakery) {
        if(!(location instanceof ModelResourceLocation))
            return originalModel;
        if(!(originalModel instanceof AbstractCTMBakedModel || originalModel instanceof WeightedBakedModel || originalModel instanceof MultiPartBakedModel))
            return originalModel;
        /* we construct a new ResourceLocation because an MRL is coming in */
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(location.getNamespace(), location.getPath()));
        if(block == null || block == Blocks.AIR || getPredicateForBlock(block).ctmOverride != null)
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
                updateBlockPredicate(block, newPredicate);
                return originalModel;
            }
        }
        return originalModel;
    }
}
