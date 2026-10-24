package org.embeddedt.modernfix.dynamiclanguages;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.ModernFix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DynamicLanguageMap extends ForwardingMap<String, String> {
    /** Null once the map has been materialized. */
    private volatile Map<String, Object> storage;
    private final LoadingCache<Resource, Map<String, String>> languageFileContents = CacheBuilder.newBuilder()
            .softValues()
            .build(new CacheLoader<>() {
                @Override
                public Map<String, String> load(Resource resource) {
                    return loadLanguageFile(resource);
                }
            });
    private volatile Map<String, String> delegate;

    private DynamicLanguageMap(Map<String, Object> storage) {
        this.storage = storage;
        this.delegate = Maps.asMap(storage.keySet(), this::lookup);
    }

    private static Map<String, Object> createStorage(Map<String, String> rawLanguageContents, List<Resource> languageResources) {
        Map<String, Object> storage = new HashMap<>(rawLanguageContents);
        for (var resource : languageResources) {
            try (var stream = resource.open()) {
                Language.loadFromJson(stream, (key, value) -> {
                    if (value != null && value.equals(storage.get(key))) {
                        storage.put(key, resource);
                    }
                });
            } catch (Exception ignored) {
            }
        }
        return Map.copyOf(storage);
    }

    public static Map<String, String> forVanillaData(Map<String, String> rawLanguageContents, List<Resource> languageResources) {
        return new DynamicLanguageMap(createStorage(rawLanguageContents, languageResources));
    }

    private static Map<String, String> loadLanguageFile(Resource resource) {
        Map<String, String> data = new Object2ObjectOpenHashMap<>();
        // A pack closed by a resource reload throws IllegalStateException, not IOException.
        try (var stream = resource.open()) {
            Language.loadFromJson(stream, data::put);
        } catch (Exception e) {
            ModernFix.LOGGER.error("Error loading language data from {}", resource.sourcePackId(), e);
        }
        return data;
    }

    private String lookup(String key) {
        Map<String, Object> storage = this.storage;
        if (storage == null) {
            return delegate.get(key);
        }
        Object value = storage.get(key);
        if (value instanceof Resource r) {
            return languageFileContents.getUnchecked(r).get(key);
        }
        return (String) value;
    }

    @Override
    protected Map<String, String> delegate() {
        return delegate;
    }

    @Override
    public String getOrDefault(Object key, String defaultValue) {
        String value = delegate().get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Loads every value into memory, so the map keeps working after the packs it reads from are closed.
     * Must be called while those packs are still open.
     */
    public void materialize() {
        Map<String, Object> storage = this.storage;
        if (storage == null) {
            return;
        }
        Map<String, String> contents = Maps.newHashMapWithExpectedSize(storage.size());
        Map<Resource, List<String>> keysByResource = new IdentityHashMap<>();
        storage.forEach((key, value) -> {
            if (value instanceof Resource r) {
                keysByResource.computeIfAbsent(r, k -> new ArrayList<>()).add(key);
            } else {
                contents.put(key, (String) value);
            }
        });
        // One file at a time, so only a single parsed file is held alongside the result.
        keysByResource.forEach((resource, keys) -> {
            Map<String, String> file = languageFileContents.getIfPresent(resource);
            if (file == null) {
                file = loadLanguageFile(resource);
            } else {
                languageFileContents.invalidate(resource);
            }
            for (String key : keys) {
                String value = file.get(key);
                if (value != null) {
                    contents.put(key, value);
                }
            }
        });
        delegate = Map.copyOf(contents);
        this.storage = null;
        languageFileContents.invalidateAll();
    }
}
