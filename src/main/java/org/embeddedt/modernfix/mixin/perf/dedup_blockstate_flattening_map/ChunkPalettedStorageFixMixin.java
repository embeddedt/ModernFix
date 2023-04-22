package org.embeddedt.modernfix.mixin.perf.dedup_blockstate_flattening_map;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.fixes.ChunkPalettedStorageFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ChunkPalettedStorageFix.class)
public class ChunkPalettedStorageFixMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/DataFixUtils;make(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;"))
    private static Object skipMakingMap(Object o, Consumer<?> consumer) {
        return o;
    }

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/datafix/fixes/BlockStateData;getTag(I)Lcom/mojang/serialization/Dynamic;"))
    private static Dynamic<?> getFakeAirTag(int id) {
        return new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
    }

    @Inject(method = "fix", at = @At("HEAD"))
    private void skipFix(CallbackInfoReturnable<Dynamic<?>> cir) {
        throw new UnsupportedOperationException("No Flattening for you.");
    }
}
