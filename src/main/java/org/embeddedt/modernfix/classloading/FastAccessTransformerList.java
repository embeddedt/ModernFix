package org.embeddedt.modernfix.classloading;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.accesstransformer.AccessTransformer;
import net.minecraftforge.accesstransformer.AccessTransformerEngine;
import net.minecraftforge.accesstransformer.INameHandler;
import net.minecraftforge.accesstransformer.Target;
import net.minecraftforge.accesstransformer.parser.AccessTransformerList;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.objectweb.asm.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class FastAccessTransformerList extends AccessTransformerList {
    private FastATMap accessTransformerMap;

    public static void attemptReplace() {
        AccessTransformerList masterList;
        FastAccessTransformerList myList = new FastAccessTransformerList();
        try {
            Field master = AccessTransformerEngine.class.getDeclaredField("masterList");
            master.setAccessible(true);
            masterList = (AccessTransformerList)master.get(AccessTransformerEngine.INSTANCE);
            Field transfomersMap = AccessTransformerList.class.getDeclaredField("accessTransformers");
            transfomersMap.setAccessible(true);
            Map<Target<?>, AccessTransformer> map = (Map<Target<?>, AccessTransformer>)transfomersMap.get(masterList);
            INameHandler nameHandler = ObfuscationReflectionHelper.getPrivateValue(AccessTransformerList.class, masterList, "nameHandler");
            myList.setNameHandler(nameHandler);
            myList.accessTransformerMap = new FastATMap(map);
            ObfuscationReflectionHelper.setPrivateValue(AccessTransformerList.class, myList, myList.accessTransformerMap, "accessTransformers");
            master.set(AccessTransformerEngine.INSTANCE, myList);
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean containsClassTarget(Type type) {
        return this.accessTransformerMap.containsType(type);
    }

    private static class FastATMap implements Map<Target<?>, AccessTransformer> {
        private final Map<Target<?>, AccessTransformer> delegate;
        private final Set<Type> allContainedTypes;

        public FastATMap(Map<Target<?>, AccessTransformer> delegate) {
            this.delegate = delegate;
            this.allContainedTypes = new ObjectOpenHashSet<>();
        }

        @Override
        public int size() {
            return this.delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object o) {
            return this.delegate.containsKey(o);
        }

        @Override
        public boolean containsValue(Object o) {
            return this.delegate.containsValue(o);
        }

        @Override
        public AccessTransformer get(Object o) {
            return this.delegate.get(o);
        }

        @Nullable
        @Override
        public AccessTransformer put(Target<?> target, AccessTransformer accessTransformer) {
            this.allContainedTypes.add(target.getASMType());
            return this.delegate.put(target, accessTransformer);
        }

        @Override
        public AccessTransformer remove(Object o) {
            if(o instanceof Target) {
                this.allContainedTypes.remove(((Target<?>)o).getASMType());
            }
            return this.delegate.remove(o);
        }

        @Override
        public void putAll(@NotNull Map<? extends Target<?>, ? extends AccessTransformer> map) {
            for(Target<?> key : map.keySet()) {
                this.allContainedTypes.add(key.getASMType());
            }
            this.delegate.putAll(map);
        }

        @Override
        public void clear() {
            this.allContainedTypes.clear();
            this.delegate.clear();
        }

        @NotNull
        @Override
        public Set<Target<?>> keySet() {
            return this.delegate.keySet();
        }

        @NotNull
        @Override
        public Collection<AccessTransformer> values() {
            return this.delegate.values();
        }

        @NotNull
        @Override
        public Set<Entry<Target<?>, AccessTransformer>> entrySet() {
            return this.delegate.entrySet();
        }

        public boolean containsType(Type type) {
            return this.allContainedTypes.contains(type);
        }
    }
}
