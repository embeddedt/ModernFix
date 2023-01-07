package org.embeddedt.modernfix.mixin.perf.parallel_deferred_suppliers;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import org.embeddedt.modernfix.registry.DeferredRegisterBaker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Supplier;

@Mixin(DeferredRegister.class)
public class DeferredRegisterMixin {
    @Shadow(remap = false) private IForgeRegistry type;

    @Shadow(remap = false) @Final private String modid;

    @ModifyArg(method = "register(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/fml/RegistryObject;", at = @At(value = "INVOKE", target = "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), index = 1, remap = false)
    private Object swapForCachedSupplier(Object original) {
        if(this.type != null)
            return DeferredRegisterBaker.cacheForComputationLater(this.type.getRegistryName(), this.modid, (Supplier<?>)original);
        else
            return original;
    }
}
