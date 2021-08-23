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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.squareup.javapoet.*;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MnType;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;

import static io.micronaut.json.generator.symbol.Names.DECODER;
import static io.micronaut.json.generator.symbol.Names.ENCODER;

@Internal
public final class SingletonSerializerGenerator {
    /**
     * Generate a constructor that directly writes the necessary fields, without going through {@link SerializerLinker}.
     * Useful for testing
     */
    static boolean generateDirectConstructor = false;

    private final GeneratorType valueType;
    @Nullable
    private ProblemReporter problemReporter = null;
    private boolean checkProblemReporter = false;
    @Nullable
    private SerializerSymbol symbol = null;
    @Nullable
    private TypeName valueReferenceName = null;
    @Nullable
    private String packageName = null;
    private boolean generateSerializer = true;
    private boolean generateDeserializer = true;

    private SingletonSerializerGenerator(GeneratorType valueType) {
        this.valueType = valueType;
    }

    public static SingletonSerializerGenerator create(ClassElement valueType) {
        return create(GeneratorType.ofClass(valueType));
    }

    public static SingletonSerializerGenerator create(GeneratorType valueType) {
        return new SingletonSerializerGenerator(valueType);
    }

    public SingletonSerializerGenerator problemReporter(ProblemReporter problemReporter) {
        this.problemReporter = problemReporter;
        return this;
    }

    /**
     * symbol to use for serialization
     */
    public SingletonSerializerGenerator symbol(SerializerSymbol symbol) {
        this.symbol = symbol;
        return this;
    }

    public SingletonSerializerGenerator linker(SerializerLinker linker) {
        return symbol(linker.findSymbol(valueType));
    }

    /**
     * type name to use for the value being serialized, must be a reference type
     */
    public SingletonSerializerGenerator valueReferenceName(TypeName valueReferenceName) {
        this.valueReferenceName = valueReferenceName;
        return this;
    }

    /**
     * Package of the generated classes
     */
    public SingletonSerializerGenerator packageName(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public SingletonSerializerGenerator generateSerializer(boolean generateSerializer) {
        this.generateSerializer = generateSerializer;
        return this;
    }

    public SingletonSerializerGenerator generateDeserializer(boolean generateDeserializer) {
        this.generateDeserializer = generateDeserializer;
        return this;
    }

    private void fillInMissingFields() {
        if (problemReporter == null) {
            problemReporter = new ProblemReporter();
            checkProblemReporter = true;
        }
        if (valueReferenceName == null) {
            if (!valueType.isArray() && valueType.isPrimitive()) {
                throw new IllegalStateException("For primitives, must pass a separate valueReferenceName");
            }
            valueReferenceName = PoetUtil.toTypeName(valueType);
            assert valueReferenceName != null;
        }
        if (symbol == null) {
            throw new IllegalStateException("Must pass a symbol or a linker");
        }
        if (packageName == null) {
            packageName = valueType.getRawClass().getPackageName();
        }
    }

    private String prefix() {
        return '$' + valueType.getRelativeTypeName(packageName).replaceAll("[. ?<>,\\[\\]]", "_");
    }

    /**
     * Generate this serializer as a single class implementing both the serializer and deserializer.
     */
    public GenerationResult generateSingle() {
        fillInMissingFields();
        assert valueReferenceName != null;
        assert symbol != null;
        assert packageName != null;
        assert problemReporter != null;

        return generateClass(generateSerializer, generateDeserializer, ClassName.get(packageName, prefix() + "$Serializer"));
    }

    /**
     * Generate this serializer as multiple classes.
     */
    public List<GenerationResult> generateMulti() {
        fillInMissingFields();
        assert valueReferenceName != null;
        assert symbol != null;
        assert packageName != null;
        assert problemReporter != null;

        List<GenerationResult> results = new ArrayList<>(2);
        if (generateSerializer) {
            results.add(generateClass(true, false, ClassName.get(packageName, prefix() + "$Serializer")));
        }
        if (generateDeserializer) {
            results.add(generateClass(false, true, ClassName.get(packageName, prefix() + "$Deserializer")));
        }
        return results;
    }

    private GenerationResult generateClass(boolean serializer, boolean deserializer, ClassName generatedName) {
        assert valueReferenceName != null;

        GeneratorContext classContext = GeneratorContext.create(problemReporter, valueReferenceName.toString());

        TypeSpec.Builder builder = TypeSpec.classBuilder(generatedName.simpleName())
                .addAnnotation(Secondary.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        if (serializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueReferenceName))
                    .addMethod(generateSerialize(classContext));
        }
        if (deserializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Deserializer.class), valueReferenceName))
                    .addMethod(generateDeserialize(classContext));
        }

        // add type parameters if necessary
        Set<MnType.Variable> freeVariables = valueType.getFreeVariables();
        for (MnType.Variable typeVariable : freeVariables) {
            builder.addTypeVariable(TypeVariableName.get(
                    typeVariable.getName(),
                    typeVariable.getBounds().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new)
            ));
        }

        // generate the fields we need to inject
        classContext.getInjected().forEach((injectable, injected) ->
                builder.addField(injectable.fieldType, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL));

        // normal constructor (@Inject, one SerializerLocator parameter)
        if (freeVariables.isEmpty()) {
            MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Inject.class);
            if (!classContext.getInjected().isEmpty()) {
                constructorBuilder.addParameter(TypeName.get(SerializerLocator.class), "locator");
                CodeBlock.Builder constructorCodeBuilder = CodeBlock.builder();
                classContext.getInjected().forEach((injectable, injected) -> {
                    // for deserialization, we stick to invariant serializers, because we don't want to deserialize an arbitrary
                    // subtype from the classpath for security. Contravariant lookup only exposes the supertypes (few), while
                    // covariant lookup would expose all subtypes (potentially unlimited).
                    String methodName = injectable.forSerialization ? "findContravariantSerializer" : "findInvariantDeserializer";
                    if (injectable.provider) {
                        methodName += "Provider";
                    }
                    constructorCodeBuilder.addStatement("this.$N = locator.$N(new $T<$T>() {});\n", injected.fieldName, methodName, GenericTypeToken.class, injectable.type);
                });
                constructorBuilder.addCode(constructorCodeBuilder.build());
            }
            builder.addMethod(constructorBuilder.build());
        }

        // if we have free type variables, we need a constructor SerializerLocator can use
        if ((generateDirectConstructor && !classContext.getInjected().isEmpty()) || !freeVariables.isEmpty()) {
            MethodSpec.Builder directConstructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC);
            CodeBlock.Builder directConstructorCode = CodeBlock.builder();
            classContext.getInjected().forEach((injectable, injected) -> {
                directConstructor.addParameter(injectable.fieldType, injected.fieldName);
                directConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);
            });
            directConstructor.addCode(directConstructorCode.build());
            builder.addMethod(directConstructor.build());
        }

        JavaFile generatedFile = JavaFile.builder(generatedName.packageName(), builder.build()).build();

        if (checkProblemReporter) {
            assert problemReporter != null;
            problemReporter.throwOnFailures();
        }

        return new GenerationResult(generatedName, generatedFile);
    }

    private MethodSpec generateSerialize(GeneratorContext classContext) {
        assert symbol != null;
        assert valueReferenceName != null;
        return MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonGenerator.class, ENCODER)
                .addParameter(valueReferenceName, "value")
                .addException(IOException.class)
                .addCode(symbol.serialize(classContext.newMethodContext("value", ENCODER), valueType, CodeBlock.of("value")))
                .build();
    }

    private MethodSpec generateDeserialize(GeneratorContext classContext) {
        assert symbol != null;
        return MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonParser.class, DECODER)
                .returns(valueReferenceName)
                .addException(IOException.class)
                .addCode(symbol.deserialize(classContext.newMethodContext(DECODER), valueType, expr -> CodeBlock.of("return $L;\n", expr)))
                .build();
    }

    public static final class GenerationResult {
        private final ClassName serializerClassName;
        private final JavaFile generatedFile;

        private GenerationResult(ClassName serializerClassName, JavaFile generatedFile) {
            this.serializerClassName = serializerClassName;
            this.generatedFile = generatedFile;
        }

        public ClassName getSerializerClassName() {
            return serializerClassName;
        }

        public JavaFile getGeneratedFile() {
            return generatedFile;
        }
    }
}
