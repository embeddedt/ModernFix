package org.embeddedt.modernfix.forge.mixin.perf.use_integrated_resources.jeresources;

import jeresources.util.LootTableHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LootTableHelper.class)
@RequiresMod("jeresources")
@ClientOnlyMixin
public class LootTableHelperMixin {
    @Redirect(method = "getManager(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/level/storage/loot/LootTables;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"))
    private static MinecraftServer useIntegrated(Level level) {
        MinecraftServer server = level.getServer();
        if(server != null)
            return server;
        return ServerLifecycleHooks.getCurrentServer();
    }
}
