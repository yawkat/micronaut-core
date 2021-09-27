package io.micronaut.inject.ast;

import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ReflectFreeTypeVariableElement extends ReflectTypeElement<TypeVariable<?>> implements FreeTypeVariableElement, ArrayableClassElement {
    private final int arrayDimensions;

    ReflectFreeTypeVariableElement(TypeVariable<?> typeVariable, int arrayDimensions) {
        super(typeVariable);
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new ReflectFreeTypeVariableElement(type, arrayDimensions);
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBounds() {
        return Arrays.stream(type.getBounds()).map(ClassElement::of).collect(Collectors.toList());
    }

    @Override
    public String getVariableName() {
        return type.getName();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        GenericDeclaration declaration = type.getGenericDeclaration();
        if (declaration instanceof Class) {
            return Optional.of(ClassElement.of((Class<?>) declaration));
        } else {
            return Optional.empty();
        }
    }
}
