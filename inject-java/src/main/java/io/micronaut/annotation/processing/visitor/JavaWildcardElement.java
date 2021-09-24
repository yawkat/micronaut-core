package io.micronaut.annotation.processing.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class JavaWildcardElement extends JavaClassElement implements WildcardElement {
    private final List<JavaClassElement> upperBounds;
    private final List<JavaClassElement> lowerBounds;

    JavaWildcardElement(List<JavaClassElement> upperBounds, List<JavaClassElement> lowerBounds) {
        super(
                upperBounds.get(0).classElement,
                upperBounds.get(0).getAnnotationMetadata(),
                upperBounds.get(0).visitorContext,
                upperBounds.get(0).typeArguments,
                upperBounds.get(0).getGenericTypeInfo()
        );
        this.upperBounds = upperBounds;
        this.lowerBounds = lowerBounds;
    }

    @Override
    public List<? extends ClassElement> getUpperBounds() {
        return upperBounds;
    }

    @Override
    public List<? extends ClassElement> getLowerBounds() {
        return lowerBounds;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        if (arrayDimensions != 0) {
            throw new UnsupportedOperationException("Can't create array of wildcard");
        }
        return this;
    }

    @Override
    public ClassElement foldTypes(Function<ClassElement, ClassElement> fold) {
        List<JavaClassElement> upperBounds = this.upperBounds.stream().map(ele -> (JavaClassElement) ele.foldTypes(fold)).collect(Collectors.toList());
        List<JavaClassElement> lowerBounds = this.lowerBounds.stream().map(ele -> (JavaClassElement) ele.foldTypes(fold)).collect(Collectors.toList());
        return fold.apply(upperBounds.contains(null) || lowerBounds.contains(null) ? null : new JavaWildcardElement(upperBounds, lowerBounds));
    }

}
