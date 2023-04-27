package org.embeddedt.modernfix.mixin.perf.dynamic_entity_renderers;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.entity.ErroredEntityRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Mixin(EntityRenderers.class)
public class EntityRenderersMixin {
    @Shadow @Final private static Map<EntityType<?>, EntityRendererProvider<?>> PROVIDERS;

    @Inject(method = "createEntityRenderers", at = @At("HEAD"), cancellable = true)
    private static void createDynamicRendererLoader(EntityRendererProvider.Context context, CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?>>> cir) {
        Map<EntityType<?>, EntityRendererProvider<?>> rendererProviders = PROVIDERS;
        LoadingCache<EntityType<?>, EntityRenderer<?>> rendererMap = CacheBuilder.newBuilder()
                .build(new CacheLoader<>() {
                    @Override
                    public EntityRenderer<?> load(EntityType<?> key) throws Exception {
                        EntityRendererProvider<?> provider = rendererProviders.get(key);
                        synchronized(EntityRenderers.class) {
                            EntityRenderer<?> renderer;
                            try {
                                if(provider == null)
                                    throw new RuntimeException("Provider not registered");
                                renderer = provider.create(context);
                                ModernFix.LOGGER.debug("Loaded entity {}", BuiltInRegistries.ENTITY_TYPE.getKey(key));
                            } catch(RuntimeException e) {
                                ModernFix.LOGGER.error("Failed to create entity model for " + BuiltInRegistries.ENTITY_TYPE.getKey(key) + ":", e);
                                renderer = new ErroredEntityRenderer<>(context);
                            }
                            return renderer;
                        }
                    }
                });
        cir.setReturnValue(new Map<EntityType<?>, EntityRenderer<?>>() {
            @Override
            public int size() {
                return rendererProviders.size();
            }

            @Override
            public boolean isEmpty() {
                return rendererProviders.isEmpty();
            }

            @Override
            public boolean containsKey(Object o) {
                return rendererProviders.containsKey(o);
            }

            @Override
            public boolean containsValue(Object o) {
                return false;
            }

            @Override
            public EntityRenderer<?> get(Object o) {
                try {
                    return rendererMap.get((EntityType<?>)o);
                } catch(ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

            @Nullable
            @Override
            public EntityRenderer<?> put(EntityType<?> entityType, EntityRenderer<?> entityRenderer) {
                EntityRenderer<?> old = rendererMap.getIfPresent(entityType);
                rendererMap.put(entityType, entityRenderer);
                return old;
            }

            @Override
            public EntityRenderer<?> remove(Object o) {
                EntityRenderer<?> r = rendererMap.getIfPresent(o);
                rendererMap.invalidate(o);
                return r;
            }

            @Override
            public void putAll(@NotNull Map<? extends EntityType<?>, ? extends EntityRenderer<?>> map) {
                rendererMap.putAll(map);
            }

            @Override
            public void clear() {
                rendererMap.invalidateAll();
            }

            @NotNull
            @Override
            public Set<EntityType<?>> keySet() {
                return rendererProviders.keySet();
            }

            @NotNull
            @Override
            public Collection<EntityRenderer<?>> values() {
                return rendererMap.asMap().values();
            }

            @NotNull
            @Override
            public Set<Entry<EntityType<?>, EntityRenderer<?>>> entrySet() {
                return rendererMap.asMap().entrySet();
            }
        });
    }
}
