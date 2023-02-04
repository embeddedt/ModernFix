package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.multipart.Selector;
import net.minecraft.state.StateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Mixin(Selector.class)
public class SelectorMixin {
    private ConcurrentHashMap<StateContainer<Block, BlockState>, Predicate<BlockState>> predicateCache = new ConcurrentHashMap<>();
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void useCachedPredicate(StateContainer<Block, BlockState> pState, CallbackInfoReturnable<Predicate<BlockState>> cir) {
        Predicate<BlockState> cached = this.predicateCache.get(pState);
        if(cached != null)
            cir.setReturnValue(cached);
    }

    @Inject(method = "getPredicate", at = @At("RETURN"))
    private void storeCachedPredicate(StateContainer<Block, BlockState> pState, CallbackInfoReturnable<Predicate<BlockState>> cir) {
        this.predicateCache.put(pState, cir.getReturnValue());
    }
}
