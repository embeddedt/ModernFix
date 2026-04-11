package org.embeddedt.modernfix.dynamiclanguages;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.ModernFix;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicLanguageMap {
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
        Map<String, Object> storage = createStorage(rawLanguageContents, languageResources);
        LoadingCache<Resource, Map<String, String>> languageFileContents = CacheBuilder.newBuilder()
                .softValues()
                .build(new CacheLoader<>() {
                    @Override
                    public Map<String, String> load(Resource resource) throws Exception {
                        Map<String, String> data = new Object2ObjectOpenHashMap<>();
                        try (var stream = resource.open()) {
                            Language.loadFromJson(stream, data::put);
                        } catch (IOException e) {
                            ModernFix.LOGGER.error("Error loading language data from {}", resource.sourcePackId(), e);
                        }
                        return data;
                    }
                });
        return Maps.asMap(storage.keySet(), k -> {
            var value = storage.get(k);
            if (value instanceof Resource r) {
                return languageFileContents.getUnchecked(r).getOrDefault(k, "");
            } else if (value instanceof String s) {
                return s;
            } else {
                return null;
            }
        });
    }
}
