package org.embeddedt.modernfix.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class EntityRendererMap implements Map<EntityType<?>, EntityRenderer<?, ?>> {
    private final Map<EntityType<?>, EntityRendererProvider<?>> rendererProviders;
    private final LoadingCache<EntityType<?>, EntityRenderer<?, ?>> rendererMap;
    private final EntityRendererProvider.Context context;

    public EntityRendererMap(Map<EntityType<?>, EntityRendererProvider<?>> rendererProviders, EntityRendererProvider.Context context) {
        this.rendererProviders = rendererProviders;
        this.context = context;
        this.rendererMap = CacheBuilder.newBuilder().build(new RenderConstructor());
    }

    class RenderConstructor extends CacheLoader<EntityType<?>, EntityRenderer<?, ?>> {
        @Override
        public EntityRenderer<?, ?> load(EntityType<?> key) throws Exception {
            EntityRendererProvider<?> provider = rendererProviders.get(key);
            synchronized(EntityRenderers.class) {
                EntityRenderer<?, ?> renderer;
                try {
                    if(provider == null)
                        throw new RuntimeException("Provider not registered");
                    renderer = provider.create(context);
                    ModernFix.LOGGER.info("Loaded entity {}", BuiltInRegistries.ENTITY_TYPE.getKey(key));
                } catch(RuntimeException e) {
                    ModernFix.LOGGER.error("Failed to create entity model for " + BuiltInRegistries.ENTITY_TYPE.getKey(key) + ":", e);
                    renderer = new ErroredEntityRenderer<>(context);
                }
                return renderer;
            }
        }
    }

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
    public EntityRenderer<?, ?> get(Object o) {
        try {
            EntityRenderer<?, ?> renderer = rendererMap.get((EntityType<?>)o);
            if(renderer == null)
                throw new AssertionError("Returned entity renderer should never be null");
            return renderer;
        } catch (IllegalStateException e) {
            return null; /* emulate value not being present if recursive load occurs */
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public EntityRenderer<?, ?> put(EntityType<?> entityType, EntityRenderer<?, ?> entityRenderer) {
        EntityRenderer<?, ?> old = rendererMap.getIfPresent(entityType);
        rendererMap.put(entityType, entityRenderer);
        return old;
    }

    @Override
    public EntityRenderer<?, ?> remove(Object o) {
        EntityRenderer<?, ?> r = rendererMap.getIfPresent(o);
        rendererMap.invalidate(o);
        return r;
    }

    @Override
    public void putAll(@NotNull Map<? extends EntityType<?>, ? extends EntityRenderer<?, ?>> map) {
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
    public Collection<EntityRenderer<?, ?>> values() {
        return rendererMap.asMap().values();
    }

    @NotNull
    @Override
    public Set<Map.Entry<EntityType<?>, EntityRenderer<?, ?>>> entrySet() {
        return rendererMap.asMap().entrySet();
    }
}
