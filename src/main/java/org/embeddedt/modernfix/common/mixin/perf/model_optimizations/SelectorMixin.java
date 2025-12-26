package org.embeddedt.modernfix.common.mixin.perf.model_optimizations;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Mixin(Selector.class)
@ClientOnlyMixin
public class SelectorMixin {
    private ConcurrentHashMap<StateDefinition<Block, BlockState>, Predicate<BlockState>> predicateCache = new ConcurrentHashMap<>();
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true)
    private void useCachedPredicate(StateDefinition<Block, BlockState> pState, CallbackInfoReturnable<Predicate<BlockState>> cir) {
        Predicate<BlockState> cached = this.predicateCache.get(pState);
        if(cached != null)
            cir.setReturnValue(cached);
    }

    @Inject(method = "getPredicate", at = @At("RETURN"))
    private void storeCachedPredicate(StateDefinition<Block, BlockState> pState, CallbackInfoReturnable<Predicate<BlockState>> cir) {
        this.predicateCache.put(pState, cir.getReturnValue());
    }
}
