package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

import java.util.Optional;

class OptionalSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;
    private final boolean recursiveSerialization;

    public OptionalSerializerSymbol(SerializerLinker linker) {
        this(linker, false);
    }

    private OptionalSerializerSymbol(SerializerLinker linker, boolean recursiveSerialization) {
        this.linker = linker;
        this.recursiveSerialization = recursiveSerialization;
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isRawTypeEquals(Optional.class);
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new OptionalSerializerSymbol(linker, true);
    }

    @Override
    public boolean supportsNullDeserialization() {
        return true;
    }

    private Optional<GeneratorType> findDelegateType(GeneratorType type) {
        return type.getTypeArgumentsExact(Optional.class).map(m -> m.get("T"));
    }

    private SerializerSymbol getDelegateSerializer(GeneratorType delegateType) {
        SerializerSymbol symbol = linker.findSymbol(delegateType);
        if (recursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        return symbol;
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        findDelegateType(type).ifPresent(t -> getDelegateSerializer(t).visitDependencies(visitor, t));
        // else assume no dependencies
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        Optional<GeneratorType> delegateType = findDelegateType(type);
        if (!delegateType.isPresent()) {
            generatorContext.getProblemReporter().fail("Could not resolve optional type", null);
            return CodeBlock.of("");
        }
        String variable = generatorContext.newLocalVariable("tmp");
        return CodeBlock.builder()
                .addStatement("$T $N = $L", PoetUtil.toTypeName(type), variable, readExpression)
                .beginControlFlow("if ($N.isPresent())", variable)
                .add(getDelegateSerializer(delegateType.get()).serialize(generatorContext, delegateType.get(), CodeBlock.of("$N.get()", variable)))
                .nextControlFlow("else")
                .addStatement("$N.writeNull()", Names.ENCODER)
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        Optional<GeneratorType> delegateType = findDelegateType(type);
        if (!delegateType.isPresent()) {
            generatorContext.getProblemReporter().fail("Could not resolve optional type", null);
            return CodeBlock.of("");
        }
        return CodeBlock.builder()
                .beginControlFlow("if ($N.currentToken() == $T.VALUE_NULL)", Names.DECODER, JsonToken.class)
                .add(setter.createSetStatement(CodeBlock.of("$T.empty()", Optional.class)))
                .nextControlFlow("else")
                .add(getDelegateSerializer(delegateType.get()).deserialize(generatorContext, delegateType.get(), expr -> setter.createSetStatement(CodeBlock.of("$T.of($L)", Optional.class, expr))))
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock getDefaultExpression(GeneratorType type) {
        return CodeBlock.of("$T.empty()", Optional.class);
    }
}
