package org.embeddedt.modernfix.mixin.perf.skip_first_datapack_reload;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.ILevelSave;
import org.embeddedt.modernfix.util.DummyServerConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public class LevelSaveMixin implements ILevelSave {
    @Shadow @Final private Path levelPath;

    public void runWorldPersistenceHooks(LevelStorageSource format) {
        ((SaveFormatAccessor)format).invokeReadLevelData(this.levelPath.toFile(), (file, dataFixer) -> {
            try {
                CompoundTag compoundTag = NbtIo.readCompressed(file);
                net.minecraftforge.fml.WorldPersistenceHooks.handleWorldDataLoad((LevelStorageSource.LevelStorageAccess)(Object)this, new DummyServerConfiguration(), compoundTag);
            } catch (Exception e) {
                ModernFix.LOGGER.error("Exception reading {}", file, e);
            }
            return null;
        });
    }
}
