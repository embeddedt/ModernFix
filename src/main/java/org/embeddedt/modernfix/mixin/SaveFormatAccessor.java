package org.embeddedt.modernfix.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.world.storage.SaveFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;
import java.util.function.BiFunction;

@Mixin(SaveFormat.class)
public interface SaveFormatAccessor {
    @Invoker
    <T> T invokeReadLevelData(File saveDir, BiFunction<File, DataFixer, T> levelDatReader);
}
