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
import org.embeddedt.modernfix.neoforge.util.AsyncLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameData.class, remap = false)
@ClientOnlyMixin
public class GameDataMixin {

    private static AsyncLoadingScreen mfix$asyncScreen;

    @Inject(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;", ordinal = 0))
    private static void createAsyncScreen(CallbackInfo ci) {
        mfix$asyncScreen = new AsyncLoadingScreen();
    }

    @Inject(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Ljava/lang/RuntimeException;getSuppressed()[Ljava/lang/Throwable;", ordinal = 0))
    private static void closeAsyncScreen(CallbackInfo ci) {
        mfix$asyncScreen.close();
        mfix$asyncScreen = null;
    }

    @Redirect(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/neoforged/bus/api/Event;)V"))
    private static <T extends Event & IModBusEvent> void swapThreadAndPost(T event) {
        RegisterEvent registryEvent = (RegisterEvent)event;
        // We control phases ourselves so we can make a separate progress bar for each phase.
        String registryName = registryEvent.getRegistryKey().location().toString();
        for(EventPriority phase : EventPriority.values()) {
            var pb = StartupNotificationManager.addProgressBar(registryName, ModList.get().size());
            try {
                ModList.get().forEachModInOrder(mc -> {
                    ModLoadingContext.get().setActiveContainer(mc);
                    pb.label(pb.name() + " - " + mc.getModInfo().getDisplayName());
                    pb.increment();
                    var bus = mc.getEventBus();
                    if(bus != null) {
                        bus.post(phase, event);
                    }
                    ModLoadingContext.get().setActiveContainer(null);
                });
            } finally {
                pb.complete();
            }
        }

    }
}
