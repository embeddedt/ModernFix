package org.embeddedt.modernfix.mixin.perf.parallel_potentially_unsafe.parallel_deferred_suppliers;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.registries.GameData;
import org.embeddedt.modernfix.registry.DeferredRegisterBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(GameData.class)
public class GameDataMixin {
    @Inject(method = "generateRegistryEvents", at = @At(value = "INVOKE", target = "Ljava/util/List;stream()Ljava/util/stream/Stream;"), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private static void bakeDeferredBeforeSendingEvents(CallbackInfoReturnable<Stream<ModLoadingStage.EventGenerator<?>>> cir, List<ResourceLocation> keys) {
        /* TODO also bake items, maybe? */
        DeferredRegisterBaker.bakeSuppliers(new ResourceLocation("minecraft", "block"));
    }
}
