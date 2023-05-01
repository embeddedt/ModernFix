package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
@ClientOnlyMixin
public class ItemRendererMixin {
    /**
     * Don't waste space putting all these locations into the cache, compute them on demand later.
     */
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemModelShaper;register(Lnet/minecraft/world/item/Item;Lnet/minecraft/client/resources/model/ModelResourceLocation;)V"))
    private void skipDefaultRegistration(ItemModelShaper shaper, Item item, ModelResourceLocation mrl) {

    }
}
