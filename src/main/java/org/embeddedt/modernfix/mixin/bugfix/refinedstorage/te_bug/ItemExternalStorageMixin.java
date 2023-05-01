package org.embeddedt.modernfix.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorage;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorageCache;
import net.minecraftforge.items.IItemHandler;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.rs.IItemExternalStorageCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

@Mixin(ItemExternalStorage.class)
@RequiresMod("refinedstorage")
public class ItemExternalStorageMixin {
    @Shadow(remap = false) @Final private ItemExternalStorageCache cache;

    @Redirect(method = "getStacks", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"), remap = false)
    private Object cacheAndGet(Supplier<IItemHandler> supplier) {
        IItemHandler handler = supplier.get();
        if(handler != null)
            ((IItemExternalStorageCache)cache).initCache(handler);
        return handler;
    }
}
