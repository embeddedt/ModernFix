package org.embeddedt.modernfix.world.gen;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SurfaceRuleOptimizer {
    public static @Nullable SurfaceRules.SurfaceRule optimizeSequenceRule(SurfaceRules.SequenceRuleSource source, SurfaceRules.Context context) {
        // First pass: collect which biomes appear and count biome-gated branches
        Reference2ObjectOpenHashMap<ResourceKey<Biome>, List<SurfaceRules.RuleSource>> perBiomeSources = new Reference2ObjectOpenHashMap<>();
        int biomeGatedBranches = 0;
        for (var innerSource : source.sequence()) {
            if (innerSource instanceof SurfaceRules.TestRuleSource testRuleSource
                    && testRuleSource.ifTrue() instanceof SurfaceRules.BiomeConditionSource biomeConditionSource) {
                biomeGatedBranches++;
                for (var biome : biomeConditionSource.biomes) {
                    perBiomeSources.putIfAbsent(biome, new ArrayList<>());
                }
            }
        }
        if (biomeGatedBranches < 3) {
            return null;
        }
        // Second pass: build per-biome source lists preserving original interleaving order
        List<SurfaceRules.RuleSource> noMatchSources = new ArrayList<>();
        for (var innerSource : source.sequence()) {
            if (innerSource instanceof SurfaceRules.TestRuleSource testRuleSource
                    && testRuleSource.ifTrue() instanceof SurfaceRules.BiomeConditionSource biomeConditionSource) {
                // Add the inner rule (condition stripped) only to the matching biomes' lists
                for (var biome : biomeConditionSource.biomes) {
                    perBiomeSources.get(biome).add(testRuleSource.thenRun());
                }
            } else {
                // Non-biome-gated rule: add to every biome list and the no-match list
                for (var list : perBiomeSources.values()) {
                    list.add(innerSource);
                }
                noMatchSources.add(innerSource);
            }
        }
        // Compile all source lists into rule lists
        Reference2ObjectOpenHashMap<ResourceKey<Biome>, List<SurfaceRules.SurfaceRule>> compiledBiomeMatch = new Reference2ObjectOpenHashMap<>(perBiomeSources.size());
        Reference2ObjectMaps.fastForEach(perBiomeSources, entry -> {
            List<SurfaceRules.SurfaceRule> compiled = new ArrayList<>(entry.getValue().size());
            for (var src : entry.getValue()) {
                compiled.add(src.apply(context));
            }
            compiledBiomeMatch.put(entry.getKey(), List.copyOf(compiled));
        });
        List<SurfaceRules.SurfaceRule> compiledNoMatch = new ArrayList<>(noMatchSources.size());
        for (var src : noMatchSources) {
            compiledNoMatch.add(src.apply(context));
        }
        return new OptimizedBiomeLookupSequenceRule(compiledBiomeMatch, List.copyOf(compiledNoMatch), context);
    }

    public record OptimizedBiomeLookupSequenceRule(
            Map<ResourceKey<Biome>, List<SurfaceRules.SurfaceRule>> rulesForBiomeMatch,
            List<SurfaceRules.SurfaceRule> rulesForNoBiomeMatch,
            SurfaceRules.Context context
    ) implements SurfaceRules.SurfaceRule {
        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            var biome = context.biome.get();
            var key = (biome instanceof Holder.Reference<Biome> ref) ? ref.key() : biome.unwrapKey().orElseThrow();
            var ruleList = rulesForBiomeMatch.getOrDefault(key, rulesForNoBiomeMatch);
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0; i < ruleList.size(); i++) {
                var rule = ruleList.get(i);
                var state = rule.tryApply(x, y, z);
                if (state != null) {
                    return state;
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            OptimizedBiomeLookupSequenceRule that = (OptimizedBiomeLookupSequenceRule) o;
            return rulesForBiomeMatch.equals(that.rulesForBiomeMatch) && rulesForNoBiomeMatch.equals(that.rulesForNoBiomeMatch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rulesForBiomeMatch, rulesForNoBiomeMatch);
        }
    }
}
