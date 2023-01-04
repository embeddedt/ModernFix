package org.embeddedt.modernfix.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.world.storage.SaveFormat;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.ILevelSave;
import org.embeddedt.modernfix.util.DummyServerConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(SaveFormat.LevelSave.class)
public class LevelSaveMixin implements ILevelSave {
    @Shadow @Final private Path levelPath;

    public void runWorldPersistenceHooks() {
        SaveFormat saveFormat = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getInstance(), "field_71469_aa");
        ((SaveFormatAccessor)saveFormat).invokeReadLevelData(this.levelPath.toFile(), (file, dataFixer) -> {
            try {
                CompoundNBT compoundTag = CompressedStreamTools.readCompressed(file);
                net.minecraftforge.fml.WorldPersistenceHooks.handleWorldDataLoad((SaveFormat.LevelSave)(Object)this, new DummyServerConfiguration(), compoundTag);
            } catch (Exception e) {
                ModernFix.LOGGER.error("Exception reading {}", file, e);
            }
            return null;
        });
    }
}
