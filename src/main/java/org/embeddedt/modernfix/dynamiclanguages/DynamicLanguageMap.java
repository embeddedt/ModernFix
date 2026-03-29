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
import java.util.Map;

public class DynamicLanguageMap {
    public static Map<String, String> forStorage(Map<String, ?> storage) {
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
