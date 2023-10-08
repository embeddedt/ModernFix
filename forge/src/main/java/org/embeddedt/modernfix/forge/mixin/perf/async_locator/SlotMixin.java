package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.embeddedt.modernfix.forge.structure.logic.CommonLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow
    public abstract ItemStack getItem();

    @Inject(
            method = "mayPickup",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    public void preventPickupOfPendingExplorationMap(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (CommonLogic.isEmptyPendingMap(getItem())) {
            cir.setReturnValue(false);
        }
    }
}
