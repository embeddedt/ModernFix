package org.embeddedt.modernfix.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("OptionalAssignedToNull")
public class EntityRendererMap implements Map<EntityType<?>, EntityRenderer<?>> {
    private final Map<EntityType<?>, EntityRendererProvider<?>> rendererProviders;
    private final LoadingCache<EntityType<?>, Optional<EntityRenderer<?>>> rendererMap;
    private final EntityRendererProvider.Context context;

    public EntityRendererMap(Map<EntityType<?>, EntityRendererProvider<?>> rendererProviders, EntityRendererProvider.Context context) {
        this.rendererProviders = rendererProviders;
        this.context = context;
        this.rendererMap = CacheBuilder.newBuilder().build(new RenderConstructor());
    }

    class RenderConstructor extends CacheLoader<EntityType<?>, Optional<EntityRenderer<?>>> {
        @Override
        public Optional<EntityRenderer<?>> load(EntityType<?> key) throws Exception {
            EntityRendererProvider<?> provider = rendererProviders.get(key);
            if(provider == null)
                return Optional.empty();
            synchronized(EntityRenderers.class) {
                EntityRenderer<?> renderer;
                try {
                    renderer = provider.create(context);
                    ModernFix.LOGGER.info("Loaded entity {}", BuiltInRegistries.ENTITY_TYPE.getKey(key));
                } catch(RuntimeException e) {
                    ModernFix.LOGGER.error("Failed to create entity model for " + BuiltInRegistries.ENTITY_TYPE.getKey(key) + ":", e);
                    renderer = new ErroredEntityRenderer<>(context);
                }
                return Optional.ofNullable(renderer);
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
    public EntityRenderer<?> get(Object o) {
        try {
            Optional<EntityRenderer<?>> renderer = rendererMap.get((EntityType<?>)o);
            return renderer.orElse(null);
        } catch (IllegalStateException e) {
            return null; /* emulate value not being present if recursive load occurs */
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public EntityRenderer<?> put(EntityType<?> entityType, EntityRenderer<?> entityRenderer) {
        Optional<EntityRenderer<?>> old = rendererMap.getIfPresent(entityType);
        rendererMap.put(entityType, Optional.ofNullable(entityRenderer));
        return old != null ? old.orElse(null) : null;
    }

    @Override
    public EntityRenderer<?> remove(Object o) {
        Optional<EntityRenderer<?>> old = rendererMap.getIfPresent(o);
        rendererMap.invalidate(o);
        return old != null ? old.orElse(null) : null;
    }

    @Override
    public void putAll(@NotNull Map<? extends EntityType<?>, ? extends EntityRenderer<?>> map) {
        rendererMap.putAll(Maps.transformValues(map, Optional::ofNullable));
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
        return new AbstractCollection<>() {
            @Override
            public Iterator<EntityRenderer<?>> iterator() {
                return Iterators.transform(Iterators.unmodifiableIterator(rendererProviders.keySet().iterator()), EntityRendererMap.this::get);
            }

            @Override
            public int size() {
                return rendererProviders.size();
            }
        };
    }

    private class Entry implements Map.Entry<EntityType<?>, EntityRenderer<?>> {
        private final EntityType<?> key;

        private Entry(EntityType<?> key) {
            this.key = key;
        }

        @Override
        public EntityType<?> getKey() {
            return key;
        }

        @Override
        public EntityRenderer<?> getValue() {
            return get(key);
        }

        @Override
        public EntityRenderer<?> setValue(EntityRenderer<?> value) {
            return put(key, value);
        }
    }

    @NotNull
    @Override
    public Set<Map.Entry<EntityType<?>, EntityRenderer<?>>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map.Entry<EntityType<?>, EntityRenderer<?>>> iterator() {
                return Iterators.transform(Iterators.unmodifiableIterator(rendererProviders.keySet().iterator()), Entry::new);
            }

            @Override
            public boolean contains(Object o) {
                if (o instanceof Map.Entry<?,?> e) {
                    return rendererProviders.containsKey(e.getKey()) && Objects.equals(get(e.getKey()), e.getValue());
                } else {
                    return false;
                }
            }

            @Override
            public int size() {
                return rendererProviders.size();
            }
        };
    }
}
