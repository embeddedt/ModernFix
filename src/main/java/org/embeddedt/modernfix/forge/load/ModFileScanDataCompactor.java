package org.embeddedt.modernfix.forge.load;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.embeddedt.modernfix.ModernFix;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

public class ModFileScanDataCompactor {
    private static final Field ANNOTATIONS_FIELD, CLASSES_FIELD;
    private static final ObjectOpenHashSet<Type> TYPES = new ObjectOpenHashSet<>();

    static {
        ANNOTATIONS_FIELD = tryGetField("annotations");
        CLASSES_FIELD = tryGetField("classes");
    }

    private static Field tryGetField(String name) {
        try {
            var f = ModFileScanData.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            ModernFix.LOGGER.error("Unable to access '{}' field on ModFileScanData", name, e);
            return null;
        }
    }

    public static void compact() {
        for (var file : ModList.get().getModFiles()) {
            var scanResult = file.getFile().getScanResult();
            try {
                compact(scanResult, file.getFile().getFileName());
            } catch (Throwable e) {
                ModernFix.LOGGER.error("An error occured while compacting {}", file.getFile().getFileName(), e);
            }
        }
        TYPES.clear();
        TYPES.trim();
    }

    private static void compact(ModFileScanData data, String fileName) {
        ObjectOpenHashSet<String> memberNames = new ObjectOpenHashSet<>();
        var annotationSet = data.getAnnotations().stream().filter(a -> {
            // Filter out annotation classes that no one is likely to look for
            String clzName = a.annotationType().getClassName();
            return !clzName.startsWith("kotlin.jvm.")
                    && !clzName.startsWith("scala.reflect.")
                    && !clzName.startsWith("org.spongepowered.asm.mixin.")
                    && !clzName.startsWith("com.llamalad7.mixinextras.")
                    && !clzName.contains("org.jetbrains.annotations.")
                    && !clzName.contains("javax.annotation.")
                    && !clzName.endsWith("kotlin.Metadata")
                    && !clzName.equals("net.minecraftforge.api.distmarker.OnlyIn");
        }).map(a -> new ModFileScanData.AnnotationData(
                TYPES.addOrGet(a.annotationType()),
                a.targetType(),
                TYPES.addOrGet(a.clazz()),
                memberNames.addOrGet(a.memberName()),
                a.annotationData().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> {
                    Object annValue = e.getValue();
                    if (annValue instanceof ArrayList<?> list) {
                        list.trimToSize();
                    }
                    return annValue;
                }))
        )).collect(ImmutableSet.toImmutableSet());
        if (annotationSet.size() < data.getAnnotations().size()) {
            ModernFix.LOGGER.debug("Removed {} unneeded annotations from file {}",
                    data.getAnnotations().size() - annotationSet.size(), fileName);
        }
        var classSet = data.getClasses().stream().map(c -> new ModFileScanData.ClassData(
                TYPES.addOrGet(c.clazz()),
                TYPES.addOrGet(c.parent()),
                c.interfaces().stream().map(TYPES::addOrGet).collect(ImmutableSet.toImmutableSet())
        )).collect(ImmutableSet.toImmutableSet());
        try {
            ANNOTATIONS_FIELD.set(data, annotationSet);
            CLASSES_FIELD.set(data, classSet);
        } catch (Exception e) {
            ModernFix.LOGGER.error("Error replacing fields on ModFileScanData", e);
        }
    }
}
