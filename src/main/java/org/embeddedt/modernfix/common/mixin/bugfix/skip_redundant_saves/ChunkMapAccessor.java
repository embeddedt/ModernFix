package org.embeddedt.modernfix.common.mixin.bugfix.skip_redundant_saves;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("releaseLightTicket")
    void mfix$invokeReleaseLightTicket(ChunkPos pos);
}
