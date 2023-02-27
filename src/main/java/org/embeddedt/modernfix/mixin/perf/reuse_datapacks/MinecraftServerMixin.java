package org.embeddedt.modernfix.mixin.perf.reuse_datapacks;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.embeddedt.modernfix.duck.reuse_datapacks.ICachingResourceClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Shadow @Final private PackRepository packRepository;

    @Redirect(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerResources;close()V"))
    private void saveResources(ServerResources resources) {
        ICachingResourceClient client = ((ICachingResourceClient) Minecraft.getInstance());
        client.setCachedDataPackConfig(this.packRepository.getSelectedPacks().stream().map(Pack::getId).collect(ImmutableList.toImmutableList()));
        client.setCachedResources(resources);
    }
}
