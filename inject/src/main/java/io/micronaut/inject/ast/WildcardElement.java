package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.List;

/**
 * Represents a wildcard. For compatibility, this wildcard acts like its first upper bound when used as a
 * {@link ClassElement}.
 */
@Experimental
public interface WildcardElement extends ClassElement {
    /**
     * @return The upper bounds of this wildcard. Never empty. To match this wildcard, a type must be assignable to all
     * upper bounds (must extend all upper bounds).
     */
    @NonNull
    List<? extends ClassElement> getUpperBounds();

    /**
     * @return The lower bounds of this wildcard. May be empty. To match this wildcard, a type must be assignable from
     * all lower bounds (must be a supertype of all lower bounds).
     */
    @NonNull
    List<? extends ClassElement> getLowerBounds();
}
