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

public final class JsonConfig {
    private final boolean useBigDecimalForFloats;
    private final boolean useBigIntegerForInts;

    public static final JsonConfig DEFAULT = new JsonConfig(false, false);

    private JsonConfig(boolean useBigDecimalForFloats, boolean useBigIntegerForInts) {
        this.useBigDecimalForFloats = useBigDecimalForFloats;
        this.useBigIntegerForInts = useBigIntegerForInts;
    }

    public boolean useBigDecimalForFloats() {
        return useBigDecimalForFloats;
    }

    public JsonConfig withUseBigDecimalForFloats(boolean useBigDecimalForFloats) {
        return new JsonConfig(useBigDecimalForFloats, useBigIntegerForInts);
    }

    public boolean useBigIntegerForInts() {
        return useBigIntegerForInts;
    }

    public JsonConfig withUseBigIntegerForInts(boolean useBigIntegerForInts) {
        return new JsonConfig(useBigDecimalForFloats, useBigIntegerForInts);
    }
}
