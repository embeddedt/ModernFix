package org.embeddedt.modernfix.registry;

import com.google.common.collect.BiMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.Function;

public class RegistryStorage {
    public static BiMap<ResourceLocation, DirectStorageRegistryObject> createStorage() {
        return new DirectStorageBiMap<>(DirectStorageRegistryObject::mfix$getResourceKey, DirectStorageRegistryObject::mfix$setResourceKey);
    }

    public static <T> BiMap<ResourceKey<T>, DirectStorageRegistryObject> createKeyStorage(ResourceKey<? extends Registry<T>> registryKey, BiMap<ResourceLocation, DirectStorageRegistryObject> storage) {
        if(storage instanceof DirectStorageBiMap) {
            DirectStorageBiMap<ResourceLocation, DirectStorageRegistryObject> directStorageBiMap = (DirectStorageBiMap<ResourceLocation, DirectStorageRegistryObject>)storage;
            // silently ignore put/putAll calls on this map
            return new TransformingBiMap<ResourceLocation, DirectStorageRegistryObject, ResourceKey<T>, DirectStorageRegistryObject>(directStorageBiMap, loc -> ResourceKey.create(registryKey, loc), ResourceKey::location, Function.identity(), Function.identity()) {
                @Override
                public DirectStorageRegistryObject put(ResourceKey<T> key, DirectStorageRegistryObject value) {
                    return null;
                }

                @Override
                public void putAll(Map<? extends ResourceKey<T>, ? extends DirectStorageRegistryObject> map) {

                }
            };
        } else
            throw new UnsupportedOperationException();
    }
}
