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
            if (component == null) {
                // erased type
                return Object[].class;
            } else if (component instanceof Class<?>) {
                return Array.newInstance((Class<?>) component, 0).getClass();
            } else {
                return new GenericArrayTypeImpl(component);
            }
        } else if (into instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) into;
            Type owner = t.getOwnerType() == null ? null : foldTypeVariables(t.getOwnerType(), fold);
            Type raw = foldTypeVariables(t.getRawType(), fold);
            Type[] args = Arrays.stream(t.getActualTypeArguments()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            if (Arrays.asList(args).contains(null)) {
                // erased type
                return raw;
            } else {
                return new ParameterizedTypeImpl(owner, (Class<?>) raw, args);
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
                return new WildcardTypeImpl(upper, lower);
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
     * @param on The type to look on for the parameterization
     * @param of The raw type to look for
     * @return One of: A {@link ParameterizedType} with the raw type being {@code of}, the original value of {@code of}
     * if {@code on} only implements {@code of} as a raw type, or {@code null} if {@code on} does not implement
     * {@code of}.
     */
    @Nullable
    public static Type findParameterization(Type on, Class<?> of) {
        if (on instanceof GenericArrayType) {
            if (of == Object.class || of == Cloneable.class || of == Serializable.class) {
                return of;
            }
            if (!of.isArray()) {
                return null;
            }
            Type componentType = ((GenericArrayType) on).getGenericComponentType();
            if (componentType instanceof TypeVariable) {
                if (isAssignableFrom(of.getComponentType(), componentType)) {
                    return on;
                } else {
                    return null;
                }
            } else {
                Type componentParameterization = findParameterization(componentType, of.getComponentType());
                return componentParameterization == null ? null : new GenericArrayTypeImpl(componentParameterization);
            }
        } else if (on instanceof ParameterizedType) {
            ParameterizedType onT = (ParameterizedType) on;
            Class<?> rawType = (Class<?>) onT.getRawType();
            if (rawType == of) {
                return onT;
            } else {
                Map<TypeVariable<?>, Type> typesToFold = new HashMap<>();
                findFoldableTypes(typesToFold, onT);
                return findParameterization(rawType, t -> foldTypeVariables(t, typesToFold::get), of);
            }
        } else if (on instanceof TypeVariable<?>) {
            for (Type bound : ((TypeVariable<?>) on).getBounds()) {
                Type boundParameterization = findParameterization(bound, of);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (on instanceof WildcardType) {
            for (Type upperBound : ((WildcardType) on).getUpperBounds()) {
                Type boundParameterization = findParameterization(upperBound, of);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (on instanceof Class<?>) {
            if (on == of) {
                return on;
            } else {
                // replace any type variables with raw types
                return findParameterization((Class<?>) on, t -> foldTypeVariables(t, v -> null), of);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported type for resolution: " + on.getClass());
        }
    }

    private static Type findParameterization(Class<?> on, Function<Type, Type> foldFunction, Class<?> of) {
        // special case Object, because Object is also a supertype of interfaces but does not appear in
        // getGenericSuperclass for those
        if (of == Object.class && !on.isPrimitive()) {
            return Object.class;
        }
        if (of.isInterface()) {
            for (Type itf : on.getGenericInterfaces()) {
                Type parameterization = findParameterization(foldFunction.apply(itf), of);
                if (parameterization != null) {
                    return parameterization;
                }
            }
        }
        if (on.getGenericSuperclass() != null) {
            return findParameterization(foldFunction.apply(on.getGenericSuperclass()), of);
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
            if (right instanceof ParameterizedType) {
                return (!isInnerClass((Class<?>) ((ParameterizedType) left).getRawType()) || typesEqual(((ParameterizedType) left).getOwnerType(), ((ParameterizedType) right).getOwnerType())) &&
                        typesEqual(((ParameterizedType) left).getRawType(), ((ParameterizedType) right).getRawType()) &&
                        typesEqual(((ParameterizedType) left).getActualTypeArguments(), ((ParameterizedType) right).getActualTypeArguments());
            } else {
                return false;
            }
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
            return new ParameterizedTypeImpl(
                    null,
                    rawType,
                    typeVariables.values().stream().map(GenericTypeUtils::argumentToReflectType).toArray(Type[]::new)
            );
        }
    }

    private static class WildcardTypeImpl implements WildcardType {
        private final Type[] upper;
        private final Type[] lower;

        public WildcardTypeImpl(@NonNull Type[] upper, @NonNull Type[] lower) {
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
            return new ParameterizedTypeImpl(owner, cl, typeParameters);
        }
    }

    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type owner;
        private final Type raw;
        private final Type[] args;

        public ParameterizedTypeImpl(@Nullable Type owner, Class<?> raw, Type[] args) {
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

    private static class GenericArrayTypeImpl implements GenericArrayType {
        private final Type component;

        public GenericArrayTypeImpl(@NonNull Type component) {
            if (component instanceof Class<?>) {
                throw new IllegalArgumentException("GenericArrayType of must not have a non-generic component type");
            }
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
}
