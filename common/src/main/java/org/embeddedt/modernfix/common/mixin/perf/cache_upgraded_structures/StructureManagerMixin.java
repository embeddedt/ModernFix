package org.embeddedt.modernfix.common.mixin.perf.cache_upgraded_structures;

import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
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
import java.io.InputStream;
import java.util.Optional;

@Mixin(StructureTemplateManager.class)
public class StructureManagerMixin {
    @Shadow @Final private DataFixer fixerUpper;

    @Shadow private ResourceManager resourceManager;

    @Shadow @Final private HolderGetter<Block> blockLookup;

    /**
     * @author embeddedt
     * @reason use our own manager to avoid needless DFU updates
     */
    @Overwrite
    private Optional<StructureTemplate> loadFromResource(ResourceLocation id) {
        ResourceLocation arg = new ResourceLocation(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
        try(InputStream stream = this.resourceManager.open(arg)) {
            return Optional.of(CachingStructureManager.readStructure(id, this.fixerUpper, stream, this.blockLookup));
        } catch(FileNotFoundException e) {
            return Optional.empty();
        } catch(IOException e) {
            ModernFix.LOGGER.error("Can't read structure", e);
            return Optional.empty();
        }
    }
}
