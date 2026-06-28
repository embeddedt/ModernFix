package org.embeddedt.modernfix.core.config;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public abstract class OptionType<T> {
    public static final OptionType<Boolean> BOOLEAN = new OptionType<Boolean>() {
        @Override
        public Boolean parse(String s) {
            if (s.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            } else if (s.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException(s);
            }
        }

        @Override
        public String serialize(Boolean value) {
            return value.toString();
        }
    };

    private static final ConcurrentHashMap<Class<? extends Enum<?>>, OptionType<? extends Enum<?>>> ENUM_TYPES = new ConcurrentHashMap<>();

    private OptionType() {

    }

    public abstract T parse(String s) throws IllegalArgumentException;
    public abstract String serialize(T value);

    private static <T extends Enum<T>> OptionType<T> createEnumType(Class<T> enumClass) {
        return new OptionType<>() {
            @Override
            public T parse(String s) throws IllegalArgumentException {
                return Enum.valueOf(enumClass, s.toUpperCase(Locale.ROOT));
            }

            @Override
            public String serialize(T value) {
                return value.name();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>> OptionType<T> enumType(Class<T> enumClass) {
        return (OptionType<T>)ENUM_TYPES.computeIfAbsent(enumClass, k -> createEnumType((Class<T>)k));
    }
}
