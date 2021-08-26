package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.function.Function;

/**
 * Abstract class for serializers for lists and maps
 */
abstract class AbstractInlineContainerSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;
    private final boolean recursiveSerialization;

    AbstractInlineContainerSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
        this.recursiveSerialization = false;
    }

    AbstractInlineContainerSerializerSymbol(AbstractInlineContainerSerializerSymbol original, boolean recursiveSerialization) {
        this.linker = original.linker;
        this.recursiveSerialization = recursiveSerialization;
    }

    @Override
    public abstract SerializerSymbol withRecursiveSerialization();

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorType type, JsonInclude.Include inclusionPolicy) {
        if (inclusionPolicy == JsonInclude.Include.NON_EMPTY) {
            return ConditionExpression.of(expr -> CodeBlock.of("!$L.isEmpty()", expr));
        }
        return SerializerSymbol.super.shouldIncludeCheck(type, inclusionPolicy);
    }

    @NonNull
    protected final SerializerSymbol getElementSymbol(GeneratorType elementType) {
        SerializerSymbol symbol = linker.findSymbol(elementType);
        if (recursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        if (!symbol.supportsNullDeserialization()) {
            symbol = new NullableSerializerSymbol(symbol);
        }
        return symbol;
    }
}
