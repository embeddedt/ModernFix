package org.embeddedt.modernfix.common.mixin.perf.cache_upgraded_structures;

import com.mojang.datafixers.DataFixer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.structure.CachingStructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

@Mixin(StructureTemplateManager.class)
public class StructureManagerMixin {
    @Shadow @Final private DataFixer fixerUpper;

    @Shadow private ResourceManager resourceManager;

    /**
     * @author embeddedt
     * @reason use our own manager to avoid needless DFU updates
     */
    @Overwrite
    private Optional<StructureTemplate> loadFromResource(ResourceLocation id) {
        ResourceLocation arg = new ResourceLocation(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
        try {
            return Optional.of(CachingStructureManager.readStructure(id, this.fixerUpper, this.resourceManager.open(arg)));
        } catch(FileNotFoundException e) {
            return Optional.empty();
        } catch(IOException e) {
            ModernFix.LOGGER.error("Can't read structure", e);
            return Optional.empty();
        }
    }
}
