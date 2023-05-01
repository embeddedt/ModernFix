package org.embeddedt.modernfix.forge.mixin.perf.skip_first_datapack_reload;

import com.mojang.datafixers.DataFixer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;
import java.util.function.BiFunction;

@Mixin(LevelStorageSource.class)
public interface SaveFormatAccessor {
    @Invoker
    <T> T invokeReadLevelData(File saveDir, BiFunction<File, DataFixer, T> levelDatReader);
}
