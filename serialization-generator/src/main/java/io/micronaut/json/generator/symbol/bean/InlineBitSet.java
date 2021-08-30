package io.micronaut.json.generator.symbol.bean;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.json.generated.JsonParseException;
import io.micronaut.json.generator.symbol.GeneratorContext;

import java.util.*;

/**
 * {@link java.util.BitSet} equivalent that is inlined as multiple generated long variables.
 */
class InlineBitSet<T> {
    private final List<String> maskVariables;
    private final Map<T, Integer> offsets;

    InlineBitSet(GeneratorContext context, Collection<T> values) {
        offsets = new HashMap<>();
        int offset = 0;
        for (T property : values) {
            offsets.put(property, offset);
            offset++;
        }

        // generate one mask for every 64 variables
        maskVariables = new ArrayList<>();
        for (int i = 0; i < offset; i += 64) {
            maskVariables.add(context.newLocalVariable("mask"));
        }
    }

    /**
     * Emit the prelude required for duplicate detection.
     */
    void emitMaskDeclarations(CodeBlock.Builder output) {
        for (String maskVariable : maskVariables) {
            output.addStatement("long $N = 0", maskVariable);
        }
    }

    private String maskVariable(int offset) {
        return maskVariables.get(offset / 64);
    }

    private String mask(int offset) {
        // shift does an implicit modulo
        long value = 1L << offset;
        return toHexLiteral(value);
    }

    private String toHexLiteral(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }

    void set(CodeBlock.Builder output, T value) {
        int offset = offsets.get(value);
        output.addStatement("$N |= $L", maskVariable(offset), mask(offset));
    }

    CodeBlock isSet(T value) {
        int offset = offsets.get(value);
        return CodeBlock.of("($N & $L) != 0", maskVariable(offset), mask(offset));
    }

    CodeBlock allSet(Collection<T> values) {
        BitSet collected = new BitSet();
        for (T value : values) {
            collected.set(offsets.get(value));
        }
        long[] expected = collected.toLongArray();
        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (int i = 0; i < expected.length; i++) {
            long value = expected[i];
            if (value != 0) {
                if (!first) {
                    builder.add(" && ");
                }
                first = false;
                String valueLiteral = toHexLiteral(value);
                builder.add("($N & $L) == $L", maskVariables.get(i), valueLiteral, valueLiteral);
            }
        }
        return builder.build();
    }

    /**
     * Emit code that checks that the required values are set.
     *
     * @param requiredValues The required values, mapped to the code block of what action to take when a value is
     *                       missing.
     */
    void onMissing(CodeBlock.Builder builder, Map<T, CodeBlock> requiredValues) {
        if (requiredValues.isEmpty()) {
            return;
        }

        builder.beginControlFlow("if (!($L))", allSet(requiredValues.keySet()));

        // if there are missing variables, determine which ones
        for (Map.Entry<T, CodeBlock> entry : requiredValues.entrySet()) {
            int offset = offsets.get(entry.getKey());
            builder.beginControlFlow("if (($N & $L) == 0)", maskVariable(offset), mask(offset));
            builder.add(entry.getValue());
            builder.endControlFlow();
        }

        builder.endControlFlow();
    }
}
