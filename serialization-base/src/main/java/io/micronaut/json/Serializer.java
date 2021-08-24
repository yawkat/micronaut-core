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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.reflect.GenericTypeUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

public interface Serializer<T> {
    void serialize(JsonGenerator encoder, T value) throws IOException;

    interface Factory extends BaseCodecFactory {
        @Override
        default Type getGenericType() {
            Type parameterization = GenericTypeUtils.findParameterization(getClass(), Factory.class);
            assert parameterization != null;
            return ((ParameterizedType) parameterization).getActualTypeArguments()[0];
        }

        @Override
        Serializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter);
    }
}
