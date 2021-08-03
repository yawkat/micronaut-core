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

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.json.GenericDeserializationConfig;

public final class DatabindUtil {
    private DatabindUtil() {}

    public static GenericDeserializationConfig toGenericConfig(ObjectMapper objectMapper) {
        return toGenericConfig(objectMapper.getDeserializationConfig())
                .withFactory(objectMapper.getFactory());
    }

    public static GenericDeserializationConfig toGenericConfig(DeserializationConfig config) {
        return GenericDeserializationConfig.DEFAULT
                .withUseBigDecimalForFloats(config.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
                .withUseBigIntegerForInts(config.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
    }
}
