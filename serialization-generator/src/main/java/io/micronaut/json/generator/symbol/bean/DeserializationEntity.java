package io.micronaut.json.generator.symbol.bean;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.json.Decoder;
import io.micronaut.json.generated.JsonParseException;
import io.micronaut.json.generator.symbol.GeneratorContext;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.PoetUtil;
import io.micronaut.json.generator.symbol.SerializerSymbol;

import java.util.*;
import java.util.stream.Collectors;

abstract class DeserializationEntity {
    /**
     * An entity that will generate a java object, placing it into a local variable.
     */
    boolean presentAsJava = false;
    /**
     * An entity that consists of multiple json properties.
     */
    boolean hasProperties = false;

    String localVariableName;

    private DeserializationEntity() {
    }

    private static CodeBlock getCreatorCall(GeneratorType type, BeanDefinition definition, CodeBlock creatorParameters) {
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

    void allocateLocals(GeneratorContext context, String ownNameHint) {
        if (localVariableName == null && presentAsJava) {
            localVariableName = context.newLocalVariable(ownNameHint);
        }
    }

    void generatePrologue(CodeBlock.Builder builder) {
    }

    void generateEpilogue(CodeBlock.Builder builder, DuplicatePropertyManager duplicatePropertyManager) {
    }

    void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, DuplicatePropertyManager duplicatePropertyManager, String decoderVariable) {
        if (!hasProperties) {
            throw new UnsupportedOperationException();
        }

        String elementDecoderVariable = generatorContext.newLocalVariable("elementDecoder");
        builder.addStatement("$T $N = $N.decodeObject()", Decoder.class, elementDecoderVariable, decoderVariable);

        // main parse loop
        builder.beginControlFlow("while (true)");
        String fieldNameVariable = generatorContext.newLocalVariable("fieldName");
        builder.addStatement("$T $N = $N.decodeKey()", String.class, fieldNameVariable, elementDecoderVariable);
        builder.add("if ($N == null) break;\n", fieldNameVariable);
        builder.beginControlFlow("switch ($N)", fieldNameVariable);

        Map<String, DeserializationEntity> collectedProperties = collectProperties();
        Map<DeserializationEntity, List<String>> collectedPropertiesReverse = new LinkedHashMap<>();
        for (Map.Entry<String, DeserializationEntity> entry : collectedProperties.entrySet()) {
            collectedPropertiesReverse.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<DeserializationEntity, List<String>> entry : collectedPropertiesReverse.entrySet()) {
            for (String alias : entry.getValue()) {
                builder.add("case $S:\n", alias);
            }
            builder.indent();
            entry.getKey().deserialize(generatorContext, builder, duplicatePropertyManager, elementDecoderVariable);
            builder.addStatement("break");
            builder.unindent();
        }

        // unknown properties
        builder.beginControlFlow("default:");
        onUnknownProperty(builder, fieldNameVariable, elementDecoderVariable);
        builder.endControlFlow();

        builder.endControlFlow();
        builder.endControlFlow();

        builder.addStatement("$N.finishStructure()", elementDecoderVariable);
    }

    void onUnknownProperty(CodeBlock.Builder builder, String keyVariable, String elementDecoderVariable) {
        throw new UnsupportedOperationException();
    }

    Map<String, DeserializationEntity> collectProperties() {
        throw new UnsupportedOperationException();
    }

    abstract Set<BeanDefinition.Property> collectPropertiesForDuplicateDetection();

    static DeserializationEntity introspect(InlineBeanSerializerSymbol symbol, GeneratorContext generatorContext, GeneratorType type) {
        BeanDefinition def = symbol.introspect(generatorContext.getProblemReporter(), type, false);
        if (def.creatorDelegatingProperty != null) {
            PropWithType singleProp = PropWithType.fromContext(type, def.creatorDelegatingProperty);
            return new Delegating(type, def, singleProp, symbol.findSymbol(singleProp));
        }
        Map<BeanDefinition.Property, DeserializationEntity> elements = new LinkedHashMap<>();
        for (BeanDefinition.Property prop : def.props) {
            PropWithType propWithType = PropWithType.fromContext(type, prop);
            if (prop.unwrapped) {
                DeserializationEntity unwrapped = introspect(symbol, generatorContext, propWithType.type);
                if (!unwrapped.hasProperties) {
                    generatorContext.getProblemReporter().fail("Invalid unwrapped property", prop.getElement());
                    continue;
                }
                elements.put(prop, unwrapped);
            } else {
                elements.put(prop, new SimpleLeafProperty(propWithType, symbol.findSymbol(propWithType)));
            }
        }
        return new Structure(type, def, elements);
    }

    private static class Delegating extends DeserializationEntity {
        final GeneratorType type;
        final BeanDefinition beanDefinition;
        final PropWithType prop;
        final SerializerSymbol symbol;

        Delegating(GeneratorType type, BeanDefinition beanDefinition, PropWithType prop, SerializerSymbol symbol) {
            this.type = type;
            this.beanDefinition = beanDefinition;
            this.prop = prop;
            this.symbol = symbol;

            presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            builder.addStatement("$T $N;", PoetUtil.toTypeName(type), localVariableName);
        }

        @Override
        public void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, DuplicatePropertyManager duplicatePropertyManager, String decoderVariable) {
            builder.add(symbol.deserialize(
                    generatorContext, decoderVariable,
                    prop.type,
                    expr -> CodeBlock.of("$N = $L;\n", localVariableName, getCreatorCall(type, beanDefinition, expr))));
        }

        @Override
        Set<BeanDefinition.Property> collectPropertiesForDuplicateDetection() {
            return Collections.emptySet();
        }
    }

    private static class Structure extends DeserializationEntity {
        private final GeneratorType type;
        private final BeanDefinition definition;
        private final Map<BeanDefinition.Property, DeserializationEntity> elements;

        private Structure(GeneratorType type, BeanDefinition definition, Map<BeanDefinition.Property, DeserializationEntity> elements) {
            this.type = type;
            this.definition = definition;
            this.elements = elements;

            presentAsJava = true;
            hasProperties = true;
        }

        @Override
        void allocateLocals(GeneratorContext context, String ownNameHint) {
            super.allocateLocals(context, ownNameHint);
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> element : elements.entrySet()) {
                element.getValue().allocateLocals(context, element.getKey().name);
            }
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            for (DeserializationEntity element : elements.values()) {
                element.generatePrologue(builder);
            }
        }

        @Override
        Map<String, DeserializationEntity> collectProperties() {
            Map<String, DeserializationEntity> result = new LinkedHashMap<>();
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> element : elements.entrySet()) {
                // todo: detect duplicates
                if (element.getValue().hasProperties) {
                    result.putAll(element.getValue().collectProperties());
                } else {
                    result.put(element.getKey().name, element.getValue());
                    for (String alias : element.getKey().aliases) {
                        result.put(alias, element.getValue());
                    }
                }
            }
            return result;
        }

        @Override
        Set<BeanDefinition.Property> collectPropertiesForDuplicateDetection() {
            return elements.values().stream()
                    .flatMap(entity -> entity.collectPropertiesForDuplicateDetection().stream())
                    .collect(Collectors.toSet());
        }

        @Override
        void onUnknownProperty(CodeBlock.Builder builder, String keyVariable, String elementDecoderVariable) {
            if (definition.ignoreUnknownProperties) {
                builder.addStatement("$N.skipValue()", elementDecoderVariable);
            } else {
                // todo: do we really want to output a potentially attacker-controlled field name to the logs here?
                builder.addStatement("throw $T.from($N, $S + $N)",
                        JsonParseException.class, elementDecoderVariable, "Unknown property for type " + type.getTypeName() + ": ", keyVariable);
            }
        }

        @Override
        void generateEpilogue(CodeBlock.Builder builder, DuplicatePropertyManager duplicatePropertyManager) {
            for (DeserializationEntity value : elements.values()) {
                value.generateEpilogue(builder, duplicatePropertyManager);
            }

            Set<BeanDefinition.Property> required = definition.props.stream().filter(BeanDefinition.Property::isRequired).collect(Collectors.toSet());
            if (!required.isEmpty()) {
                duplicatePropertyManager.emitCheckRequired(builder, required);
            }

            CodeBlock.Builder creatorParameters = CodeBlock.builder();
            boolean firstParameter = true;
            for (BeanDefinition.Property prop : definition.creatorProps) {
                if (!firstParameter) {
                    creatorParameters.add(", ");
                }
                creatorParameters.add("$L", elements.get(prop).localVariableName);
                firstParameter = false;
            }

            builder.addStatement("$T $N = $L", PoetUtil.toTypeName(type), localVariableName, getCreatorCall(type, definition, creatorParameters.build()));
            for (BeanDefinition.Property prop : definition.props) {
                // unwrapped properties are created in in their specific epilogues, required properties are checked to be present above
                CodeBlock expressionHasBeenRead = prop.unwrapped || prop.isRequired() ? null : duplicatePropertyManager.hasBeenRead(prop);

                if (expressionHasBeenRead != null) {
                    // don't set a property we haven't read
                    builder.beginControlFlow("if ($L)", expressionHasBeenRead);
                }

                String elementVariable = elements.get(prop).localVariableName;
                if (prop.setter != null) {
                    builder.addStatement("$N.$N($N)", localVariableName, prop.setter.getName(), elementVariable);
                } else if (prop.field != null) {
                    builder.addStatement("$N.$N = $N", localVariableName, prop.field.getName(), elementVariable);
                } else {
                    if (prop.creatorParameter == null) {
                        throw new AssertionError("Cannot set property, should have been filtered out during introspection");
                    }
                }

                if (expressionHasBeenRead != null) {
                    builder.endControlFlow();
                }
            }
        }
    }

    private static class SimpleLeafProperty extends DeserializationEntity {
        private final PropWithType prop;
        private final SerializerSymbol symbol;

        SimpleLeafProperty(PropWithType prop, SerializerSymbol symbol) {
            this.prop = prop;
            this.symbol = symbol;

            presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            builder.addStatement("$T $N = $L", PoetUtil.toTypeName(prop.type), localVariableName, symbol.getDefaultExpression(prop.type));
        }

        @Override
        Set<BeanDefinition.Property> collectPropertiesForDuplicateDetection() {
            return Collections.singleton(prop.property);
        }

        @Override
        void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, DuplicatePropertyManager duplicatePropertyManager, String decoderVariable) {
            duplicatePropertyManager.emitReadVariable(builder, prop.property);

            CodeBlock deserializationCode = symbol
                    .deserialize(generatorContext.withSubPath(prop.property.name), decoderVariable, prop.type, expr -> CodeBlock.of("$N = $L;\n", localVariableName, expr));
            builder.add(deserializationCode);
        }
    }
}
