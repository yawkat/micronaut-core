package io.micronaut.json.generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.micronaut.annotation.processing.visitor.JavaElementFactory;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.$GeneratePrimitiveSerializers;
import io.micronaut.json.generator.symbol.PoetUtil;
import io.micronaut.json.generator.symbol.SerializerLinker;
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Special visitor that generates serializers for primitive classes in the package of the single class annotated with {@link $GeneratePrimitiveSerializers}
 */
@Internal
public class PrimitiveVisitor extends AbstractGeneratorVisitor<$GeneratePrimitiveSerializers> implements TypeElementVisitor<$GeneratePrimitiveSerializers, $GeneratePrimitiveSerializers> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype($GeneratePrimitiveSerializers.class)) {
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
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.generate(
                    problemReporter,
                    ClassName.get(element.getPackageName(), "$PrimitiveSerializer$" + prim.getSimpleName()),
                    PoetUtil.toTypeName(prim).box(),
                    linker.findSymbol(prim),
                    prim
            ));
        }

        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.generate(
                problemReporter,
                ClassName.get(element.getPackageName(), "$Serializer$String"),
                TypeName.get(String.class),
                linker.findSymbol(ClassElement.of(String.class)),
                ClassElement.of(String.class)
        ));

        // only supporting java is fine here, since we only use this annotation in a single case.
        JavaElementFactory elementFactory = ((JavaVisitorContext) context).getElementFactory();
        ProcessingEnvironment env = ((JavaVisitorContext) context).getProcessingEnv();

        Map<String, ClassElement> listObjectGenerics = new java.util.HashMap<>();
        listObjectGenerics.put("T", elementFactory.newClassElement(
                env.getElementUtils().getTypeElement(Object.class.getName()),
                AnnotationMetadata.EMPTY_METADATA
        ));
        ClassElement listObject = elementFactory.newClassElement(
                env.getElementUtils().getTypeElement(Iterable.class.getName()),
                AnnotationMetadata.EMPTY_METADATA,
                listObjectGenerics
        );
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.generate(
                problemReporter,
                ClassName.get(element.getPackageName(), "$Serializer$List"),
                ParameterizedTypeName.get(List.class, Object.class),
                linker.findSymbol(listObject),
                listObject
        ));

        Map<String, ClassElement> mapStringObjectGenerics = new java.util.HashMap<>();
        mapStringObjectGenerics.put("K", elementFactory.newClassElement(
                env.getElementUtils().getTypeElement(String.class.getName()),
                AnnotationMetadata.EMPTY_METADATA
        ));
        mapStringObjectGenerics.put("V", elementFactory.newClassElement(
                env.getElementUtils().getTypeElement(Object.class.getName()),
                AnnotationMetadata.EMPTY_METADATA
        ));
        ClassElement mapStringObject = elementFactory.newClassElement(
                env.getElementUtils().getTypeElement(Map.class.getName()),
                AnnotationMetadata.EMPTY_METADATA,
                mapStringObjectGenerics
        );
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.generate(
                problemReporter,
                ClassName.get(element.getPackageName(), "$Serializer$Map"),
                ParameterizedTypeName.get(Map.class, String.class, Object.class),
                linker.findSymbol(mapStringObject),
                mapStringObject
        ));
    }
}
