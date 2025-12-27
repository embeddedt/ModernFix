package org.embeddedt.modernfix.common.mixin.perf.capability_list_compaction;

import com.llamalad7.mixinextras.sugar.Local;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.CapabilityHooks;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.embeddedt.modernfix.neoforge.caps.CapProviderGetter;
import org.embeddedt.modernfix.neoforge.caps.ITrackingCapEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CapabilityHooks.class, remap = false)
public class CapabilityHooksMixin {
    // Must inject as late as possible to run after mixins that add their own capabilities
    // (e.g. https://github.com/SuperMartijn642/Entangled/blob/37f2489d8badc3f52401088d8a6e25d2a63a045c/src/main/java/com/supermartijn642/entangled/mixin/neoforge/CapabilityHooksMixin.java)
    @Inject(method = "init", at = @At(value = "RETURN"))
    private static void deduplicateCaps(CallbackInfo ci, @Local(ordinal = 0) RegisterCapabilitiesEvent event) {
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
