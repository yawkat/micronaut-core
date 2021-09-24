package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FreeTypeVariableElement;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeVariable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class JavaFreeTypeVariableElement extends JavaClassElement implements FreeTypeVariableElement {
    final TypeVariable realTypeVariable;
    private final List<JavaClassElement> bounds;

    JavaFreeTypeVariableElement(TypeVariable realTypeVariable, List<JavaClassElement> bounds, int arrayDimensions) {
        super(
                bounds.get(0).classElement,
                bounds.get(0).getAnnotationMetadata(),
                bounds.get(0).visitorContext,
                bounds.get(0).typeArguments,
                bounds.get(0).getGenericTypeInfo(),
                arrayDimensions,
                true
        );
        this.realTypeVariable = realTypeVariable;
        this.bounds = bounds;
    }

    @Override
    public List<? extends ClassElement> getBounds() {
        return bounds;
    }

    private TypeParameterElement getParameterElement() {
        return (TypeParameterElement) realTypeVariable.asElement();
    }

    @Override
    public String getVariableName() {
        return getParameterElement().getSimpleName().toString();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.of(mirrorToClassElement(getParameterElement().getGenericElement().asType(), visitorContext, getGenericTypeInfo()));
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new JavaFreeTypeVariableElement(realTypeVariable, bounds, arrayDimensions);
    }

    @Override
    public ClassElement foldTypes(Function<ClassElement, ClassElement> fold) {
        return fold.apply(this);
    }

}
