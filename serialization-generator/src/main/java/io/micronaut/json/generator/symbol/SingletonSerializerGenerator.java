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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
    private boolean checkProblemReporter = false;
    @Nullable
    private SerializerSymbol symbol = null;
    @Nullable
    private TypeName valueReferenceName = null;
    @Nullable
    private String packageName = null;

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
        return '$' + valueType.getRelativeTypeName(packageName).replaceAll("[. ?<>,]", "_");
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

        return generateClass(true, true, ClassName.get(packageName, prefix() + "$Serializer"));
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

        return Arrays.asList(
                generateClass(true, false, ClassName.get(packageName, prefix() + "$Serializer")),
                generateClass(false, true, ClassName.get(packageName, prefix() + "$Deserializer"))
        );
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
        for (MnType.Variable typeVariable : valueType.getFreeVariables()) {
            builder.addTypeVariable(TypeVariableName.get(
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
            builder.addField(injectable.fieldType, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL);
            SerializerSymbol.Setter fieldSetter = expr -> CodeBlock.of("this.$N = $L", injected.fieldName, expr);
            constructorCodeBuilder.add(injectable.buildInitializationStatement(CodeBlock.of("$N", paramName), fieldSetter));
        });
        constructorBuilder.addCode(constructorCodeBuilder.build());
        builder.addMethod(constructorBuilder.build());

        if (generateDirectConstructor && !injectedParameters.isEmpty()) {
            MethodSpec.Builder directConstructor = MethodSpec.constructorBuilder();
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
