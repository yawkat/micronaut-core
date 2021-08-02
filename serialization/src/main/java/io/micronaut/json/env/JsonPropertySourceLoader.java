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
package io.micronaut.json.env;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.jr.stree.*;
import io.micronaut.context.env.AbstractPropertySourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * <p>A {@link io.micronaut.context.env.PropertySourceLoader} that reads <tt>application.json</tt> files if they exist.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for property source loader.
     */
    public static final String FILE_EXTENSION = "json";

    @Override
    public Set<String> getExtensions() {
        return Collections.singleton(FILE_EXTENSION);
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        Map<String, Object> map = readJsonAsMap(input);
        processMap(finalMap, map, "");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> readJsonAsMap(InputStream input) throws IOException {
        return (Map<String, Object>) unwrap(readJsonAsObject(input));
    }

    private JrsObject readJsonAsObject(InputStream input) throws IOException {
        try (JsonParser parser = new JsonFactory().createParser(input)) {
            return JacksonJrsTreeCodec.SINGLETON.readTree(parser);
        }
    }

    private Object unwrap(JrsValue value) {
        if (value instanceof JrsNumber) {
            return ((JrsNumber) value).getValue();
        } else if (value.isNull()) {
            return null;
        } else if (value instanceof JrsBoolean) {
            return ((JrsBoolean) value).booleanValue();
        } else if (value instanceof JrsArray) {
            List<Object> unwrapped = new ArrayList<>();
            ((JrsArray) value).elements().forEachRemaining(v -> unwrapped.add(unwrap(v)));
            return unwrapped;
        } else if (value instanceof JrsObject) {
            Map<String, Object> unwrapped = new LinkedHashMap<>();
            ((JrsObject) value).fields().forEachRemaining(e -> unwrapped.put(e.getKey(), unwrap(e.getValue())));
            return unwrapped;
        } else {
            return value.asText();
        }
    }
}
