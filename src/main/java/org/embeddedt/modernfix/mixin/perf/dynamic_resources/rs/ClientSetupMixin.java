package org.embeddedt.modernfix.mixin.perf.dynamic_resources.rs;

import com.refinedmods.refinedstorage.render.BakedModelOverrideRegistry;
import com.refinedmods.refinedstorage.setup.ClientSetup;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.dynamicresources.DynamicModelBakeEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientSetup.class)
@RequiresMod("refinedstorage")
@ClientOnlyMixin
public class ClientSetupMixin {
    @Shadow @Final private BakedModelOverrideRegistry bakedModelOverrideRegistry;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addDynamicListener(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(this::onDynamicModelBake);
    }

    private void onDynamicModelBake(DynamicModelBakeEvent event) {
        BakedModelOverrideRegistry.BakedModelOverrideFactory factory = bakedModelOverrideRegistry.get(event.getLocation() instanceof ModelResourceLocation ? new ResourceLocation(event.getLocation().getNamespace(), event.getLocation().getPath()) : event.getLocation());
        if(factory != null)
            event.setModel(factory.create(event.getModel(), event.getModelLoader().getBakedTopLevelModels()));
    }
}
