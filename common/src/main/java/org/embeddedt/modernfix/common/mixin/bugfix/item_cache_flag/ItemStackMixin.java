package org.embeddedt.modernfix.common.mixin.bugfix.item_cache_flag;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Remove emptyCacheFlag from ItemStack, as Mojang did in 1.20 due to <a href="https://bugs.mojang.com/browse/MC-258939">MC-258939</a>.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Shadow @Final @Deprecated private Item item;

    /**
     * @author embeddedt, Mojang
     * @reason avoid getItem()
     */
    @Redirect(method = "isEmpty", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
    private Item getItemDirect(ItemStack stack) {
        return this.item;
    }

    @Redirect(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/world/item/ItemStack;emptyCacheFlag:Z"))
    private boolean checkEmptyDirect(ItemStack stack) {
        return stack.isEmpty();
    }

    /**
     * @author embeddedt, Mojang
     * @reason flag is no longer used
     */
    @Overwrite
    private void updateEmptyCacheFlag() {}
}
