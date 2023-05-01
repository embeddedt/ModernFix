package org.embeddedt.modernfix.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorage;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorageCache;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.rs.IFluidExternalStorageCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(FluidExternalStorage.class)
@RequiresMod("refinedstorage")
public class FluidExternalStorageMixin {
    @Shadow(remap = false)
    @Final
    private FluidExternalStorageCache cache;

    @Redirect(method = "getStacks", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"), remap = false)
    private Object cacheAndGet(Supplier<IFluidHandler> supplier) {
        IFluidHandler handler = supplier.get();
        if(handler != null)
            ((IFluidExternalStorageCache)cache).initCache(handler);
        return handler;
    }
}
