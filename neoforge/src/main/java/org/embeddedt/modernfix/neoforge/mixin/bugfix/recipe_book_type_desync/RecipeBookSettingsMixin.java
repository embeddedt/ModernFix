package org.embeddedt.modernfix.neoforge.mixin.bugfix.recipe_book_type_desync;

import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@Mixin(RecipeBookSettings.class)
@ClientOnlyMixin
public class RecipeBookSettingsMixin {
    private static int mfix$maxVanillaOrdinal;

    static {
        int ord = 0;
        for(Field f : RecipeBookType.class.getDeclaredFields()) {
            if(RecipeBookType.class.isAssignableFrom(f.getType()) && Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    RecipeBookType type = (RecipeBookType)f.get(null);
                    ord = Math.max(type.ordinal(), ord);
                } catch(Exception e) {
                    e.printStackTrace();
                    ord = Integer.MAX_VALUE - 1;
                    break;
                }
            }
        }
        mfix$maxVanillaOrdinal = ord;
    }
    /*
    @Redirect(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readBoolean()Z"))
    private static boolean useDefaultBooleanIfVanilla(FriendlyByteBuf buf, @Local(ordinal = 0) RecipeBookType type) {
        if(type.ordinal() >= (mfix$maxVanillaOrdinal + 1)) {
            ModernFix.LOGGER.warn("Not reading recipe book data for type '{}' as we are using vanilla connection", type.name());
            return false; // skip actually reading buffer
        }
        return buf.readBoolean();
    }
    */
}
