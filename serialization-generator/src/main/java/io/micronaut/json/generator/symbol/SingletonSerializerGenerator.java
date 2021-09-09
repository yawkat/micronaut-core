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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.squareup.javapoet.*;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MnType;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Singleton;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

@Internal
public final class SingletonSerializerGenerator {
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

    private Element originatingElement;

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

    public SingletonSerializerGenerator originatingElement(Element element) {
        this.originatingElement = originatingElement;
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
        assert symbol != null;

        GeneratorContext classContext = GeneratorContext.create(problemReporter, valueReferenceName.toString());

        TypeSpec.Builder builder = TypeSpec.classBuilder(generatedName.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        if (originatingElement != null) {
            Object nativeType = originatingElement.getNativeType();
            if (nativeType instanceof javax.lang.model.element.Element) {
                builder.addOriginatingElement((javax.lang.model.element.Element) nativeType);
            }
        }

        if (serializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueReferenceName))
                    .addMethod(generateSerialize(classContext));

            ConditionExpression<CodeBlock> isEmptyCheck = symbol.shouldIncludeCheck(classContext.newMethodContext("value"), valueType, JsonInclude.Include.NON_EMPTY);
            if (!isEmptyCheck.isAlwaysTrue()) {
                builder.addMethod(MethodSpec.methodBuilder("isEmpty")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(valueReferenceName, "value")
                        .returns(boolean.class)
                        .addCode("return !($L);\n", isEmptyCheck.build(CodeBlock.of("value")))
                        .build());
            }
        }
        if (deserializer) {
            builder.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Deserializer.class), valueReferenceName))
                    .addMethod(generateDeserialize(classContext));

            if (symbol.supportsNullDeserialization()) {
                builder.addMethod(MethodSpec.methodBuilder("supportsNullDeserialization")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean.class)
                        .addCode("return true;\n")
                        .build());
            }
        }

        // add type parameters if necessary
        List<MnType.Variable> freeVariables = new ArrayList<>(valueType.getFreeVariables());
        for (MnType.Variable typeVariable : freeVariables) {
            builder.addTypeVariable(TypeVariableName.get(
                    typeVariable.getName(),
                    typeVariable.getBounds().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new)
            ));
        }

        // generate the fields we need to inject
        classContext.getInjected().forEach((injectable, injected) ->
                builder.addField(injectable.fieldType, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL));

        // generate constructor
        MethodSpec.Builder directConstructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);
        CodeBlock.Builder directConstructorCode = CodeBlock.builder();
        CodeBlock.Builder factoryCode = CodeBlock.builder().add("new $T(", generatedName);
        boolean firstInjected = true;
        for (Map.Entry<GeneratorContext.InjectableSerializerType, GeneratorContext.Injected> entry : classContext.getInjected().entrySet()) {
            GeneratorContext.InjectableSerializerType injectable = entry.getKey();
            GeneratorContext.Injected injected = entry.getValue();

            directConstructor.addParameter(injectable.fieldType, injected.fieldName);
            directConstructorCode.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);

            // for deserialization, we stick to invariant serializers, because we don't want to deserialize an arbitrary
            // subtype from the classpath for security. Contravariant lookup only exposes the supertypes (few), while
            // covariant lookup would expose all subtypes (potentially unlimited).
            String methodName = injectable.forSerialization ? "findContravariantSerializer" : "findInvariantDeserializer";
            if (injectable.provider) {
                methodName += "Provider";
            }
            if (!firstInjected) {
                factoryCode.add(", ");
            }
            firstInjected = false;
            factoryCode.add("locator.$N($L)", methodName, injectable.type.toRuntimeFactory(v -> CodeBlock.of("getTypeParameter.apply($S)", v.getName())));
        }
        factoryCode.add(")");
        directConstructor.addCode(directConstructorCode.build());
        builder.addMethod(directConstructor.build());

        // generate factory
        TypeSpec.Builder factoryType = TypeSpec.classBuilder("FactoryImpl")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(Singleton.class)
                .addAnnotation(BootstrapContextCompatible.class)
                .addField(FieldSpec.builder(Type.class, "TYPE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer(valueType.toRuntimeFactory(v -> CodeBlock.of("$T.class.getTypeParameters()[$L]", generatedName, freeVariables.indexOf(v))))
                        .build())
                .addMethod(MethodSpec.methodBuilder("getGenericType")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(Type.class)
                        .addCode(CodeBlock.of("return TYPE;\n"))
                        .build())
                .addMethod(MethodSpec.methodBuilder("newInstance")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(SerializerLocator.class, "locator")
                        .addParameter(ParameterizedTypeName.get(Function.class, String.class, Type.class), "getTypeParameter")
                        .returns(generatedName)
                        .addCode(CodeBlock.of("return $L;\n", factoryCode.build()))
                        .build());
        if (serializer) {
            factoryType.addSuperinterface(ClassName.get(Serializer.Factory.class));
        }
        if (deserializer) {
            factoryType.addSuperinterface(ClassName.get(Deserializer.Factory.class));
        }
        builder.addType(factoryType.build());

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
                .addParameter(Encoder.class, "encoder")
                .addParameter(valueReferenceName, "value")
                .addException(IOException.class)
                .addCode(symbol.serialize(classContext.newMethodContext("value", "encoder"), "encoder", valueType, CodeBlock.of("value")))
                .build();
    }

    private MethodSpec generateDeserialize(GeneratorContext classContext) {
        assert symbol != null;
        return MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Decoder.class, "decoder")
                .returns(valueReferenceName)
                .addException(IOException.class)
                .addCode(symbol.deserialize(classContext.newMethodContext("decoder"), "decoder", valueType, new SerializerSymbol.Setter() {
                    @Override
                    public CodeBlock createSetStatement(CodeBlock expr) {
                        return CodeBlock.of("return $L;\n", expr);
                    }

                    @Override
                    public boolean terminatesBlock() {
                        return true;
                    }
                }))
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
