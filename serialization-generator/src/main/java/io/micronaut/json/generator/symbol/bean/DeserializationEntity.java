package io.micronaut.json.generator.symbol.bean;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.json.Decoder;
import io.micronaut.json.generated.JsonParseException;
import io.micronaut.json.generator.symbol.GeneratorContext;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.PoetUtil;
import io.micronaut.json.generator.symbol.SerializerSymbol;

import java.util.*;
import java.util.function.Function;
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

    void generateEpilogue(CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
    }

    CodeBlock deserializeTopLevel(
            GeneratorContext generatorContext,
            String decoderVariable,
            SerializerSymbol.Setter setter
    ) {
        if (!hasProperties) {
            throw new UnsupportedOperationException();
        }
        allocateLocals(generatorContext, "result");

        CodeBlock.Builder builder = CodeBlock.builder();
        generatePrologue(builder);

        InlineBitSet<DeserializationEntity> readProperties;
        Set<DeserializationEntity> propertiesForDuplicateDetection = collectPropertiesForDuplicateDetection();
        if (propertiesForDuplicateDetection.isEmpty()) {
            readProperties = null;
        } else {
            readProperties = new InlineBitSet<>(generatorContext, propertiesForDuplicateDetection, "readProperties");
            readProperties.emitMaskDeclarations(builder);
        }

        String elementDecoderVariable = generatorContext.newLocalVariable("elementDecoder");
        builder.addStatement("$T $N = $N.decodeObject()", Decoder.class, elementDecoderVariable, decoderVariable);

        // main parse loop
        builder.beginControlFlow("while (true)");
        String fieldNameVariable = generatorContext.newLocalVariable("fieldName");
        builder.addStatement("$T $N = $N.decodeKey()", String.class, fieldNameVariable, elementDecoderVariable);
        builder.add("if ($N == null) break;\n", fieldNameVariable);
        builder.beginControlFlow("switch ($N)", fieldNameVariable);

        Map<String, ? extends DeserializationEntity> collectedProperties = collectProperties();
        Map<DeserializationEntity, List<String>> collectedPropertiesReverse = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends DeserializationEntity> entry : collectedProperties.entrySet()) {
            collectedPropertiesReverse.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<DeserializationEntity, List<String>> entry : collectedPropertiesReverse.entrySet()) {
            for (String alias : entry.getValue()) {
                builder.add("case $S:\n", alias);
            }
            builder.indent();
            entry.getKey().deserialize(generatorContext, builder, readProperties, elementDecoderVariable);
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

        generateEpilogue(builder, readProperties, decoderVariable);

        builder.add(setter.createSetStatement(CodeBlock.of("$N", localVariableName)));
        return builder.build();
    }

    void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
        throw new UnsupportedOperationException();
    }

    void onUnknownProperty(CodeBlock.Builder builder, String keyVariable, String elementDecoderVariable) {
        throw new UnsupportedOperationException();
    }

    Map<String, ? extends DeserializationEntity> collectProperties() {
        throw new UnsupportedOperationException();
    }

    Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
        return Collections.emptySet();
    }

    boolean isStructurallyIdentical(InlineBeanSerializerSymbol context, DeserializationEntity other) {
        return false;
    }

    void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
    }

    static DeserializationEntity introspect(InlineBeanSerializerSymbol symbol, GeneratorContext generatorContext, GeneratorType type) {
        BeanDefinition def = symbol.introspect(generatorContext.getProblemReporter(), type, false);
        if (def.creatorDelegatingProperty != null) {
            PropWithType singleProp = PropWithType.fromContext(type, def.creatorDelegatingProperty);
            return new Delegating(type, def, singleProp, symbol.findSymbol(singleProp));
        } else if (def.subtyping != null) {
            if (def.subtyping.deduce) {
                return new SubtypingFlat(symbol, generatorContext, type, def);
            }
            switch (def.subtyping.as) {
                case WRAPPER_ARRAY:
                case WRAPPER_OBJECT:
                    return new SubtypingWrapper(
                            type,
                            def.subtyping.subTypeNames.entrySet().stream()
                                    .collect(Collectors.toMap(e -> introspect(symbol, generatorContext, e.getKey()), Map.Entry::getValue)),
                            def.subtyping.as == JsonTypeInfo.As.WRAPPER_ARRAY
                    );
                case PROPERTY:
                    return new SubtypingFlat(symbol, generatorContext, type, def);
                default:
                    throw new AssertionError(); // todo
            }
        } else {
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
    }

    private static void onUnknownPropertyImpl(CodeBlock.Builder builder, BeanDefinition definition, GeneratorType type, String decoder, String keyVariable) {
        if (definition.ignoreUnknownProperties) {
            builder.addStatement("$N.skipValue()", decoder);
        } else {
            // todo: do we really want to output a potentially attacker-controlled field name to the logs here?
            builder.addStatement("throw $T.from($N, $S + $N)",
                    JsonParseException.class, decoder, "Unknown property for type " + type.getTypeName() + ": ", keyVariable);
        }
    }

    /**
     * Delegate to a single property. Used for delegating JsonCreator.
     */
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
        public void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            builder.add(symbol.deserialize(
                    generatorContext, decoderVariable,
                    prop.type,
                    expr -> CodeBlock.of("$N = $L;\n", localVariableName, getCreatorCall(type, beanDefinition, expr))));
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
        void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> entry : elements.entrySet()) {
                entry.setValue(function.apply(entry.getValue()));
                entry.getValue().updateChildren(function);
            }
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
        Map<String, ? extends DeserializationEntity> collectProperties() {
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
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return elements.values().stream()
                    .flatMap(entity -> entity.collectPropertiesForDuplicateDetection().stream())
                    .collect(Collectors.toSet());
        }

        @Override
        void onUnknownProperty(CodeBlock.Builder builder, String keyVariable, String elementDecoderVariable) {
            onUnknownPropertyImpl(builder, definition, type, elementDecoderVariable, keyVariable);
        }

        @Override
        void generateEpilogue(CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            for (DeserializationEntity value : elements.values()) {
                value.generateEpilogue(builder, readProperties, decoderVariable);
            }

            Set<DeserializationEntity> required = definition.props.stream().filter(BeanDefinition.Property::isRequired).map(elements::get).collect(Collectors.toSet());
            if (!required.isEmpty()) {
                readProperties.onMissing(builder, required.stream().collect(Collectors.toMap(
                        req -> req,
                        req -> CodeBlock.of("throw $T.from($N, $S);\n",
                                JsonParseException.class,
                                decoderVariable,
                                "Missing property")
                )));
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
                CodeBlock expressionHasBeenRead = prop.unwrapped || prop.isRequired() ? null : readProperties.isSet(elements.get(prop));

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

        private boolean declared = false;

        SimpleLeafProperty(PropWithType prop, SerializerSymbol symbol) {
            this.prop = prop;
            this.symbol = symbol;

            presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            if (!declared) {
                builder.addStatement("$T $N = $L", PoetUtil.toTypeName(prop.type), localVariableName, symbol.getDefaultExpression(prop.type));
                declared = true;
            }
        }

        @Override
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return Collections.singleton(this);
        }

        @Override
        boolean isStructurallyIdentical(InlineBeanSerializerSymbol context, DeserializationEntity other) {
            return other instanceof SimpleLeafProperty &&
                    context.areStructurallyIdentical(this.prop, ((SimpleLeafProperty) other).prop);
        }

        @Override
        void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            builder.add(
                    "if ($L) throw $T.from($N, $S);\n",
                    readProperties.isSet(this),
                    JsonParseException.class,
                    decoderVariable,
                    "Duplicate property " + prop.property.name
            );
            readProperties.set(builder, this);

            CodeBlock deserializationCode = symbol
                    .deserialize(generatorContext.withSubPath(prop.property.name), decoderVariable, prop.type, expr -> CodeBlock.of("$N = $L;\n", localVariableName, expr));
            builder.add(deserializationCode);
        }
    }

    /**
     * WRAPPER_OBJECT, WRAPPER_ARRAY
     */
    private static class SubtypingWrapper extends DeserializationEntity {
        private final GeneratorType superType;
        private final Map<DeserializationEntity, Collection<String>> subTypes;
        private final boolean array;

        SubtypingWrapper(GeneratorType superType, Map<DeserializationEntity, Collection<String>> subTypes, boolean array) {
            this.superType = superType;
            this.subTypes = subTypes;
            this.array = array;

            hasProperties = false;
            presentAsJava = false; // don't support saving to a local variable
        }

        @Override
        CodeBlock deserializeTopLevel(GeneratorContext generatorContext, String decoderVariable, SerializerSymbol.Setter setter) {
            CodeBlock.Builder builder = CodeBlock.builder();

            String tmpVariable;
            SerializerSymbol.Setter tmpSetter;
            if (setter.terminatesBlock()) {
                tmpVariable = generatorContext.newLocalVariable("result");
                builder.addStatement("$T $N;", PoetUtil.toTypeName(superType), tmpVariable);
                tmpSetter = expr -> CodeBlock.of("$N = $L;\n", tmpVariable, expr);
            } else {
                tmpVariable = null;
                tmpSetter = setter;
            }

            String wrapperDecoder = generatorContext.newLocalVariable("wrapperDecoder");
            builder.addStatement("$T $N = $N.$N()", Decoder.class, wrapperDecoder, decoderVariable, array ? "decodeArray" : "decodeObject");

            if (array) {
                builder.beginControlFlow("switch ($N.decodeString())", wrapperDecoder);
            } else {
                String tagVar = generatorContext.newLocalVariable("tag");
                builder.addStatement("$T $N = $N.decodeKey()", String.class, tagVar, wrapperDecoder);
                builder.addStatement("if ($N == null) throw $T.from($N, \"Expected type tag, but got object end\")", tagVar, JsonParseException.class, wrapperDecoder);
                builder.beginControlFlow("switch ($N)", tagVar);
            }
            for (Map.Entry<DeserializationEntity, Collection<String>> entry : subTypes.entrySet()) {
                for (String alias : entry.getValue()) {
                    builder.add("case $S:\n", alias);
                }
                builder.indent();
                builder.add(entry.getKey().deserializeTopLevel(generatorContext, wrapperDecoder, tmpSetter));
                builder.addStatement("break");
                builder.unindent();
            }
            builder.add("default:\n");
            builder.indent();
            builder.addStatement("throw $T.from($N, \"Unknown type tag\")", JsonParseException.class, wrapperDecoder);
            builder.unindent();
            builder.endControlFlow();

            builder.addStatement("$N.finishStructure()", wrapperDecoder);

            if (tmpVariable != null) {
                builder.add(setter.createSetStatement(CodeBlock.of("$L", tmpVariable)));
            }

            return builder.build();
        }
    }

    /**
     * PROPERTY, DEDUCTION
     */
    private static class SubtypingFlat extends DeserializationEntity {
        private final GeneratorType superType;
        private final BeanDefinition definition;
        private final Collection<DeserializationEntity> subTypes;

        private final InlineBitSet<DeserializationEntity> possibleTypes;

        private final String tagPropertyName;
        @Nullable
        private final TypeTagProperty tagProperty;

        private final Map<String, AmbiguousProperty> ambiguousProperties;

        SubtypingFlat(
                InlineBeanSerializerSymbol symbol,
                GeneratorContext context,
                GeneratorType superType,
                BeanDefinition definition
        ) {
            this.superType = superType;
            this.definition = definition;
            Map<GeneratorType, DeserializationEntity> subTypeEntities = definition.subtyping.subTypes.stream()
                    .collect(Collectors.toMap(t -> t, t -> introspect(symbol, context, t)));
            this.subTypes = subTypeEntities.values();

            tagPropertyName = definition.subtyping.propertyName;
            if (definition.subtyping.deduce) {
                tagProperty = null;
            } else {
                tagProperty = new TypeTagProperty(definition.subtyping.subTypeNames.entrySet().stream()
                        .collect(Collectors.toMap(e -> subTypeEntities.get(e.getKey()), Map.Entry::getValue)));
            }

            possibleTypes = new InlineBitSet<>(context, subTypes, "possibleTypes_" + superType.getTypeName());

            ambiguousProperties = new LinkedHashMap<>();
            for (DeserializationEntity subType : subTypes) {
                Map<String, ? extends DeserializationEntity> subTypeProperties = subType.collectProperties();
                for (Map.Entry<String, ? extends DeserializationEntity> entry : subTypeProperties.entrySet()) {
                    ambiguousProperties.computeIfAbsent(entry.getKey(), k -> new AmbiguousProperty())
                            .paths.add(new Path(subType, entry.getValue()));
                }
            }
            for (AmbiguousProperty ambiguousProperty : ambiguousProperties.values()) {
                ambiguousProperty.unite(symbol);
            }

            hasProperties = true;
            presentAsJava = true;
        }

        @Override
        void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
            for (DeserializationEntity subType : subTypes) {
                subType.updateChildren(function);
            }
        }

        @Override
        void allocateLocals(GeneratorContext context, String ownNameHint) {
            super.allocateLocals(context, ownNameHint);

            for (DeserializationEntity subType : subTypes) {
                subType.allocateLocals(context, "subType");
            }
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            possibleTypes.emitMaskDeclarations(builder, true);
            for (DeserializationEntity subType : subTypes) {
                subType.generatePrologue(builder);
            }
        }

        @Override
        Map<String, ? extends DeserializationEntity> collectProperties() {
            if (tagProperty != null) {
                Map<String, DeserializationEntity> all = new LinkedHashMap<>();
                all.put(tagPropertyName, tagProperty);
                all.putAll(ambiguousProperties);
                return all;
            } else {
                return ambiguousProperties;
            }
        }

        @Override
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return subTypes.stream()
                    .flatMap(t -> t.collectPropertiesForDuplicateDetection().stream())
                    .collect(Collectors.toSet());
        }

        @Override
        void generateEpilogue(CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            builder.addStatement("$T $N", PoetUtil.toTypeName(superType), localVariableName);

            builder.beginControlFlow("if ($L > 1)", possibleTypes.bitCount());
            builder.addStatement("throw $T.from($N, \"Ambiguous type\")", JsonParseException.class, decoderVariable);
            for (DeserializationEntity subType : subTypes) {
                builder.nextControlFlow("else if ($L)", possibleTypes.isSet(subType));
                subType.generateEpilogue(builder, readProperties, decoderVariable);
                builder.addStatement("$N = $N", localVariableName, subType.localVariableName);
            }
            builder.nextControlFlow("else");
            builder.addStatement("throw $T.from($N, \"No matching type candidate\")", JsonParseException.class, decoderVariable);
            builder.endControlFlow();
        }

        @Override
        void onUnknownProperty(CodeBlock.Builder builder, String keyVariable, String elementDecoderVariable) {
            // todo: ignoreUnknownProperties isn't actually populated for subtype handling
            onUnknownPropertyImpl(builder, definition, superType, elementDecoderVariable, keyVariable);
        }

        private class AmbiguousProperty extends DeserializationEntity {
            final List<Path> paths = new ArrayList<>();

            @Override
            void generatePrologue(CodeBlock.Builder builder) {
                throw new UnsupportedOperationException();
            }

            /**
             * Unite properties of different types that are structurally identical
             */
            void unite(InlineBeanSerializerSymbol symbol) {
                // this is potentially n² in the number of subtypes, but only if they have identically named properties
                // that all differ structurally
                for (int i = 0; i < paths.size(); i++) {
                    Path from = paths.get(i);
                    for (int j = 0; j < i; j++) {
                        Path into = paths.get(j);
                        if (into.mergeFrom(symbol, from)) {
                            paths.remove(i);
                            i--;
                            break;
                        }
                    }
                }
            }

            @Override
            void generateEpilogue(CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
                throw new UnsupportedOperationException();
            }

            @Override
            Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
                return paths.stream().flatMap(p -> p.property.collectPropertiesForDuplicateDetection().stream()).collect(Collectors.toSet());
            }

            @Override
            void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
                boolean first = true;
                for (Path path : paths) {
                    // is this path still a candidate?
                    if (first) {
                        builder.beginControlFlow("if ($L)", possibleTypes.anySet(path.subTypes));
                    } else {
                        builder.nextControlFlow("else if ($L)", possibleTypes.anySet(path.subTypes));
                    }
                    first = false;

                    // check there are no other possibilities
                    Set<DeserializationEntity> otherTypes = paths.stream().filter(p -> p != path).flatMap(p -> p.subTypes.stream()).collect(Collectors.toSet());
                    if (!otherTypes.isEmpty()) {
                        builder.addStatement(
                                "if ($L) throw $T.from($N, \"Ambiguous property\")",
                                possibleTypes.anySet(otherTypes),
                                JsonParseException.class, decoderVariable
                        );
                    }

                    // narrow the possible types
                    possibleTypes.and(builder, path.subTypes);

                    path.property.deserialize(generatorContext, builder, readProperties, decoderVariable);
                }
                builder.nextControlFlow("else");
                // todo: is this technically an unknown property and should use that handling?
                builder.addStatement(
                        "throw $T.from($N, \"Property not allowed for these types\")",
                        JsonParseException.class, decoderVariable
                );
                builder.endControlFlow();
            }
        }

        private static class Path {
            final Collection<DeserializationEntity> subTypes;
            final DeserializationEntity property;

            Path(DeserializationEntity subType, DeserializationEntity property) {
                this.subTypes = new ArrayList<>();
                this.subTypes.add(subType);
                this.property = property;
            }

            Path(Collection<DeserializationEntity> subTypes, DeserializationEntity property) {
                this.subTypes = subTypes;
                this.property = property;
            }

            boolean mergeFrom(InlineBeanSerializerSymbol symbol, Path other) {
                if (property.isStructurallyIdentical(symbol, other.property)) {
                    subTypes.addAll(other.subTypes);

                    for (DeserializationEntity subType : other.subTypes) {
                        // replace occurences of other.property with our property
                        subType.updateChildren(ent -> {
                            if (ent == other.property) {
                                return property;
                            } else {
                                return ent;
                            }
                        });
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }

        private class TypeTagProperty extends DeserializationEntity {
            private final Map<DeserializationEntity, Collection<String>> tags;

            TypeTagProperty(Map<DeserializationEntity, Collection<String>> tags) {
                this.tags = tags;

                presentAsJava = false;
                hasProperties = false;
            }

            @Override
            void deserialize(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
                builder.beginControlFlow("switch ($N.decodeString())", decoderVariable);

                for (Map.Entry<DeserializationEntity, Collection<String>> entry : tags.entrySet()) {
                    for (String alias : entry.getValue()) {
                        builder.add("case $S:\n", alias);
                    }
                    builder.indent();
                    possibleTypes.and(builder, Collections.singleton(entry.getKey()));
                    builder.addStatement("break");
                    builder.unindent();
                }

                builder.add("default:\n");
                builder.indent();
                builder.addStatement("throw $T.from($N, \"Unknown type tag\")", JsonParseException.class, decoderVariable);
                builder.unindent();
                builder.endControlFlow();
            }
        }
    }
}
