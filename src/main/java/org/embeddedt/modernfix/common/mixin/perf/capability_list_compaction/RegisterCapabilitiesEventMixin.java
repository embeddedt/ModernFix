package org.embeddedt.modernfix.common.mixin.perf.capability_list_compaction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.embeddedt.modernfix.neoforge.caps.ITrackingCapEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = RegisterCapabilitiesEvent.class, remap = false)
public class RegisterCapabilitiesEventMixin implements ITrackingCapEvent {
    private final Set<BaseCapability<?, ?>> mfix$trackedCapabilities = new HashSet<>();

    @Inject(method = "registerBlock", at = @At("HEAD"))
    private void trackBlockCap(BlockCapability<?, ?> capability, IBlockCapabilityProvider<?, ?> provider, Block[] blocks, CallbackInfo ci) {
        mfix$trackedCapabilities.add(capability);
    }

    @Inject(method = "registerBlockEntity", at = @At("HEAD"))
    private void trackBlockEntityCap(BlockCapability<?, ?> capability, BlockEntityType<?> type, ICapabilityProvider<?, ?, ?> provider, CallbackInfo ci) {
        mfix$trackedCapabilities.add(capability);
    }

    @Inject(method = "registerItem", at = @At("HEAD"))
    private void trackItemCap(ItemCapability<?, ?> capability, ICapabilityProvider<?, ?, ?> provider, ItemLike[] items, CallbackInfo ci) {
        mfix$trackedCapabilities.add(capability);
    }

    @Inject(method = "registerEntity", at = @At("HEAD"))
    private void trackEntityCap(EntityCapability<?, ?> capability, EntityType<?> type, ICapabilityProvider<?, ?, ?> provider, CallbackInfo ci) {
        mfix$trackedCapabilities.add(capability);
    }

    @Override
    public Set<BaseCapability<?, ?>> mfix$getTrackedCaps() {
        return mfix$trackedCapabilities;
    }
}
