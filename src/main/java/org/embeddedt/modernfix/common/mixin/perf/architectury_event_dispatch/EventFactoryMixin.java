package org.embeddedt.modernfix.common.mixin.perf.architectury_event_dispatch;

import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Pseudo
@Mixin(targets = { "dev/architectury/event/EventFactory" }, remap = false)
@RequiresMod("architectury")
public class EventFactoryMixin {
    @Unique
    private static final MethodType MFIX_INVOKER_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);

    @Unique
    private static final Map<Method, MethodHandle> MFIX_INVOKERS = new ConcurrentHashMap<>();

    /**
     * @author embeddedt
     * @reason avoid building a new method handle for every listener call
     */
    @Overwrite
    private static Object invokeMethod(Object listener, Method method, Object[] args) throws Throwable {
        MethodHandle invoker = MFIX_INVOKERS.get(method);
        if (invoker == null) {
            // Fixed arity: for varargs methods the proxy passes the trailing array as a single element of args.
            invoker = MethodHandles.lookup().unreflect(method)
                    .asFixedArity()
                    .asSpreader(Object[].class, method.getParameterCount())
                    .asType(MFIX_INVOKER_TYPE);
            MethodHandle existing = MFIX_INVOKERS.putIfAbsent(method, invoker);
            if (existing != null) {
                invoker = existing;
            }
        }
        return (Object) invoker.invokeExact(listener, args);
    }
}
