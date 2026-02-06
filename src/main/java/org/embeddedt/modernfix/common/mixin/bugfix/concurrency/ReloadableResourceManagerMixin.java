package org.embeddedt.modernfix.common.mixin.bugfix.concurrency;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.forge.init.ModernFixForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ReloadableResourceManager.class)
@ClientOnlyMixin
public abstract class ReloadableResourceManagerMixin {
    @Shadow
    @Final
    private PackType type;

    @Shadow
    public abstract void registerReloadListener(PreparableReloadListener listener);

    /**
     * @author embeddedt
     * @reason complain loudly when reload listeners are being registered too late in a way that would cause
     * concurrency issues, and prevent them from crashing the game
     */
    @WrapMethod(method = "registerReloadListener")
    private void checkCallingThread(PreparableReloadListener listener, Operation<Void> original) {
        if (ModernFixForge.registryEventsFired && this.type == PackType.CLIENT_RESOURCES
                && (Object)this == Minecraft.getInstance().getResourceManager()
                && !Minecraft.getInstance().isSameThread()) {
            ModernFix.LOGGER.error("A mod is calling registerReloadListener at the wrong time. This will cause random concurrency crashes when ModernFix is not installed. Please report this to them. If you are a modder, refer to https://github.com/embeddedt/ModernFix/wiki/registerReloadListener-called-on-wrong-thread for more information.", new Exception("registerReloadListener called on wrong thread"));
            // Defer the call onto the main client thread. There is a decent chance the mod's listener will be
            // ignored in this case, but it is more predictable than allowing them to randomly crash the game.
            Minecraft.getInstance().tell(() -> this.registerReloadListener(listener));
            return;
        }

        original.call(listener);
    }
}
