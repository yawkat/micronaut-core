/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.jackson.databind.JacksonDatabindCodec;
import io.micronaut.jackson.databind.JacksonFeatures;
import io.micronaut.json.JsonCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.codec.JsonCodecMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;

import java.io.IOException;

/**
 * @deprecated Use {@link #JsonCodecMediaTypeCodec}
 */
@Deprecated
public abstract class JacksonMediaTypeCodec extends JsonCodecMediaTypeCodec {
    public static final String REGULAR_JSON_MEDIA_TYPE_CODEC_NAME = "json";

    public JacksonMediaTypeCodec(BeanProvider<ObjectMapper> objectMapperProvider,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        super(
                () -> new JacksonDatabindCodec(objectMapperProvider.get()),
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
                new JacksonDatabindCodec(objectMapper),
                applicationConfiguration,
                codecConfiguration,
                mediaType
        );
    }

    /**
     * @return The object mapper
     */
    public ObjectMapper getObjectMapper() {
        return ((JacksonDatabindCodec) getObjectCodec()).getObjectMapper();
    }

    @Override
    public JsonCodecMediaTypeCodec cloneWithFeatures(JsonFeatures features) {
        return cloneWithFeatures((JacksonFeatures) features);
    }

    public abstract JacksonMediaTypeCodec cloneWithFeatures(JacksonFeatures jacksonFeatures);

    @Override
    protected JsonCodecMediaTypeCodec cloneWithMapper(JsonCodec mapper) {
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
        try {
            return getObjectMapper().treeToValue(node, type.getType());
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }
}
