package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Represents a <i>free</i> type variable. "Free" means that it is not bound to a type yet – {@code List<T>} has a free
 * type variable, {@code List<String>} does not.
 * <p>
 * For compatibility, this variable acts like its first upper bound when used as a {@link ClassElement}.
 */
@Experimental
public interface FreeTypeVariableElement extends ClassElement {
    /**
     * @return The bounds declared for this type variable.
     */
    @NonNull
    List<? extends ClassElement> getBounds();

    /**
     * @return The name of this variable.
     */
    String getVariableName();

    /**
     * @return The element declaring this variable, if it can be determined. Must be either a method or a class.
     */
    Optional<Element> getDeclaringElement();
}
