package io.micronaut.json.generator.symbol;

import io.micronaut.core.annotation.NonNull;

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
