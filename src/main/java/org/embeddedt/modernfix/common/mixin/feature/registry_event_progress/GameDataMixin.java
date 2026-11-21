package org.embeddedt.modernfix.common.mixin.feature.registry_event_progress;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.StartupMessageManager;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegisterEvent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Mixin(value = GameData.class, remap = false)
@ClientOnlyMixin
public class GameDataMixin {
    /**
     * @author embeddedt
     * @reason post registry events on a worker thread so the render thread stays free to draw the loading screen
     * and pump window events, matching NeoForge's behavior on newer versions
     */
    @WrapMethod(method = "postRegisterEvents")
    private static void mfix$postOffThread(Operation<Void> original) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                original.call();
                result.complete(null);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        }, "ModernFix registry worker");
        worker.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        worker.setDaemon(true);
        worker.start();
        boolean interrupted = false;
        try {
            while (true) {
                ImmediateWindowHandler.renderTick();
                try {
                    result.get(50, TimeUnit.MILLISECONDS);
                    return;
                } catch (TimeoutException ignored) {
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    throw mfix$sneakyThrow(e.getCause());
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException mfix$sneakyThrow(Throwable t) throws T {
        throw (T)t;
    }

    @Redirect(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/minecraftforge/eventbus/api/Event;)V"))
    private static <T extends Event & IModBusEvent> void postWithProgress(ModLoader loader, T event) {
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
