package org.embeddedt.modernfix.neoforge.mixin.perf.capability_list_compaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.CapabilityHooks;
import org.embeddedt.modernfix.neoforge.caps.CapProviderGetter;
import org.embeddedt.modernfix.neoforge.caps.ITrackingCapEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CapabilityHooks.class, remap = false)
public class CapabilityHooksMixin {
    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/neoforged/bus/api/Event;)V"))
    private static void deduplicateCaps(Event event, Operation<Void> original) {
        original.call(event);
        if(event instanceof ITrackingCapEvent) {
            //var stopwatch = Stopwatch.createStarted();
            for(BaseCapability<?, ?> cap : ((ITrackingCapEvent)event).mfix$getTrackedCaps()) {
                CapProviderGetter.deduplicateCap(cap);
            }
            //stopwatch.stop();
            //ModernFix.LOGGER.info("Deduplicated capability lists in {}", stopwatch);
        }
    }
}
