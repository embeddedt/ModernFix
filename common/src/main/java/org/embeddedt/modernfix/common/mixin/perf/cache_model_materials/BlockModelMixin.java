package org.embeddedt.modernfix.common.mixin.perf.cache_model_materials;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.ICachedMaterialsModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Mixin(BlockModel.class)
@ClientOnlyMixin
public class BlockModelMixin {
    @Shadow @Final @Mutable public Map<String, Either<Material, String>> textureMap;

    /**
     * @author embeddedt
     * @reason detect changes to the texture map, and clear the material cache as needed
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void useTrackingTextureMap(CallbackInfo ci) {
        Map<String, Either<Material, String>> backingMap = this.textureMap;
        ICachedMaterialsModel cacheHolder = (ICachedMaterialsModel)this;
        this.textureMap = new Map<String, Either<Material, String>>() {
            @Override
            public int size() {
                return backingMap.size();
            }

            @Override
            public boolean isEmpty() {
                return backingMap.isEmpty();
            }

            @Override
            public boolean containsKey(Object o) {
                return backingMap.containsKey(o);
            }

            @Override
            public boolean containsValue(Object o) {
                return backingMap.containsValue(o);
            }

            @Override
            public Either<Material, String> get(Object o) {
                return backingMap.get(o);
            }

            @Nullable
            @Override
            public Either<Material, String> put(String s, Either<Material, String> materialStringEither) {
                Either<Material, String> old = backingMap.put(s, materialStringEither);
                cacheHolder.clearMaterialsCache();
                return old;
            }

            @Override
            public Either<Material, String> remove(Object o) {
                Either<Material, String> e = backingMap.remove(o);
                cacheHolder.clearMaterialsCache();
                return e;
            }

            @Override
            public void putAll(@NotNull Map<? extends String, ? extends Either<Material, String>> map) {
                backingMap.putAll(map);
                cacheHolder.clearMaterialsCache();
            }

            @Override
            public void clear() {
                backingMap.clear();
                cacheHolder.clearMaterialsCache();
            }

            @NotNull
            @Override
            public Set<String> keySet() {
                cacheHolder.clearMaterialsCache();
                return backingMap.keySet();
            }

            @NotNull
            @Override
            public Collection<Either<Material, String>> values() {
                cacheHolder.clearMaterialsCache();
                return backingMap.values();
            }

            @NotNull
            @Override
            public Set<Entry<String, Either<Material, String>>> entrySet() {
                cacheHolder.clearMaterialsCache();
                return backingMap.entrySet();
            }
        };
    }
}
