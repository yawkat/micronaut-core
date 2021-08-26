package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Internal;

@Internal
public class NullableSerializerSymbol implements SerializerSymbol {
    private final SerializerSymbol delegate;

    public NullableSerializerSymbol(SerializerSymbol delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        throw new UnsupportedOperationException("Not part of the normal linker chain");
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsNullDeserialization() {
        return true;
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        delegate.visitDependencies(visitor, type);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        String variable = generatorContext.newLocalVariable("tmp");
        return CodeBlock.builder()
                .addStatement("$T $N = $L", PoetUtil.toTypeName(type), variable, readExpression)
                .beginControlFlow("if ($N == null)", variable)
                .addStatement("$N.writeNull()", Names.ENCODER)
                .nextControlFlow("else")
                .add(delegate.serialize(generatorContext, type, CodeBlock.of("$N", variable)))
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        return CodeBlock.builder()
                .beginControlFlow("if ($N.currentToken() == $T.VALUE_NULL)", Names.DECODER, JsonToken.class)
                .add(setter.createSetStatement(CodeBlock.of("null")))
                .nextControlFlow("else")
                .add(delegate.deserialize(generatorContext, type, setter))
                .endControlFlow()
                .build();
    }

    @Override
    public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorType type, JsonInclude.Include inclusionPolicy) {
        ConditionExpression<CodeBlock> expr = this.delegate.shouldIncludeCheck(type, inclusionPolicy);
        switch (inclusionPolicy) {
            case NON_NULL:
            case NON_ABSENT:
            case NON_EMPTY:
                expr = ConditionExpression.<CodeBlock>of(v -> CodeBlock.of("$L != null", v)).and(expr);
        }
        return expr;
    }
}
