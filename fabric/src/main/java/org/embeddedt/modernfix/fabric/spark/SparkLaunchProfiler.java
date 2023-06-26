package org.embeddedt.modernfix.fabric.spark;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.SparkPlugin;
import me.lucko.spark.common.command.sender.CommandSender;
import me.lucko.spark.common.platform.AbstractPlatformInfo;
import me.lucko.spark.common.platform.PlatformInfo;
import me.lucko.spark.common.sampler.Sampler;
import me.lucko.spark.common.sampler.ThreadDumper;
import me.lucko.spark.common.sampler.ThreadGrouper;
import me.lucko.spark.common.sampler.ThreadNodeOrder;
import me.lucko.spark.common.sampler.async.AsyncProfilerAccess;
import me.lucko.spark.common.sampler.async.AsyncSampler;
import me.lucko.spark.common.sampler.java.JavaSampler;
import me.lucko.spark.common.sampler.node.MergeMode;
import me.lucko.spark.common.util.MethodDisambiguator;
import me.lucko.spark.lib.adventure.text.Component;
import me.lucko.spark.lib.okhttp3.MediaType;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/* Inspired by CensoredASM */
public class SparkLaunchProfiler {
    private static PlatformInfo platformInfo = new ModernFixPlatformInfo();
    private static CommandSender commandSender = new ModernFixCommandSender();
    private static Map<String, Sampler> ongoingSamplers = new Object2ReferenceOpenHashMap<>();
    private static MediaType mediaType = MediaType.parse("application/x-spark-sampler");
    private static ExecutorService executor = Executors.newSingleThreadScheduledExecutor((new ThreadFactoryBuilder()).setNameFormat("spark-modernfix-async-worker").build());
    private static final SparkPlatform platform = new SparkPlatform(new ModernFixSparkPlugin());

    private static final boolean USE_JAVA_SAMPLER_FOR_LAUNCH = true; //Boolean.getBoolean("modernfix.profileLaunchWithJavaSampler");

    public static void start(String key) {
        if (!ongoingSamplers.containsKey(key)) {
            Sampler sampler;
            try {
                if(USE_JAVA_SAMPLER_FOR_LAUNCH) {
                    throw new UnsupportedOperationException();
                }
                AsyncProfilerAccess.INSTANCE.getProfiler();
                sampler = new AsyncSampler(4000, ThreadDumper.ALL, ThreadGrouper.BY_NAME);
            } catch (UnsupportedOperationException e) {
                sampler = new JavaSampler(4000, ThreadDumper.ALL, ThreadGrouper.BY_NAME, -1, true, true);
            }
            ongoingSamplers.put(key, sampler);
            ModernFixMixinPlugin.instance.logger.warn("Profiler has started for stage [{}]...", key);
            sampler.start();
        }
    }

    public static void stop(String key) {
        Sampler sampler = ongoingSamplers.remove(key);
        if (sampler != null) {
            sampler.stop();
            output(key, sampler);
        }
    }

    private static void output(String key, Sampler sampler) {
        executor.execute(() -> {
            ModernFixMixinPlugin.instance.logger.warn("Stage [{}] profiler has stopped! Uploading results...", key);
            byte[] output = sampler.formCompressedDataPayload(new Sampler.ExportProps(platformInfo, commandSender, ThreadNodeOrder.BY_TIME, "Stage: " + key, MergeMode.separateParentCalls(new MethodDisambiguator()), platform.getClassSourceLookup()));
            try {
                String urlKey = SparkPlatform.BYTEBIN_CLIENT.postContent(output, mediaType).key();
                String url = "https://spark.lucko.me/" + urlKey;
                ModernFixMixinPlugin.instance.logger.warn("Profiler results for Stage [{}]: {}", key, url);
            } catch (Exception e) {
                ModernFixMixinPlugin.instance.logger.fatal("An error occurred whilst uploading the results.", e);
            }
        });
    }

    static class ModernFixPlatformInfo extends AbstractPlatformInfo {

        @Override
        public Type getType() {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? Type.CLIENT : Type.SERVER;
        }

        @Override
        public String getName() {
            return "ModernFix";
        }

        private static String versionOfModContainer(Optional<ModContainer> containerOpt) {
            return containerOpt.map(container -> container.getMetadata().getVersion().toString()).orElse("unknown");
        }

        @Override
        public String getVersion() {
            return versionOfModContainer(FabricLoader.getInstance().getModContainer("modernfix"));
        }

        @Override
        public String getMinecraftVersion() {
            return versionOfModContainer(FabricLoader.getInstance().getModContainer("minecraft"));
        }
    }

    public static class ModernFixCommandSender implements CommandSender {

        private final UUID uuid = UUID.randomUUID();
        private final String name;

        public ModernFixCommandSender() {
            this.name = "ModernFix";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public boolean hasPermission(String s) {
            return true;
        }

        @Override
        public void sendMessage(Component component) {

        }
    }

    static class ModernFixSparkPlugin implements SparkPlugin {

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public Path getPluginDirectory() {
            return FabricLoader.getInstance().getGameDir().resolve("spark-modernfix");
        }

        @Override
        public String getCommandName() {
            return "spark-modernfix";
        }

        @Override
        public Stream<? extends CommandSender> getCommandSenders() {
            return Stream.of();
        }

        @Override
        public void executeAsync(Runnable runnable) {
            executor.execute(runnable);
        }

        @Override
        public PlatformInfo getPlatformInfo() {
            return platformInfo;
        }
    }
}
