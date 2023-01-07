package org.embeddedt.modernfix.mixin.perf.parallel_potentially_unsafe.parallel_deferred_suppliers;

import net.minecraft.block.DispenserBlock;
import net.minecraft.dispenser.IDispenseItemBehavior;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {
    @Shadow private static Map<Item, IDispenseItemBehavior> DISPENSER_REGISTRY;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void makeMapConcurrent(CallbackInfo ci) {
        DISPENSER_REGISTRY = new ConcurrentHashMap<>();
    }
}
