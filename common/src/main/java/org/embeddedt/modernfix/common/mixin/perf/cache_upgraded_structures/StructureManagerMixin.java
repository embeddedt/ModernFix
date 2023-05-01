package org.embeddedt.modernfix.common.mixin.perf.cache_upgraded_structures;

import com.mojang.datafixers.DataFixer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.embeddedt.modernfix.structure.CachingStructureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

@Mixin(StructureManager.class)
public class StructureManagerMixin {
    @Shadow @Final private DataFixer fixerUpper;

    @Redirect(method = "loadFromResource", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureManager;readStructure(Ljava/io/InputStream;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;"))
    private StructureTemplate readViaCache(StructureManager manager, InputStream stream, ResourceLocation arg) throws IOException {
        return CachingStructureManager.readStructure(arg, this.fixerUpper, stream);
    }
}
