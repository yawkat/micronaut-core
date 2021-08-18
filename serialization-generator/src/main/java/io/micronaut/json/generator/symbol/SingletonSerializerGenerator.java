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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MnType;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import jakarta.inject.Inject;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
    @Nullable
    private SerializerSymbol symbol = null;
    @Nullable
    private TypeName valueReferenceName = null;
    @Nullable
    private ClassName generatedSerializerName = null;

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
     * FQCN of the generated serializer class
     */
    public SingletonSerializerGenerator generatedSerializerName(ClassName generatedSerializerName) {
        this.generatedSerializerName = generatedSerializerName;
        return this;
    }

    public GenerationResult generate() {
        boolean checkProblemReporter = false;
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
        if (generatedSerializerName == null) {
            String pkg = valueType.getRawClass().getPackageName();
            generatedSerializerName = ClassName.get(
                    pkg,
                    '$' + valueType.getRelativeTypeName(pkg).replaceAll("[. ?<>]", "_") + "$Serializer");
            assert generatedSerializerName != null;
        }

        GeneratorContext classContext = GeneratorContext.create(problemReporter, valueReferenceName.toString());

        MethodSpec deserialize = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonParser.class, DECODER)
                .returns(valueReferenceName)
                .addException(IOException.class)
                .addCode(symbol.deserialize(classContext.newMethodContext(DECODER), valueType, expr -> CodeBlock.of("return $L;\n", expr)))
                .build();

        MethodSpec serialize = MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonGenerator.class, ENCODER)
                .addParameter(valueReferenceName, "value")
                .addException(IOException.class)
                .addCode(symbol.serialize(classContext.newMethodContext("value", ENCODER), valueType, CodeBlock.of("value")))
                .build();

        TypeSpec.Builder serializer = TypeSpec.classBuilder(generatedSerializerName.simpleName())
                .addAnnotation(Secondary.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueReferenceName))
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Deserializer.class), valueReferenceName))
                .addMethod(serialize)
                .addMethod(deserialize);

        // add type parameters if necessary
        for (MnType.Variable typeVariable : valueType.getFreeVariables()) {
            serializer.addTypeVariable(TypeVariableName.get(
                    typeVariable.getName(),
                    typeVariable.getBounds().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new)
            ));
        }

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Inject.class);
        CodeBlock.Builder constructorCodeBuilder = CodeBlock.builder();
        NameAllocator paramNameAllocator = new NameAllocator();
        Map<TypeName, String> injectedParameters = new HashMap<>();
        classContext.getInjected().forEach((injectable, injected) -> {
            String paramName = injectedParameters.computeIfAbsent(injectable.parameterType, t -> {
                String name = paramNameAllocator.newName(t.toString(), t);
                constructorBuilder.addParameter(t, name);
                return name;
            });
            serializer.addField(injectable.fieldType, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL);
            SerializerSymbol.Setter fieldSetter = expr -> CodeBlock.of("this.$N = $L", injected.fieldName, expr);
            constructorCodeBuilder.add(injectable.buildInitializationStatement(CodeBlock.of("$N", paramName), fieldSetter));
        });
        constructorBuilder.addCode(constructorCodeBuilder.build());
        serializer.addMethod(constructorBuilder.build());

        if (generateDirectConstructor && !injectedParameters.isEmpty()) {
            MethodSpec.Builder directConstructor = MethodSpec.constructorBuilder();
            CodeBlock.Builder directConstructorCode = CodeBlock.builder();
            classContext.getInjected().forEach((injectable, injected) -> {
                directConstructor.addParameter(injectable.fieldType, injected.fieldName);
                directConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);
            });
            directConstructor.addCode(directConstructorCode.build());
            serializer.addMethod(directConstructor.build());
        }

        JavaFile generatedFile = JavaFile.builder(generatedSerializerName.packageName(), serializer.build()).build();

        if (checkProblemReporter) {
            problemReporter.throwOnFailures();
        }

        return new GenerationResult(generatedSerializerName, generatedFile);
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
