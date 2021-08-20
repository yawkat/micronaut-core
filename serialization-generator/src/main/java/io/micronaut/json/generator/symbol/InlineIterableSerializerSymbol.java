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

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.Serializer;
import io.micronaut.json.generated.JsonParseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.micronaut.json.generator.symbol.Names.DECODER;
import static io.micronaut.json.generator.symbol.Names.ENCODER;

/**
 * {@link SerializerSymbol} that deserializes iterables (and arrays) inline, i.e. without a separate
 * {@link Serializer} implementation.
 */
abstract class InlineIterableSerializerSymbol extends AbstractInlineContainerSerializerSymbol implements SerializerSymbol {

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        super(linker);
    }

    private InlineIterableSerializerSymbol(InlineIterableSerializerSymbol original, boolean recursiveSerialization) {
        super(original, recursiveSerialization);
    }

    @NonNull
    protected abstract GeneratorType getElementType(GeneratorType type);

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (visitor.visitStructure()) {
            GeneratorType elementType = getElementType(type);
            visitor.visitStructureElement(getElementSymbol(elementType), elementType, null);
        }
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        GeneratorType elementType = getElementType(type);
        SerializerSymbol elementSerializer = getElementSymbol(elementType);
        String itemName = generatorContext.newLocalVariable("item");
        return CodeBlock.builder()
                .addStatement("$N.writeStartArray()", ENCODER)
                .beginControlFlow("for ($T $N : $L)", PoetUtil.toTypeName(elementType), itemName, readExpression)
                .add(elementSerializer.serialize(generatorContext.withSubPath("[*]"), elementType, CodeBlock.of("$N", itemName)))
                .endControlFlow()
                .addStatement("$N.writeEndArray()", ENCODER)
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        GeneratorType elementType = getElementType(type);
        SerializerSymbol elementDeserializer = getElementSymbol(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("intermediate");

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_ARRAY) throw $T.from($N, \"Unexpected token \" + $N.currentToken() + \", expected START_ARRAY\");\n", DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);
        block.add(createIntermediate(elementType, intermediateVariable));
        block.beginControlFlow("while ($N.nextToken() != $T.END_ARRAY)", DECODER, JsonToken.class);
        block.add(elementDeserializer.deserialize(generatorContext, elementType, expr -> CodeBlock.of("$N.add($L);\n", intermediateVariable, expr)));
        block.endControlFlow();
        block.add(setter.createSetStatement(finishDeserialize(elementType, intermediateVariable)));
        return block.build();
    }

    protected CodeBlock createIntermediate(GeneratorType elementType, String intermediateVariable) {
        return CodeBlock.of("$T<$T> $N = new $T<>();\n", ArrayList.class, PoetUtil.toTypeName(elementType), intermediateVariable, ArrayList.class);
    }

    protected abstract CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable);

    static class ArrayImpl extends InlineIterableSerializerSymbol {
        ArrayImpl(SerializerLinker linker) {
            super(linker);
        }

        private ArrayImpl(ArrayImpl original, boolean recursiveSerialization) {
            super(original, recursiveSerialization);
        }

        @Override
        public SerializerSymbol withRecursiveSerialization() {
            return new ArrayImpl(this, true);
        }

        @Override
        public boolean canSerialize(GeneratorType type) {
            return type.isArray();
        }

        @Override
        @NonNull
        protected GeneratorType getElementType(GeneratorType type) {
            return type.fromArray();
        }

        @Override
        protected CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable) {
            return CodeBlock.of("$N.toArray(new $T[0])", intermediateVariable, PoetUtil.toTypeName(elementType));
        }
    }

    /**
     * Can also do {@link Iterable} and {@link java.util.List}.
     */
    static class ArrayListImpl extends InlineIterableSerializerSymbol {
        ArrayListImpl(SerializerLinker linker) {
            super(linker);
        }

        private ArrayListImpl(ArrayListImpl original, boolean recursiveSerialization) {
            super(original, recursiveSerialization);
        }

        @Override
        public SerializerSymbol withRecursiveSerialization() {
            return new ArrayListImpl(this, true);
        }

        @Override
        public boolean canSerialize(GeneratorType type) {
            return type.isRawTypeEquals(Iterable.class) || type.isRawTypeEquals(Collection.class) || type.isRawTypeEquals(List.class) || type.isRawTypeEquals(ArrayList.class);
        }

        @Override
        @NonNull
        protected GeneratorType getElementType(GeneratorType type) {
            /* todo: bug in getTypeArguments(class)? only returns java.lang.Object
            return type.getTypeArguments(Iterable.class).get("T");
            */
            return type.getTypeArgumentsExact(ArrayList.class).map(m -> m.get("E")).orElseGet(() ->
                    type.getTypeArgumentsExact(List.class).map(m -> m.get("E")).orElseGet(() ->
                            type.getTypeArgumentsExact(Collection.class).map(m -> m.get("E")).orElseGet(() ->
                                    type.getTypeArgumentsExact(Iterable.class).map(m -> m.get("T")).orElseThrow(() ->
                                            new UnsupportedOperationException("unsupported type")))));
        }

        @Override
        protected CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable) {
            return CodeBlock.of("$N", intermediateVariable);
        }
    }
}
