package org.embeddedt.modernfix.forge.mixin.bugfix.removed_dimensions;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelStorageSource.class)
public class LevelStorageSourceMixin {
    @Inject(method = "readWorldGenSettings", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/Dynamic;)Lcom/mojang/serialization/DataResult;", remap = false))
    private static <T> void freezeRegistriesBeforeParsing(Dynamic<T> nbt, DataFixer fixer, int version, CallbackInfoReturnable<Pair<WorldGenSettings, Lifecycle>> cir) {
        DynamicOps<T> var10 = nbt.getOps();
        if (var10 instanceof RegistryOps<T> ops) {
            ops.registryAccess.ownedRegistries().forEach((e) -> {
                e.value().freeze();
            });
        }
    }
}
