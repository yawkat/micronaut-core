package io.micronaut.http.netty;

import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Jackson-databind creates this serializer magically, we need to add our own
 */
@Singleton
class NettyHttpMethodSerializer implements Serializer<HttpMethod>, Deserializer<HttpMethod> {
    private final Serializer<? super String> stringSerializer;
    private final Deserializer<? extends String> stringDeserializer;

    @Inject
    NettyHttpMethodSerializer(SerializerLocator locator) {
        this.stringSerializer = locator.findContravariantSerializer(String.class);
        this.stringDeserializer = locator.findInvariantDeserializer(String.class);
    }

    @Override
    public HttpMethod deserialize(Decoder decoder) throws IOException {
        return HttpMethod.valueOf(stringDeserializer.deserialize(decoder));
    }

    @Override
    public void serialize(JsonGenerator encoder, HttpMethod value) throws IOException {
        stringSerializer.serialize(encoder, value.name());
    }
}
