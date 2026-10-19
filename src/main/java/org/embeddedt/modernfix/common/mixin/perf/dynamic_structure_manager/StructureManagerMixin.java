package org.embeddedt.modernfix.common.mixin.perf.dynamic_structure_manager;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Mixin(StructureTemplateManager.class)
public class StructureManagerMixin {
    @Shadow @Final @Mutable
    private Map<ResourceLocation, Optional<StructureTemplate>> structureRepository;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeStructuresSafe(ResourceManager arg, LevelStorageSource.LevelStorageAccess arg2, DataFixer dataFixer, HolderGetter<Block> arg3, CallbackInfo ci) {
        /* Structures needing to be reloaded is not a huge issue since we optimize loading them already */
        Cache<ResourceLocation, Optional<StructureTemplate>> structureCache = CacheBuilder.newBuilder()
                .softValues()
                .build();
        this.structureRepository = structureCache.asMap();
    }

    /* Guava's computeIfAbsent holds a segment lock for the entire load, stalling all other lookups in that segment */
    @WrapOperation(method = "get", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object loadOutsideLock(Map<ResourceLocation, Optional<StructureTemplate>> map, Object key, Function<ResourceLocation, Optional<StructureTemplate>> loader, Operation<Object> original) {
        Optional<StructureTemplate> existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        ResourceLocation id = (ResourceLocation)key;
        Optional<StructureTemplate> loaded = loader.apply(id);
        Optional<StructureTemplate> raced = map.putIfAbsent(id, loaded);
        return raced != null ? raced : loaded;
    }
}
