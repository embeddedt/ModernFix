package org.embeddedt.modernfix.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorageCache;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.embeddedt.modernfix.duck.rs.IFluidExternalStorageCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(FluidExternalStorageCache.class)
public class FluidExternalStorageCacheMixin implements IFluidExternalStorageCache {
    @Shadow(remap = false) private List<FluidStack> cache;

    @Shadow(remap = false) private int stored;

    public boolean initCache(IFluidHandler handler) {
        if (cache != null) {
            return false;
        }

        cache = new ArrayList<>();

        int stored = 0;
        for (int i = 0; i < handler.getTanks(); ++i) {
            FluidStack stack = handler.getFluidInTank(i).copy();
            cache.add(stack);
            stored += stack.getAmount();
        }
        this.stored = stored;

        return true;
    }

    /**
     * Make sure all cache creation goes through initCache.
     */
    @Inject(method = "update", at = @At(value = "FIELD", target = "Lcom/refinedmods/refinedstorage/apiimpl/storage/externalstorage/FluidExternalStorageCache;cache:Ljava/util/List;", ordinal = 0), cancellable = true, remap = false)
    private void checkNullCache(INetwork network, @Nullable IFluidHandler handler, CallbackInfo ci) {
        if(initCache(handler))
            ci.cancel();
    }
}
