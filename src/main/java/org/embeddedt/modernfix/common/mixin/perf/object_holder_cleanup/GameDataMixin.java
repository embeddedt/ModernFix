package org.embeddedt.modernfix.common.mixin.perf.object_holder_cleanup;

import net.minecraftforge.registries.GameData;
import org.embeddedt.modernfix.forge.registry.ObjectHolderClearer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameData.class, remap = false)
public class GameDataMixin {
    @Inject(method = "postRegisterEvents", at = @At("RETURN"))
    private static void clearRedundantHolders(CallbackInfo ci) {
        ObjectHolderClearer.removeRedundantHolders();
    }
}
