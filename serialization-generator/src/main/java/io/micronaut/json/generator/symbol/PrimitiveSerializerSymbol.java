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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.json.generated.JsonParseException;

import java.math.BigDecimal;
import java.math.BigInteger;

import static io.micronaut.json.generator.symbol.Names.DECODER;
import static io.micronaut.json.generator.symbol.Names.ENCODER;

final class PrimitiveSerializerSymbol implements SerializerSymbol {
    private static final String FQCN_BIG_DECIMAL = BigDecimal.class.getName();
    private static final String FQCN_BIG_INTEGER = BigInteger.class.getName();

    static final PrimitiveSerializerSymbol INSTANCE = new PrimitiveSerializerSymbol();

    private PrimitiveSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        return !type.isArray() && ((type.isPrimitive() && !type.equals(PrimitiveElement.VOID)) ||
                type.getName().equals(FQCN_BIG_DECIMAL) || type.getName().equals(FQCN_BIG_INTEGER));
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, ClassElement type) {
        // scalar, no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return CodeBlock.of("$N.writeBoolean($L);\n", ENCODER, readExpression);
        } else {
            return CodeBlock.of("$N.writeNumber($L);\n", ENCODER, readExpression);
        }
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, ClassElement type, Setter setter) {
        if (!canSerialize(type)) {
            throw new UnsupportedOperationException("This symbol can only handle primitives");
        }
        return CodeBlock.builder()
                .add(checkCorrectToken(generatorContext, type))
                .add(setter.createSetStatement(CodeBlock.of(deserializeExpression(type))))
                .build();
    }

    @Override
    public CodeBlock getDefaultExpression(ClassElement type) {
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return CodeBlock.of("false");
        } else if (type.isPrimitive()) {
            return CodeBlock.of("0");
        } else {
            // bigdecimal, biginteger
            return SerializerSymbol.super.getDefaultExpression(type);
        }
    }

    private CodeBlock checkCorrectToken(GeneratorContext generatorContext, ClassElement type) {
        String tokenVar = generatorContext.newLocalVariable("token");
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return CodeBlock.builder()
                    .addStatement("$T $N = $N.currentToken()", JsonToken.class, tokenVar, DECODER)
                    .addStatement(
                            "if ($N != $T.VALUE_TRUE && $N != $T.VALUE_FALSE) throw $T.from($N, $S + $N)",
                            tokenVar, JsonToken.class,
                            tokenVar, JsonToken.class,
                            JsonParseException.class, DECODER,
                            "Bad value for field " + generatorContext.getReadablePath() + ": Expected boolean, got ", tokenVar
                    )
                    .build();
        } else {
            // for numbers, we accept floats and ints interchangeably, because json makes no distinction. Other serialization formats might, though.
            return CodeBlock.builder()
                    .addStatement("$T $N = $N.currentToken()", JsonToken.class, tokenVar, DECODER)
                    .addStatement(
                            "if ($N != $T.VALUE_NUMBER_INT && $N != $T.VALUE_NUMBER_FLOAT) throw $T.from($N, $S + $N)",
                            tokenVar, JsonToken.class,
                            tokenVar, JsonToken.class,
                            JsonParseException.class, DECODER,
                            "Bad value for field " + generatorContext.getReadablePath() + ": Expected number, got ", tokenVar
                    )
                    .build();
        }
    }

    private String deserializeExpression(ClassElement type) {
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return DECODER + ".getBooleanValue()";
        } else if (type.equals(PrimitiveElement.BYTE)) {
            return DECODER + ".getByteValue()";
        } else if (type.equals(PrimitiveElement.SHORT)) {
            return DECODER + ".getShortValue()";
        } else if (type.equals(PrimitiveElement.CHAR)) {
            return "(char) " + DECODER + ".getIntValue()"; // TODO
        } else if (type.equals(PrimitiveElement.INT)) {
            return DECODER + ".getIntValue()";
        } else if (type.equals(PrimitiveElement.LONG)) {
            return DECODER + ".getLongValue()";
        } else if (type.equals(PrimitiveElement.FLOAT)) {
            return DECODER + ".getFloatValue()";
        } else if (type.equals(PrimitiveElement.DOUBLE)) {
            return DECODER + ".getDoubleValue()";
        } else if (type.getName().equals(FQCN_BIG_INTEGER)) {
            return DECODER + ".getBigIntegerValue()";
        } else if (type.getName().equals(FQCN_BIG_DECIMAL)) {
            return DECODER + ".getDecimalValue()";
        } else {
            throw new AssertionError("unknown primitive type " + type);
        }
    }
}
