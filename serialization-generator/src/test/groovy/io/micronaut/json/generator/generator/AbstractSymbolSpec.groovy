package io.micronaut.json.generator.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.json.Serializer
import io.micronaut.json.generator.SerializerUtils
import io.micronaut.json.generator.symbol.ProblemReporter
import io.micronaut.json.generator.symbol.SerializerSymbol
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator

import java.lang.reflect.Type

class AbstractSymbolSpec extends AbstractTypeElementSpec implements SerializerUtils {
    public <T> Serializer<T> buildBasicSerializer(Type type, SerializerSymbol symbol, ClassElement classElement = ClassElement.of(type)) {
        def problemReporter = new ProblemReporter()
        def generationResult = SingletonSerializerGenerator.generate(
                problemReporter,
                ClassName.get("example", "SerializerImpl"),
                TypeName.get(type),
                symbol,
                classElement
        )
        if (problemReporter.isFailed()) {
            throw new AssertionError("Problems found, use debugger to tell which :)");
        }

        def loader = buildClassLoader(generationResult.serializerClassName.reflectionName(), generationResult.generatedFile.toString())
        def serializerClass = loader.loadClass(generationResult.serializerClassName.reflectionName())
        return (Serializer<T>) serializerClass.getConstructor().newInstance()
    }
}
