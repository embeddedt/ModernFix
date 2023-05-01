package org.embeddedt.modernfix.forge.mixin.bugfix.refinedstorage.te_bug;

import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorage;
import com.refinedmods.refinedstorage.apiimpl.storage.externalstorage.FluidExternalStorageProvider;
import com.refinedmods.refinedstorage.tile.FluidInterfaceTile;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FluidExternalStorageProvider.class)
@RequiresMod("refinedstorage")
public class FluidExternalStorageProviderMixin {
    /**
     * @author embeddedt
     * @reason replace supplier in 2nd arg, no easy way of doing so without overwrite
     */
    @Overwrite
    public IExternalStorage<FluidStack> provide(IExternalStorageContext context, BlockEntity blockEntity, Direction direction) {
        return new FluidExternalStorage(context, () -> {
            Level level = blockEntity.getLevel();

            if (level == null) {
                return null;
            }

            BlockPos blockPos = blockEntity.getBlockPos();

            if (!level.isLoaded(blockPos)) {
                return null;
            }

            BlockEntity currentBlockEntity = level.getBlockEntity(blockPos);

            return WorldUtils.getFluidHandler(currentBlockEntity, direction.getOpposite());
        }, blockEntity instanceof FluidInterfaceTile);
    }
}
