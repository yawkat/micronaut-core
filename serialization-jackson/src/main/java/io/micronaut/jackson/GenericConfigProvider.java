package io.micronaut.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.GenericDeserializationConfig;

@Factory
@BootstrapContextCompatible
public class GenericConfigProvider {
    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;

    public GenericConfigProvider(ObjectMapper objectMapper, @Nullable JsonFactory jsonFactory) {
        this.objectMapper = objectMapper;
        this.jsonFactory = jsonFactory;
    }

    @Bean
    public GenericDeserializationConfig deserializationConfig() {
        GenericDeserializationConfig config = DatabindUtil.toGenericConfig(objectMapper.getDeserializationConfig());
        if (jsonFactory != null) {
            config = config.withFactory(jsonFactory);
        }
        return config;
    }
}
