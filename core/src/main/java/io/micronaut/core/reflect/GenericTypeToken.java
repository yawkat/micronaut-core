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
package io.micronaut.core.reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class GenericTypeToken<T> {
    private final Type type;

    protected GenericTypeToken() {
        Type parameterization = GenericTypeUtils.findParameterization(GenericTypeUtils.parameterizeWithFreeVariables(getClass()), GenericTypeToken.class);
        assert parameterization != null;
        this.type = ((ParameterizedType) parameterization).getActualTypeArguments()[0];
    }

    public final Type getType() {
        return type;
    }
}
