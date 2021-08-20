package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.generated.JsonParseException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static io.micronaut.json.generator.symbol.Names.DECODER;
import static io.micronaut.json.generator.symbol.Names.ENCODER;

final class InlineStringMapSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;
    private final boolean recursiveSerialization;

    InlineStringMapSerializerSymbol(SerializerLinker linker) {
        this(linker, false);
    }

    private InlineStringMapSerializerSymbol(SerializerLinker linker, boolean recursiveSerialization) {
        this.linker = linker;
        this.recursiveSerialization = recursiveSerialization;
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        return Stream.of(LinkedHashMap.class, HashMap.class, Map.class).anyMatch(type::isRawTypeEquals)
                && getType(type, "K").isRawTypeEquals(String.class);
    }

    private GeneratorType getType(GeneratorType type, String varName) {
        return type.getTypeArgumentsExact().get(varName);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (visitor.visitStructure()) {
            GeneratorType elementType = getType(type, "V");
            visitor.visitStructureElement(getSymbol(elementType), elementType, null);
        }
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new InlineStringMapSerializerSymbol(linker, true);
    }

    @NonNull
    private SerializerSymbol getSymbol(GeneratorType elementType) {
        SerializerSymbol symbol = linker.findSymbol(elementType);
        if (recursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        return symbol;
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression) {
        GeneratorType elementType = getType(type, "V");
        SerializerSymbol elementSerializer = getSymbol(elementType);
        String entryName = generatorContext.newLocalVariable("entry");
        return CodeBlock.builder()
                .addStatement("$N.writeStartObject()", ENCODER)
                .beginControlFlow("for ($T $N : $L.entrySet())",
                        ParameterizedTypeName.get(
                                ClassName.get(Map.Entry.class),
                                ClassName.get(String.class),
                                PoetUtil.toTypeName(elementType)
                        ),
                        entryName,
                        readExpression
                )
                .addStatement("$N.writeFieldName($N.getKey())", ENCODER, entryName)
                .add(elementSerializer.serialize(generatorContext.withSubPath("[*]"), elementType, CodeBlock.of("$N.getValue()", entryName)))
                .endControlFlow()
                .addStatement("$N.writeEndObject()", ENCODER)
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter) {
        GeneratorType elementType = getType(type, "V");
        SerializerSymbol elementDeserializer = getSymbol(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("map");

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_OBJECT) throw $T.from($N, \"Unexpected token \" + $N.currentToken() + \", expected START_OBJECT\");\n", DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);
        block.addStatement("$T $N = new $T<>()",
                ParameterizedTypeName.get(
                        ClassName.get(LinkedHashMap.class),
                        ClassName.get(String.class),
                        PoetUtil.toTypeName(elementType)
                ),
                intermediateVariable,
                LinkedHashMap.class
        );
        block.beginControlFlow("while ($N.nextToken() == $T.FIELD_NAME)", DECODER, JsonToken.class);
        String keyVariable = generatorContext.newLocalVariable("key");
        block.addStatement("$T $N = $N.getCurrentName()", String.class, keyVariable, DECODER);
        block.addStatement("$N.nextToken()", DECODER);
        block.add(elementDeserializer.deserialize(generatorContext, elementType, expr -> CodeBlock.of("$N.put($N, $L);\n", intermediateVariable, keyVariable, expr)));
        block.endControlFlow();

        block.add("if ($N.currentToken() != $T.END_OBJECT) throw $T.from($N, \"Unexpected token \" + $N.currentToken() + \", expected FIELD_NAME or END_OBJECT\");\n", DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);

        block.add(setter.createSetStatement(CodeBlock.of("$N", intermediateVariable)));
        return block.build();
    }
}
