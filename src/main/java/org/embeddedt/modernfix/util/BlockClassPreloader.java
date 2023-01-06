package org.embeddedt.modernfix.util;

import com.google.common.base.Stopwatch;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.embeddedt.modernfix.ModernFix;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class BlockClassPreloader {
    public static void preloadClasses() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        ModernFix.LOGGER.warn("Preparing to preload classes...");
        HashMap<Type, Boolean> isABlockClass = new HashMap<>();
        isABlockClass.put(Type.getType(AbstractBlock.class), true);
        isABlockClass.put(Type.getType(Block.class), true);
        Field selfField, parentField;
        List<CompletableFuture> futures = new ArrayList<>();
        try {
            selfField = ModFileScanData.ClassData.class.getDeclaredField("clazz");
            selfField.setAccessible(true);
            parentField = ModFileScanData.ClassData.class.getDeclaredField("parent");
            parentField.setAccessible(true);
            List<ModFileScanData.ClassData> currentCandidates = ModList.get().getModFiles().stream()
                    .map(ModFileInfo::getFile)
                    .map(ModFile::getScanResult)
                    .flatMap(data -> data.getClasses().stream())
                    .collect(Collectors.toList());
            HashSet<Type> blockClasses = new HashSet<>();
            blockClasses.add(Type.getType(AbstractBlock.class));
            HashSet<Type> nonBlockClasses = new HashSet<>();
            int previousSize = -1;
            nonBlockClasses.add(Type.getType(Object.class));
            currentCandidates.removeIf(clz -> {
                Type self;
                try {
                    self = (Type)selfField.get(clz);
                } catch(ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                return (nonBlockClasses.contains(self) || blockClasses.contains(self));
            });
            while(blockClasses.size() > previousSize && currentCandidates.size() > 0) {
                previousSize = blockClasses.size();
                currentCandidates.removeIf(clz -> {
                    Type parent, self;
                    try {
                        parent = (Type)parentField.get(clz);
                        self = (Type)selfField.get(clz);
                    } catch(ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                    if(nonBlockClasses.contains(parent)) {
                        nonBlockClasses.add(self);
                        return true;
                    } else if(blockClasses.contains(parent)) {
                        blockClasses.add(self);
                        futures.add(CompletableFuture.runAsync(() -> {
                            if(self.getClassName().toLowerCase(Locale.ROOT).contains("mixin"))
                                return;
                            try {
                                Class.forName(self.getClassName());
                            } catch(Throwable e) {
                                ModernFix.LOGGER.warn("Couldn't load " + self.getClassName(), e);
                            }
                        }, ModWorkManager.parallelExecutor()));
                        return true;
                    } else
                        return false;
                });
            }
            futures.forEach(CompletableFuture::join);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            ModernFix.LOGGER.warn("Preloading classes took " + stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
            stopwatch.stop();
        }
    }
}
