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
@Mixin(targets = { "slimeknights/mantle/client/model/connected/ConnectedModel$Baked" }, remap = false)
@RequiresMod("mantle")
@ClientOnlyMixin
public class ConnectedModelMixin {
    @SuppressWarnings("unused")
    @Shadow
    @Final
    @Mutable
    private final Map<String, String> nameMappingCache = new ConcurrentHashMap<>();
}
