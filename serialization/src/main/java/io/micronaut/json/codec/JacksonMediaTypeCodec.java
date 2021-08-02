/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.json.codec;

import com.fasterxml.jackson.core.*;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.json.ExtendedObjectCodec;
import io.micronaut.json.GenericDeserializationConfig;
import io.micronaut.json.GenericJsonMediaTypeCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.runtime.ApplicationConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link MediaTypeCodec} Jackson based implementations.
 *
 * @author Graeme Rocher
 * @author svishnyakov
 * @since 1.3.0
 */
public abstract class JacksonMediaTypeCodec implements MediaTypeCodec, GenericJsonMediaTypeCodec {

    protected final ApplicationConfiguration applicationConfiguration;
    protected final List<MediaType> additionalTypes;
    protected final CodecConfiguration codecConfiguration;
    protected final MediaType mediaType;
    private final BeanProvider<ExtendedObjectCodec> objectMapperProvider;
    private ExtendedObjectCodec objectMapper;

    /**
     * @param objectMapperProvider     To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     * @param mediaType                Client request/response media type
     */
    public JacksonMediaTypeCodec(BeanProvider<ExtendedObjectCodec> objectMapperProvider,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        this.objectMapperProvider = objectMapperProvider;
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
        this.mediaType = mediaType;
        if (codecConfiguration != null) {
            this.additionalTypes = codecConfiguration.getAdditionalTypes();
        } else {
            this.additionalTypes = Collections.emptyList();
        }
    }

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     * @param mediaType                Client request/response media type
     */
    public JacksonMediaTypeCodec(ExtendedObjectCodec objectMapper,
                                 ApplicationConfiguration applicationConfiguration,
                                 CodecConfiguration codecConfiguration,
                                 MediaType mediaType) {
        this(() -> objectMapper, applicationConfiguration, codecConfiguration, mediaType);
        ArgumentUtils.requireNonNull("objectMapper", objectMapper);
        this.objectMapper = objectMapper;
    }

    /**
     * @return The object mapper
     */
    public ExtendedObjectCodec getObjectMapper() {
        ExtendedObjectCodec objectMapper = this.objectMapper;
        if (objectMapper == null) {
            synchronized (this) { // double check
                objectMapper = this.objectMapper;
                if (objectMapper == null) {
                    objectMapper = objectMapperProvider.get();
                    this.objectMapper = objectMapper;
                }
            }
        }
        return objectMapper;
    }

    @Override
    public GenericJsonMediaTypeCodec cloneWithFeatures(JsonFeatures features) {
        return cloneWithMapper(getObjectMapper().cloneWithFeatures(features));
    }

    @Override
    public GenericJsonMediaTypeCodec cloneWithViewClass(Class<?> viewClass) {
        return cloneWithMapper(getObjectMapper().cloneWithViewClass(viewClass));
    }

    protected abstract JacksonMediaTypeCodec cloneWithMapper(ExtendedObjectCodec mapper);

    @Override
    public Collection<MediaType> getMediaTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(mediaType);
        mediaTypes.addAll(additionalTypes);
        return mediaTypes;
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return !(CharSequence.class.isAssignableFrom(type));
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try (JsonParser parser = getObjectMapper().getObjectCodec().getFactory().createParser(inputStream)) {
            return getObjectMapper().readValue(parser, type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
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
    @Override
    public <T> T decode(Argument<T> type, TreeNode node) throws CodecException {
        try {
            ExtendedObjectCodec om = getObjectMapper();
            return om.readValue(node.traverse(om.getObjectCodec()), type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) buffer.toString(applicationConfiguration.getDefaultCharset());
            } else {
                try (JsonParser parser = getObjectMapper().getObjectCodec().getFactory().createParser(buffer.toByteArray())) {
                    return getObjectMapper().readValue(parser, type);
                }
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding stream for type [" + type.getType() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decode(Argument<T> type, byte[] bytes) throws CodecException {
        try {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                return (T) new String(bytes, applicationConfiguration.getDefaultCharset());
            } else {
                try (JsonParser parser = getObjectMapper().getObjectCodec().getFactory().createParser(bytes)) {
                    return getObjectMapper().readValue(parser, type);
                }
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding stream for type [" + type.getType() + "]: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, String data) throws CodecException {
        try (JsonParser parser = getObjectMapper().getObjectCodec().getFactory().createParser(data)) {
            return getObjectMapper().readValue(parser, type);
        } catch (IOException e) {
            throw new CodecException("Error decoding JSON stream for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try (JsonGenerator generator = getObjectMapper().getObjectCodec().getFactory().createGenerator(outputStream)) {
            getObjectMapper().getObjectCodec().writeValue(generator, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if (object instanceof byte[]) {
                return (byte[]) object;
            } else {
                return getObjectMapper().writeValueAsBytes(object);
            }
        } catch (JsonProcessingException e) {
            throw new CodecException("Error encoding object [" + object + "] to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
        if (object instanceof byte[]) {
            return allocator.copiedBuffer((byte[]) object);
        }
        ByteBuffer<B> buffer = allocator.buffer();
        OutputStream outputStream = buffer.toOutputStream();
        encode(object, outputStream);
        return buffer;
    }

    @Override
    public GenericDeserializationConfig getDeserializationConfig() {
        return getObjectMapper().getDeserializationConfig();
    }
}
