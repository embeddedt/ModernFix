package org.embeddedt.modernfix.common.mixin.safety;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ItemProperties.class, priority = 700)
@ClientOnlyMixin
public class ItemPropertiesMixin {
    @Shadow @Final @Mutable private static Map<ResourceLocation, ItemPropertyFunction> GENERIC_PROPERTIES;
    @Shadow @Final @Mutable private static Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void useConcurrentMaps(CallbackInfo ci) {
        GENERIC_PROPERTIES = new ConcurrentHashMap<>(GENERIC_PROPERTIES);
        PROPERTIES = new ConcurrentHashMap<>(PROPERTIES);
    }
}
