package org.embeddedt.modernfix.duck;

import net.minecraft.network.FriendlyByteBuf;

public interface IClientNetHandler {
    FriendlyByteBuf getCopiedCustomBuffer();
}
