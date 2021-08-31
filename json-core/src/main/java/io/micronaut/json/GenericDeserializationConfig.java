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

public final class GenericDeserializationConfig {
    private final JsonFactory factory;
    private final boolean useBigDecimalForFloats;
    private final boolean useBigIntegerForInts;

    public static final GenericDeserializationConfig DEFAULT = new GenericDeserializationConfig(new JsonFactory(), false, false);

    private GenericDeserializationConfig(JsonFactory factory, boolean useBigDecimalForFloats, boolean useBigIntegerForInts) {
        this.factory = factory;
        this.useBigDecimalForFloats = useBigDecimalForFloats;
        this.useBigIntegerForInts = useBigIntegerForInts;
    }

    public JsonFactory getFactory() {
        return factory.copy(); // defensive copy
    }

    public GenericDeserializationConfig withFactory(JsonFactory factory) {
        return new GenericDeserializationConfig(factory.copy(), useBigDecimalForFloats, useBigIntegerForInts);
    }

    public boolean useBigDecimalForFloats() {
        return useBigDecimalForFloats;
    }

    public GenericDeserializationConfig withUseBigDecimalForFloats(boolean useBigDecimalForFloats) {
        return new GenericDeserializationConfig(factory, useBigDecimalForFloats, useBigIntegerForInts);
    }

    public boolean useBigIntegerForInts() {
        return useBigIntegerForInts;
    }

    public GenericDeserializationConfig withUseBigIntegerForInts(boolean useBigIntegerForInts) {
        return new GenericDeserializationConfig(factory, useBigDecimalForFloats, useBigIntegerForInts);
    }
}
