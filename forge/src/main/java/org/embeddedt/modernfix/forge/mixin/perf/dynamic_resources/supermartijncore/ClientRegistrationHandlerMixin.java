package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources.supermartijncore;

import com.supermartijn642.core.registry.ClientRegistrationHandler;
import com.supermartijn642.core.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.forge.dynamicresources.DynamicModelBakeEvent;
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
    @Shadow @Final private List<Pair<Supplier<Stream<ResourceLocation>>, Function<BakedModel, BakedModel>>> modelOverwrites;

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
        MinecraftForge.EVENT_BUS.addListener(this::onDynamicModelBake);
    }

    @SubscribeEvent
    public void onDynamicModelBake(DynamicModelBakeEvent event) {
        Function<BakedModel, BakedModel> replacer = modelOverwritesByLocation.get(event.getLocation());
        if(replacer != null)
            event.setModel(replacer.apply(event.getModel()));
    }
}
