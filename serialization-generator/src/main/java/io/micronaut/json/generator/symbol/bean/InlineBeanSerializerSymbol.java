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
package io.micronaut.json.generator.symbol.bean;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.json.Decoder;
import io.micronaut.json.generated.JsonParseException;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.micronaut.json.generator.symbol.Names.ENCODER;

@Internal
public class InlineBeanSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;

    @Nullable
    private final AnnotationValue<SerializableBean> fixedAnnotation;

    public InlineBeanSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
        this.fixedAnnotation = null;
    }

    private InlineBeanSerializerSymbol(InlineBeanSerializerSymbol original, AnnotationValue<SerializableBean> fixedAnnotation) {
        this.linker = original.linker;
        this.fixedAnnotation = fixedAnnotation;
    }

    /**
     * Create a new {@link InlineBeanSerializerSymbol} that uses the given annotation for configuration, instead of the
     * one actually declared on the class.
     */
    public InlineBeanSerializerSymbol withFixedAnnotation(AnnotationValue<SerializableBean> fixedAnnotation) {
        return new InlineBeanSerializerSymbol(this, fixedAnnotation);
    }

    private BeanDefinition introspect(ProblemReporter problemReporter, GeneratorType type, boolean forSerialization) {
        return BeanIntrospector.introspect(problemReporter, type.getRawClass(), Collections.emptyList(), forSerialization);
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        // can we serialize inline?
        return canSerialize(type, true);
    }

    public boolean canSerializeStandalone(GeneratorType type) {
        return canSerialize(type, false);
    }

    private boolean canSerialize(GeneratorType type, boolean inlineRole) {
        AnnotationValue<SerializableBean> annotation = getAnnotation(type);
        if (annotation == null) {
            return false;
        }
        return annotation.get("inline", Boolean.class).orElse(false) == inlineRole;
    }

    @Nullable
    private AnnotationValue<SerializableBean> getAnnotation(GeneratorType type) {
        if (fixedAnnotation != null) {
            return fixedAnnotation;
        }
        return ElementUtil.getAnnotation(SerializableBean.class, type.getRawClass(), Collections.emptyList());
    }

    /**
     * Returns {@code false} iff this direction is explicitly disabled (e.g. by
     * {@link SerializableBean#allowDeserialization()}).
     */
    public boolean supportsDirection(GeneratorType type, boolean serialization) {
        AnnotationValue<SerializableBean> annotation = getAnnotation(type);
        if (annotation == null) {
            return true;
        }
        return annotation.booleanValue(serialization ? "allowSerialization" : "allowDeserialization").orElse(true);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (!visitor.visitStructure()) {
            return;
        }
        // have to check both ser/deser, in case property types differ (e.g. when setters and getters have different types)
        // technically, this could lead to false positives for checking, since ser types will be considered in a subgraph that is only reachable through deser
        for (boolean ser : new boolean[]{true, false}) {
            if (!supportsDirection(type, ser)) {
                continue;
            }

            ProblemReporter problemReporter = new ProblemReporter();
            BeanDefinition definition = introspect(problemReporter, type, ser);
            if (problemReporter.isFailed()) {
                // definition may be in an invalid state. The actual errors will be reported by the codegen, so just skip here
                continue;
            }
            for (BeanDefinition.Property prop : definition.props) {
                PropWithType propWithType = PropWithType.fromContext(type, prop);
                SerializerSymbol symbol = linker.findSymbol(propWithType.type);
                if (prop.permitRecursiveSerialization) {
                    symbol = symbol.withRecursiveSerialization();
                }
                visitor.visitStructureElement(symbol, propWithType.type, prop.getElement());
            }
        }
    }

    private SerializerSymbol findSymbol(PropWithType prop) {
        SerializerSymbol symbol = linker.findSymbol(prop.type);
        if (prop.property.permitRecursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        Boolean nullable = prop.property.nullable;
        // if no nullity is given, infer nullity from the value null support.
        // most types will be wrapped with NullableSerializerSymbol, but e.g. Optional won't be.
        if (nullable == null) {
            if (prop.type.isPrimitive() && !prop.type.isArray()) {
                nullable = false;
            } else {
                nullable = !symbol.supportsNullDeserialization();
            }
        }
        if (nullable) {
            symbol = new NullableSerializerSymbol(symbol);
        }
        return symbol;
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        if (!supportsDirection(type, true)) {
            // todo: don't implement Serializer at all
            return CodeBlock.of("throw new $T(\"Serialization of this type is disabled\");\n", UnsupportedOperationException.class);
        }

        BeanDefinition definition = introspect(generatorContext.getProblemReporter(), type, true);
        if (generatorContext.getProblemReporter().isFailed()) {
            // definition may be in an invalid state, so just skip codegen
            return CodeBlock.of("");
        }

        if (definition.valueProperty != null) {
            // @JsonValue
            return findSymbol(PropWithType.fromContext(type, definition.valueProperty)).serialize(
                    generatorContext,
                    definition.valueProperty.getType(type.typeParametersAsFoldFunction()),
                    // we don't need a temp variable here, getPropertyAccessExpression only evaluates the read expression once
                    getPropertyAccessExpression(readExpression, definition.valueProperty)
            );
        } else {
            // normal path
            CodeBlock.Builder serialize = CodeBlock.builder();
            String objectVarName = generatorContext.newLocalVariable("object");
            serialize.addStatement("$T $N = $L", PoetUtil.toTypeName(type), objectVarName, readExpression);
            // passing the value to writeStartObject helps with debugging, but will not affect functionality
            serialize.addStatement("$N.writeStartObject($N)", ENCODER, objectVarName);
            serializeBeanProperties(generatorContext, type, definition, CodeBlock.of("$N", objectVarName), serialize);
            serialize.addStatement("$N.writeEndObject()", ENCODER);
            return serialize.build();
        }
    }

    /**
     * @param generatorContext   Generator context
     * @param definition         Definition of the bean we're serializing
     * @param beanReadExpression The expression to use for accessing bean properties. May be evaluated multiple times.
     * @param serialize          The output
     */
    private void serializeBeanProperties(
            GeneratorContext generatorContext,
            GeneratorType outerType,
            BeanDefinition definition,
            CodeBlock beanReadExpression,
            CodeBlock.Builder serialize
    ) {
        for (BeanDefinition.Property prop : definition.props) {
            CodeBlock propRead = getPropertyAccessExpression(beanReadExpression, prop);
            GeneratorContext subGenerator = generatorContext.withSubPath(prop.name);
            PropWithType propWithType = PropWithType.fromContext(outerType, prop);
            if (prop.unwrapped) {
                propRead = onlyReadOnce(generatorContext, propWithType, propRead, serialize);

                BeanDefinition subDefinition = introspect(generatorContext.getProblemReporter(), propWithType.type, true);
                serializeBeanProperties(subGenerator, propWithType.type, subDefinition, propRead, serialize);
            } else {
                SerializerSymbol symbol = findSymbol(propWithType);
                ConditionExpression<CodeBlock> shouldIncludeCheck = symbol.shouldIncludeCheck(generatorContext, propWithType.type, prop.valueInclusionPolicy);
                if (!shouldIncludeCheck.isAlwaysTrue()) {
                    propRead = onlyReadOnce(generatorContext, propWithType, propRead, serialize);
                    serialize.beginControlFlow("if ($L)", shouldIncludeCheck.build(propRead));
                }

                serialize.addStatement("$N.writeFieldName($S)", ENCODER, prop.name);
                serialize.add(symbol.serialize(subGenerator, propWithType.type, propRead));

                if (!shouldIncludeCheck.isAlwaysTrue()) {
                    serialize.endControlFlow();
                }
            }
        }
    }

    /**
     * Helper method to save a property read expression into a local variable so that it is only read once.
     */
    private CodeBlock onlyReadOnce(
            GeneratorContext generatorContext,
            PropWithType propWithType,
            CodeBlock propRead,
            CodeBlock.Builder serialize) {
        String tempVariable = generatorContext.newLocalVariable(propWithType.property.name);
        serialize.addStatement("$T $N = $L", PoetUtil.toTypeName(propWithType.type), tempVariable, propRead);
        return CodeBlock.of("$N", tempVariable);
    }

    private CodeBlock getPropertyAccessExpression(CodeBlock beanReadExpression, BeanDefinition.Property prop) {
        if (prop.getter != null) {
            return CodeBlock.of("$L.$N()", beanReadExpression, prop.getter.getName());
        } else if (prop.field != null) {
            return CodeBlock.of("$L.$N", beanReadExpression, prop.field.getName());
        } else {
            throw new AssertionError("No accessor, property should have been filtered");
        }
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        if (!supportsDirection(type, false)) {
            // todo: don't implement Serializer at all
            return CodeBlock.of("throw new $T(\"Deserialization of this type is disabled\");\n", UnsupportedOperationException.class);
        }

        return new DeserGen(generatorContext, type).generate(setter, decoderVariable);
    }

    private class DeserGen {
        private final GeneratorContext generatorContext;
        private final GeneratorType rootType;

        private final BeanDefinition rootDefinition;
        private final Map<BeanDefinition.Property, BeanDefinition> unwrappedDefinitions = new HashMap<>(); // filled in introspectRecursive
        private final List<PropWithType> leafProperties = new ArrayList<>(); // filled in introspectRecursive
        /**
         * Names of the local variables properties are saved in.
         */
        private final Map<BeanDefinition.Property, String> localVariableNames;

        private final DuplicatePropertyManager duplicatePropertyManager;

        /**
         * Main deser code.
         */
        private final CodeBlock.Builder deserialize = CodeBlock.builder();

        private final String elementDecoderVariable;

        DeserGen(GeneratorContext generatorContext, GeneratorType type) {
            this.generatorContext = generatorContext;
            this.rootType = type;
            elementDecoderVariable = generatorContext.newLocalVariable("elementDecoder");

            rootDefinition = introspectRecursive(type);
            localVariableNames = leafProperties.stream()
                    .collect(Collectors.toMap(prop -> prop.property, prop -> generatorContext.newLocalVariable(prop.property.name)));
            duplicatePropertyManager = new DuplicatePropertyManager(generatorContext, leafProperties.stream().map(p -> p.property).collect(Collectors.toList()), elementDecoderVariable);
        }

        private BeanDefinition introspectRecursive(GeneratorType type) {
            BeanDefinition def = introspect(generatorContext.getProblemReporter(), type, false);
            for (BeanDefinition.Property prop : def.props) {
                if (prop.unwrapped) {
                    unwrappedDefinitions.put(prop, introspectRecursive(prop.getType(type.typeParametersAsFoldFunction())));
                } else {
                    leafProperties.add(PropWithType.fromContext(type, prop));
                }
            }
            return def;
        }

        private CodeBlock generate(Setter setter, String outerDecoderVariable) {
            // if there were failures, the definition may be in an inconsistent state, so we avoid codegen.
            if (generatorContext.getProblemReporter().isFailed()) {
                return CodeBlock.of("");
            }

            if (rootDefinition.creatorDelegatingProperty != null) {
                // delegating to another type
                return findSymbol(PropWithType.fromContext(rootType, rootDefinition.creatorDelegatingProperty)).deserialize(
                        generatorContext, outerDecoderVariable,
                        rootDefinition.creatorDelegatingProperty.getType(rootType.typeParametersAsFoldFunction()),
                        Setter.delegate(setter, expr -> getCreatorCall(rootType, rootDefinition, expr)));
            }

            deserialize.addStatement("$T $N = $N.decodeObject()", Decoder.class, elementDecoderVariable, outerDecoderVariable);

            duplicatePropertyManager.emitMaskDeclarations(deserialize);

            // create a local variable for each property
            for (PropWithType prop : leafProperties) {
                deserialize.addStatement("$T $N = $L", PoetUtil.toTypeName(prop.type), localVariableNames.get(prop.property), findSymbol(prop).getDefaultExpression(prop.type));
            }

            // main parse loop
            deserialize.beginControlFlow("while (true)");
            String fieldNameVariable = generatorContext.newLocalVariable("fieldName");
            deserialize.addStatement("$T $N = $N.decodeKey()", String.class, fieldNameVariable, elementDecoderVariable);
            deserialize.add("if ($N == null) break;\n", fieldNameVariable);
            deserialize.beginControlFlow("switch ($N)", fieldNameVariable);
            for (PropWithType prop : leafProperties) {
                // todo: detect duplicates
                for (String alias : prop.property.aliases) {
                    deserialize.addStatement("case $S:\n", alias);
                }
                deserialize.beginControlFlow("case $S:", prop.property.name);
                deserializeProperty(prop);
                deserialize.addStatement("break");
                deserialize.endControlFlow();
            }

            // unknown properties
            deserialize.beginControlFlow("default:");
            if (rootDefinition.ignoreUnknownProperties) {
                deserialize.addStatement("$N.skipValue()", elementDecoderVariable);
            } else {
                // todo: do we really want to output a potentially attacker-controlled field name to the logs here?
                deserialize.addStatement("throw $T.from($N, $S + $N)",
                        JsonParseException.class, elementDecoderVariable, "Unknown property for type " + rootType.getTypeName() + ": ", fieldNameVariable);
            }
            deserialize.endControlFlow();

            deserialize.endControlFlow();
            deserialize.endControlFlow();

            deserialize.addStatement("$N.finishStructure()", elementDecoderVariable);

            duplicatePropertyManager.emitCheckRequired(deserialize);

            // assemble the result object

            String resultVariable = combineLocalsToResultVariable(rootType, rootDefinition);
            deserialize.add(setter.createSetStatement(CodeBlock.of("$N", resultVariable)));
            return deserialize.build();
        }

        private void deserializeProperty(PropWithType prop) {
            duplicatePropertyManager.emitReadVariable(deserialize, prop.property);

            CodeBlock deserializationCode = findSymbol(prop)
                    .deserialize(generatorContext.withSubPath(prop.property.name), elementDecoderVariable, prop.type, expr -> CodeBlock.of("$N = $L;\n", localVariableNames.get(prop.property), expr));
            deserialize.add(deserializationCode);
        }

        /**
         * Combine all the local variables into the final result variable.
         *
         * @return the result variable name
         */
        private String combineLocalsToResultVariable(GeneratorType type, BeanDefinition definition) {
            Map<BeanDefinition.Property, String> allPropertyLocals = new HashMap<>(localVariableNames);
            for (BeanDefinition.Property prop : definition.props) {
                if (prop.unwrapped) {
                    allPropertyLocals.put(prop, combineLocalsToResultVariable(prop.getType(type.typeParametersAsFoldFunction()), unwrappedDefinitions.get(prop)));
                }
            }

            String resultVariable = generatorContext.newLocalVariable("result");

            CodeBlock.Builder creatorParameters = CodeBlock.builder();
            boolean firstParameter = true;
            for (BeanDefinition.Property prop : definition.creatorProps) {
                if (!firstParameter) {
                    creatorParameters.add(", ");
                }
                creatorParameters.add("$L", allPropertyLocals.get(prop));
                firstParameter = false;
            }

            deserialize.addStatement("$T $N = $L", PoetUtil.toTypeName(type), resultVariable, getCreatorCall(type, definition, creatorParameters.build()));
            for (BeanDefinition.Property prop : definition.props) {
                CodeBlock expressionHasBeenRead = duplicatePropertyManager.getVariableReadExpression(prop);

                if (expressionHasBeenRead != null) {
                    // don't set a property we haven't read
                    deserialize.beginControlFlow("if ($L)", expressionHasBeenRead);
                }

                String localVariable = allPropertyLocals.get(prop);
                if (prop.setter != null) {
                    deserialize.addStatement("$N.$N($N)", resultVariable, prop.setter.getName(), localVariable);
                } else if (prop.field != null) {
                    deserialize.addStatement("$N.$N = $N", resultVariable, prop.field.getName(), localVariable);
                } else {
                    if (prop.creatorParameter == null) {
                        throw new AssertionError("Cannot set property, should have been filtered out during introspection");
                    }
                }

                if (expressionHasBeenRead != null) {
                    deserialize.endControlFlow();
                }
            }
            return resultVariable;
        }

        private CodeBlock getCreatorCall(GeneratorType type, BeanDefinition definition, CodeBlock creatorParameters) {
            if (definition.creator instanceof ConstructorElement) {
                return CodeBlock.of("new $T($L)", PoetUtil.toTypeName(type), creatorParameters);
            } else if (definition.creator.isStatic()) {
                return CodeBlock.of(
                        "$T.$N($L)",
                        PoetUtil.toTypeName(definition.creator.getDeclaringType()),
                        definition.creator.getName(),
                        creatorParameters
                );
            } else {
                throw new AssertionError("bad creator, should have been detected in BeanIntrospector");
            }
        }
    }

    private static class PropWithType {
        final BeanDefinition.Property property;
        final GeneratorType type;

        PropWithType(BeanDefinition.Property property, GeneratorType type) {
            this.property = property;
            this.type = type;
        }

        static PropWithType fromContext(GeneratorType context, BeanDefinition.Property property) {
            return new PropWithType(property, property.getType(context.typeParametersAsFoldFunction()));
        }
    }

    /**
     * This class detects duplicate and missing properties using an inlined BitSet.
     * <p>
     * Note: implementation must use the same bit layout as BitSet to allow internal use of {@link BitSet#toLongArray()}.
     */
    private static class DuplicatePropertyManager {
        /**
         * Used for creating exceptions
         */
        private final String decoderVariable;

        private final Collection<BeanDefinition.Property> properties;

        private final List<String> maskVariables;
        private final Map<BeanDefinition.Property, Integer> offsets;

        private final BitSet requiredMask;

        DuplicatePropertyManager(
                GeneratorContext context,
                Collection<BeanDefinition.Property> properties,
                String decoderVariable
        ) {
            requiredMask = new BitSet(properties.size());
            this.properties = properties;
            this.decoderVariable = decoderVariable;

            offsets = new HashMap<>();
            int offset = 0;
            for (BeanDefinition.Property property : properties) {
                offsets.put(property, offset);
                // todo: only require when required=true is set
                if (property.creatorParameter != null) {
                    requiredMask.set(offset);
                }

                offset++;
            }

            // generate one mask for every 64 variables
            maskVariables = new ArrayList<>();
            for (int i = 0; i < offset; i += 64) {
                maskVariables.add(context.newLocalVariable("mask"));
            }
        }

        /**
         * Emit the prelude required for duplicate detection.
         */
        void emitMaskDeclarations(CodeBlock.Builder output) {
            for (String maskVariable : maskVariables) {
                output.addStatement("long $N = 0", maskVariable);
            }
        }

        /**
         * Emit the code when a variable is read. Checks for duplicates, and marks this property as read.
         */
        void emitReadVariable(CodeBlock.Builder output, BeanDefinition.Property prop) {
            int offset = offsets.get(prop);
            String maskVariable = maskVariable(offset);
            String mask = mask(offset);
            output.add(
                    "if (($N & $L) != 0) throw $T.from($N, $S);\n",
                    maskVariable,
                    mask,
                    JsonParseException.class,
                    decoderVariable,
                    "Duplicate property " + prop.name
            );
            output.addStatement("$N |= $L", maskVariable, mask);
        }

        private String maskVariable(int offset) {
            return maskVariables.get(offset / 64);
        }

        private String mask(int offset) {
            // shift does an implicit modulo
            long value = 1L << offset;
            return toHexLiteral(value);
        }

        private String toHexLiteral(long value) {
            return "0x" + Long.toHexString(value) + "L";
        }

        /**
         * Emit the final checks that all required properties are present.
         */
        void emitCheckRequired(CodeBlock.Builder output) {
            if (requiredMask.isEmpty()) {
                return;
            }

            // first, check whether there are any values missing, by simple mask comparison. This is the fast check.
            long[] expected = requiredMask.toLongArray();
            output.add("if (");
            boolean first = true;
            for (int i = 0; i < expected.length; i++) {
                long value = expected[i];
                if (value != 0) {
                    if (!first) {
                        output.add(" || ");
                    }
                    first = false;
                    String valueLiteral = toHexLiteral(value);
                    output.add("($N & $L) != $L", maskVariables.get(i), valueLiteral, valueLiteral);
                }
            }
            output.add(") {\n").indent();

            // if there are missing variables, determine which ones
            int offset = 0;
            for (BeanDefinition.Property prop : properties) {
                if (requiredMask.get(offset)) {
                    output.add(
                            "if (($N & $L) == 0) throw $T.from($N, $S);\n",
                            maskVariable(offset),
                            mask(offset),
                            JsonParseException.class,
                            decoderVariable,
                            "Missing property " + prop.name
                    );
                }
                offset++;
            }
            output.add("// should never reach here, all possible missing properties are checked\n");
            output.addStatement("throw new $T()", AssertionError.class);

            output.endControlFlow();
        }

        /**
         * Get the boolean expression that checks whether the given variable was present, or {@code null} if the
         * variable is guaranteed to be present (checked by {@link #emitCheckRequired(CodeBlock.Builder)}).
         */
        @Nullable
        CodeBlock getVariableReadExpression(BeanDefinition.Property prop) {
            int offset = offsets.get(prop);
            if (requiredMask.get(offset)) {
                return null;
            }
            String maskVariable = maskVariable(offset);
            String mask = mask(offset);
            return CodeBlock.of("($N & $L) != 0", maskVariable, mask);
        }
    }
}
