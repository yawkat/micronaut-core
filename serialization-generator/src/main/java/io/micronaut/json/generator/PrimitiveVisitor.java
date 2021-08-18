package io.micronaut.json.generator;

import com.squareup.javapoet.ClassName;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.generator.symbol.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Special visitor that generates serializers for primitive classes in the package of the single class annotated with {@value TYPE}
 */
@Internal
public class PrimitiveVisitor extends AbstractGeneratorVisitor<Object> implements TypeElementVisitor<Object, Object> {
    private static final String TYPE = "io.micronaut.json.generated.serializer.PrimitiveGenerators.GeneratePrimitiveSerializers";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(TYPE);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(TYPE)) {
            return;
        }

        SerializerLinker linker = new SerializerLinker(context);
        for (ClassElement prim : Arrays.asList(
                PrimitiveElement.BOOLEAN,
                PrimitiveElement.BYTE,
                PrimitiveElement.SHORT,
                PrimitiveElement.CHAR,
                PrimitiveElement.INT,
                PrimitiveElement.LONG,
                PrimitiveElement.FLOAT,
                PrimitiveElement.DOUBLE
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofClass(prim))
                    .problemReporter(problemReporter)
                    .packageName(element.getPackageName())
                    .valueReferenceName(PoetUtil.toTypeName(prim).box())
                    .linker(linker)
                    .generateMulti());
        }

        for (Class<?> t : Arrays.asList(
                String.class,
                BigDecimal.class,
                BigInteger.class
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofClass(ClassElement.of(t)))
                    .problemReporter(problemReporter)
                    .packageName(element.getPackageName())
                    .linker(linker)
                    .generateMulti());
        }

        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(createGeneratorType(List.class, Object.class))
                .problemReporter(problemReporter)
                .packageName(element.getPackageName())
                .linker(linker)
                .generateMulti());
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(createGeneratorType(Map.class, String.class, Object.class))
                .problemReporter(problemReporter)
                .packageName(element.getPackageName())
                .linker(linker)
                .generateMulti());
    }

    private static GeneratorType createGeneratorType(Class<?> rawType, Class<?>... args) {
        return GeneratorType.ofParameterized(rawType, args);
    }
}
