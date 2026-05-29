package org.embeddedt.modernfix.common.mixin.perf.resourcepacks;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    private static final Set<PackRepository> MFIX$INJECTED_REPOSITORIES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * @author embeddedt
     * @reason we do not want to inject the Forge pack finder more than once to any given repository
     */
    @WrapWithCondition(method = "configurePackRepository", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/resource/ResourcePackLoader;loadResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/function/Function;)V"))
    private static boolean skipInjectIfAlreadyInjected(PackRepository resourcePacks, Function<Map<IModFile, ? extends PathPackResources>, ? extends RepositorySource> packFinder) {
        return MFIX$INJECTED_REPOSITORIES.add(resourcePacks);
    }
}
