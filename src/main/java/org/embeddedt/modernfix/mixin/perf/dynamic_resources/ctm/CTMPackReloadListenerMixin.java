package org.embeddedt.modernfix.mixin.perf.dynamic_resources.ctm;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IRegistryDelegate;
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
    @Shadow @Final private static Map<IRegistryDelegate<Block>, Predicate<RenderType>> blockRenderChecks;

    @Shadow protected abstract Predicate<RenderType> getLayerCheck(BlockState state, BakedModel model);

    @Shadow protected abstract Predicate<RenderType> getExistingRenderCheck(Block block);

    private Map<ModelResourceLocation, BlockState> locationToState = new Object2ObjectOpenHashMap<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, this::onModelBake);
    }

    @Overwrite(remap = false)
    private void refreshLayerHacks() {
        blockRenderChecks.forEach((b, p) -> ItemBlockRenderTypes.setRenderLayer((Block) b.get(), p));
        blockRenderChecks.clear();
        if(locationToState.isEmpty()) {
            for(Block block : ForgeRegistries.BLOCKS.getValues()) {
                for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                    locationToState.put(BlockModelShaper.stateToModelLocation(state), state);
                }
            }
        }
    }

    private void onModelBake(DynamicModelBakeEvent event) {
        if(!(event.getModel() instanceof AbstractCTMBakedModel || event.getModel() instanceof WeightedBakedModel || event.getModel() instanceof MultiPartBakedModel))
            return;
        BlockState state = locationToState.get(event.getLocation());
        if(state == null)
            return;
        Block block = state.getBlock();
        if(blockRenderChecks.containsKey(block.delegate))
            return;
        Predicate<RenderType> newPredicate = this.getLayerCheck(state, event.getModel());
        if(newPredicate != null) {
            blockRenderChecks.put(block.delegate, this.getExistingRenderCheck(block));
            ItemBlockRenderTypes.setRenderLayer(block, newPredicate);
        }
    }
}
