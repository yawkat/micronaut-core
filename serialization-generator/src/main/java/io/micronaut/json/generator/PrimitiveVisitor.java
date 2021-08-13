package io.micronaut.json.generator;

import com.squareup.javapoet.ClassName;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.generator.symbol.*;

import java.lang.reflect.TypeVariable;
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
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(prim)
                            .problemReporter(problemReporter)
                            .generatedSerializerName(ClassName.get(element.getPackageName(), "$PrimitiveSerializer$" + prim.getSimpleName()))
                            .valueReferenceName(PoetUtil.toTypeName(prim).box())
                            .linker(linker)
                            .generate());
        }

        for (Class<?> t : Arrays.asList(
                String.class,
                BigDecimal.class,
                BigInteger.class
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(ClassElement.of(t))
                    .problemReporter(problemReporter)
                    .generatedSerializerName(ClassName.get(element.getPackageName(), "$Serializer$" + t.getSimpleName()))
                    .linker(linker)
                    .generate());
        }

        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(createClassElement(List.class, Object.class))
                        .problemReporter(problemReporter)
                        .generatedSerializerName(ClassName.get(element.getPackageName(), "$Serializer$List"))
                        .linker(linker)
                        .generate());
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(createClassElement(Map.class, String.class, Object.class))
                        .problemReporter(problemReporter)
                        .generatedSerializerName(ClassName.get(element.getPackageName(), "$Serializer$Map"))
                        .linker(linker)
                        .generate());
    }

    private static ClassElement createClassElement(Class<?> rawType, Class<?>... args) {
        return createClassElement(rawType, Arrays.stream(args).map(ClassElement::of).toArray(ClassElement[]::new));
    }

    private static ClassElement createClassElement(Class<?> rawType, ClassElement... args) {
        Map<String, ClassElement> argMap = new LinkedHashMap<>();
        TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
        for (int i = 0; i < typeParameters.length; i++) {
            argMap.put(typeParameters[i].getName(), args[i]);
        }
        return ClassElement.of(rawType, AnnotationMetadata.EMPTY_METADATA, argMap);
    }
}
