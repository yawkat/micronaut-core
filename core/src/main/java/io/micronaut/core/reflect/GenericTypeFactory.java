package io.micronaut.core.reflect;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.lang.reflect.*;
import java.util.Objects;

public class GenericTypeFactory {
    private GenericTypeFactory() {}

    public static Type makeArrayType(@NonNull Type component) {
        Objects.requireNonNull(component, "component");

        if (component instanceof Class<?>) {
            return Array.newInstance((Class<?>) component, 0).getClass();
        } else if (component instanceof GenericArrayType ||
                component instanceof ParameterizedType ||
                component instanceof WildcardType ||
                component instanceof TypeVariable) {
            return new GenericArrayTypeImpl(component);
        } else {
            throw new IllegalArgumentException("Can't create an array type from " + component.getTypeName());
        }
    }

    private static class GenericArrayTypeImpl implements GenericArrayType {
        private final Type component;

        GenericArrayTypeImpl(@NonNull Type component) {
            this.component = component;
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    public static ParameterizedType makeParameterizedTypeWithOwner(@Nullable Type owner, @NonNull Class<?> raw, Type... args) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(args, "args");

        return new ParameterizedTypeImpl(owner, raw, args);
    }

    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type owner;
        private final Type raw;
        private final Type[] args;

        ParameterizedTypeImpl(@Nullable Type owner, Class<?> raw, Type[] args) {
            this.args = args;
            this.raw = raw;
            this.owner = owner == null ? raw.getEnclosingClass() : owner;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return args.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return owner;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (owner != null) {
                builder.append(owner.getTypeName()).append('.');
            }
            builder.append(raw.getTypeName()).append('<');
            for (int i = 0; i < args.length; i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(args[i].getTypeName());
            }
            builder.append('>');
            return builder.toString();
        }
    }

    public static WildcardType makeWildcardType(@NonNull Type[] upper, @NonNull Type[] lower) {
        Objects.requireNonNull(upper, "upper");
        Objects.requireNonNull(lower, "lower");
        return new WildcardTypeImpl(upper, lower);
    }

    private static class WildcardTypeImpl implements WildcardType {
        private final Type[] upper;
        private final Type[] lower;

        private WildcardTypeImpl(@NonNull Type[] upper, @NonNull Type[] lower) {
            this.upper = upper;
            this.lower = lower;
        }

        @Override
        public Type[] getUpperBounds() {
            return upper.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lower.clone();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("?");
            for (Type type : upper) {
                if (type != Object.class) {
                    builder.append(" extends ").append(type.getTypeName());
                }
            }
            for (Type type : lower) {
                builder.append(" super ").append(type.getTypeName());
            }
            return builder.toString();
        }
    }
}
