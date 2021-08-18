package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.json.generated.JsonParseException;

import java.util.List;
import java.util.stream.Collectors;

import static io.micronaut.json.generator.symbol.Names.DECODER;

class InlineEnumSerializerSymbol implements SerializerSymbol {
    static final InlineEnumSerializerSymbol INSTANCE = new InlineEnumSerializerSymbol();

    private InlineEnumSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return type.isEnum();
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        EnumDefinition enumDefinition = new EnumDefinition((EnumElement) type.getClassElement());

        CodeBlock.Builder builder = CodeBlock.builder();
        builder.beginControlFlow("switch ($L)", readExpression);
        for (int i = 0; i < enumDefinition.constants.size(); i++) {
            builder.beginControlFlow("case $N:", enumDefinition.constants.get(i));
            builder.add(enumDefinition.valueSerializer.serialize(
                    generatorContext,
                    enumDefinition.valueType,
                    enumDefinition.serializedLiterals.get(i)
            ));
            builder.addStatement("break");
            builder.endControlFlow();
        }

        builder.beginControlFlow("default:");
        // new enum constant added. Throw ICCE to be consistent with java switch expressions
        builder.addStatement("throw new $T()", IncompatibleClassChangeError.class);
        builder.endControlFlow();

        builder.endControlFlow();
        return builder.build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        EnumDefinition enumDefinition = new EnumDefinition((EnumElement) type.getClassElement());

        return enumDefinition.valueSerializer.deserialize(generatorContext, type, expr -> {
            CodeBlock.Builder builder = CodeBlock.builder();
            builder.beginControlFlow("switch ($L)", expr);

            for (int i = 0; i < enumDefinition.constants.size(); i++) {
                builder.beginControlFlow("case $L:", enumDefinition.serializedLiterals.get(i));
                builder.add(setter.createSetStatement(CodeBlock.of("$T.$N", PoetUtil.toTypeName(type), enumDefinition.constants.get(i))));
                builder.addStatement("break");
                builder.endControlFlow();
            }

            builder.beginControlFlow("default:");
            builder.addStatement(
                    "throw $T.from($N, $S)",
                    JsonParseException.class, DECODER,
                    "Bad enum value for field " + generatorContext.getReadablePath()
            );
            builder.endControlFlow();

            builder.endControlFlow();
            return builder.build();
        });
    }

    private static class EnumDefinition {
        private final List<String> constants;
        private final List<CodeBlock> serializedLiterals;

        private final SerializerSymbol valueSerializer;
        private final GeneratorType valueType;

        EnumDefinition(EnumElement element) {
            // todo: support @JsonProperty
            // todo: optimize the case where name()/valueOf() can be used
            this.constants = element.values();
            this.serializedLiterals = element.values().stream()
                    .map(v -> CodeBlock.of("$S", v))
                    .collect(Collectors.toList());
            this.valueSerializer = StringSerializerSymbol.INSTANCE;
            this.valueType = GeneratorType.STRING;
        }
    }
}
