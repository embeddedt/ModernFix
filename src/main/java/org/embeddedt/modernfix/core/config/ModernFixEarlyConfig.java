package org.embeddedt.modernfix.core.config;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ExplodedDirectoryLocator;
import net.minecraftforge.fml.loading.moddiscovery.MinecraftLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.IModProvider;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String MIXIN_CLIENT_ONLY_DESC = "Lorg/embeddedt/modernfix/annotation/ClientOnlyMixin;";
    private static final String MIXIN_REQUIRES_MOD_DESC = "Lorg/embeddedt/modernfix/annotation/RequiresMod;";

    private final Set<String> mixinOptions = new ObjectOpenHashSet<>();
    private final Map<String, String> mixinsMissingMods = new Object2ObjectOpenHashMap<>();

    public Map<String, String> getPermanentlyDisabledMixins() {
        return mixinsMissingMods;
    }

    private void scanForAndBuildMixinOptions() {
        IModFile file = FMLLoader.getLoadingModList().getModFileById("modernfix").getFile();
        Path mixinFolder = file.findResource("org", "embeddedt", "modernfix", "mixin");
        try(Stream<Path> mixinFiles = Files.find(mixinFolder, Integer.MAX_VALUE, (p, a) -> true)) {
            Splitter dotSplitter = Splitter.on('.');
            // filter via toString
            mixinFiles
                    .filter(p -> {
                        Path fileName = p.getFileName();
                        return fileName != null && fileName.toString().endsWith(".class");
                    })
                    .forEach(path -> {
                        try(InputStream stream = Files.newInputStream(path)) {
                            ClassReader reader = new ClassReader(stream);
                            ClassNode node = new ClassNode();
                            reader.accept(node,  ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                            if(node.invisibleAnnotations == null)
                                return;
                            boolean isMixin = false, isClientOnly = false, requiredModPresent = true;
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
                                                requiredModPresent = modPresent(modId);
                                                requiredModId = modId;
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            if(isMixin) {
                                String mixinClassName = node.name.replace("org/embeddedt/modernfix/mixin/", "").replace('/', '.');
                                if(!requiredModPresent)
                                    mixinsMissingMods.put(mixinClassName, requiredModId);
                                else if(isClientOnly && FMLLoader.getDist() != Dist.CLIENT)
                                    mixinsMissingMods.put(mixinClassName, "[not client]");
                                List<String> mixinOptionNames = dotSplitter.splitToList(mixinClassName);
                                StringBuilder optionBuilder = new StringBuilder(mixinClassName.length());
                                optionBuilder.append("mixin");
                                for(int i = 0; i < mixinOptionNames.size() - 1; i++) {
                                    optionBuilder.append('.');
                                    optionBuilder.append(mixinOptionNames.get(i));
                                    mixinOptions.add(optionBuilder.toString());
                                }
                            }
                        } catch(IOException e) {
                            ModernFix.LOGGER.error("Error scanning file " + path, e);
                        }
                    });
        } catch(IOException e) {
            ModernFix.LOGGER.error("Error scanning for mixins", e);
        }
    }

    private static final boolean shouldReplaceSearchTrees;
    private static final boolean isDevEnv = !FMLLoader.isProduction() && FMLLoader.getLoadingModList().getModFileById("modernfix").getFile().getProvider() instanceof ExplodedDirectoryLocator;;

    static {
        shouldReplaceSearchTrees = modPresent("jei");
    }

    private static final ImmutableMap<String, Boolean> DEFAULT_SETTING_OVERRIDES = ImmutableMap.<String, Boolean>builder()
            .put("mixin.perf.dynamic_resources", false)
            .put("mixin.feature.reduce_loading_screen_freezes", false)
            .put("mixin.feature.direct_stack_trace", false)
            .put("mixin.perf.rewrite_registry", false)
            .put("mixin.perf.clear_mixin_classinfo", false)
            .put("mixin.perf.compress_blockstate", false)
            .put("mixin.bugfix.packet_leak", false)
            .put("mixin.perf.deduplicate_location", false)
            .put("mixin.perf.preload_block_classes", false)
            .put("mixin.perf.faster_singleplayer_load", false)
            .put("mixin.perf.blast_search_trees", shouldReplaceSearchTrees)
            .put("mixin.devenv", isDevEnv)
            .put("mixin.perf.remove_spawn_chunks", isDevEnv)
            .build();

    private ModernFixEarlyConfig(File file) {
        this.configFile = file;

        this.scanForAndBuildMixinOptions();
        for(String optionName : mixinOptions) {
            boolean defaultEnabled = DEFAULT_SETTING_OVERRIDES.getOrDefault(optionName, true);
            this.options.putIfAbsent(optionName, new Option(optionName, defaultEnabled, false));
        }
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("launch.class_search_cache", true);
        /*
        this.addMixinRule("perf.use_integrated_resources.jepb", modPresent("jepb"));
        this.addMixinRule("perf.use_integrated_resources.jeresources", modPresent("jeresources"));
        this.addMixinRule("perf.jeresources_startup", modPresent("jeresources"));
        this.addMixinRule("perf.state_definition_construct", modPresent("ferritecore"));
        this.addMixinRule("bugfix.starlight_emptiness", modPresent("starlight"));
        this.addMixinRule("bugfix.chunk_deadlock.valhesia", modPresent("valhelsia_structures"));
        this.addMixinRule("bugfix.tf_cme_on_load", modPresent("twilightforest"));
        this.addMixinRule("bugfix.refinedstorage", modPresent("refinedstorage"));
        this.addMixinRule("perf.async_jei", modPresent("jei"));
        this.addMixinRule("perf.patchouli_deduplicate_books", modPresent("patchouli"));
        this.addMixinRule("perf.kubejs", modPresent("kubejs"));
         */

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
