package org.embeddedt.modernfix.duck;

import net.minecraft.network.PacketBuffer;

public interface IClientNetHandler {
    PacketBuffer getCopiedCustomBuffer();
}
