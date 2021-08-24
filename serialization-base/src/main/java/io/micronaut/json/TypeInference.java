package io.micronaut.json;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;

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
        Type parameterization = GenericTypeUtils.findParameterization(targetType, GenericTypeUtils.getErasure(freeType));
        if (parameterization == null) {
            return null;
        }
        return inferExact(freeType, parameterization);
    }

    private static boolean hasFreeVariables(Type[] types) {
        for (Type type : types) {
            if (hasFreeVariables(type)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasFreeVariables(@Nullable Type type) {
        if (type instanceof TypeVariable) {
            return true;
        } else if (type instanceof GenericArrayType) {
            return hasFreeVariables(((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            return hasFreeVariables(((ParameterizedType) type).getOwnerType()) ||
                    hasFreeVariables(((ParameterizedType) type).getRawType()) ||
                    hasFreeVariables(((ParameterizedType) type).getActualTypeArguments());
        } else if (type instanceof WildcardType) {
            return hasFreeVariables(((WildcardType) type).getUpperBounds()) || hasFreeVariables(((WildcardType) type).getLowerBounds());
        } else {
            return false;
        }
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
            if (target instanceof Class<?> && ((Class<?>) target).isArray()) {
                return inferRecursive(inferred, ((GenericArrayType) generic).getGenericComponentType(), ((Class<?>) target).getComponentType());
            }

            return target instanceof GenericArrayType &&
                    inferRecursive(inferred, ((GenericArrayType) generic).getGenericComponentType(), ((GenericArrayType) target).getGenericComponentType());
        } else if (generic instanceof Class<?>) {
            return generic == target;
        } else {
            throw new UnsupportedOperationException(generic.getClass().getName());
        }
    }

}
