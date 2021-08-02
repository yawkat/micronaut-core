package io.micronaut.json;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.jr.stree.JacksonJrsTreeCodec;
import com.fasterxml.jackson.jr.stree.JrsValue;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.TreeGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

public interface ExtendedObjectCodec {
    ObjectCodec getObjectCodec();

    default JrsValue valueToTree(Object value) {
        TreeGenerator generator = new TreeGenerator();
        try {
            getObjectCodec().writeValue(generator, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return generator.getCompletedValue();
    }

    <T> T readValue(JsonParser parser, Argument<T> type) throws IOException;

    default <T> T readValue(JsonParser parser, Class<T> type) throws IOException {
        return getObjectCodec().readValue(parser, type);
    }

    void updateValue(JsonParser parser, Object value) throws IOException;

    ExtendedObjectCodec cloneWithFeatures(JsonFeatures features);

    ExtendedObjectCodec cloneWithViewClass(Class<?> viewClass);

    default byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
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

    GenericDeserializationConfig getDeserializationConfig();
}
