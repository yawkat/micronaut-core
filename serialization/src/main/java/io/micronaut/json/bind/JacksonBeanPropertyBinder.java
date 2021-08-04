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
package io.micronaut.json.bind;

import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.ExtendedObjectCodec;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.json.tree.MicronautTreeCodec;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An {@link io.micronaut.core.bind.ArgumentBinder} capable of binding from an object from a map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
class JacksonBeanPropertyBinder implements BeanPropertyBinder {

    private final ExtendedObjectCodec objectMapper;
    private final int arraySizeThreshhold;

    /**
     * @param objectMapper  To read/write JSON
     * @param configuration The configuration for Jackson JSON parser
     */
    JacksonBeanPropertyBinder(ExtendedObjectCodec objectMapper, JsonConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.arraySizeThreshhold = configuration.getArraySizeThreshold();
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            JsonNode objectNode = buildSourceObjectNode(source.entrySet());
            JsonParser jsonParser = objectNode.traverse(objectMapper.getObjectCodec());
            Object result = objectMapper.readValue(jsonParser, context.getArgument());
            return () -> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return new BindingResult<Object>() {
                @Override
                public List<ConversionError> getConversionErrors() {
                    return CollectionUtils.iterableToList(context);
                }

                @Override
                public boolean isSatisfied() {
                    return false;
                }

                @Override
                public Optional<Object> getValue() {
                    return Optional.empty();
                }
            };
        }
    }

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            JsonNode objectNode = buildSourceObjectNode(source);
            return objectMapper.readValue(objectNode.traverse(objectMapper.getObjectCodec()), type);
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            JsonNode objectNode = buildSourceObjectNode(source);
            objectMapper.updateValue(objectNode.traverse(objectMapper.getObjectCodec()), object);
        } catch (Exception e) {
            context.reject(e);
        }
        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            JsonNode objectNode = buildSourceObjectNode(source);
            objectMapper.updateValue(objectNode.traverse(objectMapper.getObjectCodec()), object);
        } catch (Exception e) {
            throw newConversionError(object, e);
        }
        return object;
    }

    /**
     * @param object The bean
     * @param e      The exception object
     * @return The new conversion error
     */
    protected ConversionErrorException newConversionError(Object object, Exception e) {
        /* todo
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Object originalValue = ife.getValue();
            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.ofNullable(originalValue);
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            List<JsonMappingException.Reference> path = ife.getPath();
            String name;
            if (!path.isEmpty()) {
                name = path.get(path.size() - 1).getFieldName();
            } else {
                name = NameUtils.decapitalize(type.getSimpleName());
            }
            return new ConversionErrorException(Argument.of(ife.getTargetType(), name), conversionError);
        } else */ {

            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.empty();
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            return new ConversionErrorException(Argument.of(type), conversionError);
        }
    }

    private JsonNode buildSourceObjectNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        ObjectBuilder rootNode = new ObjectBuilder();
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            String property = key.toString();
            ValueBuilder current = rootNode;
            String index = null;
            Iterator<String> tokenIterator = StringUtils.splitOmitEmptyStringsIterator(property, '.');
            while (tokenIterator.hasNext()) {
                String token = tokenIterator.next();
                int j = token.indexOf('[');
                if (j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length() - 1);
                    token = token.substring(0, j);
                }

                if (!tokenIterator.hasNext()) {
                    if (current instanceof ObjectBuilder) {
                        ObjectBuilder objectNode = (ObjectBuilder) current;
                        if (index != null) {
                            ValueBuilder existing = objectNode.values.get(index);
                            if (!(existing instanceof ObjectBuilder)) {
                                existing = new ObjectBuilder();
                                objectNode.values.put(index, existing);
                            }
                            ObjectBuilder node = (ObjectBuilder) existing;
                            node.values.put(token, new FixedValue(objectMapper.valueToTree(value)));
                            index = null;
                        } else {
                            objectNode.values.put(token, new FixedValue(objectMapper.valueToTree(value)));
                        }
                    } else if (current instanceof ArrayBuilder && index != null) {
                        ArrayBuilder arrayNode = (ArrayBuilder) current;
                        int arrayIndex = Integer.parseInt(index);
                        if (arrayIndex < arraySizeThreshhold) {

                            if (arrayIndex >= arrayNode.values.size()) {
                                expandArrayToThreshold(arrayIndex, arrayNode);
                            }
                            ValueBuilder jsonNode = arrayNode.values.get(arrayIndex);
                            if (!(jsonNode instanceof ObjectBuilder)) {
                                jsonNode = new ObjectBuilder();
                                arrayNode.values.set(arrayIndex, jsonNode);
                            }
                            ((ObjectBuilder) jsonNode).values.put(token, new FixedValue(objectMapper.valueToTree(value)));
                        }
                        index = null;
                    }
                } else {
                    if (current instanceof ObjectBuilder) {
                        ObjectBuilder objectNode = (ObjectBuilder) current;
                        ValueBuilder existing = objectNode.values.get(token);
                        if (index != null) {
                            ValueBuilder jsonNode;
                            if (StringUtils.isDigits(index)) {
                                int arrayIndex = Integer.parseInt(index);
                                ArrayBuilder arrayNode;
                                if (!(existing instanceof ArrayBuilder)) {
                                    arrayNode = new ArrayBuilder();
                                    objectNode.values.put(token, arrayNode);
                                } else {
                                    arrayNode = (ArrayBuilder) existing;
                                }
                                expandArrayToThreshold(arrayIndex, arrayNode);
                                jsonNode = getOrCreateNodeAtIndex(arrayNode, arrayIndex);
                            } else {
                                if (!(existing instanceof ObjectBuilder)) {
                                    existing = new ObjectBuilder();
                                    objectNode.values.put(token, existing);
                                }
                                jsonNode = ((ObjectBuilder) existing).values.get(index);
                                if (!(jsonNode instanceof ObjectBuilder)) {
                                    jsonNode = new ObjectBuilder();
                                    ((ObjectBuilder) existing).values.put(index, jsonNode);
                                }
                            }

                            current = jsonNode;
                            index = null;
                        } else {
                            if (!(existing instanceof ObjectBuilder)) {
                                existing = new ObjectBuilder();
                                objectNode.values.put(token, existing);
                            }
                            current = existing;
                        }
                    } else if (current instanceof ArrayBuilder && StringUtils.isDigits(index)) {
                        ArrayBuilder arrayNode = (ArrayBuilder) current;
                        int arrayIndex = Integer.parseInt(index);
                        expandArrayToThreshold(arrayIndex, arrayNode);
                        ObjectBuilder jsonNode = getOrCreateNodeAtIndex(arrayNode, arrayIndex);

                        current = new ObjectBuilder();
                        jsonNode.values.put(token, current);
                        index = null;
                    }
                }
            }
        }
        return rootNode.build();
    }

    private ObjectBuilder getOrCreateNodeAtIndex(ArrayBuilder arrayNode, int arrayIndex) {
        ValueBuilder jsonNode = arrayNode.values.get(arrayIndex);
        if (!(jsonNode instanceof ObjectBuilder)) {
            jsonNode = new ObjectBuilder();
            arrayNode.values.set(arrayIndex, jsonNode);
        }
        return (ObjectBuilder) jsonNode;
    }

    private void expandArrayToThreshold(int arrayIndex, ArrayBuilder arrayNode) {
        if (arrayIndex < arraySizeThreshhold) {
            while (arrayNode.values.size() != arrayIndex + 1) {
                arrayNode.values.add(FixedValue.NULL);
            }
        }
    }

    private interface ValueBuilder {
        JsonNode build();
    }

    private static class FixedValue implements ValueBuilder {
        static final FixedValue NULL = new FixedValue(MicronautTreeCodec.getInstance().nullNode());

        final JsonNode value;

        FixedValue(JsonNode value) {
            this.value = value;
        }

        @Override
        public JsonNode build() {
            return value;
        }
    }

    private static class ObjectBuilder implements ValueBuilder {
        final Map<String, ValueBuilder> values = new LinkedHashMap<>();

        @Override
        public JsonNode build() {
            Map<String, JsonNode> built = new LinkedHashMap<>();
            for (Map.Entry<String, ValueBuilder> entry : values.entrySet()) {
                // todo: is it dangerous to recurse here?
                built.put(entry.getKey(), entry.getValue().build());
            }
            return MicronautTreeCodec.getInstance().createObjectNode(built);
        }
    }

    private static class ArrayBuilder implements ValueBuilder {
        final List<ValueBuilder> values = new ArrayList<>();

        @Override
        public JsonNode build() {
            return MicronautTreeCodec.getInstance().createArrayNode(values.stream().map(ValueBuilder::build).collect(Collectors.toList()));
        }
    }
}
