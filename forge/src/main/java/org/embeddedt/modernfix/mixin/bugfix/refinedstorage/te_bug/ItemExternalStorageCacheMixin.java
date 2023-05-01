package org.embeddedt.modernfix.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorageCache;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.duck.rs.IItemExternalStorageCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(ItemExternalStorageCache.class)
@RequiresMod("refinedstorage")
public class ItemExternalStorageCacheMixin implements IItemExternalStorageCache {
    @Shadow(remap = false) private List<ItemStack> cache;

    @Shadow(remap = false) private int stored;

    public boolean initCache(IItemHandler handler) {
        if (cache != null) {
            return false;
        }

        cache = new ArrayList<>();

        int stored = 0;
        for (int i = 0; i < handler.getSlots(); ++i) {
            ItemStack stack = handler.getStackInSlot(i).copy();
            cache.add(stack);
            stored += stack.getCount();
        }
        this.stored = stored;

        return true;
    }

    /**
     * Make sure all cache creation goes through initCache.
     */
    @Inject(method = "update", at = @At(value = "FIELD", remap = false, target = "Lcom/refinedmods/refinedstorage/apiimpl/storage/externalstorage/ItemExternalStorageCache;cache:Ljava/util/List;", ordinal = 0), cancellable = true, remap = false)
    private void checkNullCache(INetwork network, @Nullable IItemHandler handler, CallbackInfo ci) {
        if(initCache(handler))
            ci.cancel();
    }
}
