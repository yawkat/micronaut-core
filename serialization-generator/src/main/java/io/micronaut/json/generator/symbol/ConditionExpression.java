package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.CodeBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A builder for a {@link CodeBlock} that is a boolean expression. Accepts a parameter {@code I} to build the
 * condition.
 */
public class ConditionExpression<I> {
    private static final ConditionExpression<Object> ALWAYS_TRUE = new ConditionExpression<>(Collections.emptyList());

    private final List<? extends Function<? super I, CodeBlock>> andTerms;

    private ConditionExpression(List<? extends Function<? super I, CodeBlock>> andTerms) {
        this.andTerms = andTerms;
    }

    @SuppressWarnings("unchecked")
    public static <I> ConditionExpression<I> alwaysTrue() {
        return (ConditionExpression<I>) ALWAYS_TRUE;
    }

    public static <I> ConditionExpression<I> of(Function<? super I, CodeBlock> condition) {
        return new ConditionExpression<>(Collections.singletonList(condition));
    }

    public ConditionExpression<I> and(ConditionExpression<I> other) {
        List<Function<? super I, CodeBlock>> newAndTerms = new ArrayList<>(andTerms.size() + other.andTerms.size());
        newAndTerms.addAll(andTerms);
        newAndTerms.addAll(other.andTerms);
        return new ConditionExpression<>(newAndTerms);
    }

    public <J> ConditionExpression<J> compose(Function<J, I> function) {
        return new ConditionExpression<>(andTerms.stream()
                .map(f -> f.compose(function))
                .collect(Collectors.toList()));
    }

    public CodeBlock build(I input) {
        if (isAlwaysTrue()) {
            return CodeBlock.of("true");
        }

        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (Function<? super I, CodeBlock> andTerm : andTerms) {
            if (!first) {
                builder.add(" && ");
            }
            first = false;
            builder.add(andTerm.apply(input));
        }
        return builder.build();
    }

    public boolean isAlwaysTrue() {
        return andTerms.isEmpty();
    }
}
