package io.micronaut.json;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

public interface JsonCodec {
    <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException;

    default <T> T readValueFromTree(JsonNode tree, Class<T> type) throws IOException {
        return readValueFromTree(tree, Argument.of(type));
    }

    JsonNode writeValueToTree(Object value) throws IOException;

    @Internal
    default void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Internal
    default JsonCodec cloneWithFeatures(JsonFeatures features) {
        throw new UnsupportedOperationException();
    }

    @Internal
    default JsonCodec cloneWithViewClass(Class<?> viewClass) {
        throw new UnsupportedOperationException();
    }

    <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException;

    <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException;

    default <T> T readValue(String byteArray, Argument<T> type) throws IOException {
        return readValue(byteArray.getBytes(StandardCharsets.UTF_8), type);
    }

    void writeValue(OutputStream outputStream, Object object) throws IOException;

    byte[] writeValueAsBytes(Object object) throws IOException;

    JsonConfig getDeserializationConfig();

    Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray);

    @Internal
    default Optional<JsonFeatures> detectFeatures(AnnotationMetadata annotations) {
        return Optional.empty();
    }
}
