package org.embeddedt.modernfix.common.mixin.perf.dynamic_dfu;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevent fetchChoiceType calls from loading DFU early. Vanilla doesn't need the return values here.
 */
@Mixin(BlockEntityType.class)
public class BlockEntityTypeMixin {
    @Redirect(method = "register", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;fetchChoiceType(Lcom/mojang/datafixers/DSL$TypeReference;Ljava/lang/String;)Lcom/mojang/datafixers/types/Type;"))
    private static Type<?> skipSchemaCheck(DSL.TypeReference ref, String s) {
        return null;
    }
}
