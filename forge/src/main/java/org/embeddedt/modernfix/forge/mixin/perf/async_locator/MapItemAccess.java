package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapItem.class)
public interface MapItemAccess {
	@Invoker
	static MapItemSavedData callCreateAndStoreSavedData(ItemStack pStack, Level pLevel, int pX, int pZ, int pScale, boolean pTrackingPosition, boolean pUnlimitedTracking, ResourceKey<Level> pDimension) {
		throw new UnsupportedOperationException();
	}
}
