package io.micronaut.json;

import com.fasterxml.jackson.core.TreeNode;
import io.micronaut.core.type.Argument;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;

public interface GenericJsonMediaTypeCodec extends MediaTypeCodec {
    String REGULAR_JSON_MEDIA_TYPE_CODEC_NAME = "json";

    /**
     * Decodes the given JSON node.
     *
     * @param type The type
     * @param node The Json Node
     * @param <T>  The generic type
     * @return The decoded object
     * @throws CodecException When object cannot be decoded
     */
    <T> T decode(Argument<T> type, TreeNode node) throws CodecException;

    GenericDeserializationConfig getDeserializationConfig();

    GenericJsonMediaTypeCodec cloneWithFeatures(JsonFeatures features);

    GenericJsonMediaTypeCodec cloneWithViewClass(Class<?> viewClass);
}
