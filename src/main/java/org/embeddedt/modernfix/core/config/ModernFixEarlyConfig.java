package org.embeddedt.modernfix.core.config;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ModernFixEarlyConfig {
    private static final Logger LOGGER = LogManager.getLogger("ModernFixConfig");

    private final Map<String, Option> options = new HashMap<>();

    public static final boolean OPTIFINE_PRESENT;

    private File configFile;

    static {
        boolean hasOfClass = false;
        try {
            Class.forName("optifine.OptiFineTransformationService");
            hasOfClass = true;
        } catch(Throwable e) {
        }
        OPTIFINE_PRESENT = hasOfClass;
    }

    private static boolean modPresent(String modId) {
        if(modId.equals("optifine"))
            return OPTIFINE_PRESENT;
        else
            return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    private ModernFixEarlyConfig(File file) {
        this.configFile = file;
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("core", true); // TODO: Don't actually allow the user to disable this
        this.addMixinRule("feature.branding", true);
        this.addMixinRule("feature.measure_time", true);
        this.addMixinRule("feature.reduce_loading_screen_freezes", false);
        this.addMixinRule("feature.direct_stack_trace", false);
        this.addMixinRule("perf.fast_registry_validation", true);
        // not stable yet
        this.addMixinRule("perf.rewrite_registry", false);
        this.addMixinRule("perf.use_integrated_resources.jepb", modPresent("jepb"));
        this.addMixinRule("perf.use_integrated_resources.jeresources", modPresent("jeresources"));
        this.addMixinRule("perf.jeresources_startup", modPresent("jeresources"));
        this.addMixinRule("perf.remove_biome_temperature_cache", true);
        this.addMixinRule("perf.resourcepacks", true);
        this.addMixinRule("perf.reduce_blockstate_cache_rebuilds", true);
        this.addMixinRule("perf.boost_worker_count", true);
        this.addMixinRule("perf.skip_first_datapack_reload", true);
        this.addMixinRule("perf.reuse_datapacks", true);
        this.addMixinRule("perf.model_optimizations", true);
        this.addMixinRule("perf.dynamic_resources", false);
        this.addMixinRule("perf.dedicated_reload_executor", true);
        /* Use a simpler ArrayMap if FerriteCore is using the map intelligently anyway */
        this.addMixinRule("perf.state_definition_construct", modPresent("ferritecore"));
        this.addMixinRule("perf.cache_strongholds", true);
        this.addMixinRule("perf.dedup_blockstate_flattening_map", false);
        this.addMixinRule("perf.clear_mixin_classinfo", false);
        this.addMixinRule("perf.cache_upgraded_structures", true);
        this.addMixinRule("perf.biome_zoomer", true);
        this.addMixinRule("perf.compress_blockstate", false);
        this.addMixinRule("bugfix.concurrency", true);
        this.addMixinRule("bugfix.preserve_early_window_pos", true);
        this.addMixinRule("bugfix.edge_chunk_not_saved", true);
        this.addMixinRule("bugfix.starlight_emptiness", modPresent("starlight"));
        this.addMixinRule("bugfix.packet_leak", false);
        this.addMixinRule("perf.dynamic_structure_manager", true);
        this.addMixinRule("bugfix.mc218112", true);
        this.addMixinRule("bugfix.chunk_deadlock", true);
        this.addMixinRule("bugfix.paper_chunk_patches", true);
        this.addMixinRule("bugfix.chunk_deadlock.valhesia", modPresent("valhelsia_structures"));
        this.addMixinRule("bugfix.tf_cme_on_load", modPresent("twilightforest"));
        this.addMixinRule("bugfix.refinedstorage", modPresent("refinedstorage"));
        this.addMixinRule("perf.async_jei", modPresent("jei"));
        this.addMixinRule("perf.thread_priorities", true);
        this.addMixinRule("perf.preload_block_classes", false);
        this.addMixinRule("perf.scan_cache", true);
        this.addMixinRule("perf.compress_biome_container", true);
        this.addMixinRule("perf.nuke_empty_chunk_sections", true);
        this.addMixinRule("perf.flatten_model_predicates", true);
        this.addMixinRule("perf.deduplicate_location", false);
        this.addMixinRule("perf.cache_blockstate_cache_arrays", true);
        this.addMixinRule("perf.cache_model_materials", true);
        this.addMixinRule("perf.nbt_memory_usage", true);
        this.addMixinRule("perf.patchouli_deduplicate_books", modPresent("patchouli"));
        this.addMixinRule("perf.datapack_reload_exceptions", true);
        this.addMixinRule("perf.async_locator", true);
        this.addMixinRule("perf.faster_texture_stitching", true);
        this.addMixinRule("perf.faster_texture_loading", true);
        this.addMixinRule("perf.faster_font_loading", true);
        this.addMixinRule("perf.kubejs", modPresent("kubejs"));
        this.addMixinRule("perf.faster_singleplayer_load", false);
        /* Keep this off if JEI isn't installed to prevent breaking vanilla gameplay */
        this.addMixinRule("perf.blast_search_trees", modPresent("jei"));
        this.addMixinRule("safety", true);
        this.addMixinRule("launch.class_search_cache", true);
        boolean isDevEnv = !FMLLoader.isProduction() && FMLLoader.getLoadingModList().getModFileById("modernfix").getFile().getLocator() instanceof ExplodedDirectoryLocator;
        this.addMixinRule("devenv", isDevEnv);

        /* Mod compat */
        disableIfModPresent("mixin.perf.thread_priorities", "smoothboot");
        disableIfModPresent("mixin.perf.boost_worker_count", "smoothboot");
        disableIfModPresent("mixin.perf.async_jei", "modernui");
        disableIfModPresent("mixin.perf.compress_biome_container", "chocolate", "betterendforge");
        disableIfModPresent("mixin.bugfix.mc218112", "performant");
        disableIfModPresent("mixin.perf.reuse_datapacks", "tac");
        disableIfModPresent("mixin.launch.class_search_cache", "optifine");
    }

    private void disableIfModPresent(String configName, String... ids) {
        for(String id : ids) {
            if(modPresent(id)) {
                Option option = this.options.get(configName);
                if(option != null)
                    option.addModOverride(false, id);
                else
                    LOGGER.warn("Can't disable missing option {}", configName);
            }
        }
    }

    /**
     * Defines a Mixin rule which can be configured by users and other mods.
     * @throws IllegalStateException If a rule with that name already exists
     * @param mixin The name of the mixin package which will be controlled by this rule
     * @param enabled True if the rule will be enabled by default, otherwise false
     */
    private void addMixinRule(String mixin, boolean enabled) {
        String name = getMixinRuleName(mixin);

        if (this.options.putIfAbsent(name, new Option(name, enabled, false)) != null) {
            throw new IllegalStateException("Mixin rule already defined: " + mixin);
        }
    }

    private void readProperties(Properties props) {
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            Option option = this.options.get(key);

            if (option == null) {
                LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            boolean enabled;

            if (value.equalsIgnoreCase("true")) {
                enabled = true;
            } else if (value.equalsIgnoreCase("false")) {
                enabled = false;
            } else {
                LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring", value, key);
                continue;
            }

            option.setEnabled(enabled, true);
        }
    }

    /**
     * Returns the effective option for the specified class name. This traverses the package path of the given mixin
     * and checks each root for configuration rules. If a configuration rule disables a package, all mixins located in
     * that package and its children will be disabled. The effective option is that of the highest-priority rule, either
     * a enable rule at the end of the chain or a disable rule at the earliest point in the chain.
     *
     * @return Null if no options matched the given mixin name, otherwise the effective option for this Mixin
     */
    public Option getEffectiveOptionForMixin(String mixinClassName) {
        int lastSplit = 0;
        int nextSplit;

        Option rule = null;

        while ((nextSplit = mixinClassName.indexOf('.', lastSplit)) != -1) {
            String key = getMixinRuleName(mixinClassName.substring(0, nextSplit));

            Option candidate = this.options.get(key);

            if (candidate != null) {
                rule = candidate;

                if (!rule.isEnabled()) {
                    return rule;
                }
            }

            lastSplit = nextSplit + 1;
        }

        return rule;
    }

    /**
     * Loads the configuration file from the specified location. If it does not exist, a new configuration file will be
     * created. The file on disk will then be updated to include any new options.
     */
    public static ModernFixEarlyConfig load(File file) {
        ModernFixEarlyConfig config = new ModernFixEarlyConfig(file);
        Properties props = new Properties();
        if(file.exists()) {
            try (FileInputStream fin = new FileInputStream(file)){
                props.load(fin);
            } catch (IOException e) {
                throw new RuntimeException("Could not load config file", e);
            }
            config.readProperties(props);
        }

        try {
            config.save();
        } catch (IOException e) {
            LOGGER.warn("Could not write configuration file", e);
        }

        return config;
    }

    public void save() throws IOException {
        File dir = configFile.getParentFile();

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Could not create parent directories");
            }
        } else if (!dir.isDirectory()) {
            throw new IOException("The parent file is not a directory");
        }

        try (Writer writer = new FileWriter(configFile)) {
            writer.write("# This is the configuration file for ModernFix.\n");
            writer.write("#\n");
            writer.write("# The following options can be enabled or disabled if there is a compatibility issue.\n");
            writer.write("# Add a line mixin.example_name=true/false without the # sign to enable/disable a rule.\n");
            List<String> keys = this.options.keySet().stream()
                    .filter(key -> !key.equals("mixin.core"))
                    .sorted()
                    .collect(Collectors.toList());
            for(String line : keys) {
                if(!line.equals("mixin.core"))
                    writer.write("#  " + line + "\n");
            }

            for (String key : keys) {
                Option option = this.options.get(key);
                if(option.isUserDefined())
                    writer.write(key + "=" + option.isEnabled() + "\n");
            }
        }
    }

    private static String getMixinRuleName(String name) {
        return "mixin." + name;
    }

    public int getOptionCount() {
        return this.options.size();
    }

    public int getOptionOverrideCount() {
        return (int) this.options.values()
                .stream()
                .filter(Option::isOverridden)
                .count();
    }

    public Map<String, Option> getOptionMap() {
        return Collections.unmodifiableMap(this.options);
    }
}
