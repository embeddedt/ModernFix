package org.embeddedt.modernfix.forge.mixin.feature.registry_event_progress;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegisterEvent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.forge.util.AsyncLoadingScreen;
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

    @Redirect(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/minecraftforge/eventbus/api/Event;)V"))
    private static <T extends Event & IModBusEvent> void swapThreadAndPost(ModLoader loader, T event) {
        RegisterEvent registryEvent = (RegisterEvent)event;
        var pb = StartupMessageManager.addProgressBar(registryEvent.getRegistryKey().location().toString(), ModList.get().size());
        try {
            loader.postEventWithWrapInModOrder(event, (mc, e) -> {
                ModLoadingContext.get().setActiveContainer(mc);
                pb.label(pb.name() + " - " + mc.getModInfo().getDisplayName());
                pb.increment();
            }, (mc, e) -> {
                ModLoadingContext.get().setActiveContainer(null);
            });
        } finally {
            pb.complete();
        }
    }
}
