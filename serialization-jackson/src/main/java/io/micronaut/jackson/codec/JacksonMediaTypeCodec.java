package io.micronaut.jackson.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.jackson.DatabindObjectCodec;
import io.micronaut.json.MicronautObjectCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Deprecated
public abstract class JacksonMediaTypeCodec extends io.micronaut.json.codec.JacksonMediaTypeCodec {
    public static final String REGULAR_JSON_MEDIA_TYPE_CODEC_NAME = "json";

    public JacksonMediaTypeCodec(BeanProvider<ObjectMapper> objectMapperProvider,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        super(
                () -> new DatabindObjectCodec(objectMapperProvider.get()),
                applicationConfiguration,
                codecConfiguration,
                mediaType
        );
    }

    public JacksonMediaTypeCodec(ObjectMapper objectMapper,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        super(
                new DatabindObjectCodec(objectMapper),
                applicationConfiguration,
                codecConfiguration,
                mediaType
        );
    }

    public ObjectMapper getObjectMapper() {
        return (ObjectMapper) getObjectCodec().getObjectCodec();
    }

    @Override
    public io.micronaut.json.codec.JacksonMediaTypeCodec cloneWithFeatures(JsonFeatures features) {
        return cloneWithFeatures((JacksonFeatures) features);
    }

    public abstract JacksonMediaTypeCodec cloneWithFeatures(JacksonFeatures jacksonFeatures);

    @Override
    protected io.micronaut.json.codec.JacksonMediaTypeCodec cloneWithMapper(MicronautObjectCodec mapper) {
        throw new UnsupportedOperationException();
    }

    /**
     * Decodes the given JSON node.
     *
     * @param type The type
     * @param node The Json Node
     * @param <T>  The generic type
     * @return The decoded object
     * @throws CodecException When object cannot be decoded
     */
    public <T> T decode(Argument<T> type, JsonNode node) throws CodecException {
        return super.decode(type, node);
    }
}
