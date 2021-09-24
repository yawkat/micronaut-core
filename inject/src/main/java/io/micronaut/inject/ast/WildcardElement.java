package io.micronaut.inject.ast;

import java.util.List;

public interface WildcardElement extends ClassElement {
    List<? extends ClassElement> getUpperBounds();

    List<? extends ClassElement> getLowerBounds();
}
