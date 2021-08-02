package io.micronaut.json;

public interface JsonConfiguration {
    boolean isAlwaysSerializeErrorsAsList();

    int getArraySizeThreshold();
}
