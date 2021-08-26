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
package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.DatabindChoice;
import io.micronaut.json.convert.JsonConverterRegistrar;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Converter registrar for Jackson.
 *
 * @author graemerocher
 * @since 2.0
 */
@Singleton
@Internal
@DatabindChoice.RequiresJackson
public class JacksonConverterRegistrar implements TypeConverterRegistrar {

    private final BeanProvider<ObjectMapper> objectMapper;
    private final JsonConverterRegistrar genericConverter;

    /**
     * Default constructor.
     * @param objectMapper The object mapper provider
     * @param genericConverter The generic json converter (without jackson-databind)
     */
    @Inject
    protected JacksonConverterRegistrar(
            BeanProvider<ObjectMapper> objectMapper,
            JsonConverterRegistrar genericConverter) {
        this.objectMapper = objectMapper;
        this.genericConverter = genericConverter;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                ArrayNode.class,
                Object[].class,
                genericConverter.arrayNodeToObjectConverter()
        );
        conversionService.addConverter(
                ArrayNode.class,
                Iterable.class,
                genericConverter.arrayNodeToIterableConverter()
        );
        conversionService.addConverter(
                Object.class,
                JsonNode.class,
                objectToJsonNodeConverter()
        );
        conversionService.addConverter(
                CharSequence.class,
                PropertyNamingStrategy.class,
                (charSequence, targetType, context) -> {

                    Optional<PropertyNamingStrategy> propertyNamingStrategy = resolvePropertyNamingStrategy(charSequence);

                    if (!propertyNamingStrategy.isPresent()) {
                        context.reject(charSequence, new IllegalArgumentException(String.format("Unable to convert '%s' to a com.fasterxml.jackson.databind.PropertyNamingStrategy", charSequence)));
                    }

                    return propertyNamingStrategy;
                }
        );
    }

    /**
     * @return A converter that converts an object to a json node
     */
    protected TypeConverter<Object, JsonNode> objectToJsonNodeConverter() {
        // same as in JsonConverterRegistrar, but with JsonNode
        return (object, targetType, context) -> {
            try {
                return Optional.of(objectMapper.get().valueToTree(object));
            } catch (IllegalArgumentException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    @NonNull
    private Optional<PropertyNamingStrategy> resolvePropertyNamingStrategy(@Nullable CharSequence charSequence) {
        if (charSequence != null) {
            String stringValue = NameUtils.environmentName(charSequence.toString());
            if (StringUtils.isNotEmpty(stringValue)) {
                switch (stringValue) {
                    case "SNAKE_CASE":
                        return Optional.of(PropertyNamingStrategies.SNAKE_CASE);
                    case "UPPER_CAMEL_CASE":
                        return Optional.of(PropertyNamingStrategies.UPPER_CAMEL_CASE);
                    case "LOWER_CASE":
                        return Optional.of(PropertyNamingStrategies.LOWER_CASE);
                    case "KEBAB_CASE":
                        return Optional.of(PropertyNamingStrategies.KEBAB_CASE);
                    case "LOWER_CAMEL_CASE":
                        return Optional.of(PropertyNamingStrategies.LOWER_CAMEL_CASE);
                    case "LOWER_DOT_CASE":
                        return Optional.of(PropertyNamingStrategies.LOWER_DOT_CASE);
                    default:
                        return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }
}
