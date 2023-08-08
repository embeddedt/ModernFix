package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevent fetchChoiceType calls from loading DFU early. Vanilla doesn't need the return values here.
 */
@Mixin(EntityType.Builder.class)
public class EntityTypeBuilderMixin {
    @Redirect(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;fetchChoiceType(Lcom/mojang/datafixers/DSL$TypeReference;Ljava/lang/String;)Lcom/mojang/datafixers/types/Type;"))
    private Type<?> skipSchemaCheck(DSL.TypeReference ref, String s) {
        return null;
    }
}
