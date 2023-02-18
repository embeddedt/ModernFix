package org.embeddedt.modernfix.mixin.bugfix.structure_manager_crash;

import com.mojang.datafixers.DataFixer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;

@Mixin(StructureManager.class)
public class StructureManagerMixin {
    @Shadow @Final @Mutable
    private Map<ResourceLocation, StructureTemplate> structureRepository;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void makeStructuresSafe(ResourceManager arg, LevelStorageSource.LevelStorageAccess arg2, DataFixer dataFixer, CallbackInfo ci) {
        this.structureRepository = Collections.synchronizedMap(this.structureRepository);
    }
}
