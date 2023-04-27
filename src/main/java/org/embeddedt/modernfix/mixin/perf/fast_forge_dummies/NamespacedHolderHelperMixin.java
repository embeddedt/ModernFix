package org.embeddedt.modernfix.mixin.perf.fast_forge_dummies;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

@Mixin(targets = { "net/minecraftforge/registries/NamespacedWrapper" })
public abstract class NamespacedHolderHelperMixin<T> extends MappedRegistry<T>  {
    @Shadow private Map<ResourceLocation, Holder.Reference<T>> holdersByName;

    public NamespacedHolderHelperMixin(ResourceKey<? extends Registry<T>> arg, Lifecycle lifecycle) {
        super(arg, lifecycle);
    }

    @Inject(method = "freeze", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraftforge/registries/NamespacedWrapper;holdersByName:Ljava/util/Map;"), cancellable = true)
    private void fastDummyCheck(CallbackInfoReturnable<Registry<T>> cir) {
        // Quickly iterate without making any streams, etc. to see if everything is fine
        // Use the slow path (by returning without cancelling) when there is an error
        for(Holder.Reference<T> ref : this.holdersByName.values()) {
            if(!ref.isBound())
                return;
        }
        if (this.unregisteredIntrusiveHolders != null) {
            for(Holder.Reference<T> ref : this.unregisteredIntrusiveHolders.values()) {
                if(ref.getType() == Holder.Reference.Type.INTRUSIVE && !ref.isBound())
                    return;
            }
        }
        // Skip the creation of streams
        cir.setReturnValue(this);
    }
}
