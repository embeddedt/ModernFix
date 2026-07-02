package org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.world.gen.ExtendedSurfaceContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(NamespacedSurfaceRuleSource.class)
@RequiresMod("terrablender")
@RequiresFeatureLevel(FeatureLevel.BETA)
public class NamespacedSurfaceRuleSourceMixin {
    @Shadow
    @Final
    private Map<String, SurfaceRules.RuleSource> sources;

    @Shadow
    @Final
    private SurfaceRules.RuleSource base;

    /**
     * @author embeddedt
     * @reason Avoid doing an expensive biome lookup per block in cases where we can prove all biomes will be from a
     * single namespace. This achieves much of the benefit of TerraBlenderFix without the compatibility issues.
     */
    @Inject(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;", at = @At("HEAD"), cancellable = true, remap = false)
    private void modernfix$fastApply(SurfaceRules.Context context, CallbackInfoReturnable<SurfaceRules.SurfaceRule> cir,
                                     @Share("possibleNamespaces") LocalRef<Set<String>> possibleNamespacesRef) {
        var possibleBiomes = ((ExtendedSurfaceContext)(Object)context).mfix$getPossibleBiomes();
        if (possibleBiomes == null) {
            return;
        }
        Set<String> namespaces = mfix$findNamespaces(possibleBiomes);
        possibleNamespacesRef.set(namespaces);
        if (namespaces.size() != 1) {
            return;
        }
        String singleNamespace = namespaces.iterator().next();
        // In a single namespace scenario, we can bypass the biome lookup and directly construct a sequence rule
        SurfaceRules.RuleSource namespacedSource = this.sources.get(singleNamespace);
        if (namespacedSource == null) {
            // Sequence rule wrapper not required
            cir.setReturnValue(this.base.apply(context));
        } else {
            cir.setReturnValue(new SurfaceRules.SequenceRule(ImmutableList.of(namespacedSource.apply(context), this.base.apply(context))));
        }
    }

    /**
     * @author embeddedt
     * @reason Even if we have to fall back to the namespaced source, avoid compiling surface rules for namespaces that
     * will never be hit in the given chunk.
     */
    @ModifyArg(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;", at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"), remap = false)
    private Consumer<Map.Entry<String, SurfaceRules.RuleSource>> mfix$filterConsumer(Consumer<Map.Entry<String, SurfaceRules.RuleSource>> originalConsumer,
                                                                                     @Share("possibleNamespaces") LocalRef<Set<String>> possibleNamespacesRef) {
        var possibleNamespaces = possibleNamespacesRef.get();
        if (possibleNamespaces == null) {
            return originalConsumer;
        }
        return entry -> {
            if(possibleNamespaces.contains(entry.getKey())) {
                originalConsumer.accept(entry);
            }
        };
    }

    private static Set<String> mfix$findNamespaces(Set<ResourceKey<Biome>> possibleBiomes) {
        if (possibleBiomes.size() == 1) {
            return Set.of(possibleBiomes.iterator().next().identifier().getNamespace());
        } else {
            var namespaces = new ObjectArraySet<String>(4);
            for (var key : possibleBiomes) {
                namespaces.add(key.identifier().getNamespace());
            }
            return Set.copyOf(namespaces);
        }
    }
}
