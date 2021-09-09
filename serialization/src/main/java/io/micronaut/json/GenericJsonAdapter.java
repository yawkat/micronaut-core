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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.generated.GeneratedObjectCodec;

import java.util.Iterator;
import java.util.ServiceLoader;

// todo: would be nice to remove this
@Internal
public interface GenericJsonAdapter {
    static GenericJsonAdapter getUnboundInstance() {
        return Helper.INSTANCE;
    }

    MicronautObjectCodec createObjectMapper();
}

class Helper {
    static final GenericJsonAdapter INSTANCE;

    static {
        Iterator<GenericJsonAdapter> iterator = ServiceLoader.load(GenericJsonAdapter.class).iterator();
        if (iterator.hasNext()) {
            INSTANCE = iterator.next();
        } else {
            // TODO: don't ApplicationContext.run...
            INSTANCE = () -> new GeneratedObjectCodec(new SerializerLocator(ApplicationContext.run()));
        }
    }
}
