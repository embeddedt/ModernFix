package org.embeddedt.modernfix.forge.mixin.bugfix.mantle_model_cme;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fix ConcurrentModificationException from Mantle models by making the cache thread-safe.
 */
@Pseudo
@Mixin(targets = { "slimeknights/mantle/client/model/RetexturedModel$BakedModel" }, remap = false)
@RequiresMod("mantle")
@ClientOnlyMixin
public class RetexturedModelBakedMixin {
    @SuppressWarnings("unused")
    @Shadow
    @Final
    @Mutable
    private final Map<ResourceLocation, BakedModel> cache = new ConcurrentHashMap<>();
}
