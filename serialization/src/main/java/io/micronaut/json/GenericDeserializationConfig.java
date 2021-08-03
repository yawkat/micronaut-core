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
import com.fasterxml.jackson.jr.stree.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public JacksonJrsTreeCodec getJrsTreeCodec() {
        if (useBigDecimalForFloats || useBigIntegerForInts) {
            return new JrsTreeCodec();
        } else {
            // don't need our special codec
            return JacksonJrsTreeCodec.SINGLETON;
        }
    }

    private class JrsTreeCodec extends JacksonJrsTreeCodec {
        // adapted from the original tree codec, with added bigdecimal handling.

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TreeNode> T readTree(JsonParser p) throws IOException {
            return (T) nodeFrom(p);
        }

        private JrsValue nodeFrom(JsonParser p) throws IOException {
            int tokenId = p.hasCurrentToken()
                    ? p.currentTokenId() : p.nextToken().id();

            switch (tokenId) {
                case JsonTokenId.ID_TRUE:
                    return JrsBoolean.TRUE;
                case JsonTokenId.ID_FALSE:
                    return JrsBoolean.FALSE;
                case JsonTokenId.ID_NUMBER_INT:
                    if (useBigIntegerForInts) {
                        return new JrsNumber(p.getBigIntegerValue());
                    } else {
                        return new JrsNumber(p.getNumberValue());
                    }
                case JsonTokenId.ID_NUMBER_FLOAT:
                    if (useBigDecimalForFloats) {
                        return new JrsNumber(p.getDecimalValue());
                    } else {
                        return new JrsNumber(p.getNumberValue());
                    }
                case JsonTokenId.ID_STRING:
                    return new JrsString(p.getText());
                case JsonTokenId.ID_START_ARRAY: {
                    List<JrsValue> values = _list();
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        values.add(nodeFrom(p));
                    }
                    return new JrsArray(values);
                }
                case JsonTokenId.ID_START_OBJECT: {
                    Map<String, JrsValue> values = _map();
                    while (p.nextToken() != JsonToken.END_OBJECT) {
                        final String currentName = p.getCurrentName();
                        p.nextToken();
                        values.put(currentName, nodeFrom(p));
                    }
                    return new JrsObject(values);
                }
                case JsonTokenId.ID_EMBEDDED_OBJECT:
                    return new JrsEmbeddedObject(p.getEmbeddedObject());

                case JsonTokenId.ID_NULL:
                    return JrsNull.instance();
                default:
            }
            throw new UnsupportedOperationException("Unsupported token id " + tokenId + " (" + p.currentToken() + ")");
        }
    }
}
