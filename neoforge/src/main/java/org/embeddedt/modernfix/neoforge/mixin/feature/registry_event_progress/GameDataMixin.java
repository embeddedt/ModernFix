package org.embeddedt.modernfix.neoforge.mixin.feature.registry_event_progress;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforge.registries.GameData;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GameData.class, remap = false)
@ClientOnlyMixin
public class GameDataMixin {

    @Redirect(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/neoforged/bus/api/Event;)V"))
    private static <T extends Event & IModBusEvent> void postWithProgressBar(T event) {
        if(ModLoader.hasErrors()) {
            return;
        }
        RegisterEvent registryEvent = (RegisterEvent)event;
        // We control phases ourselves so we can make a separate progress bar for each phase.
        String registryName = registryEvent.getRegistryKey().location().toString();
        for(EventPriority phase : EventPriority.values()) {
            // FIXME need to use prepend rather than append for it to be visible for now
            var pb = StartupNotificationManager.prependProgressBar(registryName, ModList.get().size());
            try {
                ModList.get().forEachModInOrder(mc -> {
                    ModLoadingContext.get().setActiveContainer(mc);
                    pb.label(pb.name() + " - " + mc.getModInfo().getDisplayName());
                    pb.increment();
                    mc.acceptEvent(phase, event);
                    ModLoadingContext.get().setActiveContainer(null);
                });
            } finally {
                pb.complete();
            }
        }

    }
}
