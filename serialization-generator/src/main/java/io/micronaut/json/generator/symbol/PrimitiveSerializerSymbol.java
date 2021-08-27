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

import com.squareup.javapoet.CodeBlock;

import java.math.BigDecimal;
import java.math.BigInteger;

import static io.micronaut.json.generator.symbol.Names.ENCODER;

final class PrimitiveSerializerSymbol implements SerializerSymbol {
    static final PrimitiveSerializerSymbol INSTANCE = new PrimitiveSerializerSymbol();

    private PrimitiveSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return !type.isArray() && ((type.isPrimitive() && !type.isRawTypeEquals(void.class)) ||
                type.isRawTypeEquals(BigDecimal.class) || type.isRawTypeEquals(BigInteger.class));
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        // scalar, no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        if (type.isRawTypeEquals(boolean.class)) {
            return CodeBlock.of("$N.writeBoolean($L);\n", ENCODER, readExpression);
        } else {
            return CodeBlock.of("$N.writeNumber($L);\n", ENCODER, readExpression);
        }
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        if (!canSerialize(type)) {
            throw new UnsupportedOperationException("This symbol can only handle primitives");
        }
        return setter.createSetStatement(CodeBlock.of("$N.$N()", decoderVariable, deserializeMethod(type)));
    }

    @Override
    public CodeBlock getDefaultExpression(GeneratorType type) {
        if (type.isRawTypeEquals(boolean.class)) {
            return CodeBlock.of("false");
        } else if (type.isPrimitive()) {
            return CodeBlock.of("0");
        } else {
            // bigdecimal, biginteger
            return SerializerSymbol.super.getDefaultExpression(type);
        }
    }

    private String deserializeMethod(GeneratorType type) {
        if (type.isRawTypeEquals(boolean.class)) {
            return "decodeBoolean";
        } else if (type.isRawTypeEquals(byte.class)) {
            return "decodeByte";
        } else if (type.isRawTypeEquals(short.class)) {
            return "decodeShort";
        } else if (type.isRawTypeEquals(char.class)) {
            return "decodeChar";
        } else if (type.isRawTypeEquals(int.class)) {
            return "decodeInt";
        } else if (type.isRawTypeEquals(long.class)) {
            return "decodeLong";
        } else if (type.isRawTypeEquals(float.class)) {
            return "decodeFloat";
        } else if (type.isRawTypeEquals(double.class)) {
            return "decodeDouble";
        } else if (type.isRawTypeEquals(BigInteger.class)) {
            return "decodeBigInteger";
        } else if (type.isRawTypeEquals(BigDecimal.class)) {
            return "decodeBigDecimal";
        } else {
            throw new AssertionError("unknown primitive type " + type);
        }
    }

    private CodeBlock coerceFromString(GeneratorType type, CodeBlock expression) {
        // todo: make coercion configurable

        if (type.isRawTypeEquals(boolean.class)) {
            return CodeBlock.of("$L.equalsIgnoreCase(\"true\")", expression);
        } else if (type.isRawTypeEquals(byte.class)) {
            return CodeBlock.of("(byte) $T.parseLong($L)", Long.class, expression);
        } else if (type.isRawTypeEquals(short.class)) {
            return CodeBlock.of("(short) $T.parseLong($L)", Long.class, expression);
        } else if (type.isRawTypeEquals(char.class)) {
            return CodeBlock.of("(char) $T.parseLong($L)", Long.class, expression);
        } else if (type.isRawTypeEquals(int.class)) {
            return CodeBlock.of("(int) $T.parseLong($L)", Long.class, expression);
        } else if (type.isRawTypeEquals(long.class)) {
            return CodeBlock.of("$T.parseLong($L)", Long.class, expression);
        } else if (type.isRawTypeEquals(float.class)) {
            return CodeBlock.of("$T.parseFloat($L)", Float.class, expression);
        } else if (type.isRawTypeEquals(double.class)) {
            return CodeBlock.of("$T.parseDouble($L)", Double.class, expression);
        }  else if (type.isRawTypeEquals(BigInteger.class)) {
            return CodeBlock.of("new $T($L)", BigInteger.class, expression);
        } else if (type.isRawTypeEquals(BigDecimal.class)) {
            return CodeBlock.of("new $T($L)", BigDecimal.class, expression);
        } else {
            throw new AssertionError("unknown primitive type " + type);
        }
    }
}
