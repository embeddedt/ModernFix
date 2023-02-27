package org.embeddedt.modernfix.mixin.perf.modern_resourcepacks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Mixin(VanillaPackResources.class)
public class VanillaPackResourcesMixin {
    @Shadow @Final private static Map<PackType, Path> ROOT_DIR_BY_TYPE;

    /**
     * @author embeddedt
     * @reason avoid going through the module class loader when we know exactly what path this resource should come
     * from
     */
    @Overwrite
    protected InputStream getResourceAsStream(PackType type, ResourceLocation location) {
        Path rootPath = ROOT_DIR_BY_TYPE.get(type);
        Path targetPath = rootPath.resolve(location.getNamespace() + "/" + location.getPath());
        try {
            return Files.newInputStream(targetPath);
        } catch(IOException e) {
            return null;
        }
    }
}
