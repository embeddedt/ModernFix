package org.embeddedt.modernfix.forge.mixin.perf.twilightforest.structure_spawn_fix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import twilightforest.world.components.structures.start.TFStructureStart;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(TFStructureStart.class)
@RequiresMod("twilightforest")
public class TFStructureStartMixin {
    private static List<ResourceLocation> legacyStructureNames;
    @Redirect(method = "gatherPotentialSpawns", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"), remap = false)
    private static List<ConfiguredStructureFeature<?, ?>> getTFStructureFeatures(Stream<ConfiguredStructureFeature<?, ?>> stream, StructureFeatureManager structureManager, MobCategory classification, BlockPos pos) {
        if(legacyStructureNames == null) {
            legacyStructureNames = stream.map(feature -> feature.feature.getRegistryName()).collect(Collectors.toList());
        }
        var registry = structureManager.registryAccess().ownedRegistryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY);
        return legacyStructureNames.stream()
                .map(registry::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
