package org.embeddedt.modernfix.forge.mixin.safety;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
@ClientOnlyMixin
public class EntityRenderDispatcherMixin {

    @Shadow @Final @Mutable private Map<EntityType<?>, EntityRenderer<?>> renderers;
    private static Map<EntityType<? extends Entity>, IRenderFactory<? extends Entity>> RENDER_FACTORIES;

    static {
        RenderingRegistry instance = ObfuscationReflectionHelper.getPrivateValue(RenderingRegistry.class, null, "INSTANCE");
        RENDER_FACTORIES = ObfuscationReflectionHelper.getPrivateValue(RenderingRegistry.class, instance, "entityRenderers");
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeRenderersConcurrent(CallbackInfo ci) {
        renderers = new ConcurrentHashMap<>(renderers);
    }

    @Inject(method = "getRenderer", at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void checkRenderer(T entity, CallbackInfoReturnable<EntityRenderer<?>> cir) {
        if(cir.getReturnValue() == null) {
            synchronized (EntityRenderDispatcher.class) {
                EntityType<?> type = entity.getType();
                if(type == null) {
                    throw new IllegalStateException("Entity with class " + entity.getClass().getName() + " UUID " + entity.getName() + " has no type???");
                }
                ResourceLocation key = Registry.ENTITY_TYPE.getKey(type);
                EntityRenderer<?> renderer = null;
                if(RENDER_FACTORIES != null) {
                    IRenderFactory<? extends Entity> factory = RENDER_FACTORIES.get(type);
                    if(factory != null) {
                        try {
                            renderer = factory.createRenderFor((EntityRenderDispatcher)(Object)this);
                        } catch(RuntimeException e) {
                            ModernFix.LOGGER.error("Failed to create fallback renderer", e);
                        }
                        if(renderer != null) {
                            this.renderers.put(type, renderer);
                            ModernFix.LOGGER.warn("Entity renderer for {} was somehow not registered, injecting.", key);
                            cir.setReturnValue(renderer);
                        }
                    }
                }
                if(cir.getReturnValue() == null) {
                    ModernFix.LOGGER.error("Backing renderer map is a " + renderers.getClass().getName());
                    throw new IllegalStateException("No renderer for entity with type " + key);
                }
            }
        }
    }
}
