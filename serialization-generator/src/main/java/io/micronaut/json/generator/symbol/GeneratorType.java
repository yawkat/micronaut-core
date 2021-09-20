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
            new MnType.Variable() {
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
                public List<? extends MnType> getBounds() {
                    return Collections.singletonList(ClassElement.of(Object.class).getRawMnType());
                }
            }.getArrayType()
    );

    private final ClassElement classElement;
    private final MnType fullType;

    private GeneratorType(ClassElement classElement, MnType fullType) {
        this.classElement = classElement;
        this.fullType = fullType;
    }

    public static GeneratorType ofClass(ClassElement raw) {
        MnType rawMn = raw.getRawMnType();
        if (rawMn instanceof MnType.RawClass) {
            // if the class has type parameters, instead return a parameterized type with those parameters as free variables.
            // List -> List<E>
            List<? extends MnType.Variable> typeVariables = ((MnType.RawClass) rawMn).getTypeVariables();
            if (!typeVariables.isEmpty()) {
                return new GeneratorType(raw, new ParameterizedImpl((MnType.RawClass) rawMn, typeVariables));
            }
        }
        return new GeneratorType(raw, rawMn);
    }

    public static GeneratorType ofParameterized(Class<?> raw, Class<?>... params) {
        // null elements in the params array are allowed, those are treated as free variables

        if (params.length == 0) {
            ClassElement ele = ClassElement.of(raw);
            return new GeneratorType(ele, ele.getRawMnType());
        }

        MnType.RawClass mnRaw = (MnType.RawClass) ClassElement.of(raw).getRawMnType();

        List<MnType> mnArgs = new ArrayList<>();
        Map<String, ClassElement> argMap = new LinkedHashMap<>();
        List<? extends MnType.Variable> typeVariables = mnRaw.getTypeVariables();
        for (int i = 0; i < typeVariables.size(); i++) {
            MnType.Variable variable = typeVariables.get(i);
            if (params[i] != null) {
                // concrete type
                ClassElement element = ClassElement.of(params[i]);
                argMap.put(variable.getName(), element);
                mnArgs.add(element.getRawMnType());
            } else {
                // type variable
                argMap.put(variable.getName(), variable.getBounds().get(0).getErasureElement());
                mnArgs.add(variable);
            }
        }
        ClassElement classElement = ClassElement.of(raw, AnnotationMetadata.EMPTY_METADATA, argMap);
        return new GeneratorType(classElement, new ParameterizedImpl((MnType.RawClass) classElement.getRawMnType(), mnArgs));
    }

    private static class ParameterizedImpl extends MnType.Parameterized {
        private final RawClass raw;
        private final List<? extends MnType> parameters;

        ParameterizedImpl(RawClass raw, List<? extends MnType> parameters) {
            this.raw = raw;
            this.parameters = parameters;
        }

        @Nullable
        @Override
        public MnType getOuter() {
            return null;
        }

        @NonNull
        @Override
        public RawClass getRaw() {
            return raw;
        }

        @NonNull
        @Override
        public List<? extends MnType> getParameters() {
            return parameters;
        }
    }

    public static GeneratorType fieldType(FieldElement element, Function<MnType, MnType> fold) {
        return new GeneratorType(
                element.getGenericType(),
                element.getMnType().foldTypes(fold)
        );
    }

    public static GeneratorType methodReturnType(MethodElement element, Function<MnType, MnType> fold) {
        return new GeneratorType(
                element.getGenericReturnType(),
                element.getMnReturnType().foldTypes(fold)
        );
    }

    public static GeneratorType parameterType(ParameterElement element, Function<MnType, MnType> fold) {
        return new GeneratorType(
                element.getGenericType(),
                element.getMnType().foldTypes(fold)
        );
    }

    public ClassElement getClassElement() {
        return classElement;
    }

    public Set<MnType.Variable> getFreeVariables() {
        return fullType.getFreeVariables();
    }

    public ClassElement getRawClass() {
        return fullType.getErasureElement();
    }

    public boolean isArray() {
        return fullType instanceof MnType.Array;
    }

    public GeneratorType fromArray() {
        if (!(fullType instanceof MnType.Array)) {
            throw new IllegalStateException("not an array");
        }
        if (!classElement.isArray()) {
            throw new IllegalStateException("not an array (but only on ClassElement? BUG)");
        }
        return new GeneratorType(classElement.fromArray(), ((MnType.Array) fullType).getComponent());
    }

    public boolean isPrimitive() {
        return classElement.isPrimitive();
    }

    public boolean isEnum() {
        return classElement.isEnum();
    }

    public Map<String, GeneratorType> getTypeArgumentsExact() {
        Map<String, ClassElement> args = classElement.getTypeArguments();
        List<? extends MnType> parameterizedArgs = ((MnType.Parameterized) this.fullType).getParameters();
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

    public Function<MnType, MnType> typeParametersAsFoldFunction(MnType context) {
        return typeParametersAsFoldFunction0(fullType.findParameterization(context));
    }

    private static Function<MnType, MnType> typeParametersAsFoldFunction0(MnType t) {
        if (t instanceof MnType.RawClass) {
            // raw class, replace type variables by their bound
            List<? extends MnType.Variable> variables = ((MnType.RawClass) t).getTypeVariables();
            return type -> {
                if (type instanceof MnType.Variable) {
                    if (variables.contains(type)) {
                        return type.getErasure();
                    }
                }
                return type;
            };
        } else {
            assert t instanceof MnType.Parameterized;
            List<? extends MnType.Variable> variables = ((MnType.Parameterized) t).getRaw().getTypeVariables();
            List<? extends MnType> arguments = ((MnType.Parameterized) t).getParameters();
            return type -> {
                if (type instanceof MnType.Variable) {
                    // note: for groovy, MnType.Variable.equals breaks, so we just compare names
                    for (int i = 0; i < variables.size(); i++) {
                        if (variables.get(i).getName().equals(((MnType.Variable) type).getName())) {
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
    CodeBlock toRuntimeFactory(Function<MnType.Variable, CodeBlock> variableResolve) {
        return toRuntimeFactory(fullType, variableResolve);
    }

    private static CodeBlock toRuntimeFactory(MnType type, Function<MnType.Variable, CodeBlock> variableResolve) {
        if (type instanceof MnType.Array) {
            return CodeBlock.of("$T.makeArrayType($L)",
                    GenericTypeFactory.class,
                    toRuntimeFactory(((MnType.Array) type).getComponent(), variableResolve));
        } else if (type instanceof MnType.RawClass) {
            return CodeBlock.of("$T.class", PoetUtil.toTypeNameRaw(((MnType.RawClass) type).getClassElement()));
        } else if (type instanceof MnType.Parameterized) {
            MnType outer = ((MnType.Parameterized) type).getOuter();
            return CodeBlock.of("$T.makeParameterizedTypeWithOwner($L, $L$L)",
                    GenericTypeFactory.class,
                    outer == null ? "null" : toRuntimeFactory(outer, variableResolve),
                    toRuntimeFactory(((MnType.Parameterized) type).getRaw(), variableResolve),
                    toRuntimeFactoryVarargs(((MnType.Parameterized) type).getParameters(), true, variableResolve));
        } else if (type instanceof MnType.Wildcard) {
            return CodeBlock.of("$T.makeWildcardType(new $T[] {$L}, new $T[] {$L})",
                    GenericTypeFactory.class,
                    Type.class, toRuntimeFactoryVarargs(((MnType.Wildcard) type).getUpperBounds(), false, variableResolve),
                    Type.class, toRuntimeFactoryVarargs(((MnType.Wildcard) type).getLowerBounds(), false, variableResolve));
        } else if (type instanceof MnType.Variable) {
            return variableResolve.apply((MnType.Variable) type);
        } else {
            throw new AssertionError(type.getClass().getName());
        }
    }

    private static CodeBlock toRuntimeFactoryVarargs(Collection<? extends MnType> types, boolean leadingComma, Function<MnType.Variable, CodeBlock> variableResolve) {
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
