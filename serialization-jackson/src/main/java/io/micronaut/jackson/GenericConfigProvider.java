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
