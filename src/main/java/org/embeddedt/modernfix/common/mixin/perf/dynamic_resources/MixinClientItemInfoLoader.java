package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Mixin(ClientItemInfoLoader.class)
@ClientOnlyMixin
public abstract class MixinClientItemInfoLoader {
    @Shadow
    private static ClientItemInfoLoader.PendingLoad lambda$scheduleLoad$3(Identifier resourceId, Resource resource, RegistryAccess.Frozen registries) {
        throw new AssertionError();
    }

    /**
     * @author embeddedt
     * @reason Load client item infos dynamically instead of all at once
     */
    @ModifyArg(method = "scheduleLoad", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"))
    private static Function<Map<Identifier, Resource>, ? extends CompletionStage<ClientItemInfoLoader.LoadedClientInfos>> skipAOTClientItemLoad(
            Function<Map<Identifier, Resource>, ? extends CompletionStage<ClientItemInfoLoader.LoadedClientInfos>> original,
            @Local(ordinal = 0) RegistryAccess.Frozen staticRegistries) {
        return resourceMap -> CompletableFuture.completedFuture(DynamicModelSystem.createDynamicClientInfos(resourceMap, (resourceId, resource) -> {
            ClientItemInfoLoader.PendingLoad load = lambda$scheduleLoad$3(resourceId, resource, staticRegistries);
            return load.clientItemInfo();
        }));
    }
}
