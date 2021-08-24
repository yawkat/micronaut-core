/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.reflect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Utility methods for dealing with generic types via reflection. Generally reflection is to be avoided in Micronaut. Hence
 * this class is regarded as internal and used for only certain niche cases.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GenericTypeUtils {
    private static final Class<?> RECORD_CLASS;

    static {
        Class<?> cl;
        try {
            cl = Class.forName("java.lang.Record");
        } catch (ClassNotFoundException e) {
            cl = null;
        }
        RECORD_CLASS = cl;
    }

    /**
     * Resolves a single generic type argument for the given field.
     *
     * @param field The field
     * @return The type argument or {@link Optional#empty()}
     */
    public static Optional<Class> resolveGenericTypeArgument(Field field) {
        Type genericType = field != null ? field.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length > 0) {
                Type typeArg = typeArguments[0];
                return resolveParameterizedTypeArgument(typeArg);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve all of the type arguments for the given interface from the given type. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveInterfaceTypeArguments(Class<?> type, Class<?> interfaceType) {
        Optional<Type> resolvedType = getAllGenericInterfaces(type)
                .stream()
                .filter(t -> {
                            if (t instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) t;
                                return pt.getRawType() == interfaceType;
                            }
                            return false;
                        }
                )
                .findFirst();
        return resolvedType.map(GenericTypeUtils::resolveTypeArguments)
                .orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    /**
     * Resolve all of the type arguments for the given super type from the given type.
     *
     * @param type      The type to resolve from
     * @param superTypeToResolve The suepr type to resolve from
     * @return The type arguments to the interface
     */
    public static Class[] resolveSuperTypeGenericArguments(Class<?> type, Class<?> superTypeToResolve) {
        Type supertype = type.getGenericSuperclass();
        Class<?> superclass = type.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            if (supertype instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) supertype;
                if (pt.getRawType() == superTypeToResolve) {
                    return resolveTypeArguments(supertype);
                }
            }

            supertype = superclass.getGenericSuperclass();
            superclass = superclass.getSuperclass();
        }
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    /**
     * Resolves a single generic type argument from the super class of the given type.
     *
     * @param type The type to resolve from
     * @return A single Class or null
     */
    public static Optional<Class> resolveSuperGenericTypeArgument(Class type) {
        try {
            Type genericSuperclass = type.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                return resolveSingleTypeArgument(genericSuperclass);
            }
            return Optional.empty();
        } catch (NoClassDefFoundError e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the type arguments for a generic type.
     *
     * @param genericType The generic type
     * @return The type arguments
     */
    public static Class[] resolveTypeArguments(Type genericType) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            typeArguments = resolveParameterizedType(pt);
        }
        return typeArguments;
    }

    /**
     * Resolves a single type argument from the given interface of the given class. Also
     * searches superclasses.
     *
     * @param type          The type to resolve from
     * @param interfaceType The interface to resolve for
     * @return The class or null
     */
    public static Optional<Class> resolveInterfaceTypeArgument(Class type, Class interfaceType) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if (pt.getRawType() == interfaceType) {
                    return resolveSingleTypeArgument(genericInterface);
                }
            }
        }
        Class superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return resolveInterfaceTypeArgument(superClass, interfaceType);
        }
        return Optional.empty();
    }

    /**
     * Resolve a single type from the given generic type.
     *
     * @param genericType The generic type
     * @return An {@link Optional} of the type
     */
        private static Optional<Class> resolveSingleTypeArgument(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if (actualTypeArguments.length == 1) {
                Type actualTypeArgument = actualTypeArguments[0];
                return resolveParameterizedTypeArgument(actualTypeArgument);
            }
        }
        return Optional.empty();
    }

    /**
     * @param actualTypeArgument The actual type argument
     * @return An optional with the resolved parameterized class
     */
    private static Optional<Class> resolveParameterizedTypeArgument(Type actualTypeArgument) {
        if (actualTypeArgument instanceof Class) {
            return Optional.of((Class) actualTypeArgument);
        }
        if (actualTypeArgument instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) actualTypeArgument;
            Type rawType = pt.getRawType();
            if (rawType instanceof Class) {
                return Optional.of((Class) rawType);
            }
        }
        return Optional.empty();
    }

    /**
     * @param aClass A class
     * @return All generic interfaces
     */
    private static Set<Type> getAllGenericInterfaces(Class<?> aClass) {
        Set<Type> interfaces = new LinkedHashSet<>();
        return populateInterfaces(aClass, interfaces);
    }

    /**
     * @param aClass     Some class
     * @param interfaces The interfaces
     * @return A set of interfaces
     */
    @SuppressWarnings("Duplicates")
    private static Set<Type> populateInterfaces(Class<?> aClass, Set<Type> interfaces) {
        Type[] theInterfaces = aClass.getGenericInterfaces();
        interfaces.addAll(Arrays.asList(theInterfaces));
        for (Type theInterface : theInterfaces) {
            if (theInterface instanceof Class) {
                Class<?> i = (Class<?>) theInterface;
                if (ArrayUtils.isNotEmpty(i.getGenericInterfaces())) {
                    populateInterfaces(i, interfaces);
                }
            }
        }
        if (!aClass.isInterface()) {
            Class<?> superclass = aClass.getSuperclass();
            while (superclass != null) {
                populateInterfaces(superclass, interfaces);
                superclass = superclass.getSuperclass();
            }
        }
        return interfaces;
    }

    private static Class[] resolveParameterizedType(ParameterizedType pt) {
        Class[] typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
        Type[] actualTypeArguments = pt.getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length > 0) {
            typeArguments = new Class[actualTypeArguments.length];
            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type actualTypeArgument = actualTypeArguments[i];
                Optional<Class> opt = resolveParameterizedTypeArgument(actualTypeArgument);
                if (opt.isPresent()) {
                    typeArguments[i] = opt.get();
                } else {
                    typeArguments = ReflectionUtils.EMPTY_CLASS_ARRAY;
                    break;
                }
            }
        }
        return typeArguments;
    }

    @Internal
    @Nullable
    public static Type foldTypeVariables(Type into, VariableFold fold) {
        if (into instanceof GenericArrayType) {
            Type component = foldTypeVariables(((GenericArrayType) into).getGenericComponentType(), fold);
            // erased type
            return component == null ? Object[].class : GenericTypeFactory.makeArrayType(component);
        } else if (into instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) into;
            Type owner = t.getOwnerType() == null ? null : foldTypeVariables(t.getOwnerType(), fold);
            Type raw = foldTypeVariables(t.getRawType(), fold);
            Type[] args = Arrays.stream(t.getActualTypeArguments()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            if (Arrays.asList(args).contains(null)) {
                // erased type
                return raw;
            } else {
                return GenericTypeFactory.makeParameterizedTypeWithOwner(owner, (Class<?>) raw, args);
            }
        } else if (into instanceof TypeVariable<?>) {
            return fold.apply((TypeVariable<?>) into);
        } else if (into instanceof WildcardType) {
            WildcardType t = (WildcardType) into;
            Type[] lower = Arrays.stream(t.getLowerBounds()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            Type[] upper = Arrays.stream(t.getUpperBounds()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            if (Arrays.asList(lower).contains(null) || Arrays.asList(upper).contains(null)) {
                // erase type
                return null;
            } else {
                return GenericTypeFactory.makeWildcardType(upper, lower);
            }
        } else if (into instanceof Class) {
            return into;
        } else {
            throw new UnsupportedOperationException("Unsupported type for folding: " + into.getClass());
        }
    }

    public static Class<?> getErasure(Type type) {
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

    @Internal
    @FunctionalInterface
    public interface VariableFold {
        /**
         * Fold the given type variable to a new type.
         *
         * @return The folded type, or {@code null} if the generic type this type variable was part of should be
         * replaced by a raw type.
         */
        @Nullable
        Type apply(@NonNull TypeVariable<?> variable);
    }

    /**
     * Find the parameterization of a raw type on a type. For example, if {@code on} is {@code List<String>}, and
     * {@code of} is {@code Iterable.class}, this method will return a type corresponding to {@code Iterable<String>}.
     *
     * @param child The type to look on for the parameterization
     * @param parent The raw type to look for
     * @return One of: A {@link ParameterizedType} with the raw type being {@code of}, the original value of {@code of}
     * if {@code on} only implements {@code of} as a raw type, or {@code null} if {@code on} does not implement
     * {@code of}.
     */
    @Nullable
    public static Type findParameterization(Type child, Class<?> parent) {
        if (child instanceof GenericArrayType) {
            if (parent == Object.class || parent == Cloneable.class || parent == Serializable.class) {
                return parent;
            }
            if (!parent.isArray()) {
                return null;
            }
            Type componentType = ((GenericArrayType) child).getGenericComponentType();
            if (componentType instanceof TypeVariable) {
                if (isAssignableFrom(parent.getComponentType(), componentType)) {
                    return child;
                } else {
                    return null;
                }
            } else {
                Type componentParameterization = findParameterization(componentType, parent.getComponentType());
                return componentParameterization == null ? null : GenericTypeFactory.makeArrayType(componentParameterization);
            }
        } else if (child instanceof ParameterizedType) {
            ParameterizedType onT = (ParameterizedType) child;
            Class<?> rawType = (Class<?>) onT.getRawType();
            if (rawType == parent) {
                return onT;
            } else {
                Map<TypeVariable<?>, Type> typesToFold = new HashMap<>();
                findFoldableTypes(typesToFold, onT);
                return findParameterization(rawType, t -> foldTypeVariables(t, typesToFold::get), parent);
            }
        } else if (child instanceof TypeVariable<?>) {
            for (Type bound : ((TypeVariable<?>) child).getBounds()) {
                Type boundParameterization = findParameterization(bound, parent);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (child instanceof WildcardType) {
            for (Type upperBound : ((WildcardType) child).getUpperBounds()) {
                Type boundParameterization = findParameterization(upperBound, parent);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (child instanceof Class<?>) {
            if (child == parent) {
                return child;
            } else {
                // replace any type variables with raw types
                return findParameterization((Class<?>) child, t -> foldTypeVariables(t, v -> null), parent);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported type for resolution: " + child.getClass());
        }
    }

    private static Type findParameterization(Class<?> child, Function<Type, Type> foldFunction, Class<?> parent) {
        if (child.isArray()) {
            if (parent == Serializable.class || parent == Cloneable.class) {
                return parent;
            } else if (parent.isArray()) {
                Type componentParameterization = findParameterization(child.getComponentType(), foldFunction, parent.getComponentType());
                return componentParameterization == null ? null : GenericTypeFactory.makeArrayType(componentParameterization);
            } else {
                return null;
            }
        }
        // special case Object, because Object is also a supertype of interfaces but does not appear in
        // getGenericSuperclass for those
        if (parent == Object.class && !child.isPrimitive()) {
            return Object.class;
        }
        if (parent.isInterface()) {
            for (Type itf : child.getGenericInterfaces()) {
                Type parameterization = findParameterization(foldFunction.apply(itf), parent);
                if (parameterization != null) {
                    return parameterization;
                }
            }
        }
        if (child.getGenericSuperclass() != null) {
            return findParameterization(foldFunction.apply(child.getGenericSuperclass()), parent);
        } else {
            return null;
        }
    }

    private static void findFoldableTypes(Map<TypeVariable<?>, Type> typesToFold, ParameterizedType parameterizedType) {
        TypeVariable<?>[] typeParameters = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        for (int i = 0; i < typeParameters.length; i++) {
            typesToFold.put(typeParameters[i], typeArguments[i]);
        }
        if (parameterizedType.getOwnerType() instanceof ParameterizedType) {
            findFoldableTypes(typesToFold, (ParameterizedType) parameterizedType.getOwnerType());
        }
    }

    public static boolean typesEqual(@Nullable Type left, @Nullable Type right) {
        if (left == right) {
            return true;
        } else if (left == null || right == null) {
            return false;
        } else if (left.equals(right)) {
            return true;
        } else if (left instanceof GenericArrayType) {
            return right instanceof GenericArrayType &&
                    typesEqual(((GenericArrayType) left).getGenericComponentType(), ((GenericArrayType) right).getGenericComponentType());
        } else if (left instanceof ParameterizedType) {
            return right instanceof ParameterizedType &&
                    (!isInnerClass((Class<?>) ((ParameterizedType) left).getRawType()) || typesEqual(((ParameterizedType) left).getOwnerType(), ((ParameterizedType) right).getOwnerType())) &&
                    typesEqual(((ParameterizedType) left).getRawType(), ((ParameterizedType) right).getRawType()) &&
                    typesEqual(((ParameterizedType) left).getActualTypeArguments(), ((ParameterizedType) right).getActualTypeArguments());
        } else if (left instanceof TypeVariable<?> || left instanceof Class<?>) {
            // covered in equals above
            return false;
        } else if (left instanceof WildcardType) {
            if (right instanceof WildcardType) {
                return typesEqual(((WildcardType) left).getLowerBounds(), ((WildcardType) right).getLowerBounds()) &&
                        typesEqual(((WildcardType) left).getUpperBounds(), ((WildcardType) right).getUpperBounds());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static final int HASH_CODE_RANDOMIZER = ThreadLocalRandom.current().nextInt();

    public static int typeHashCode(@Nullable Type type) {
        return typeHashCode0(type) ^ HASH_CODE_RANDOMIZER;
    }

    private static int typeHashCode0(@Nullable Type type) {
        if (type == null) {
            return 0;
        } else if (type instanceof GenericArrayType) {
            return 31 + typeHashCode0(((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            return 31 * 31 * 31 * 2 +
                    31 * 31 * typeHashCode0(isInnerClass((Class<?>) ((ParameterizedType) type).getRawType()) ? ((ParameterizedType) type).getOwnerType() : null) +
                    31 * typeHashCode0(((ParameterizedType) type).getRawType()) +
                    typeHashCode0(((ParameterizedType) type).getActualTypeArguments());
        } else if (type instanceof TypeVariable<?>) {
            return 31 * 31 * 3 +
                    31 * ((TypeVariable<?>) type).getGenericDeclaration().hashCode() +
                    type.getTypeName().hashCode();
        } else if (type instanceof WildcardType) {
            return 31 * 31 * 4 +
                    31 * typeHashCode0(((WildcardType) type).getUpperBounds()) +
                    typeHashCode0(((WildcardType) type).getLowerBounds());
        } else if (type instanceof Class) {
            return type.hashCode();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
    }

    private static int typeHashCode0(Type[] types) {
        int v = 1;
        for (Type t : types) {
            v = 31 * v + typeHashCode0(t);
        }
        return v;
    }

    private static boolean typesEqual(Type[] left, Type[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!typesEqual(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAssignableFrom(@NonNull Type to, @NonNull Type from) {
        return isAssignableFrom(to, from, false);
    }

    /**
     * @param antisymmetric If true, the relation of this method must be <i>antisymmetric</i>, meaning that there are
     *                      no two distinct types S, T so that {@code S <: T} and {@code T <: S}. Normal java assignment
     *                      rules are not antisymmetric, e.g. {@code List} and {@code List<String>} are assignable in
     *                      both directions.
     */
    @Internal
    public static boolean isAssignableFrom(@NonNull Type to, @NonNull Type from, boolean antisymmetric) {
        if (to instanceof GenericArrayType) {
            // antisymmetry invariant is maintained by the recursive call.
            if (from instanceof Class<?>) {
                return ((Class<?>) from).isArray() &&
                        isAssignableFrom(((GenericArrayType) to).getGenericComponentType(), ((Class<?>) from).getComponentType(), antisymmetric);
            } else {
                return from instanceof GenericArrayType &&
                        isAssignableFrom(((GenericArrayType) to).getGenericComponentType(), ((GenericArrayType) from).getGenericComponentType(), antisymmetric);
            }
        } else if (to instanceof ParameterizedType) {
            ParameterizedType toT = (ParameterizedType) to;
            Class<?> erasure = (Class<?>) toT.getRawType();
            // find the parameterization of the same type, if any exists.
            Type fromParameterization = findParameterization(from, erasure);
            if (fromParameterization == null) {
                // raw types aren't compatible
                return false;
            }
            if (fromParameterization instanceof Class<?>) {
                // in normal java rules, raw types are assignable to parameterized types
                // if we need antisymmetry, we don't allow assignment in this direction
                return !antisymmetric;
            }
            ParameterizedType fromParameterizationT = (ParameterizedType) fromParameterization;
            if (toT.getOwnerType() != null && isInnerClass(erasure)) {
                if (fromParameterizationT.getOwnerType() == null) {
                    return false;
                }
                if (!isAssignableFrom(toT.getOwnerType(), fromParameterizationT.getOwnerType(), antisymmetric)) {
                    return false;
                }
            }
            Type[] toArgs = toT.getActualTypeArguments();
            Type[] fromArgs = fromParameterizationT.getActualTypeArguments();
            for (int i = 0; i < toArgs.length; i++) {
                if (toArgs[i] instanceof WildcardType) {
                    if (!contains((WildcardType) toArgs[i], fromArgs[i])) {
                        return false;
                    }
                } else {
                    if (!typesEqual(toArgs[i], fromArgs[i])) {
                        return false;
                    }
                }
            }
            return true;
        } else if (to instanceof Class<?>) {
            // to is a raw type
            // wrt antisymmetry, if `from` is parameterized, we consider it assignable to `to`.
            return findParameterization(from, (Class<?>) to) != null;
        } else {
            throw new IllegalArgumentException("Not a valid assignment target: " + to);
        }
    }

    private static boolean contains(WildcardType wildcard, Type type) {
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (!isAssignableFrom(upperBound, type)) {
                return false;
            }
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (!isAssignableFrom(type, lowerBound)) {
                return false;
            }
        }
        return true;
    }

    public static Type argumentToReflectType(Argument<?> argument) {
        Class<?> rawType = argument.getType();
        Map<String, Argument<?>> typeVariables = argument.getTypeVariables();
        if (typeVariables.isEmpty()) {
            return rawType;
        } else {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(
                    null,
                    rawType,
                    typeVariables.values().stream().map(GenericTypeUtils::argumentToReflectType).toArray(Type[]::new)
            );
        }
    }

    public static boolean isInnerClass(Class<?> cls) {
        Class<?> enclosingClass = cls.getEnclosingClass();
        return enclosingClass != null &&
                !Modifier.isStatic(cls.getModifiers()) &&
                !cls.isInterface() &&
                !cls.isEnum() &&
                !enclosingClass.isInterface() &&
                // can't use isRecord, but this check should do at least for valid java code
                // isRecord does additional checks
                cls.getSuperclass() != RECORD_CLASS;
    }

    @Internal
    public static Type parameterizeWithFreeVariables(Class<?> cl) {
        Type owner = isInnerClass(cl) ? parameterizeWithFreeVariables(cl.getEnclosingClass()) : null;
        TypeVariable<? extends Class<?>>[] typeParameters = cl.getTypeParameters();
        if ((owner == null || owner instanceof Class) && typeParameters.length == 0) {
            return cl;
        } else {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(owner, cl, typeParameters);
        }
    }
}
