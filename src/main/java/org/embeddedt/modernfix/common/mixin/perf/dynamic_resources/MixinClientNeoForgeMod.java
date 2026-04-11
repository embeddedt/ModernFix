package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.ClientNeoForgeMod;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientNeoForgeMod.class)
@ClientOnlyMixin
public class MixinClientNeoForgeMod {
    /**
     * @author embeddedt
     * @reason avoid triggering eager load of every item model
     */
    @Redirect(method = "lambda$new$7", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;getItemModel(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/item/ItemModel;"))
    private static ItemModel checkExistenceWithoutLoadingModel(ModelManager instance, Identifier id) {
        if (!((ModelManagerAccessor)instance).mfix$getBakedItemModels().containsKey(id)) {
            ModernFix.LOGGER.warn("Missing item model '{}'", id);
        }
        return null;
    }
}
