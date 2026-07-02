package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;
import java.util.zip.ZipFile;

@Mixin(FilePackResources.SharedZipFileAccess.class)
public interface SharedZipFileAccessAccessor {
    @Invoker("getOrCreateZipFile")
    ZipFile mfix$getOrCreateZipFile();

    @Accessor("file")
    File mfix$getFile();
}
