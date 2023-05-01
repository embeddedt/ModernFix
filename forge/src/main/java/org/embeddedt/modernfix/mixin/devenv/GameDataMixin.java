package org.embeddedt.modernfix.mixin.devenv;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameData.class)
public class GameDataMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/ForgeRegistry;dump(Lnet/minecraft/resources/ResourceLocation;)V"))
    private static void noDump(ForgeRegistry<?> reg, ResourceLocation id) {
    }
}
