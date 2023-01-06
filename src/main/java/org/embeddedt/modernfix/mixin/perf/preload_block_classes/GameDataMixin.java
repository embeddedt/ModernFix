package org.embeddedt.modernfix.mixin.perf.preload_block_classes;

import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

import org.embeddedt.modernfix.util.BlockClassPreloader;

@Mixin(GameData.class)
public class GameDataMixin {
    @Inject(method = "generateRegistryEvents", at = @At("RETURN"), remap = false)
    private static void preloadBlockClasses(CallbackInfoReturnable<Stream<ModLoadingStage.EventGenerator<?>>> cir) {
        BlockClassPreloader.preloadClasses();
    }
}
