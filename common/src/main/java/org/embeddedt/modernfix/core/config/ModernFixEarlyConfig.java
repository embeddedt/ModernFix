package org.embeddedt.modernfix.core.config;

import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.IgnoreOutsideDev;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ModernFixEarlyConfig {
    private static final Logger LOGGER = LogManager.getLogger("ModernFixConfig");

    private final Map<String, Option> options = new HashMap<>();
    private final Multimap<String, Option> optionsByCategory = HashMultimap.create();

    private static final boolean ALLOW_OVERRIDE_OVERRIDES = Boolean.getBoolean("modernfix.unsupported.allowOverriding");

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
            return ModernFixPlatformHooks.INSTANCE.modPresent(modId);
    }

    private static final String MIXIN_DESC = Type.getDescriptor(Mixin.class);
    private static final String MIXIN_CLIENT_ONLY_DESC = Type.getDescriptor(ClientOnlyMixin.class);
    private static final String MIXIN_REQUIRES_MOD_DESC = Type.getDescriptor(RequiresMod.class);
    private static final String MIXIN_DEV_ONLY_DESC = Type.getDescriptor(IgnoreOutsideDev.class);

    private static final Pattern PLATFORM_PREFIX = Pattern.compile("(forge|fabric|common)\\.");

    public static String sanitize(String mixinClassName) {
        return PLATFORM_PREFIX.matcher(mixinClassName).replaceFirst("");
    }

    private final Set<String> mixinOptions = new ObjectOpenHashSet<>();
    private final Map<String, String> mixinsMissingMods = new Object2ObjectOpenHashMap<>();

    public static boolean isFabric = ModernFixEarlyConfig.class.getClassLoader().getResourceAsStream("modernfix-fabric.mixins.json") != null;

    public Map<String, String> getPermanentlyDisabledMixins() {
        return mixinsMissingMods;
    }

    private void scanForAndBuildMixinOptions() {
        List<String> configFiles = ImmutableList.of("modernfix-common.mixins.json", "modernfix-fabric.mixins.json", "modernfix-forge.mixins.json");
        List<String> mixinPaths = new ArrayList<>();
        for(String configFile : configFiles) {
            InputStream stream = ModernFixEarlyConfig.class.getClassLoader().getResourceAsStream(configFile);
            if(stream == null)
                continue;
            try(Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonObject configObject = (JsonObject)new JsonParser().parse(reader);
                List<JsonElement> mixinList = Stream.of("mixins", "client")
                        .map(key -> Optional.ofNullable(configObject.getAsJsonArray(key)))
                        .flatMap(arr -> arr.map(jsonElements -> StreamSupport.stream(jsonElements.spliterator(), false)).orElseGet(Stream::of))
                        .collect(Collectors.toList());
                String packageName = configObject.get("package").getAsString().replace('.', '/');
                for(JsonElement mixin : mixinList) {
                    mixinPaths.add(packageName + "/" + mixin.getAsString().replace('.', '/') + ".class");
                }
            } catch(IOException | JsonParseException e) {
                LOGGER.error("Error loading config " + configFile, e);
            }
        }
        Splitter dotSplitter = Splitter.on('.');
        for(String mixinPath : mixinPaths) {
            try(InputStream stream = ModernFixEarlyConfig.class.getClassLoader().getResourceAsStream(mixinPath)) {
                ClassReader reader = new ClassReader(stream);
                ClassNode node = new ClassNode();
                reader.accept(node,  ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                if(node.invisibleAnnotations == null)
                    return;
                boolean isMixin = false, isClientOnly = false, requiredModPresent = true, isDevOnly = false;
                String requiredModId = "";
                for(AnnotationNode annotation : node.invisibleAnnotations) {
                    if(Objects.equals(annotation.desc, MIXIN_DESC)) {
                        isMixin = true;
                    } else if(Objects.equals(annotation.desc, MIXIN_CLIENT_ONLY_DESC)) {
                        isClientOnly = true;
                    } else if(Objects.equals(annotation.desc, MIXIN_REQUIRES_MOD_DESC)) {
                        for(int i = 0; i < annotation.values.size(); i += 2) {
                            if(annotation.values.get(i).equals("value")) {
                                String modId = (String)annotation.values.get(i + 1);
                                if(modId != null) {
                                    requiredModPresent = modId.startsWith("!") ? !modPresent(modId.substring(1)) : modPresent(modId);
                                    requiredModId = modId;
                                }
                                break;
                            }
                        }
                    } else if(Objects.equals(annotation.desc, MIXIN_DEV_ONLY_DESC)) {
                        isDevOnly = true;
                    }
                }
                if(isMixin && (!isDevOnly || ModernFixPlatformHooks.INSTANCE.isDevEnv())) {
                    String mixinClassName = sanitize(node.name.replace('/', '.')).replace("org.embeddedt.modernfix.mixin.", "");
                    if(!requiredModPresent)
                        mixinsMissingMods.put(mixinClassName, requiredModId);
                    else if(isClientOnly && !ModernFixPlatformHooks.INSTANCE.isClient())
                        mixinsMissingMods.put(mixinClassName, "[not client]");
                    String mixinCategoryName = "mixin." + mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
                    mixinOptions.add(mixinCategoryName);
                }
            } catch(IOException e) {
                LOGGER.error("Error scanning file " + mixinPath, e);
            }
        }
    }

    private static final boolean isDevEnv = ModernFixPlatformHooks.INSTANCE.isDevEnv();

    private static class DefaultSettingMapBuilder extends ImmutableMap.Builder<String, Boolean> {
        public DefaultSettingMapBuilder putConditionally(BooleanSupplier condition, String k, Boolean v) {
            if(condition.getAsBoolean())
                put(k, v);
            return this;
        }

        @Override
        public DefaultSettingMapBuilder put(String key, Boolean value) {
            super.put(key, value);
            return this;
        }
    }

    private static final ImmutableMap<String, Boolean> DEFAULT_SETTING_OVERRIDES = new DefaultSettingMapBuilder()
            .put("mixin.perf.dynamic_resources", false)
            .put("mixin.feature.direct_stack_trace", false)
            .put("mixin.feature.stalled_chunk_load_detection", false)
            .put("mixin.perf.blast_search_trees.force", false)
            .put("mixin.bugfix.restore_old_dragon_movement", false)
            .put("mixin.perf.worldgen_allocation", false) // experimental
            .put("mixin.feature.cause_lag_by_disabling_threads", false)
            .put("mixin.perf.clear_mixin_classinfo", false)
            .put("mixin.perf.deduplicate_climate_parameters", false)
            .put("mixin.bugfix.packet_leak", false)
            .put("mixin.perf.deduplicate_location", false)
            .put("mixin.perf.dynamic_entity_renderers", false)
            .put("mixin.feature.integrated_server_watchdog", true)
            .put("mixin.perf.faster_item_rendering", false)
            .put("mixin.feature.spam_thread_dump", false)
            .put("mixin.feature.disable_unihex_font", false)
            .put("mixin.feature.remove_chat_signing", false)
            .put("mixin.feature.snapshot_easter_egg", true)
            .put("mixin.feature.warn_missing_perf_mods", true)
            .put("mixin.feature.spark_profile_launch", false)
            .put("mixin.devenv", isDevEnv)
            .put("mixin.perf.remove_spawn_chunks", isDevEnv)
            .putConditionally(() -> !isFabric, "mixin.bugfix.fix_config_crashes", true)
            .putConditionally(() -> !isFabric, "mixin.bugfix.forge_at_inject_error", true)
            .putConditionally(() -> !isFabric, "mixin.feature.registry_event_progress", false)
            .putConditionally(() -> isFabric, "mixin.perf.clear_fabric_mapping_tables", false)
            .build();

    private ModernFixEarlyConfig(File file) {
        this.configFile = file;
        OptionCategories.load();
        this.scanForAndBuildMixinOptions();
        mixinOptions.addAll(DEFAULT_SETTING_OVERRIDES.keySet());
        for(String optionName : mixinOptions) {
            boolean defaultEnabled = DEFAULT_SETTING_OVERRIDES.getOrDefault(optionName, true);
            Option option = new Option(optionName, defaultEnabled, false);
            this.options.putIfAbsent(optionName, option);
            this.optionsByCategory.put(OptionCategories.getCategoryForOption(optionName), option);
        }
        for(Map.Entry<String, Option> entry : this.options.entrySet()) {
            int idx = entry.getKey().lastIndexOf('.');
            if(idx <= 0)
                continue;
            String potentialParentKey = entry.getKey().substring(0, idx);
            Option potentialParent = this.options.get(potentialParentKey);
            if(potentialParent != null) {
                entry.getValue().setParent(potentialParent);
            }
        }
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("launch.class_search_cache", true);

        /* Mod compat */
        disableIfModPresent("mixin.perf.thread_priorities", "smoothboot", "threadtweak");
        disableIfModPresent("mixin.perf.boost_worker_count", "smoothboot", "threadtweak");
        disableIfModPresent("mixin.perf.compress_biome_container", "chocolate", "betterendforge" ,"skyblockbuilder", "modern_beta", "worldedit");
        disableIfModPresent("mixin.bugfix.mc218112", "performant");
        disableIfModPresent("mixin.bugfix.remove_block_chunkloading", "performant");
        disableIfModPresent("mixin.bugfix.paper_chunk_patches", "c2me");
        disableIfModPresent("mixin.bugfix.preserve_early_window_pos", "better_loading_screen");
        disableIfModPresent("mixin.perf.dynamic_dfu", "litematica");
        disableIfModPresent("mixin.perf.cache_strongholds", "littletiles", "c2me");
        // content overlap
        disableIfModPresent("mixin.perf.deduplicate_wall_shapes", "dashloader");
        disableIfModPresent("mixin.perf.nbt_memory_usage", "c2me");
        disableIfModPresent("mixin.bugfix.item_cache_flag", "lithium", "canary", "radium");
        // DimThread makes changes to the server chunk manager (understandably), C2ME probably does the same
        disableIfModPresent("mixin.bugfix.chunk_deadlock", "c2me", "dimthread");
        disableIfModPresent("mixin.perf.reuse_datapacks", "tac");
        disableIfModPresent("mixin.launch.class_search_cache", "optifine");
        disableIfModPresent("mixin.perf.faster_texture_stitching", "optifine");
        disableIfModPresent("mixin.bugfix.entity_pose_stack", "optifine");
        disableIfModPresent("mixin.perf.datapack_reload_exceptions", "cyanide");
        disableIfModPresent("mixin.bugfix.buffer_builder_leak", "isometric-renders", "witherstormmod");
        disableIfModPresent("mixin.feature.remove_chat_signing", "nochatreports");
        disableIfModPresent("mixin.perf.faster_texture_loading", "stitch", "optifine", "changed");
        if(isFabric) {
            disableIfModPresent("mixin.bugfix.packet_leak", "memoryleakfix");
        }

        checkBlockstateCacheRebuilds();
        checkModelDataManager();
    }

    private void checkBlockstateCacheRebuilds() {
        if(!ModernFixPlatformHooks.INSTANCE.isDevEnv())
            return;
        try {
            URL deobfClass = isFabric ?
                    ModernFixEarlyConfig.class.getResource("/net/minecraft/world/level/Level.class") :
                    ModernFixEarlyConfig.class.getClassLoader().getResource("/net/minecraft/world/level/Level.class");
            if(deobfClass == null) {
                LOGGER.warn("We are in a non-Mojmap dev environment. Disabling blockstate cache patch");
                this.options.get("mixin.perf.reduce_blockstate_cache_rebuilds").addModOverride(false, "[not mojmap]");
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    private void checkModelDataManager() {
        if(!isFabric && modPresent("rubidium") && !modPresent("embeddium")) {
            Option option = this.options.get("mixin.bugfix.model_data_manager_cme");
            if(option != null) {
                LOGGER.warn("ModelDataManager bugfixes have been disabled to prevent broken rendering with Rubidium installed. Please migrate to Embeddium.");
                option.addModOverride(false, "rubidium");
            }
        }
    }

    private void disableIfModPresent(String configName, String... ids) {
        for(String id : ids) {
            if(!ModernFixPlatformHooks.INSTANCE.isEarlyLoadingNormally() || modPresent(id)) {
                Option option = this.options.get(configName);
                if(option != null)
                    option.addModOverride(false, id);
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

    private void readJVMProperties() {
        for(String optionKey : this.options.keySet()) {
            String value = System.getProperty("modernfix.config." + optionKey);
            if(value == null || value.length() == 0)
                continue;
            boolean isEnabled = Boolean.valueOf(value);
            ModernFixMixinPlugin.instance.logger.info("Configured {} to '{}' via JVM property.", optionKey, isEnabled);
            this.options.get(optionKey).setEnabled(isEnabled, true);
        }
    }

    private void readProperties(Properties props) {
        if(ALLOW_OVERRIDE_OVERRIDES)
            LOGGER.fatal("JVM argument given to override mod overrides. Issues opened with this option present will be ignored unless they can be reproduced without.");

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

            if(ALLOW_OVERRIDE_OVERRIDES || !option.isModDefined())
                option.setEnabled(enabled, true);
            else
                LOGGER.warn("Option '{}' already disabled by a mod. Ignoring user configuration", key);
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
        if(!Boolean.getBoolean("modernfix.ignoreConfigForTesting")) {
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

            config.readJVMProperties();
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
            writer.write("# In general, prefer using the config screen to editing this file. It can be accessed\n");
            writer.write("# via the standard mod menu on your respective mod loader. Changes will, however,\n");
            writer.write("# require restarting the game to take effect.\n");
            writer.write("#\n");
            writer.write("# The following options can be enabled or disabled if there is a compatibility issue.\n");
            writer.write("# Add a line with your option name and =true or =false at the bottom of the file to enable\n");
            writer.write("# or disable a rule. For example:\n");
            writer.write("#   mixin.perf.dynamic_resources=true\n");
            writer.write("# Do not include the #. You may reset to defaults by deleting this file.\n");
            writer.write("#\n");
            writer.write("# Available options:\n");
            List<String> keys = this.options.keySet().stream()
                    .filter(key -> !key.equals("mixin.core"))
                    .sorted()
                    .collect(Collectors.toList());
            for(String line : keys) {
                if(!line.equals("mixin.core")) {
                    Option option = this.options.get(line);
                    String extraContext = "";
                    if(option != null) {
                        if(!option.isUserDefined())
                            extraContext = "=" + option.isEnabled() + " # " + (option.isModDefined() ? "(overridden for mod compat)" : "(default)");
                        else {
                            boolean defaultEnabled = DEFAULT_SETTING_OVERRIDES.getOrDefault(line, true);
                            extraContext = "=" + defaultEnabled + " # (default)";
                        }
                    }
                    writer.write("#  " + line + extraContext + "\n");
                }
            }

            writer.write("#\n");
            writer.write("# User overrides go here.\n");

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

    public Multimap<String, Option> getOptionCategoryMap() {
        return Multimaps.unmodifiableMultimap(this.optionsByCategory);
    }
}
