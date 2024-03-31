package org.embeddedt.modernfix.forge.mixin.bugfix.mantle_model_cme;

import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = { "slimeknights/mantle/client/model/fluid/FluidTextureModel$Loader" }, remap = false)
@RequiresMod("mantle")
@ClientOnlyMixin
public class FluidTextureModelLoaderMixin {
    @SuppressWarnings("unused")
    @Shadow
    @Final
    @Mutable
    private final Map<?, ?> modelCache = new ConcurrentHashMap<>();
}
