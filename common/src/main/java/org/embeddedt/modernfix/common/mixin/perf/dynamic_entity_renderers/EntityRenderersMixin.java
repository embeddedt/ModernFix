package org.embeddedt.modernfix.common.mixin.perf.dynamic_entity_renderers;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.entity.EntityRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(EntityRenderers.class)
@ClientOnlyMixin
public class EntityRenderersMixin {
    @Shadow @Final private static Map<EntityType<?>, EntityRendererProvider<?>> PROVIDERS;

    @Inject(method = "createEntityRenderers", at = @At("HEAD"), cancellable = true)
    private static void createDynamicRendererLoader(EntityRendererProvider.Context context, CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?, ?>>> cir) {
        cir.setReturnValue(new EntityRendererMap(PROVIDERS, context));
        ModernFix.LOGGER.info("Dynamic entity renderer hook setup");
    }
}
