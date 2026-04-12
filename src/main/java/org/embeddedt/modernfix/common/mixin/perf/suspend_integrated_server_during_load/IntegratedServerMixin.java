package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Mixin(IntegratedServer.class)
@ClientOnlyMixin
public abstract class IntegratedServerMixin extends MinecraftServer implements IDeferrableIntegratedServer {
    @Shadow
    private boolean paused;

    private int mfix$numTickServerCalls = 0;
    private final AtomicBoolean mfix$hasPrimaryClientJoined = new AtomicBoolean(false);

    public IntegratedServerMixin(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, Proxy proxy, DataFixer fixerUpper, Services services, LevelLoadListener levelLoadListener, boolean propagatesCrashes) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes);
    }

    /**
     * @author embeddedt
     * @reason Wait to be finished processing all expensive packets (recipes, tags, etc.)
     * before continuing to tick the integrated server.
     */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z", ordinal = 0))
    private boolean preventTicks(Minecraft instance, Operation<Boolean> original) {
        return !mfix$hasPrimaryClientJoined.get() || original.call(instance);
    }

    /**
     * @author embeddedt
     * @reason Keep our own tick count for the integrated server specifically, rather than relying on super
     * to increment.
     */
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void mfix$countTicks(CallbackInfo ci) {
        this.mfix$numTickServerCalls++;
    }

    /**
     * @author embeddedt
     * @reason If waiting for a client connection to exist, we only need to tick the server connection,
     * not the whole server as vanilla does. However, we must tick the whole server once to accommodate mods
     * that rely on the first tick to initialize state as a side effect. Not doing this causes issues like
     * <a href="https://github.com/embeddedt/ModernFix/issues/639">#639</a>.
     */
    @WrapWithCondition(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V", ordinal = 0))
    private boolean preventRunningFullServerTick(MinecraftServer server, BooleanSupplier hasTimeLeft) {
        if (this.mfix$numTickServerCalls >= 2 && this.paused && !mfix$hasPrimaryClientJoined.get()) {
            var conn = this.getConnection();
            if (conn != null) {
                conn.tick();
            }
            return false;
        }
        return true;
    }

    @Override
    public void mfix$markClientLoadFinished() {
        mfix$hasPrimaryClientJoined.set(true);
    }
}
