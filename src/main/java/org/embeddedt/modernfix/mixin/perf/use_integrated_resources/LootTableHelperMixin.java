package org.embeddedt.modernfix.mixin.perf.use_integrated_resources;

import jeresources.util.LootTableHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LootTableHelper.class)
public class LootTableHelperMixin {
    @Redirect(method = "getLootTables", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"))
    private static MinecraftServer useIntegrated(Level level) {
        MinecraftServer server = level.getServer();
        if(server != null)
            return server;
        return ServerLifecycleHooks.getCurrentServer();
    }
}
