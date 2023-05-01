package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MerchantOffer.class)
public interface MerchantOfferAccess {
	@Mutable
	@Accessor
	void setMaxUses(int maxUses);
}
