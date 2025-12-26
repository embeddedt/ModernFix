package org.embeddedt.modernfix.common.mixin.perf.remove_spawn_chunks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.SortedArraySet;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SortedArraySet.class)
public class SortedArraySetMixin<T> {
    /**
     * @author embeddedt
     * @reason Make add() not crash with a null key, since some mods (Carpet) assume there will always be a spawn ticket,
     * and then assume the reference they have is non-null (it can be null with this option enabled).
     */
    @WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SortedArraySet;findIndex(Ljava/lang/Object;)I"), require = 0)
    private int checkStatus(SortedArraySet<T> instance, T object, Operation<Integer> original) {
        if(object == null) {
            ModernFix.LOGGER.error("Attempted to insert a null key into SortedArraySet, ignoring");
            return 0;
        }
        return original.call(instance, object);
    }
}
