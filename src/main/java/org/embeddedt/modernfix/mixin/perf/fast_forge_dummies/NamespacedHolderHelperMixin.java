package org.embeddedt.modernfix.mixin.perf.fast_forge_dummies;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Function;

@Mixin(targets = { "net/minecraftforge/registries/NamespacedHolderHelper" })
public class NamespacedHolderHelperMixin<T extends IForgeRegistryEntry<T>> {
    @Shadow private Map<ResourceLocation, Holder.Reference<T>> holdersByName;

    @Shadow @Final private @Nullable Function<T, Holder.Reference<T>> holderLookup;

    @Shadow private Map<T, Holder.Reference<T>> holders;

    @Shadow @Final private Registry<T> self;

    @Inject(method = "freeze", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraftforge/registries/NamespacedHolderHelper;holdersByName:Ljava/util/Map;"), cancellable = true, remap = false)
    private void fastDummyCheck(CallbackInfoReturnable<Registry<T>> cir) {
        // Quickly iterate without making any streams, etc. to see if everything is fine
        // Use the slow path (by returning without cancelling) when there is an error
        for(Holder.Reference<T> ref : this.holdersByName.values()) {
            if(!ref.isBound())
                return;
        }
        if (this.holderLookup != null) {
            for(Holder.Reference<T> ref : this.holders.values()) {
                if(ref.getType() == Holder.Reference.Type.INTRUSIVE && !ref.isBound())
                    return;
            }
        }
        // Skip the creation of streams
        cir.setReturnValue(this.self);
    }
}
