package org.embeddedt.modernfix.structure.logic;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.mixin.perf.async_locator.MerchantOfferAccess;
import org.embeddedt.modernfix.structure.AsyncLocator;

import java.util.Optional;

public class MerchantLogic {
	private static final boolean REMOVE_OFFER = false;
	private MerchantLogic() {}

	public static void invalidateMap(AbstractVillager merchant, ItemStack mapStack) {
		mapStack.setHoverName(new TranslatableComponent("asynclocator.map.none"));
		Optional<MerchantOffer> offerOptional = merchant.getOffers()
			.stream()
			.filter(offer -> offer.getResult() == mapStack)
			.findFirst();
		if(offerOptional.isPresent()) {
			removeOffer(merchant, offerOptional.get());
		} else {
			ModernFix.LOGGER.warn("Failed to find merchant offer for map");
		}
	}

	public static void removeOffer(AbstractVillager merchant, MerchantOffer offer) {
		if (REMOVE_OFFER) {
			merchant.getOffers().remove(offer);
		} else {
			((MerchantOfferAccess) offer).setMaxUses(0);
			offer.setToOutOfStock();
		}
	}

	public static void handleLocationFound(
		ServerLevel level,
		AbstractVillager merchant,
		ItemStack mapStack,
		String displayName,
		MapDecoration.Type destinationType,
		BlockPos pos
	) {
		if (pos == null) {
			invalidateMap(merchant, mapStack);
		} else {
			CommonLogic.updateMap(mapStack, level, pos, 2, destinationType, displayName);
		}

		if (merchant.getTradingPlayer() instanceof ServerPlayer) {
			ServerPlayer tradingPlayer = (ServerPlayer)merchant.getTradingPlayer();
			tradingPlayer.sendMerchantOffers(
				tradingPlayer.containerMenu.containerId,
				merchant.getOffers(),
				merchant instanceof Villager ? ((Villager)merchant).getVillagerData().getLevel() : 1,
				merchant.getVillagerXp(),
				merchant.showProgressBar(),
				merchant.canRestock()
			);
		}
	}

	public static MerchantOffer updateMapAsync(
		Entity pTrader,
		int emeraldCost,
		String displayName,
		MapDecoration.Type destinationType,
		int maxUses,
		int villagerXp,
		StructureFeature<?> destination
	) {
		return updateMapAsyncInternal(
			pTrader,
			emeraldCost,
			maxUses,
			villagerXp,
			(level, merchant, mapStack) -> AsyncLocator.locateLevel(level, ImmutableSet.of(destination), merchant.blockPosition(), 100, true)
				.thenOnServerThread(pos -> handleLocationFound(
					level,
					merchant,
					mapStack,
					displayName,
					destinationType,
					pos
				))
		);
	}

	private static MerchantOffer updateMapAsyncInternal(
		Entity trader, int emeraldCost, int maxUses, int villagerXp, MapUpdateTask task
	) {
		if (trader instanceof AbstractVillager) {
			AbstractVillager merchant = (AbstractVillager)trader;
			ItemStack mapStack = CommonLogic.createEmptyMap();
			task.apply((ServerLevel) trader.level, merchant, mapStack);

			return new MerchantOffer(
				new ItemStack(Items.EMERALD, emeraldCost),
				new ItemStack(Items.COMPASS),
				mapStack,
				maxUses,
				villagerXp,
				0.2F
			);
		} else {
			return null;
		}
	}

	public interface MapUpdateTask {
		void apply(ServerLevel level, AbstractVillager merchant, ItemStack mapStack);
	}
}
