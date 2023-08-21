package org.embeddedt.modernfix.forge.mixin.bugfix.loading_screen_freeze;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.registries.GameData;
import org.embeddedt.modernfix.forge.util.AsyncLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GameData.class, remap = false)
public class GameDataMixin {
    @WrapOperation(method = "postRegisterEvents", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;postEventWrapContainerInModOrder(Lnet/minecraftforge/eventbus/api/Event;)V"))
    private static void swapThreadAndPost(ModLoader loader, Event event, Operation<Void> operation) {
        try(AsyncLoadingScreen ignored = new AsyncLoadingScreen()) {
            operation.call(loader, event);
        }
    }
}
