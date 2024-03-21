package org.embeddedt.modernfix.util;

import com.mojang.datafixers.util.Either;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public class EitherUtil {
    private static final Class<?> LEFT, RIGHT;
    private static final MethodHandle LEFT_VAL, RIGHT_VAL;

    static {
        try {
            LEFT = Class.forName("com.mojang.datafixers.util.Either$Left");
            RIGHT = Class.forName("com.mojang.datafixers.util.Either$Right");
            Field lvalue = LEFT.getDeclaredField("value");
            lvalue.setAccessible(true);
            Field rvalue = RIGHT.getDeclaredField("value");
            rvalue.setAccessible(true);
            LEFT_VAL = MethodHandles.publicLookup().unreflectGetter(lvalue).asType(MethodType.methodType(Object.class, Either.class));
            RIGHT_VAL = MethodHandles.publicLookup().unreflectGetter(rvalue).asType(MethodType.methodType(Object.class, Either.class));
        } catch(ReflectiveOperationException e) {
            throw new AssertionError("Failed to hook DFU Either", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <L, R> L leftOrNull(Either<L, R> either) {
        if(either.getClass() == LEFT) {
            try {
                return (L)LEFT_VAL.invokeExact(either);
            } catch(Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <L, R> R rightOrNull(Either<L, R> either) {
        if(either.getClass() == RIGHT) {
            try {
                return (R)RIGHT_VAL.invokeExact(either);
            } catch(Throwable e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
