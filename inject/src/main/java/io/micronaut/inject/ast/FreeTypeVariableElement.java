package io.micronaut.inject.ast;

import java.util.List;
import java.util.Optional;

public interface FreeTypeVariableElement extends ClassElement {
    List<? extends ClassElement> getBounds();

    String getVariableName();

    Optional<Element> getDeclaringElement();
}
