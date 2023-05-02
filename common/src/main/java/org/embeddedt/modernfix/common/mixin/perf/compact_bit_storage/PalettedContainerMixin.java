package org.embeddedt.modernfix.common.mixin.perf.compact_bit_storage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {
    @Shadow private volatile PalettedContainer.Data<T> data;

    @Shadow protected abstract PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> data, int id);

    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/chunk/PalettedContainer;data:Lnet/minecraft/world/level/chunk/PalettedContainer$Data;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void validateData(FriendlyByteBuf buffer, CallbackInfo ci, int i) {
        if(i <= 1)
            return;
        long[] storArray = this.data.storage().getRaw();
        boolean empty = true;
        for (long l : storArray) {
            if (l != 0) {
                empty = false;
                break;
            }
        }
        if(empty && storArray.length > 0) {
            /* it means the chunk is oversized and wasting memory, take the ID out of the palette and recreate a smaller chunk */
            T value = this.data.palette().valueFor(0);
            this.data = this.createOrReuseData(null, 0);
            this.data.palette().idFor(value);
        }
    }
}
