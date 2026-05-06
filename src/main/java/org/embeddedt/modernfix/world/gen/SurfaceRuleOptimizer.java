package org.embeddedt.modernfix.world.gen;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SurfaceRuleOptimizer {
    public static SurfaceRules.RuleSource optimizeSequenceRuleSource(SurfaceRules.SequenceRuleSource source) {
        // First pass: collect which biomes appear and count biome-gated branches
        Reference2ObjectOpenHashMap<ResourceKey<Biome>, List<SurfaceRules.RuleSource>> perBiomeSources = null;
        int biomeGatedBranches = 0;
        var sequence = source.sequence();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < sequence.size(); i++) {
            if (sequence.get(i) instanceof SurfaceRules.TestRuleSource testRuleSource
                    && testRuleSource.ifTrue() instanceof SurfaceRules.BiomeConditionSource biomeConditionSource) {
                biomeGatedBranches++;
                if (perBiomeSources == null) {
                    perBiomeSources = new Reference2ObjectOpenHashMap<>();
                }
                for (var biome : biomeConditionSource.biomes) {
                    perBiomeSources.putIfAbsent(biome, new ArrayList<>());
                }
            }
        }
        if (biomeGatedBranches < 3) {
            // Just use the source as-is, not worth optimizing
            return source;
        }
        // Second pass: build per-biome source lists preserving original interleaving order
        List<SurfaceRules.RuleSource> noMatchSources = new ArrayList<>();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < sequence.size(); i++) {
            var innerSource = sequence.get(i);
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
        return new OptimizedBiomeLookupSequenceRule(perBiomeSources, List.copyOf(noMatchSources));
    }

    public record OptimizedBiomeLookupSequenceRule(
            Reference2ObjectMap<ResourceKey<Biome>, List<SurfaceRules.RuleSource>> sourcesForBiomeMatch,
            List<SurfaceRules.RuleSource> sourcesForNoBiomeMatch
    ) implements SurfaceRules.RuleSource {
        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            var sourcesForBiomeMatch = this.sourcesForBiomeMatch;
            Reference2ObjectOpenHashMap<ResourceKey<Biome>, List<SurfaceRules.SurfaceRule>> compiledBiomeMatch =
                    new Reference2ObjectOpenHashMap<>(sourcesForBiomeMatch.size());
            Reference2ObjectMaps.fastForEach(sourcesForBiomeMatch, entry -> {
                SurfaceRules.SurfaceRule[] compiled = new SurfaceRules.SurfaceRule[entry.getValue().size()];
                var uncompiled = entry.getValue();
                for (int i = 0; i < uncompiled.size(); i++) {
                    compiled[i] = uncompiled.get(i).apply(context);
                }
                compiledBiomeMatch.put(entry.getKey(), List.of(compiled));
            });
            var sourcesForNoBiomeMatch = this.sourcesForNoBiomeMatch;
            SurfaceRules.SurfaceRule[] compiledNoMatch = new SurfaceRules.SurfaceRule[sourcesForNoBiomeMatch.size()];
            for (int i = 0; i < sourcesForNoBiomeMatch.size(); i++) {
                compiledNoMatch[i] = sourcesForNoBiomeMatch.get(i).apply(context);
            }
            return new CompiledOptimizedBiomeLookupRule(compiledBiomeMatch, List.of(compiledNoMatch), context);
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            throw new UnsupportedOperationException("Do not try to serialize OptimizedBiomeLookupSequenceRule");
        }
    }

    private record CompiledOptimizedBiomeLookupRule(
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
            CompiledOptimizedBiomeLookupRule that = (CompiledOptimizedBiomeLookupRule) o;
            return rulesForBiomeMatch.equals(that.rulesForBiomeMatch) && rulesForNoBiomeMatch.equals(that.rulesForNoBiomeMatch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rulesForBiomeMatch, rulesForNoBiomeMatch);
        }
    }
}
