/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Representation of types as declared in source code.
 */
@Experimental
public abstract class SourceType {
    SourceType() {
    }

    /**
     * @return An array type with this type as the component.
     * @throws IllegalArgumentException if this is a {@link Wildcard}.
     */
    @NonNull
    public final Array createArrayType() {
        return new Array(this);
    }

    /**
     * The source code string representation of this type.
     *
     * @return The source type name.
     */
    @NonNull
    public final String getTypeName() {
        return getTypeName(null);
    }

    /**
     * The source code string representation of this type, relative to the given package. Classes in the given package
     * are returned as simple names instead of qualified names.
     *
     * @param packageRelative The package, or {@code null} to work identically to {@link #getTypeName()}.
     * @return The source type name.
     */
    @NonNull
    public abstract String getTypeName(@Nullable String packageRelative);

    /**
     * Run a fold operation on this type tree. First the fold is recursively applied on all member types (e.g. type
     * parameters) of this type, then on this type itself. Evaluation may be lazy.
     * <p>
     * For example, if this type is {@code List<? extends String>}, this method will return
     * {@code f(f(List)<f(? extends f(String))>)}.
     *
     * @param fold The fold function.
     * @return This type after the final fold operation.
     */
    public final SourceType foldTypes(Function<SourceType, SourceType> fold) {
        return fold.apply(foldTypes0(fold));
    }

    /**
     * Apply {@link #foldTypes} to all "member" types. For example, if this type is {@code List<? extends String>},
     * this method will return {@code f(List)<f(? extends f(String))>}. Note that the final, outer fold is done in
     * {@link #foldTypes}.
     */
    abstract SourceType foldTypes0(Function<SourceType, SourceType> fold);

    /**
     * <i>Eagerly</i> fold all type variables in this type expression. When the fold function returns {@code null}, the
     * type should be erased: E.g. if this type is {@code List<? extends T>}, and {@code fold} returns {@code null} for
     * {@code T}, this function should return {@code List}.
     */
    @Nullable
    abstract SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold);

    /**
     * Get the erasure of this type. Always either a {@link RawClass} or (non-generic) {@link Array}.
     *
     * @return The erased type.
     */
    @NonNull
    public abstract SourceType getErasure();

    /**
     * @return The {@link ClassElement} representing the {@link #getErasure() erasure} of this type.
     */
    public final ClassElement getErasureElement() {
        SourceType erasure = getErasure();
        if (erasure instanceof RawClass) {
            return ((RawClass) erasure).getClassElement();
        } else {
            return ((Array) erasure).getComponent().getErasureElement().toArray();
        }
    }

    /**
     * @return A set of all <i>free</i> variables of this type, variables that are not bound yet.
     */
    @NonNull
    public final Set<Variable> getFreeVariables() {
        Set<Variable> variables = new LinkedHashSet<>();
        visit(t -> {
            if (t instanceof Variable) {
                variables.add((Variable) t);
            }
            return true;
        });
        return variables;
    }

    /**
     * First visit this type, and if the predicate returns {@code true}, all the member types as well.
     */
    abstract void visit(Predicate<SourceType> predicate);

    /**
     * Find the parameterization of a raw type on a type. For example, if {@code this} is {@code List<String>}, and
     * {@code parent} is {@code Iterable.class}, this method will return a type corresponding to {@code Iterable<String>}.
     *
     * @param parent The raw type to look for. Must be either {@link RawClass} or {@link Array}.
     * @return One of: A {@link Parameterized} with the raw type being {@code parent}, the original value of
     * {@code parent} if {@code this} only implements {@code parent} as a raw type, or {@code null} if {@code this}
     * does not implement {@code parent}.
     */
    @Nullable
    public abstract SourceType findParameterization(SourceType parent);

    /**
     * @see Class#isAssignableFrom(Class)
     */
    public abstract boolean isAssignableFrom(SourceType from);

    private static boolean elementEquals(ClassElement a, ClassElement b) {
        return a.getRawSourceType().equals(b.getRawSourceType());
    }

    private static boolean elementEquals(MethodElement a, MethodElement b) {
        if (!elementEquals(a.getDeclaringType(), b.getDeclaringType())) {
            return false;
        }
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        if (!a.getDeclaredReturnSourceType().equals(b.getDeclaredReturnSourceType())) {
            return false;
        }
        ParameterElement[] paramsA = a.getParameters();
        ParameterElement[] paramsB = b.getParameters();
        if (paramsA.length != paramsB.length) {
            return false;
        }
        for (int i = 0; i < paramsA.length; i++) {
            if (!paramsA[i].getDeclaredSourceType().equals(paramsB[i].getDeclaredSourceType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Represents a non-array class or primitive type. {@link RawClass} has no further "member types" relevant for
     * {@link #equals equality}, {@link #foldTypes type folding}, and so on.
     * <p>
     * Note that while {@link RawClass} has various access methods e.g. for determining the
     * {@link #getSupertype() declared supertype}, they are not considered "member types".
     */
    public static abstract class RawClass extends SourceType {
        /**
         * @return The {@link ClassElement} for this type.
         */
        public abstract ClassElement getClassElement();

        /**
         * @return Type variables declared on this type.
         */
        @NonNull
        public abstract List<? extends Variable> getTypeVariables();

        /**
         * @return The <i>declared</i> supertype of this type, or {@code null} if there is no supertype (e.g.
         * interfaces, {@link Object}, primitives).
         */
        @Nullable
        public abstract SourceType getSupertype();

        /**
         * @return The <i>declared</i> supertype of this type, or an empty list if there are none.
         */
        @NonNull
        public abstract List<? extends SourceType> getInterfaces();

        /**
         * Equality is determined by having the same class name.
         */
        @Override
        public final boolean equals(Object o) {
            return o instanceof RawClass && getTypeName().equals(((RawClass) o).getTypeName());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getClassElement());
        }

        @NonNull
        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            ClassElement classElement = getClassElement();
            if (packageRelative != null && classElement.getPackageName().equals(packageRelative)) {
                return classElement.getName().substring(classElement.getName().lastIndexOf('.') + 1);
            } else {
                return classElement.getName();
            }
        }

        @Override
        public final String toString() {
            return "RawClass{" + getTypeName() + "}";
        }

        @Override
        final SourceType foldTypes0(Function<SourceType, SourceType> fold) {
            return this;
        }

        @Override
        final SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold) {
            return this;
        }

        @NonNull
        @Override
        public final SourceType getErasure() {
            return this;
        }

        @Override
        final void visit(Predicate<SourceType> predicate) {
            predicate.test(this);
        }

        boolean isInnerClass() {
            ClassElement classElement = getClassElement();
            Optional<ClassElement> enclosingType = classElement.getEnclosingType();
            return enclosingType.isPresent() &&
                    classElement.getModifiers().contains(ElementModifier.STATIC) &&
                    !classElement.isInterface() &&
                    !classElement.isEnum() &&
                    !enclosingType.get().isInterface() &&
                    !classElement.isRecord();
        }

        @Override
        public final boolean isAssignableFrom(SourceType from) {
            return from.findParameterization(this) != null;
        }

        @Nullable
        final SourceType findParameterization(SourceType.RawClass parent, Function<SourceType, SourceType> fold) {
            // special case Object, because Object is also a supertype of interfaces but does not appear in
            // getSupertype for those
            if (parent.getTypeName().equals("java.lang.Object") && !getClassElement().isPrimitive()) {
                return parent;
            }
            if (parent.getClassElement().isInterface()) {
                for (SourceType itf : getInterfaces()) {
                    SourceType parameterization = fold.apply(itf).findParameterization(parent);
                    if (parameterization != null) {
                        return parameterization;
                    }
                }
            }
            SourceType supertype = getSupertype();
            if (supertype != null) {
                return fold.apply(supertype).findParameterization(parent);
            } else {
                return null;
            }
        }

        @Nullable
        @Override
        public final SourceType findParameterization(SourceType parent) {
            if (this.equals(parent)) {
                return parent;
            } else {
                if (parent instanceof Array) {
                    return null;
                }
                // replace any type variables with raw types
                return findParameterization((RawClass) parent, t -> t.foldTypeVariablesEager(v -> null));
            }
        }
    }

    /**
     * Represents an array type, e.g. {@code String[]}, {@code int[]}, {@code List<String>[]}, {@code T[]}.
     */
    public static final class Array extends SourceType {
        @NonNull
        final SourceType component;

        Array(@NonNull SourceType component) {
            if (component instanceof Wildcard) {
                throw new IllegalArgumentException("Cannot create array of wildcard type");
            }

            this.component = component;
        }

        @Override
        public final boolean equals(Object o) {
            return o instanceof Array && component.equals(((Array) o).component);
        }

        @Override
        public final int hashCode() {
            return Objects.hash(component);
        }

        @NonNull
        public SourceType getComponent() {
            return component;
        }

        @NonNull
        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            return component.getTypeName(packageRelative) + "[]";
        }

        @Override
        public final String toString() {
            return "Array{" + getTypeName() + "}";
        }

        @Override
        final SourceType foldTypes0(Function<SourceType, SourceType> fold) {
            return component.foldTypes(fold).createArrayType();
        }

        @Override
        final SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold) {
            SourceType component = this.component.foldTypeVariablesEager(fold);
            if (component == null) {
                component = ClassElement.of(Object.class).getRawSourceType();
            }
            return component.createArrayType();
        }

        @NonNull
        @Override
        public final SourceType getErasure() {
            return getComponent().getErasure().createArrayType();
        }

        @Override
        final void visit(Predicate<SourceType> predicate) {
            if (predicate.test(this)) {
                getComponent().visit(predicate);
            }
        }

        @Override
        public final boolean isAssignableFrom(SourceType from) {
            return from instanceof Array &&
                    component.isAssignableFrom(((Array) from).component);
        }

        @Nullable
        @Override
        public final SourceType findParameterization(SourceType parent) {
            if (parent instanceof RawClass) {
                String typeName = parent.getTypeName();
                if (typeName.equals(Object.class.getName()) || typeName.equals(Cloneable.class.getName()) || typeName.equals(Serializable.class.getName())) {
                    return parent;
                }
            }
            if (!(parent instanceof Array)) {
                return null;
            }

            SourceType component = getComponent();
            SourceType parentComponent = ((Array) parent).getComponent();
            if (component instanceof Variable) {
                if (parentComponent.isAssignableFrom(component)) {
                    return component;
                } else {
                    return null;
                }
            } else {
                SourceType componentParameterization = component.findParameterization(parentComponent);
                return componentParameterization == null ? null : componentParameterization.createArrayType();
            }
        }
    }

    /**
     * A parameterized type, e.g. {@code List<String>} or {@code Outer<Number>.Inner<String>}.
     */
    public abstract static class Parameterized extends SourceType {
        /**
         * If {@link #getRaw()} is an inner class, this can be the parameterized outer class. For
         * {@code Outer<Number>.Inner<String>}, this method returns {@code Outer<Number>}.
         *
         * @return The outer type, or {@code null} if not applicable.
         */
        @Nullable
        public abstract SourceType getOuter();

        /**
         * @return The raw class of this parameterized type, e.g. {@code List} for {@code List<String>}.
         */
        @NonNull
        public abstract RawClass getRaw();

        /**
         * @return The type parameters.
         */
        @NonNull
        public abstract List<? extends SourceType> getParameters();

        @Override
        public final boolean equals(Object obj) {
            return obj instanceof Parameterized &&
                    Objects.equals(getOuter(), ((Parameterized) obj).getOuter()) &&
                    getRaw().equals(((Parameterized) obj).getRaw()) &&
                    getParameters().equals(((Parameterized) obj).getParameters());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getOuter(), getRaw(), getParameters());
        }

        @NonNull
        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            StringBuilder builder = new StringBuilder();
            SourceType outer = getOuter();
            if (outer != null) {
                builder.append(outer.getTypeName(packageRelative));
            }
            builder.append(getRaw().getTypeName(packageRelative));
            builder.append('<');
            boolean firstParam = true;
            for (SourceType parameter : getParameters()) {
                if (!firstParam) {
                    builder.append(", ");
                }
                firstParam = false;
                builder.append(parameter.getTypeName(packageRelative));
            }
            builder.append('>');
            return builder.toString();
        }

        @Override
        public final String toString() {
            return "Parameterized{" + getTypeName() + "}";
        }

        @Override
        final SourceType foldTypes0(Function<SourceType, SourceType> fold) {
            Parameterized delegate = this;
            return new Parameterized() {
                @Nullable
                @Override
                public SourceType getOuter() {
                    SourceType outer = delegate.getOuter();
                    return outer == null ? null : outer.foldTypes(fold);
                }

                @NonNull
                @Override
                public RawClass getRaw() {
                    return (RawClass) delegate.getRaw().foldTypes(fold);
                }

                @NonNull
                @Override
                public List<? extends SourceType> getParameters() {
                    return delegate.getParameters().stream()
                            .map(p -> p.foldTypes(fold))
                            .collect(Collectors.toList());
                }
            };
        }

        @Override
        final SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold) {
            List<SourceType> foldedParams = getParameters().stream().map(t -> t.foldTypeVariablesEager(fold)).collect(Collectors.toList());
            if (foldedParams.contains(null)) {
                // erased type
                return getRaw();
            } else {
                Parameterized delegate = this;
                return new Parameterized() {
                    @Nullable
                    @Override
                    public SourceType getOuter() {
                        SourceType outer = delegate.getOuter();
                        return outer == null ? null : outer.foldTypeVariablesEager(fold);
                    }

                    @NonNull
                    @Override
                    public RawClass getRaw() {
                        return delegate.getRaw();
                    }

                    @NonNull
                    @Override
                    public List<? extends SourceType> getParameters() {
                        return foldedParams;
                    }
                };
            }
        }

        @NonNull
        @Override
        public final SourceType getErasure() {
            return getRaw();
        }

        @Override
        final void visit(Predicate<SourceType> predicate) {
            if (predicate.test(this)) {
                SourceType outer = getOuter();
                if (outer != null) {
                    getOuter().visit(predicate);
                }
                getRaw().visit(predicate);
                for (SourceType parameter : getParameters()) {
                    parameter.visit(predicate);
                }
            }
        }

        @Override
        public final boolean isAssignableFrom(SourceType from) {
            SourceType.RawClass erasure = getRaw();
            // find the parameterization of the same type, if any exists.
            SourceType fromParameterization = from.findParameterization(erasure);
            if (fromParameterization == null) {
                // raw types aren't compatible
                return false;
            }
            if (fromParameterization instanceof RawClass) {
                // in normal java rules, raw types are assignable to parameterized types
                return true;
            }
            Parameterized fromParameterizationT = (Parameterized) fromParameterization;
            SourceType toOuter = getOuter();
            if (toOuter != null && erasure.isInnerClass()) {
                SourceType fromOuter = fromParameterizationT.getOuter();
                if (fromOuter == null || !toOuter.isAssignableFrom(fromOuter)) {
                    return false;
                }
            }
            List<? extends SourceType> toArgs = getParameters();
            List<? extends SourceType> fromArgs = fromParameterizationT.getParameters();
            for (int i = 0; i < toArgs.size(); i++) {
                SourceType toArg = toArgs.get(i);
                if (toArg instanceof Wildcard) {
                    if (!((Wildcard) toArg).contains(fromArgs.get(i))) {
                        return false;
                    }
                } else if (!toArg.equals(fromArgs.get(i))) {
                    return false;
                }
            }
            return true;
        }

        @Nullable
        @Override
        public final SourceType findParameterization(SourceType parent) {
            if (parent instanceof Array) {
                return null;
            }

            RawClass raw = getRaw();
            if (raw.equals(parent)) {
                return this;
            } else {
                Map<Variable, SourceType> typesToFold = new HashMap<>();
                findFoldableTypes(typesToFold);
                return raw.findParameterization((RawClass) parent, (SourceType t) -> t.foldTypeVariablesEager(typesToFold::get));
            }
        }

        private void findFoldableTypes(Map<Variable, SourceType> typesToFold) {
            List<? extends Variable> typeVariables = getRaw().getTypeVariables();
            List<? extends SourceType> parameters = getParameters();
            for (int i = 0; i < typeVariables.size(); i++) {
                typesToFold.put(typeVariables.get(i), parameters.get(i));
            }
            SourceType outer = getOuter();
            if (outer instanceof Parameterized) {
                ((Parameterized) outer).findFoldableTypes(typesToFold);
            }
        }
    }

    /**
     * Unbound type variable.
     */
    public abstract static class Variable extends SourceType {
        /**
         * Element that declares this variable. Either a {@link MethodElement} or a {@link ClassElement}.
         *
         * @throws UnsupportedOperationException If the declaring element can't be determined.
         */
        @NonNull
        public abstract Element getDeclaringElement();

        /**
         * @return The name of the type variable, e.g. {@code T}.
         */
        @NonNull
        public abstract String getName();

        /**
         * @return Upper bounds of the type variable ({@code extends} clauses).
         */
        @NonNull
        public abstract List<? extends SourceType> getBounds();

        // bounds are not considered for eq/hc, they may be recursive

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Variable)) {
                return false;
            }
            Element declA = getDeclaringElement();
            Element declB = ((Variable) o).getDeclaringElement();
            if (declA instanceof ClassElement) {
                return declB instanceof ClassElement && SourceType.elementEquals((ClassElement) declA, (ClassElement) declB);
            }
            if (declA instanceof MethodElement) {
                return declB instanceof MethodElement && SourceType.elementEquals((MethodElement) declA, (MethodElement) declB);
            }
            throw new IllegalStateException("Illegal declaring element type: " + declA.getClass());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getDeclaringElement(), getName());
        }

        @NonNull
        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            return getName();
        }

        @Override
        public final String toString() {
            return "Variable{" + getTypeName() + "}";
        }

        @Override
        final SourceType foldTypes0(Function<SourceType, SourceType> fold) {
            return this;
        }

        @Nullable
        @Override
        final SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold) {
            return fold.apply(this);
        }

        @NonNull
        @Override
        public final SourceType getErasure() {
            return getBounds().get(0).getErasure();
        }

        @Override
        final void visit(Predicate<SourceType> predicate) {
            predicate.test(this);
        }

        @Override
        public final boolean isAssignableFrom(SourceType from) {
            throw new UnsupportedOperationException("Type variable not materialized");
        }

        @Nullable
        @Override
        public final SourceType findParameterization(SourceType parent) {
            for (SourceType bound : getBounds()) {
                SourceType parameterization = bound.findParameterization(parent);
                if (parameterization != null) {
                    return parameterization;
                }
            }
            return null;
        }
    }

    /**
     * Wildcard type, e.g. {@code ? extends CharSequence} or {@code ? super String}. Can only appear as a
     * {@link Parameterized#getParameters() type parameter}.
     */
    public abstract static class Wildcard extends SourceType {
        /**
         * @return Upper bounds of this wildcard, i.e. supertypes.
         */
        public abstract List<? extends SourceType> getUpperBounds();

        /**
         * @return Lower bounds of this wildcard, i.e. subtypes.
         */
        public abstract List<? extends SourceType> getLowerBounds();

        @Override
        public final boolean equals(Object o) {
            return o instanceof Wildcard &&
                    getUpperBounds().equals(((Wildcard) o).getUpperBounds()) &&
                    getLowerBounds().equals(((Wildcard) o).getLowerBounds());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getUpperBounds(), getLowerBounds());
        }

        @NonNull
        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            StringBuilder builder = new StringBuilder("?");
            List<? extends SourceType> upperBounds = getUpperBounds();
            if (!upperBounds.isEmpty()) {
                boolean first = true;
                for (SourceType upperBound : upperBounds) {
                    builder.append(first ? " extends " : " & ").append(upperBound.getTypeName(packageRelative));
                    first = false;
                }
            }
            List<? extends SourceType> lowerBounds = getLowerBounds();
            if (!lowerBounds.isEmpty()) {
                boolean first = true;
                for (SourceType upperBound : lowerBounds) {
                    builder.append(first ? " super " : " | ").append(upperBound.getTypeName(packageRelative));
                    first = false;
                }
            }
            return builder.toString();
        }

        @Override
        public final String toString() {
            return "Wildcard{" + getTypeName() + "}";
        }

        @Override
        final SourceType foldTypes0(Function<SourceType, SourceType> fold) {
            Wildcard delegate = this;
            return new Wildcard() {
                @Override
                public List<? extends SourceType> getUpperBounds() {
                    return delegate.getUpperBounds().stream()
                            .map(t -> t.foldTypes(fold))
                            .collect(Collectors.toList());
                }

                @Override
                public List<? extends SourceType> getLowerBounds() {
                    return delegate.getLowerBounds().stream()
                            .map(t -> t.foldTypes(fold))
                            .collect(Collectors.toList());
                }
            };
        }

        @Nullable
        @Override
        final SourceType foldTypeVariablesEager(Function<Variable, SourceType> fold) {
            List<SourceType> upper = getUpperBounds().stream().map(t -> t.foldTypeVariablesEager(fold)).collect(Collectors.toList());
            List<SourceType> lower = getLowerBounds().stream().map(t -> t.foldTypeVariablesEager(fold)).collect(Collectors.toList());
            if (upper.contains(null) || lower.contains(null)) {
                // erase type
                return null;
            } else {
                return new Wildcard() {
                    @Override
                    public List<? extends SourceType> getUpperBounds() {
                        return upper;
                    }

                    @Override
                    public List<? extends SourceType> getLowerBounds() {
                        return lower;
                    }
                };
            }
        }

        @NonNull
        @Override
        public final SourceType getErasure() {
            return getUpperBounds().get(0).getErasure();
        }

        @Override
        final void visit(Predicate<SourceType> predicate) {
            if (predicate.test(this)) {
                for (SourceType upperBound : getUpperBounds()) {
                    upperBound.visit(predicate);
                }
                for (SourceType lowerBound : getLowerBounds()) {
                    lowerBound.visit(predicate);
                }
            }
        }

        @Override
        public final boolean isAssignableFrom(SourceType from) {
            throw new UnsupportedOperationException("Wildcard is not assignable");
        }

        final boolean contains(SourceType type) {
            return getUpperBounds().stream().allMatch(upperBound -> upperBound.isAssignableFrom(type)) &&
                    getLowerBounds().stream().allMatch(type::isAssignableFrom);
        }

        @Nullable
        @Override
        public final SourceType findParameterization(SourceType parent) {
            for (SourceType bound : getUpperBounds()) {
                SourceType parameterization = bound.findParameterization(parent);
                if (parameterization != null) {
                    return parameterization;
                }
            }
            return null;
        }
    }
}
