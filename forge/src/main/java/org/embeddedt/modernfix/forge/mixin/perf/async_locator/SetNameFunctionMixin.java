package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import org.embeddedt.modernfix.forge.structure.logic.CommonLogic;
import org.embeddedt.modernfix.forge.structure.logic.ExplorationMapFunctionLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SetNameFunction.class)
public class SetNameFunctionMixin {
    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;setHoverName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    public ItemStack deferSetName(ItemStack stack, Component name) {
        if (CommonLogic.isEmptyPendingMap(stack))
            ExplorationMapFunctionLogic.cacheName(stack, name);
        return stack;
    }
}
