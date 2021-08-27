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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;

@Internal
public final class InjectingSerializerSymbol implements SerializerSymbol {
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
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
        if (inclusionPolicy == JsonInclude.Include.NON_EMPTY) {
            return ConditionExpression.of(expr -> CodeBlock.of("!$L.isEmpty($L)", getSerializerAccess(generatorContext, type, true), expr));
        }
        return SerializerSymbol.super.shouldIncludeCheck(generatorContext, type, inclusionPolicy);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        return setter.createSetStatement(CodeBlock.of("$L.deserialize($N)", getSerializerAccess(generatorContext, type, false), decoderVariable));
    }

    private CodeBlock getSerializerAccess(GeneratorContext generatorContext, GeneratorType type, boolean forSerialization) {
        GeneratorContext.InjectableSerializerType injectable = new GeneratorContext.InjectableSerializerType(type, provider, forSerialization);
        CodeBlock accessExpression = generatorContext.requestInjection(injectable).getAccessExpression();
        if (provider) {
            accessExpression = CodeBlock.of("$L.get()", accessExpression);
        }
        return accessExpression;
    }
}
