package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.forge.dynamicresources.DynamicModelBakeEvent;
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
public abstract class CTMPackReloadListenerMixin {
    /* caches the original render checks */
    @Shadow @Final private static Map<Holder.Reference<Block>, Predicate<RenderType>> blockRenderChecks;

    private static Map<Holder.Reference<Block>, Predicate<RenderType>> renderCheckOverrides = new ConcurrentHashMap<>();

    private static ChunkRenderTypeSet DEFAULT_TYPE_SET = ChunkRenderTypeSet.of(RenderType.solid());

    @Shadow protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow protected abstract ChunkRenderTypeSet getExistingRenderCheck(Block block);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, this::onModelBake);
    }

    @Overwrite(remap = false)
    private void refreshLayerHacks() {
        renderCheckOverrides.clear();
        if(blockRenderChecks.isEmpty()) {
            for(Block block : ForgeRegistries.BLOCKS.getValues()) {
                Holder.Reference<Block> holder = ForgeRegistries.BLOCKS.getDelegateOrThrow(block);
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

    private void onModelBake(DynamicModelBakeEvent event) {
        if(!(event.getModel() instanceof AbstractCTMBakedModel || event.getModel() instanceof WeightedBakedModel || event.getModel() instanceof MultiPartBakedModel))
            return;
        /* we construct a new ResourceLocation because an MRL is coming in */
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(event.getLocation().getNamespace(), event.getLocation().getPath()));
        Holder.Reference<Block> delegate = block != null ? ForgeRegistries.BLOCKS.getDelegateOrThrow(block) : null;
        if(block == null || block == Blocks.AIR || renderCheckOverrides.containsKey(delegate))
            return;
        /* find all states that match this MRL */
        ImmutableList<BlockState> allStates;
        try {
            allStates = ((IExtendedModelBakery)(Object)event.getModelLoader()).getBlockStatesForMRL(block.getStateDefinition(), (ModelResourceLocation)event.getLocation());
        } catch(RuntimeException e) {
            ModernFix.LOGGER.error("Couldn't get state for MRL " + event.getLocation(), e);
            return;
        }
        for(BlockState state : allStates) {
            Predicate<RenderType> newPredicate = this.getLayerCheck(state, event.getModel());
            if(newPredicate != null) {
                renderCheckOverrides.put(delegate, newPredicate);
                return;
            }
        }
    }
}
