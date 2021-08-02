package io.micronaut.jackson;

import io.micronaut.json.ExtendedObjectCodec;
import io.micronaut.json.GenericJsonAdapter;

/**
 * For ServiceLoader (used where no app context is available)
 */
public class JacksonJsonAdapter implements GenericJsonAdapter {
    @Override
    public ExtendedObjectCodec createObjectMapper() {
        return new DatabindObjectCodec(new ObjectMapperFactory().objectMapper(null, null));
    }
}
