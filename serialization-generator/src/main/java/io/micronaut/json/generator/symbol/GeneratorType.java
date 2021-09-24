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
package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.*;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.GenericTypeFactory;
import io.micronaut.inject.ast.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Internal
public class GeneratorType {
    // TODO: temporary combined class. Replace either by MnType or by ClassElement in the future

    static final GeneratorType STRING = ofClass(ClassElement.of(String.class));

    /**
     * the type T[]
     */
    @Internal
    public static final GeneratorType GENERIC_ARRAY = new GeneratorType(ClassElement.of(GenericArrayHolder.class.getFields()[0].getGenericType()));

    @SuppressWarnings("unused")
    private static class GenericArrayHolder<T> {
        public T[] field;
    }

    private final ClassElement classElement;

    private GeneratorType(ClassElement classElement) {
        this.classElement = classElement;
    }

    public static GeneratorType ofClass(ClassElement raw) {
        if (raw.getBoundTypeArguments().isEmpty()) {
            raw = raw.bindTypeArguments(raw.getDeclaredTypeVariables());
        }
        return new GeneratorType(raw);
    }

    public static GeneratorType ofParameterized(Class<?> raw, Class<?>... params) {
        // null elements in the params array are allowed, those are treated as free variables

        if (params.length == 0) {
            ClassElement ele = ClassElement.of(raw);
            return new GeneratorType(ele);
        } else {
            Type[] paramsMapped = new Type[params.length];
            for (int i = 0; i < params.length; i++) {
                if (params[i] != null) {
                    paramsMapped[i] = params[i];
                } else {
                    paramsMapped[i] = raw.getTypeParameters()[i];
                }
            }
            return new GeneratorType(ClassElement.of(GenericTypeFactory.makeParameterizedTypeWithOwner(null, raw, paramsMapped)));
        }
    }

    public static GeneratorType fieldType(FieldElement element, Function<ClassElement, ClassElement> fold) {
        return new GeneratorType(element.getType().foldTypes(fold));
    }

    public static GeneratorType methodReturnType(MethodElement element, Function<ClassElement, ClassElement> fold) {
        return new GeneratorType(element.getGenericReturnType().foldTypes(fold));
    }

    public static GeneratorType parameterType(ParameterElement element, Function<ClassElement, ClassElement> fold) {
        return new GeneratorType(element.getGenericType().foldTypes(fold));
    }

    public ClassElement getClassElement() {
        return classElement;
    }

    public AnnotationMetadata getClassLevelAnnotations() {
        return classElement.getRawClass();
    }

    public Collection<FreeTypeVariableElement> getFreeVariables() {
        Map<String, FreeTypeVariableElement> freeVariables = new HashMap<>();
        getFreeVariables(freeVariables, classElement);
        return freeVariables.values();
    }

    private static void getFreeVariables(Map<String, FreeTypeVariableElement> freeVariables, ClassElement type) {
        while (type.isArray()) {
            type = type.fromArray();
        }
        if (type.isFreeTypeVariable()) {
            freeVariables.put(((FreeTypeVariableElement) type).getVariableName(), (FreeTypeVariableElement) type);
        } else if (type.isWildcard()) {
            for (ClassElement bound : ((WildcardElement) type).getUpperBounds()) {
                getFreeVariables(freeVariables, bound);
            }
            for (ClassElement bound : ((WildcardElement) type).getLowerBounds()) {
                getFreeVariables(freeVariables, bound);
            }
        } else {
            for (ClassElement arg : type.getBoundTypeArguments()) {
                getFreeVariables(freeVariables, arg);
            }
        }
    }

    public ClassElement getRawClass() {
        return classElement.getRawClass();
    }

    public boolean isArray() {
        return classElement.isArray();
    }

    public GeneratorType fromArray() {
        if (!classElement.isArray()) {
            throw new IllegalStateException("not an array");
        }
        return new GeneratorType(classElement.fromArray());
    }

    public boolean isPrimitive() {
        return classElement.isPrimitive();
    }

    public boolean isEnum() {
        return classElement.isEnum();
    }

    public Map<String, GeneratorType> getTypeArgumentsExact() {
        List<? extends ClassElement> boundTypeArguments = classElement.getBoundTypeArguments();
        List<? extends FreeTypeVariableElement> typeVariables = classElement.getDeclaredTypeVariables();
        Map<String, GeneratorType> args = new HashMap<>();
        for (int i = 0; i < typeVariables.size(); i++) {
            FreeTypeVariableElement typeVariable = typeVariables.get(i);
            if (i < boundTypeArguments.size()) {
                args.put(typeVariable.getVariableName(), new GeneratorType(boundTypeArguments.get(i)));
            }
        }
        return args;
    }

    public Optional<Map<String, GeneratorType>> getTypeArgumentsExact(Class<?> forType) {
        // todo: replace this method

        if (!isRawTypeEquals(forType)) {
            return Optional.empty();
        }
        return Optional.of(getTypeArgumentsExact());
    }

    public boolean isRawTypeEquals(Class<?> forType) {
        if (forType.isArray()) {
            return isArray() && fromArray().isRawTypeEquals(forType.getComponentType());
        } else {
            return !isArray() && classElement.getName().equals(forType.getName());
        }
    }

    public Function<ClassElement, ClassElement> typeParametersAsFoldFunction(ClassElement context) {
        return typeParametersAsFoldFunction0(ClassElementUtil.findParameterization(classElement, context).get());
    }

    private static Function<ClassElement, ClassElement> typeParametersAsFoldFunction0(ClassElement t) {
        List<? extends ClassElement> boundTypeArguments = t.getBoundTypeArguments();
        List<? extends FreeTypeVariableElement> variables = t.getDeclaredTypeVariables();
        return type -> {
            if (type.isFreeTypeVariable()) {
                // note: for groovy, MnType.Variable.equals breaks, so we just compare names
                for (int i = 0; i < variables.size(); i++) {
                    if (variables.get(i).getVariableName().equals(((FreeTypeVariableElement) type).getVariableName())) {
                        if (boundTypeArguments.size() <= i) {
                            return null;
                        } else {
                            return boundTypeArguments.get(i);
                        }
                    }
                }
            }
            return type;
        };
    }

    public String getTypeName() {
        return PoetUtil.toTypeName(classElement).toString();
    }

    public String getRelativeTypeName(String packageRelative) {
        return PoetUtil.toStringRelative(PoetUtil.toTypeName(classElement), packageRelative);
    }

    TypeName toPoetName() {
        return PoetUtil.toTypeName(classElement);
    }

    public boolean typeEquals(GeneratorType other) {
        return typesEqual(classElement, other.classElement);
    }

    private static boolean typesEqual(ClassElement a, ClassElement b) {
        if (a.isArray()) {
            return b.isArray() && typesEqual(a.fromArray(), b.fromArray());
        } else if (a.isFreeTypeVariable()) {
            if (b.isFreeTypeVariable()) {
                FreeTypeVariableElement ftva = (FreeTypeVariableElement) a;
                FreeTypeVariableElement ftvb = (FreeTypeVariableElement) b;
                if (ftva.getVariableName().equals(ftvb.getVariableName())) {
                    Optional<Element> declaringA = ftva.getDeclaringElement();
                    Optional<Element> declaringB = ftvb.getDeclaringElement();
                    return declaringA.isPresent() && declaringB.isPresent() &&
                            declaringA.get() instanceof ClassElement &&
                            declaringB.get() instanceof ClassElement &&
                            typesEqual((ClassElement) declaringA.get(), (ClassElement) declaringB.get());
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else if (a.isWildcard()) {
            return b.isWildcard() &&
                    typesEqual(((WildcardElement) a).getUpperBounds(), ((WildcardElement) b).getUpperBounds()) &&
                    typesEqual(((WildcardElement) a).getLowerBounds(), ((WildcardElement) b).getLowerBounds());
        } else {
            return a.getName().equals(b.getName()) &&
                    typesEqual(a.getBoundTypeArguments(), b.getBoundTypeArguments());
        }
    }

    private static boolean typesEqual(Collection<? extends ClassElement> a, Collection<? extends ClassElement> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Iterator<? extends ClassElement> itrA = a.iterator();
        Iterator<? extends ClassElement> itrB = b.iterator();
        while (itrA.hasNext()) {
            if (!typesEqual(itrA.next(), itrB.next())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create an expression that returns the equivalent {@link Type} at runtime.
     *
     * @param variableResolve Function to resolve type variables.
     */
    CodeBlock toRuntimeFactory(Function<String, CodeBlock> variableResolve) {
        return toRuntimeFactory(classElement, variableResolve);
    }

    private static CodeBlock toRuntimeFactory(ClassElement type, Function<String, CodeBlock> variableResolve) {
        if (type.isArray()) {
            return CodeBlock.of("$T.makeArrayType($L)",
                    GenericTypeFactory.class,
                    toRuntimeFactory(type.fromArray(), variableResolve));
        } else if (type.isWildcard()) {
            return CodeBlock.of("$T.makeWildcardType(new $T[] {$L}, new $T[] {$L})",
                    GenericTypeFactory.class,
                    Type.class, toRuntimeFactoryVarargs(((WildcardElement) type).getUpperBounds(), false, variableResolve),
                    Type.class, toRuntimeFactoryVarargs(((WildcardElement) type).getLowerBounds(), false, variableResolve));
        } else if (type.isFreeTypeVariable()) {
            return variableResolve.apply(((FreeTypeVariableElement) type).getVariableName());
        } else {
            CodeBlock block = CodeBlock.of("$T.class", PoetUtil.toTypeName(type.getRawClass()));
            if (!type.getBoundTypeArguments().isEmpty()) {
                block = CodeBlock.of("$T.makeParameterizedTypeWithOwner(null, $L$L)",
                        GenericTypeFactory.class, block,
                        toRuntimeFactoryVarargs(type.getBoundTypeArguments(), true, variableResolve));
            }
            return block;
        }
    }

    private static CodeBlock toRuntimeFactoryVarargs(Collection<? extends ClassElement> types, boolean leadingComma, Function<String, CodeBlock> variableResolve) {
        return varargsCodeBlock(
                types.stream()
                        .map(p -> toRuntimeFactory(p, variableResolve))
                        .collect(Collectors.toList()),
                leadingComma);
    }

    private static CodeBlock varargsCodeBlock(Collection<CodeBlock> values, boolean leadingComma) {
        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (CodeBlock value : values) {
            if (!first || leadingComma) {
                builder.add(", ");
            }
            first = false;
            builder.add("$L", value);
        }
        return builder.build();
    }
}
