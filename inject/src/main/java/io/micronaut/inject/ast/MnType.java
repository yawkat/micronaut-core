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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Internal
public abstract class MnType {
    MnType() {
    }

    public final Array getArrayType() {
        return new Array(this);
    }

    public final String getTypeName() {
        return getTypeName(null);
    }

    public abstract String getTypeName(@Nullable String packageRelative);

    /**
     * Run a fold operation on this type tree. First the fold is recursively applied on all member types (e.g. type
     * parameters) of this type, then on this type itself. Evaluation may be lazy.
     * <p>
     * For example, if this type is {@code List<? extends String>}, this method will return
     * {@code f(f(List)<f(? extends f(String))>)}.
     */
    public final MnType foldTypes(Function<MnType, MnType> fold) {
        return fold.apply(foldTypes0(fold));
    }

    abstract MnType foldTypes0(Function<MnType, MnType> fold);

    public abstract MnType getErasure();

    public final ClassElement getErasureElement() {
        MnType erasure = getErasure();
        if (erasure instanceof RawClass) {
            return ((RawClass) erasure).getClassElement();
        } else {
            return ((Array) erasure).getComponent().getErasureElement().toArray();
        }
    }

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

    abstract void visit(Predicate<MnType> predicate);

    private static boolean elementEquals(ClassElement a, ClassElement b) {
        return a.getRawMnType().equals(b.getRawMnType());
    }

    private static boolean elementEquals(MethodElement a, MethodElement b) {
        if (!elementEquals(a.getDeclaringType(), b.getDeclaringType())) {
            return false;
        }
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        if (!a.getMnReturnType().equals(b.getMnReturnType())) {
            return false;
        }
        ParameterElement[] paramsA = a.getParameters();
        ParameterElement[] paramsB = b.getParameters();
        if (paramsA.length != paramsB.length) {
            return false;
        }
        for (int i = 0; i < paramsA.length; i++) {
            if (!paramsA[i].getMnType().equals(paramsB[i].getMnType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean elementEquals(FieldElement a, FieldElement b) {
        return elementEquals(a.getDeclaringType(), b.getDeclaringType()) &&
                a.getName().equals(b.getName()) &&
                a.getMnType().equals(b.getMnType());
    }

    public static abstract class RawClass extends MnType {
        public abstract ClassElement getClassElement();

        public abstract List<? extends Variable> getTypeVariables();

        @Override
        public final boolean equals(Object o) {
            return o instanceof RawClass && getTypeName().equals(((RawClass) o).getTypeName());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getClassElement());
        }

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
        final MnType foldTypes0(Function<MnType, MnType> fold) {
            return this;
        }

        @Override
        public final MnType getErasure() {
            return this;
        }

        @Override
        final void visit(Predicate<MnType> predicate) {
            predicate.test(this);
        }
    }

    public static final class Array extends MnType {
        @NonNull
        final MnType component;

        Array(@NonNull MnType component) {
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
        public MnType getComponent() {
            return component;
        }

        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            return component.getTypeName(packageRelative) + "[]";
        }

        @Override
        public final String toString() {
            return "Array{" + getTypeName() + "}";
        }

        @Override
        final MnType foldTypes0(Function<MnType, MnType> fold) {
            return component.foldTypes(fold).getArrayType();
        }

        @Override
        public final MnType getErasure() {
            return getComponent().getErasure().getArrayType();
        }

        @Override
        final void visit(Predicate<MnType> predicate) {
            if (predicate.test(this)) {
                getComponent().visit(predicate);
            }
        }
    }

    public abstract static class Parameterized extends MnType {
        @Nullable
        public abstract MnType getOuter();

        @NonNull
        public abstract RawClass getRaw();

        @NonNull
        public abstract List<? extends MnType> getParameters();

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

        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            StringBuilder builder = new StringBuilder();
            MnType outer = getOuter();
            if (outer != null) {
                builder.append(outer.getTypeName(packageRelative));
            }
            builder.append(getRaw().getTypeName(packageRelative));
            builder.append('<');
            boolean firstParam = true;
            for (MnType parameter : getParameters()) {
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
        final MnType foldTypes0(Function<MnType, MnType> fold) {
            Parameterized delegate = this;
            return new Parameterized() {
                @Nullable
                @Override
                public MnType getOuter() {
                    MnType outer = delegate.getOuter();
                    return outer == null ? null : outer.foldTypes(fold);
                }

                @NonNull
                @Override
                public RawClass getRaw() {
                    return (RawClass) delegate.getRaw().foldTypes(fold);
                }

                @NonNull
                @Override
                public List<? extends MnType> getParameters() {
                    return delegate.getParameters().stream()
                            .map(p -> p.foldTypes(fold))
                            .collect(Collectors.toList());
                }
            };
        }

        @Override
        public final MnType getErasure() {
            return getRaw();
        }

        @Override
        final void visit(Predicate<MnType> predicate) {
            if (predicate.test(this)) {
                MnType outer = getOuter();
                if (outer != null) {
                    getOuter().visit(predicate);
                }
                getRaw().visit(predicate);
                for (MnType parameter : getParameters()) {
                    parameter.visit(predicate);
                }
            }
        }
    }

    public abstract static class Variable extends MnType {
        @NonNull
        public abstract Element getDeclaringElement();

        @NonNull
        public abstract String getName();

        @NonNull
        public abstract List<? extends MnType> getBounds();

        // bounds are not considered for eq/hc, they may be recursive

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Variable)) {
                return false;
            }
            Element declA = getDeclaringElement();
            Element declB = ((Variable) o).getDeclaringElement();
            if (declA instanceof ClassElement) {
                return declB instanceof ClassElement && MnType.elementEquals((ClassElement) declA, (ClassElement) declB);
            }
            if (declA instanceof MethodElement) {
                return declB instanceof MethodElement && MnType.elementEquals((MethodElement) declA, (MethodElement) declB);
            }
            throw new IllegalStateException("Illegal declaring element type: " + declA.getClass());
        }

        @Override
        public final int hashCode() {
            return Objects.hash(getDeclaringElement(), getName());
        }

        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            return getName();
        }

        @Override
        public final String toString() {
            return "Variable{" + getTypeName() + "}";
        }

        @Override
        final MnType foldTypes0(Function<MnType, MnType> fold) {
            return this;
        }

        @Override
        public final MnType getErasure() {
            return getBounds().get(0).getErasure();
        }

        @Override
        final void visit(Predicate<MnType> predicate) {
            predicate.test(this);
        }
    }

    public abstract static class Wildcard extends MnType {
        public abstract List<? extends MnType> getUpperBounds();

        public abstract List<? extends MnType> getLowerBounds();

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

        @Override
        public final String getTypeName(@Nullable String packageRelative) {
            StringBuilder builder = new StringBuilder("?");
            List<? extends MnType> upperBounds = getUpperBounds();
            if (!upperBounds.isEmpty()) {
                boolean first = true;
                for (MnType upperBound : upperBounds) {
                    builder.append(first ? " extends " : " & ").append(upperBound.getTypeName(packageRelative));
                    first = false;
                }
            }
            List<? extends MnType> lowerBounds = getLowerBounds();
            if (!lowerBounds.isEmpty()) {
                boolean first = true;
                for (MnType upperBound : lowerBounds) {
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
        final MnType foldTypes0(Function<MnType, MnType> fold) {
            Wildcard delegate = this;
            return new Wildcard() {
                @Override
                public List<? extends MnType> getUpperBounds() {
                    return delegate.getUpperBounds().stream()
                            .map(t -> t.foldTypes(fold))
                            .collect(Collectors.toList());
                }

                @Override
                public List<? extends MnType> getLowerBounds() {
                    return delegate.getLowerBounds().stream()
                            .map(t -> t.foldTypes(fold))
                            .collect(Collectors.toList());
                }
            };
        }

        @Override
        public final MnType getErasure() {
            return getUpperBounds().get(0).getErasure();
        }

        @Override
        final void visit(Predicate<MnType> predicate) {
            if (predicate.test(this)) {
                for (MnType upperBound : getUpperBounds()) {
                    upperBound.visit(predicate);
                }
                for (MnType lowerBound : getLowerBounds()) {
                    lowerBound.visit(predicate);
                }
            }
        }
    }
}
