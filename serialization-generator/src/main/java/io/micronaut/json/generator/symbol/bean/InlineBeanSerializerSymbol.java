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
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.*;

import java.util.*;

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

    BeanDefinition introspect(ProblemReporter problemReporter, GeneratorType type, boolean forSerialization) {
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

    SerializerSymbol findSymbol(PropWithType prop) {
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

        DeserializationEntity entity = DeserializationEntity.introspect(this, generatorContext, type);

        // if there were failures, the entity may be in an inconsistent state, so we avoid codegen.
        if (generatorContext.getProblemReporter().isFailed()) {
            return CodeBlock.of("");
        }

        entity.allocateLocals(generatorContext, "result");

        CodeBlock.Builder builder = CodeBlock.builder();
        entity.generatePrologue(builder);

        DuplicatePropertyManager duplicatePropertyManager;
        Set<BeanDefinition.Property> propertiesForDuplicateDetection = entity.collectPropertiesForDuplicateDetection();
        if (propertiesForDuplicateDetection.isEmpty()) {
            duplicatePropertyManager = null;
        } else {
            duplicatePropertyManager = new DuplicatePropertyManager(generatorContext, propertiesForDuplicateDetection, decoderVariable);
            duplicatePropertyManager.emitMaskDeclarations(builder);
        }

        entity.deserialize(generatorContext, builder, duplicatePropertyManager, decoderVariable);

        entity.generateEpilogue(builder, duplicatePropertyManager);

        builder.add(setter.createSetStatement(CodeBlock.of("$N", entity.localVariableName)));
        return builder.build();
    }
}
