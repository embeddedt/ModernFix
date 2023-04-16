package org.embeddedt.modernfix.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.dynamicresources.DynamicModelBakeEvent;
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
public abstract class CTMPackReloadListenerMixin {
    @Shadow @Final private static Map<Holder.Reference<Block>, Predicate<RenderType>> blockRenderChecks;

    @Shadow protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow protected abstract ChunkRenderTypeSet getExistingRenderCheck(Block block);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, this::onModelBake);
    }

    @Overwrite(remap = false)
    private void refreshLayerHacks() {
        blockRenderChecks.forEach((b, p) -> ItemBlockRenderTypes.setRenderLayer((Block) b.get(), p));
        blockRenderChecks.clear();
    }

    private void onModelBake(DynamicModelBakeEvent event) {
        if(!(event.getModel() instanceof AbstractCTMBakedModel || event.getModel() instanceof WeightedBakedModel || event.getModel() instanceof MultiPartBakedModel))
            return;
        /* we construct a new ResourceLocation because an MRL is coming in */
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(event.getLocation().getNamespace(), event.getLocation().getPath()));
        Holder.Reference<Block> delegate = block != null ? ForgeRegistries.BLOCKS.getDelegateOrThrow(block) : null;
        if(block == null || block == Blocks.AIR || blockRenderChecks.containsKey(delegate))
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
                blockRenderChecks.put(delegate, this.getExistingRenderCheck(block)::contains);
                ItemBlockRenderTypes.setRenderLayer(block, newPredicate);
                return;
            }
        }
    }
}
