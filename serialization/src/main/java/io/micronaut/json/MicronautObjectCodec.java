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
package io.micronaut.json;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.json.tree.TreeGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

public abstract class MicronautObjectCodec {
    public abstract ObjectCodec getObjectCodec();

    public final JsonNode valueToTree(Object value) {
        TreeGenerator generator = new TreeGenerator();
        try {
            getObjectCodec().writeValue(generator, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return generator.getCompletedValue();
    }

    public final <T> T readValue(JsonParser parser, Class<T> type) throws IOException {
        return getObjectCodec().readValue(parser, type);
    }

    public final byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        ByteArrayBuilder bb = new ByteArrayBuilder(getObjectCodec().getFactory()._getBufferRecycler());
        try (JsonGenerator generator = getObjectCodec().getFactory().createGenerator(bb)) {
            getObjectCodec().writeValue(generator, value);
        } catch (JsonProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
    }

    public abstract <T> T readValue(JsonParser parser, Argument<T> type) throws IOException;

    public abstract void updateValue(JsonParser parser, Object value) throws IOException;

    public abstract MicronautObjectCodec cloneWithFeatures(JsonFeatures features);

    public abstract MicronautObjectCodec cloneWithViewClass(Class<?> viewClass);

    public abstract GenericDeserializationConfig getDeserializationConfig();

    @Nullable
    public abstract JsonFeatures detectFeatures(AnnotationMetadata annotations);
}
