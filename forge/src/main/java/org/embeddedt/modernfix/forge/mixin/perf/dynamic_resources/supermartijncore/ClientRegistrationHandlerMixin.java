package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.supermartijncore;

import com.supermartijn642.core.registry.ClientRegistrationHandler;
import com.supermartijn642.core.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Mixin(ClientRegistrationHandler.class)
@RequiresMod("supermartijn642corelib")
@ClientOnlyMixin
public class ClientRegistrationHandlerMixin {
    @Shadow(remap = false) @Final private List<Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>>> modelOverwrites;

    private Map<ResourceLocation, Function<BakedModel, BakedModel>> modelOverwritesByLocation = new Object2ObjectOpenHashMap<>();

    @Redirect(method = "handleModelBakeEvent", at = @At(value = "FIELD", target = "Lcom/supermartijn642/core/registry/ClientRegistrationHandler;modelOverwrites:Ljava/util/List;"), remap = false)
    private List<?> skipModelOverwrites(ClientRegistrationHandler h) {
        modelOverwritesByLocation.clear();
        for(Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>> pair : this.modelOverwrites) {
            Stream<ResourceLocation> locationStream = pair.left().get();
            Function<BakedModel, BakedModel> swapper = pair.right();
            locationStream.forEach(l -> {
                modelOverwritesByLocation.put(l, swapper);
            });
        }
        return Collections.emptyList();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void registerDynBake(String modid, CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(new ModernFixClientIntegration() {
            @Override
            public BakedModel onBakedModelLoad(ResourceLocation location, UnbakedModel baseModel, BakedModel originalModel, ModelState state, ModelBakery bakery) {
                Function<BakedModel, BakedModel> replacer = modelOverwritesByLocation.get(location);
                if(replacer != null)
                    return replacer.apply(originalModel);
                else
                    return originalModel;
            }
        });
    }
}
