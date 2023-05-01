package org.embeddedt.modernfix.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorage;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.ItemExternalStorageProvider;
import com.refinedmods.refinedstorage.tile.InterfaceTile;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ItemExternalStorageProvider.class)
@RequiresMod("refinedstorage")
public class ItemExternalStorageProviderMixin {
    /**
     * @author embeddedt
     * @reason replace supplier in 2nd arg, no easy way of doing so without overwrite
     */
    @Overwrite
    public IExternalStorage<ItemStack> provide(IExternalStorageContext context, BlockEntity blockEntity, Direction direction) {
        return new ItemExternalStorage(context, () -> {
            Level level = blockEntity.getLevel();

            if (level == null) {
                return null;
            }

            BlockPos blockPos = blockEntity.getBlockPos();

            if (!level.isLoaded(blockPos)) {
                return null;
            }

            BlockEntity currentBlockEntity = level.getBlockEntity(blockPos);

            return WorldUtils.getItemHandler(currentBlockEntity, direction.getOpposite());
        }, blockEntity instanceof InterfaceTile);
    }
}
