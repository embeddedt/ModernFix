package org.embeddedt.modernfix.neoforge.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFix;

public enum SmartIngredientSyncPayload implements CustomPacketPayload {
    INSTANCE;

    public static final ThreadLocal<Boolean> CLIENT_HAS_SMART_INGREDIENT_SYNC = ThreadLocal.withInitial(() -> false);

    public static final CustomPacketPayload.Type<SmartIngredientSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModernFix.MODID, "ingredient_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SmartIngredientSyncPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}