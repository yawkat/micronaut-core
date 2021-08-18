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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Provider;

import java.util.Objects;

final class InjectingSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;

    /**
     * Whether to wrap the injection with a {@link BeanProvider}.
     */
    private final boolean provider;

    public InjectingSerializerSymbol(SerializerLinker linker) {
        this(linker, false);
    }

    private InjectingSerializerSymbol(SerializerLinker linker, boolean provider) {
        this.linker = linker;
        this.provider = provider;
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        visitor.visitInjected(type, provider);
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        // no generics of primitive types!
        return type.isArray() || !type.isPrimitive();
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new InjectingSerializerSymbol(linker, true);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        return CodeBlock.of("$L.serialize($N, $L);\n", getSerializerAccess(generatorContext, type, true), Names.ENCODER, readExpression);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        return setter.createSetStatement(CodeBlock.of("$L.deserialize($N)", getSerializerAccess(generatorContext, type, false), Names.DECODER));
    }

    private CodeBlock getSerializerAccess(GeneratorContext generatorContext, GeneratorType type, boolean forSerialization) {
        GeneratorContext.Injectable injectable = new OtherSerializerInjectable(PoetUtil.toTypeName(type), provider, forSerialization);
        CodeBlock accessExpression = generatorContext.requestInjection(injectable).getAccessExpression();
        if (provider) {
            accessExpression = CodeBlock.of("$L.get()", accessExpression);
        }
        return accessExpression;
    }

    private static class OtherSerializerInjectable extends GeneratorContext.Injectable {
        private final TypeName type;
        private final boolean provider;
        private final boolean forSerialization;

        public OtherSerializerInjectable(TypeName type, boolean provider, boolean forSerialization) {
            super(fieldType(type, provider, forSerialization), ClassName.get(SerializerLocator.class));
            this.type = type;
            this.provider = provider;
            this.forSerialization = forSerialization;
        }

        private static TypeName fieldType(TypeName type, boolean provider, boolean forSerialization) {
            ParameterizedTypeName serType;
            if (forSerialization) {
                serType = ParameterizedTypeName.get(ClassName.get(Serializer.class), WildcardTypeName.supertypeOf(type));
            } else {
                serType = ParameterizedTypeName.get(ClassName.get(Deserializer.class), WildcardTypeName.subtypeOf(type));
            }
            if (provider) {
                serType = ParameterizedTypeName.get(ClassName.get(Provider.class), WildcardTypeName.subtypeOf(serType));
            }
            return serType;
        }

        @Override
        protected CodeBlock buildInitializationStatement(CodeBlock parameterExpression, Setter fieldSetter) {
            // for deserialization, we stick to invariant serializers, because we don't want to deserialize an arbitrary
            // subtype from the classpath for security. Contravariant lookup only exposes the supertypes (few), while
            // covariant lookup would expose all subtypes (potentially unlimited).
            String methodName = forSerialization ? "findContravariantSerializer" : "findInvariantDeserializer";
            if (provider) {
                methodName += "Provider";
            }
            return fieldSetter.createSetStatement(CodeBlock.of("$L.$N(new $T<$T>() {});\n", parameterExpression, methodName, GenericTypeToken.class, type));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OtherSerializerInjectable that = (OtherSerializerInjectable) o;
            return provider == that.provider && forSerialization == that.forSerialization && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, provider, forSerialization);
        }
    }
}
