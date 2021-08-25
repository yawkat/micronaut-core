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
import io.micronaut.json.generated.JsonParseException;

import static io.micronaut.json.generator.symbol.Names.DECODER;
import static io.micronaut.json.generator.symbol.Names.ENCODER;

final class StringSerializerSymbol implements SerializerSymbol {
    static final StringSerializerSymbol INSTANCE = new StringSerializerSymbol();

    private StringSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isRawTypeEquals(String.class) || type.isRawTypeEquals(CharSequence.class);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        // scalar, no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        // todo: handle charsequence
        return CodeBlock.of("$N.writeString($L);\n", ENCODER, readExpression);
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        String stringVar = generatorContext.newLocalVariable("string");
        return CodeBlock.builder()
                .addStatement("$T $N = $N.getValueAsString()", String.class, stringVar, DECODER)
                .addStatement(
                        "if ($N == null) throw $T.from($N, $S + $N.currentToken())",
                        stringVar,
                        JsonParseException.class, DECODER,
                        "Bad value for field " + generatorContext.getReadablePath() + ": Expected string, got ", DECODER
                )
                .add(setter.createSetStatement(CodeBlock.of("$N", stringVar)))
                .build();
    }
}
