package io.micronaut.json.generator.symbol.bean;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.generated.JsonParseException;
import io.micronaut.json.generator.symbol.GeneratorContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class detects duplicate and missing properties using an inlined BitSet.
 * <p>
 * Note: implementation must use the same bit layout as BitSet to allow internal use of {@link BitSet#toLongArray()}.
 */
class DuplicatePropertyManager {
    private final InlineBitSet<BeanDefinition.Property> bitSet;
    /**
     * Used for creating exceptions
     */
    private final String decoderVariable;

    DuplicatePropertyManager(
            GeneratorContext context,
            Collection<BeanDefinition.Property> properties,
            String decoderVariable
    ) {
        this.decoderVariable = decoderVariable;
        bitSet = new InlineBitSet<>(context, properties);
    }

    void emitMaskDeclarations(CodeBlock.Builder output) {
        bitSet.emitMaskDeclarations(output);
    }

    /**
     * Emit the code when a variable is read. Checks for duplicates, and marks this property as read.
     */
    void emitReadVariable(CodeBlock.Builder output, BeanDefinition.Property prop) {
        output.add(
                "if ($L) throw $T.from($N, $S);\n",
                bitSet.isSet(prop),
                JsonParseException.class,
                decoderVariable,
                "Duplicate property " + prop.name
        );
        bitSet.set(output, prop);
    }

    /**
     * Emit the final checks that all required properties are present.
     */
    void emitCheckRequired(CodeBlock.Builder output, Set<BeanDefinition.Property> required) {
        if (required.isEmpty()) {
            return;
        }

        bitSet.onMissing(output, required.stream().collect(Collectors.toMap(
                req -> req,
                req -> CodeBlock.of("throw $T.from($N, $S);\n",
                        JsonParseException.class,
                        decoderVariable,
                        "Missing property " + req.name)
        )));
    }

    CodeBlock hasBeenRead(BeanDefinition.Property prop) {
        return bitSet.isSet(prop);
    }
}
