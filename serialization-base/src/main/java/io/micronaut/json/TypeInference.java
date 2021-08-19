package io.micronaut.json;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.reflect.ReflectionUtils;

import java.lang.reflect.*;
import java.util.*;

class TypeInference {
    @Nullable
    static Map<TypeVariable<?>, Type> inferExact(Type freeType, Type targetType) {
        Map<TypeVariable<?>, Type> map = new HashMap<>();
        if (!inferRecursive(map, freeType, targetType)) {
            return null;
        }
        return map;
    }

    @Nullable
    static Map<TypeVariable<?>, Type> inferContravariant(Type freeType, Type targetType) {
        Class<?> targetErasure = getErasure(targetType);
        Type parameterization = GenericTypeUtils.findParameterization(freeType, targetErasure);
        if (parameterization == null) {
            return null;
        }
        return inferExact(freeType, parameterization);
    }

    private static boolean inferRecursive(Map<TypeVariable<?>, Type> inferred, Type[] generic, Type[] target) {
        if (generic.length != target.length) {
            return false;
        }
        for (int i = 0; i < generic.length; i++) {
            if (!inferRecursive(inferred, generic[i], target[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean inferRecursive(Map<TypeVariable<?>, Type> inferred, @Nullable Type generic, @Nullable Type target) {
        if (generic == null) {
            return target == null;
        } else if (generic instanceof TypeVariable<?>) {
            Type existing = inferred.put((TypeVariable<?>) generic, target);
            return existing == null || existing.equals(target);
        } else if (generic instanceof ParameterizedType) {
            return target instanceof ParameterizedType &&
                    ((ParameterizedType) generic).getRawType().equals(((ParameterizedType) target).getRawType()) &&
                    (!GenericTypeUtils.isInnerClass((Class<?>) ((ParameterizedType) generic).getRawType()) || inferRecursive(inferred, ((ParameterizedType) generic).getOwnerType(), ((ParameterizedType) target).getOwnerType())) &&
                    inferRecursive(inferred, ((ParameterizedType) generic).getActualTypeArguments(), ((ParameterizedType) target).getActualTypeArguments());
        } else if (generic instanceof WildcardType) {
            return target instanceof WildcardType &&
                    inferRecursive(inferred, ((WildcardType) generic).getUpperBounds(), ((WildcardType) target).getUpperBounds()) &&
                    inferRecursive(inferred, ((WildcardType) generic).getLowerBounds(), ((WildcardType) target).getLowerBounds());
        } else if (generic instanceof GenericArrayType) {
            return target instanceof GenericArrayType &&
                    inferRecursive(inferred, ((GenericArrayType) generic).getGenericComponentType(), ((GenericArrayType) target).getGenericComponentType());
        } else if (generic instanceof Class<?>) {
            return generic == target;
        } else {
            throw new UnsupportedOperationException(generic.getClass().getName());
        }
    }

    private static Class<?> getErasure(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance(getErasure(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof ParameterizedType) {
            return getErasure(((ParameterizedType) type).getRawType());
        } else if (type instanceof WildcardType) {
            return getErasure(((WildcardType) type).getUpperBounds()[0]);
        } else if (type instanceof TypeVariable) {
            return getErasure(((TypeVariable<?>) type).getBounds()[0]);
        } else {
            throw new UnsupportedOperationException(type.getClass().getName());
        }
    }
}
