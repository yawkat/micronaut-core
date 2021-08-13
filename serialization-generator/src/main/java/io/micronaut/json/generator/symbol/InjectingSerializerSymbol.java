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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.inject.ast.ClassElement;
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
    public void visitDependencies(DependencyVisitor visitor, ClassElement type) {
        visitor.visitInjected(type, provider);
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        // no generics of primitive types!
        return type.isArray() || !type.isPrimitive();
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new InjectingSerializerSymbol(linker, true);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        return CodeBlock.of("$L.serialize($N, $L);\n", getSerializerAccess(generatorContext, type), Names.ENCODER, readExpression);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, ClassElement type, Setter setter) {
        return setter.createSetStatement(CodeBlock.of("$L.deserialize($N)", getSerializerAccess(generatorContext, type), Names.DECODER));
    }

    private CodeBlock getSerializerAccess(GeneratorContext generatorContext, ClassElement type) {
        GeneratorContext.Injectable injectable = new OtherSerializerInjectable(PoetUtil.toTypeName(type), provider);
        CodeBlock accessExpression = generatorContext.requestInjection(injectable).getAccessExpression();
        if (provider) {
            accessExpression = CodeBlock.of("$L.get()", accessExpression);
        }
        return accessExpression;
    }

    private static class OtherSerializerInjectable extends GeneratorContext.Injectable {
        private final TypeName type;
        private final boolean provider;

        public OtherSerializerInjectable(TypeName type, boolean provider) {
            super(
                    provider ? ParameterizedTypeName.get(ClassName.get(Provider.class), ParameterizedTypeName.get(ClassName.get(Serializer.class), type)) :
                            ParameterizedTypeName.get(ClassName.get(Serializer.class), type),
                    ClassName.get(SerializerLocator.class)
            );
            this.type = type;
            this.provider = provider;
        }

        @Override
        protected CodeBlock buildInitializationStatement(CodeBlock parameterExpression, Setter fieldSetter) {
            String methodName = provider ? "findInvariantSerializerProvider" : "findInvariantSerializer";
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
            return provider == that.provider && Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, provider);
        }
    }
}
