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
    public static final GeneratorType GENERIC_ARRAY = new GeneratorType(
            ClassElement.of(Object[].class),
            new SourceType.Variable() {
                @NonNull
                @Override
                public Element getDeclaringElement() {
                    // well...
                    return ClassElement.of(Object[].class);
                }

                @NonNull
                @Override
                public String getName() {
                    return "T";
                }

                @NonNull
                @Override
                public List<? extends SourceType> getBounds() {
                    return Collections.singletonList(ClassElement.of(Object.class).getRawSourceType());
                }
            }.createArrayType()
    );

    private final ClassElement classElement;
    private final SourceType fullType;

    private GeneratorType(ClassElement classElement, SourceType fullType) {
        this.classElement = classElement;
        this.fullType = fullType;
    }

    public static GeneratorType ofClass(ClassElement raw) {
        SourceType rawMn = raw.getRawSourceType();
        if (rawMn instanceof SourceType.RawClass) {
            // if the class has type parameters, instead return a parameterized type with those parameters as free variables.
            // List -> List<E>
            List<? extends SourceType.Variable> typeVariables = ((SourceType.RawClass) rawMn).getTypeVariables();
            if (!typeVariables.isEmpty()) {
                return new GeneratorType(raw, new ParameterizedImpl((SourceType.RawClass) rawMn, typeVariables));
            }
        }
        return new GeneratorType(raw, rawMn);
    }

    public static GeneratorType ofParameterized(Class<?> raw, Class<?>... params) {
        // null elements in the params array are allowed, those are treated as free variables

        if (params.length == 0) {
            ClassElement ele = ClassElement.of(raw);
            return new GeneratorType(ele, ele.getRawSourceType());
        }

        SourceType.RawClass mnRaw = (SourceType.RawClass) ClassElement.of(raw).getRawSourceType();

        List<SourceType> mnArgs = new ArrayList<>();
        Map<String, ClassElement> argMap = new LinkedHashMap<>();
        List<? extends SourceType.Variable> typeVariables = mnRaw.getTypeVariables();
        for (int i = 0; i < typeVariables.size(); i++) {
            SourceType.Variable variable = typeVariables.get(i);
            if (params[i] != null) {
                // concrete type
                ClassElement element = ClassElement.of(params[i]);
                argMap.put(variable.getName(), element);
                mnArgs.add(element.getRawSourceType());
            } else {
                // type variable
                argMap.put(variable.getName(), variable.getBounds().get(0).getErasureElement());
                mnArgs.add(variable);
            }
        }
        ClassElement classElement = ClassElement.of(raw, AnnotationMetadata.EMPTY_METADATA, argMap);
        return new GeneratorType(classElement, new ParameterizedImpl((SourceType.RawClass) classElement.getRawSourceType(), mnArgs));
    }

    private static class ParameterizedImpl extends SourceType.Parameterized {
        private final RawClass raw;
        private final List<? extends SourceType> parameters;

        ParameterizedImpl(RawClass raw, List<? extends SourceType> parameters) {
            this.raw = raw;
            this.parameters = parameters;
        }

        @Nullable
        @Override
        public SourceType getOuter() {
            return null;
        }

        @NonNull
        @Override
        public RawClass getRaw() {
            return raw;
        }

        @NonNull
        @Override
        public List<? extends SourceType> getParameters() {
            return parameters;
        }
    }

    public static GeneratorType fieldType(FieldElement element, Function<SourceType, SourceType> fold) {
        return new GeneratorType(
                element.getGenericType(),
                element.getDeclaredSourceType().foldTypes(fold)
        );
    }

    public static GeneratorType methodReturnType(MethodElement element, Function<SourceType, SourceType> fold) {
        return new GeneratorType(
                element.getGenericReturnType(),
                element.getDeclaredReturnSourceType().foldTypes(fold)
        );
    }

    public static GeneratorType parameterType(ParameterElement element, Function<SourceType, SourceType> fold) {
        return new GeneratorType(
                element.getGenericType(),
                element.getDeclaredSourceType().foldTypes(fold)
        );
    }

    public ClassElement getClassElement() {
        return classElement;
    }

    public Set<SourceType.Variable> getFreeVariables() {
        return fullType.getFreeVariables();
    }

    public ClassElement getRawClass() {
        return fullType.getErasureElement();
    }

    public boolean isArray() {
        return fullType instanceof SourceType.Array;
    }

    public GeneratorType fromArray() {
        if (!(fullType instanceof SourceType.Array)) {
            throw new IllegalStateException("not an array");
        }
        if (!classElement.isArray()) {
            throw new IllegalStateException("not an array (but only on ClassElement? BUG)");
        }
        return new GeneratorType(classElement.fromArray(), ((SourceType.Array) fullType).getComponent());
    }

    public boolean isPrimitive() {
        return classElement.isPrimitive();
    }

    public boolean isEnum() {
        return classElement.isEnum();
    }

    public Map<String, GeneratorType> getTypeArgumentsExact() {
        Map<String, ClassElement> args = classElement.getTypeArguments();
        List<? extends SourceType> parameterizedArgs = ((SourceType.Parameterized) this.fullType).getParameters();
        int i = 0;
        Map<String, GeneratorType> mappedArgs = new LinkedHashMap<>();
        for (Map.Entry<String, ClassElement> entry : args.entrySet()) {
            mappedArgs.put(entry.getKey(), new GeneratorType(entry.getValue(), parameterizedArgs.get(i++)));
        }
        return mappedArgs;
    }

    public Optional<Map<String, GeneratorType>> getTypeArgumentsExact(Class<?> forType) {
        // todo: replace this method

        if (!isRawTypeEquals(forType)) {
            return Optional.empty();
        }
        return Optional.of(getTypeArgumentsExact());
    }

    public boolean isRawTypeEquals(Class<?> forType) {
        return classElement.getName().equals(forType.getName());
    }

    public Function<SourceType, SourceType> typeParametersAsFoldFunction(SourceType context) {
        return typeParametersAsFoldFunction0(fullType.findParameterization(context));
    }

    private static Function<SourceType, SourceType> typeParametersAsFoldFunction0(SourceType t) {
        if (t instanceof SourceType.RawClass) {
            // raw class, replace type variables by their bound
            List<? extends SourceType.Variable> variables = ((SourceType.RawClass) t).getTypeVariables();
            return type -> {
                if (type instanceof SourceType.Variable) {
                    if (variables.contains(type)) {
                        return type.getErasure();
                    }
                }
                return type;
            };
        } else {
            assert t instanceof SourceType.Parameterized;
            List<? extends SourceType.Variable> variables = ((SourceType.Parameterized) t).getRaw().getTypeVariables();
            List<? extends SourceType> arguments = ((SourceType.Parameterized) t).getParameters();
            return type -> {
                if (type instanceof SourceType.Variable) {
                    // note: for groovy, MnType.Variable.equals breaks, so we just compare names
                    for (int i = 0; i < variables.size(); i++) {
                        if (variables.get(i).getName().equals(((SourceType.Variable) type).getName())) {
                            return arguments.get(i);
                        }
                    }
                }
                return type;
            };
        }
    }

    public String getTypeName() {
        return getRelativeTypeName(null);
    }

    public String getRelativeTypeName(String packageRelative) {
        return fullType.getTypeName(packageRelative);
    }

    TypeName toPoetName() {
        return PoetUtil.toTypeName(fullType);
    }

    public boolean typeEquals(GeneratorType other) {
        return fullType.equals(other.fullType);
    }

    /**
     * Create an expression that returns the equivalent {@link Type} at runtime.
     *
     * @param variableResolve Function to resolve type variables.
     */
    CodeBlock toRuntimeFactory(Function<SourceType.Variable, CodeBlock> variableResolve) {
        return toRuntimeFactory(fullType, variableResolve);
    }

    private static CodeBlock toRuntimeFactory(SourceType type, Function<SourceType.Variable, CodeBlock> variableResolve) {
        if (type instanceof SourceType.Array) {
            return CodeBlock.of("$T.makeArrayType($L)",
                    GenericTypeFactory.class,
                    toRuntimeFactory(((SourceType.Array) type).getComponent(), variableResolve));
        } else if (type instanceof SourceType.RawClass) {
            return CodeBlock.of("$T.class", PoetUtil.toTypeNameRaw(((SourceType.RawClass) type).getClassElement()));
        } else if (type instanceof SourceType.Parameterized) {
            SourceType outer = ((SourceType.Parameterized) type).getOuter();
            return CodeBlock.of("$T.makeParameterizedTypeWithOwner($L, $L$L)",
                    GenericTypeFactory.class,
                    outer == null ? "null" : toRuntimeFactory(outer, variableResolve),
                    toRuntimeFactory(((SourceType.Parameterized) type).getRaw(), variableResolve),
                    toRuntimeFactoryVarargs(((SourceType.Parameterized) type).getParameters(), true, variableResolve));
        } else if (type instanceof SourceType.Wildcard) {
            return CodeBlock.of("$T.makeWildcardType(new $T[] {$L}, new $T[] {$L})",
                    GenericTypeFactory.class,
                    Type.class, toRuntimeFactoryVarargs(((SourceType.Wildcard) type).getUpperBounds(), false, variableResolve),
                    Type.class, toRuntimeFactoryVarargs(((SourceType.Wildcard) type).getLowerBounds(), false, variableResolve));
        } else if (type instanceof SourceType.Variable) {
            return variableResolve.apply((SourceType.Variable) type);
        } else {
            throw new AssertionError(type.getClass().getName());
        }
    }

    private static CodeBlock toRuntimeFactoryVarargs(Collection<? extends SourceType> types, boolean leadingComma, Function<SourceType.Variable, CodeBlock> variableResolve) {
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
