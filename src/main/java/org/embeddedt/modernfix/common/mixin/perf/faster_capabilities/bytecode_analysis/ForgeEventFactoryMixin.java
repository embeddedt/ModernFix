package org.embeddedt.modernfix.common.mixin.perf.faster_capabilities.bytecode_analysis;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.embeddedt.modernfix.duck.IBatchingCapEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ForgeEventFactory.class, remap = false)
public class ForgeEventFactoryMixin {
    @WrapOperation(method = "gatherCapabilities(Lnet/minecraftforge/event/AttachCapabilitiesEvent;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lnet/minecraftforge/common/capabilities/CapabilityDispatcher;", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean modernfix$sortAfterPost(IEventBus instance, Event event, Operation<Boolean> original) {
        boolean result = original.call(instance, event);
        ((IBatchingCapEvent) event).mfix$sortCaps();
        return result;
    }
}
