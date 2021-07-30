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
package io.micronaut.json.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import com.fasterxml.jackson.jr.stree.*;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.processor.SingleThreadedBufferingProcessor;
import io.micronaut.json.GenericDeserializationConfig;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * A Reactive streams publisher that publishes a {@link JrsValue} once the JSON has been fully consumed.
 * Uses {@link NonBlockingJsonParser} internally allowing the parsing of
 * JSON from an incoming stream of bytes in a non-blocking manner
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JacksonJrProcessor extends SingleThreadedBufferingProcessor<byte[], JrsValue> {

    private static final Logger LOG = LoggerFactory.getLogger(JacksonJrProcessor.class);

    private NonBlockingJsonParser currentNonBlockingJsonParser;
    private final Deque<StructureBuilder> nodeStack = new ArrayDeque<>();
    private final JsonFactory jsonFactory;
    private final GenericDeserializationConfig deserializationConfig;
    private final boolean streamArray;
    private boolean rootIsArray;
    private boolean jsonStream;

    /**
     * Creates a new JacksonProcessor.
     *
     * @param streamArray Whether arrays should be streamed
     */
    public JacksonJrProcessor(boolean streamArray, @NonNull GenericDeserializationConfig deserializationConfig) {
        this.jsonFactory = deserializationConfig.getFactory();
        this.streamArray = streamArray;
        this.jsonStream = true;
        this.deserializationConfig = deserializationConfig;
        try {
            this.currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create non-blocking JSON parser: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new JacksonProcessor.
     *
     * @param streamArray Whether arrays should be streamed
     */
    public JacksonJrProcessor(boolean streamArray) {
        this(streamArray, GenericDeserializationConfig.DEFAULT);
    }

    /**
     * @param deserializationConfig The jackson deserialization configuration
     */
    public JacksonJrProcessor(GenericDeserializationConfig deserializationConfig) {
        this(false, deserializationConfig);
    }

    /**
     * Default constructor.
     */
    public JacksonJrProcessor() {
        this(GenericDeserializationConfig.DEFAULT);
    }

    /**
     * @return Whether more input is needed
     */
    public boolean needMoreInput() {
        return currentNonBlockingJsonParser.getNonBlockingInputFeeder().needMoreInput();
    }

    @Override
    protected void doOnComplete() {
        if (jsonStream && nodeStack.isEmpty()) {
            super.doOnComplete();
        } else if (needMoreInput()) {
            doOnError(new JsonEOFException(currentNonBlockingJsonParser, JsonToken.NOT_AVAILABLE, "Unexpected end-of-input"));
        } else {
            super.doOnComplete();
        }
    }

    @Override
    protected void onUpstreamMessage(byte[] message) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received upstream bytes of length: " + message.length);
        }

        try {
            if (message.length == 0) {
                if (needMoreInput()) {
                    requestMoreInput();
                }
                return;
            }

            final ByteArrayFeeder byteFeeder = byteFeeder(message);

            JsonToken event = currentNonBlockingJsonParser.nextToken();
            checkForStreaming(event);

            while (event != JsonToken.NOT_AVAILABLE) {
                final JrsValue root = asJsonNode(event);
                if (root != null) {

                    final boolean isLast = nodeStack.isEmpty() && !jsonStream;
                    if (isLast) {
                        byteFeeder.endOfInput();
                        if (streamArray && root.isArray()) {
                            break;
                        }
                    }

                    publishNode(root);
                    if (isLast) {
                        break;
                    }
                }
                event = currentNonBlockingJsonParser.nextToken();
            }
            if (jsonStream) {
                if (nodeStack.isEmpty()) {
                    byteFeeder.endOfInput();
                }
                requestMoreInput();
            } else if (needMoreInput()) {
                requestMoreInput();
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void checkForStreaming(JsonToken event) {
        if (event == JsonToken.START_ARRAY && nodeStack.peekFirst() == null) {
            rootIsArray = true;
            jsonStream = false;
        }
    }

    private void publishNode(final JrsValue root) {
        final Optional<Subscriber<? super JrsValue>> opt = currentDownstreamSubscriber();
        if (opt.isPresent()) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Materialized new JsonNode call onNext...");
            }
            opt.get().onNext(root);
        }
    }

    private void requestMoreInput() {
        if (LOG.isTraceEnabled()) {
            LOG.trace("More input required to parse JSON. Demanding more.");
        }
        upstreamSubscription.request(1);
        upstreamDemand++;
    }

    private ByteArrayFeeder byteFeeder(byte[] message) throws IOException {
        ByteArrayFeeder byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
        final boolean needMoreInput = byteFeeder.needMoreInput();
        if (!needMoreInput) {
            currentNonBlockingJsonParser = (NonBlockingJsonParser) jsonFactory.createNonBlockingByteArrayParser();
            byteFeeder = currentNonBlockingJsonParser.getNonBlockingInputFeeder();
        }

        byteFeeder.feedInput(message, 0, message.length);
        return byteFeeder;
    }

    /**
     * @return The root node when the whole tree is built.
     **/
    private JrsValue asJsonNode(JsonToken event) throws IOException {
        switch (event) {
            case START_OBJECT:
                nodeStack.push(new ObjectBuilder());
                break;

            case START_ARRAY:
                if (nodeStack.isEmpty()) {
                    rootIsArray = true;
                }
                nodeStack.push(new ArrayBuilder());
                break;

            case END_OBJECT:
            case END_ARRAY:
                checkEmptyNodeStack(event);
                final StructureBuilder current = nodeStack.pop();
                if (nodeStack.isEmpty()) {
                    return current.build();
                }

                nodeStack.peekFirst().addValue(current.build());
                break;

            case FIELD_NAME:
                checkEmptyNodeStack(event);
                nodeStack.peekFirst().setCurrentFieldName(currentNonBlockingJsonParser.getCurrentName());
                break;

            case VALUE_NUMBER_INT:
                checkEmptyNodeStack(event);
                addIntegerNumber(nodeStack.peekFirst());
                break;

            case VALUE_STRING:
                checkEmptyNodeStack(event);
                nodeStack.peekFirst().addValue(new JrsString(currentNonBlockingJsonParser.getValueAsString()));
                break;

            case VALUE_NUMBER_FLOAT:
                checkEmptyNodeStack(event);
                addFloatNumber(nodeStack.peekFirst());
                break;

            case VALUE_NULL:
                checkEmptyNodeStack(event);
                nodeStack.peekFirst().addValue(JrsNull.instance());
                break;

            case VALUE_TRUE:
            case VALUE_FALSE:
                checkEmptyNodeStack(event);
                nodeStack.peekFirst().addValue(currentNonBlockingJsonParser.getBooleanValue() ? JrsBoolean.TRUE : JrsBoolean.FALSE);
                break;

            default:
                throw new IllegalStateException("Unsupported JSON event: " + event);
        }

        // it is an array and the stack size is 1 which means the value is scalar
        if (rootIsArray && streamArray && nodeStack.size() == 1) {
            final ArrayBuilder array = (ArrayBuilder) nodeStack.peekFirst();
            if (array.values.size() > 0) {
                return array.values.remove(array.values.size() - 1);
            }
        }

        return null;
    }

    private static String tokenType(JsonToken token) {
        switch (token) {
            case END_OBJECT:
            case END_ARRAY:
                return "container end";
            case FIELD_NAME:
                return "field";
            case VALUE_NUMBER_INT:
                return "integer";
            case VALUE_STRING:
                return "string";
            case VALUE_NUMBER_FLOAT:
                return "float";
            case VALUE_NULL:
                return "null";
            case VALUE_TRUE:
            case VALUE_FALSE:
                return "boolean";
            default:
                return "";
        }
    }

    private void addIntegerNumber(StructureBuilder parent) throws IOException {
        if (deserializationConfig.useBigIntegerForInts()) {
            parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getBigIntegerValue()));
        } else {
            final JsonParser.NumberType numberIntType = currentNonBlockingJsonParser.getNumberType();
            switch (numberIntType) {
                case BIG_INTEGER:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getBigIntegerValue()));
                    break;
                case LONG:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getLongValue()));
                    break;
                case INT:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getIntValue()));
                    break;
                default:
                    throw new IllegalStateException("Unsupported number type: " + numberIntType);
            }
        }
    }

    private void addFloatNumber(StructureBuilder parent) throws IOException {
        if (deserializationConfig.useBigDecimalForFloats()) {
            parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getDecimalValue()));
        } else {
            final JsonParser.NumberType numberDecimalType = currentNonBlockingJsonParser.getNumberType();
            switch (numberDecimalType) {
                case FLOAT:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getFloatValue()));
                    break;
                case DOUBLE:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getDoubleValue()));
                    break;
                case BIG_DECIMAL:
                    parent.addValue(new JrsNumber(currentNonBlockingJsonParser.getDecimalValue()));
                    break;
                default:
                    // shouldn't get here
                    throw new IllegalStateException("Unsupported number type: " + numberDecimalType);
            }
        }
    }

    private void checkEmptyNodeStack(JsonToken token) throws JsonParseException {
        if (nodeStack.isEmpty()) {
            throw new JsonParseException(currentNonBlockingJsonParser, "Unexpected " + tokenType(token) + " literal");
        }
    }

    private interface StructureBuilder {
        void addValue(JrsValue value) throws JsonParseException;

        void setCurrentFieldName(String currentFieldName) throws JsonParseException;

        JrsValue build();
    }

    private class ObjectBuilder implements StructureBuilder {
        final Map<String, JrsValue> values = new LinkedHashMap<>();
        String currentFieldName = null;

        @Override
        public void addValue(JrsValue value) throws JsonParseException {
            if (currentFieldName == null) {
                throw new JsonParseException(currentNonBlockingJsonParser, "Expected field name, got value");
            }
            values.put(currentFieldName, value);
            currentFieldName = null;
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) {
            this.currentFieldName = currentFieldName;
        }

        @Override
        public JrsValue build() {
            return new JrsObject(values);
        }
    }

    private class ArrayBuilder implements StructureBuilder {
        final List<JrsValue> values = new ArrayList<>();

        @Override
        public void addValue(JrsValue value) {
            values.add(value);
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) throws JsonParseException {
            throw new JsonParseException(currentNonBlockingJsonParser, "Expected array value, got field name");
        }

        @Override
        public JrsValue build() {
            return new JrsArray(values);
        }
    }
}
