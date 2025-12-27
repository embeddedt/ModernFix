package org.embeddedt.modernfix.common.mixin.perf.smart_ingredient_sync;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.neoforge.packet.SmartIngredientSyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraft/world/item/crafting/Ingredient$1"})
public abstract class IngredientMixin {

    @Inject(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/crafting/Ingredient;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/ItemStack;LIST_STREAM_CODEC:Lnet/minecraft/network/codec/StreamCodec;"),
            cancellable = true)
    private void checkForVanillaTagIngredient(RegistryFriendlyByteBuf buf, Ingredient ingredient, CallbackInfo ci) {
        if (!SmartIngredientSyncPayload.CLIENT_HAS_SMART_INGREDIENT_SYNC.get() || ingredient.isCustom()) {
            return;
        }
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length == 1 && values[0] instanceof Ingredient.TagValue tagValue) {
            var optionalHolderSet = BuiltInRegistries.ITEM.getTag(tagValue.tag());
            if (optionalHolderSet.isEmpty()) {
                // Use default serialization logic for tags that do not exist
                return;
            }

            // Encode this as our tag ingredient type instead of using vanilla's flattening logic.
            ci.cancel();
            buf.writeVarInt(-2);
            buf.writeResourceLocation(tagValue.tag().location());
        }
    }

    @Inject(method = "decode(Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lnet/minecraft/world/item/crafting/Ingredient;",
            at = @At(value = "HEAD"),
            cancellable = true, remap = false)
    private void decodeSmartIngredient(RegistryFriendlyByteBuf buf, CallbackInfoReturnable<Ingredient> cir) {
        int readerIndex = buf.readerIndex();
        var sizeId = buf.readVarInt();
        if (sizeId == -2) {
            // Probably our ingredient
            var tagKey = TagKey.create(Registries.ITEM, buf.readResourceLocation());
            cir.setReturnValue(Ingredient.of(tagKey));
        } else {
            buf.readerIndex(readerIndex);
        }
    }
}
