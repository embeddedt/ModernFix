package org.embeddedt.modernfix.dfu;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.embeddedt.modernfix.ModernFix;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public class DFUBlaster {
    public static void blastMaps() {
        Cache<Pair<IntFunction<RewriteResult<?, ?>>, Integer>, RewriteResult<?, ?>> hmapApplyCache = CacheBuilder.newBuilder()
                .maximumSize(200) /* should mean approximately 50MB used max */
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .build();
        Cache<Triple<Type<?>, TypeRewriteRule, PointFreeRule>, Optional<? extends RewriteResult<?, ?>>> rewriteCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(3, TimeUnit.MINUTES)
                .build();
        try {
            Class<?> FOLD_CLASS = Class.forName("com.mojang.datafixers.functions.Fold");
            Field hmapField = FOLD_CLASS.getDeclaredField("HMAP_APPLY_CACHE");
            hmapField.setAccessible(true);
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe)theUnsafe.get(null);
            Object base = unsafe.staticFieldBase(hmapField);
            long offset = unsafe.staticFieldOffset(hmapField);
            unsafe.putObject(base, offset, hmapApplyCache.asMap());
            Field rewriteCacheField = Type.class.getDeclaredField("REWRITE_CACHE");
            rewriteCacheField.setAccessible(true);
            base = unsafe.staticFieldBase(rewriteCacheField);
            offset = unsafe.staticFieldOffset(rewriteCacheField);
            unsafe.putObject(base, offset, rewriteCache.asMap());
        } catch(Throwable e) {
            ModernFix.LOGGER.error("Could not replace DFU map", e);
        }
    }
}
