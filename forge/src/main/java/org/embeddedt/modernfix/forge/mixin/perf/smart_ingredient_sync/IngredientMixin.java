package org.embeddedt.modernfix.forge.mixin.perf.smart_ingredient_sync;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Ingredient.class)
public abstract class IngredientMixin {
    @Shadow public abstract boolean isVanilla();

    @Shadow @Final private Ingredient.Value[] values;

    @Unique
    private static final ResourceLocation MODERNFIX_TAG_VALUE = new ResourceLocation("modernfix", "tag_value");

    @Inject(method = "toNetwork",
            at = @At("HEAD"),
            cancellable = true)
    private void checkForVanillaTagIngredient(FriendlyByteBuf buf, CallbackInfo ci) {
        if (!PacketHandler.CLIENT_HAS_SMART_INGREDIENT_SYNC.get() || !this.isVanilla()) {
            return;
        }
        Ingredient.Value[] values = this.values;
        if (values.length == 1 && values[0] instanceof Ingredient.TagValue tagValue) {
            var optionalHolderSet = BuiltInRegistries.ITEM.getTag(tagValue.tag);
            if (optionalHolderSet.isEmpty()) {
                // Use default serialization logic for tags that do not exist
                return;
            }

            // Encode this as our tag ingredient type instead of using vanilla's flattening logic.
            ci.cancel();
            buf.writeVarInt(-1);
            buf.writeResourceLocation(MODERNFIX_TAG_VALUE);
            buf.writeResourceLocation(tagValue.tag.location());
        }
    }

    @Inject(method = "fromNetwork", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readResourceLocation()Lnet/minecraft/resources/ResourceLocation;", ordinal = 0), cancellable = true)
    private static void deserializeModernFixTagValue(FriendlyByteBuf buffer, CallbackInfoReturnable<Ingredient> cir) {
        int readerIndex = buffer.readerIndex();
        var type = buffer.readResourceLocation();
        if (!type.equals(MODERNFIX_TAG_VALUE)) {
            // Allow Forge to read the original packet
            buffer.readerIndex(readerIndex);
            return;
        }
        var tag = buffer.readResourceLocation();
        cir.setReturnValue(Ingredient.of(TagKey.create(Registries.ITEM, tag)));
    }
}
