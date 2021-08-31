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
package io.micronaut.json.convert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.*;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.MicronautObjectCodec;
import io.micronaut.json.tree.JsonArray;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.*;

@Singleton
public final class JsonConverterRegistrar implements TypeConverterRegistrar {
    private final BeanProvider<MicronautObjectCodec> objectCodec;
    private final ConversionService<?> conversionService;
    private final BeanProvider<BeanPropertyBinder> beanPropertyBinder;

    @Inject
    public JsonConverterRegistrar(
            BeanProvider<MicronautObjectCodec> objectCodec,
            ConversionService<?> conversionService,
            BeanProvider<BeanPropertyBinder> beanPropertyBinder
    ) {
        this.objectCodec = objectCodec;
        this.conversionService = conversionService;
        this.beanPropertyBinder = beanPropertyBinder;
    }

    @Override
    public void register(ConversionService<?> conversionService) {
        conversionService.addConverter(
                JsonArray.class,
                Object[].class,
                arrayNodeToObjectConverter()
        );
        conversionService.addConverter(
                JsonArray.class,
                Iterable.class,
                arrayNodeToIterableConverter()
        );
        conversionService.addConverter(
                TreeNode.class,
                ConvertibleValues.class,
                objectNodeToConvertibleValuesConverter()
        );
        conversionService.addConverter(
                TreeNode.class,
                Object.class,
                jsonNodeToObjectConverter()
        );
        conversionService.addConverter(
                Map.class,
                Object.class,
                mapToObjectConverter()
        );
        conversionService.addConverter(
                Object.class,
                TreeNode.class,
                objectToJsonNodeConverter()
        );
    }

    /**
     * @return A converter that converts object nodes to convertible values
     */
    @Internal
    public TypeConverter<TreeNode, ConvertibleValues> objectNodeToConvertibleValuesConverter() {
        return (object, targetType, context) -> Optional.of(new ObjectNodeConvertibleValues<>(object, conversionService));
    }

    /**
     * @param <ARR> Type of {@link TreeNode} that is accepted
     * @return Converts array nodes to iterables.
     */
    public <ARR extends TreeNode> TypeConverter<ARR, Iterable> arrayNodeToIterableConverter() {
        return (node, targetType, context) -> {
            Map<String, Argument<?>> typeVariables = context.getTypeVariables();
            Class elementType = typeVariables.isEmpty() ? Map.class : typeVariables.values().iterator().next().getType();
            List results = new ArrayList();
            for (int i = 0; i < node.size(); i++) {
                Optional converted = conversionService.convert(node.get(i), elementType, context);
                if (converted.isPresent()) {
                    results.add(converted.get());
                }
            }
            return Optional.of(results);
        };
    }

    /**
     * @param <ARR> Type of {@link TreeNode} that is accepted
     * @return Converts array nodes to objects.
     */
    @Internal
    public <ARR extends TreeNode> TypeConverter<ARR, Object[]> arrayNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                MicronautObjectCodec om = this.objectCodec.get();
                Object[] result = om.readValue(node.traverse(om.getObjectCodec()), targetType);
                return Optional.of(result);
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The map to object converter
     */
    protected TypeConverter<Map, Object> mapToObjectConverter() {
        return (map, targetType, context) -> {
            ArgumentConversionContext<Object> conversionContext;
            if (context instanceof ArgumentConversionContext) {
                conversionContext = (ArgumentConversionContext<Object>) context;
            } else {
                conversionContext = ConversionContext.of(targetType);
            }
            ArgumentBinder binder = this.beanPropertyBinder.get();
            ArgumentBinder.BindingResult result = binder.bind(conversionContext, correctKeys(map));
            return result.getValue();
        };
    }

    private Map correctKeys(Map<?, ?> map) {
        Map mapWithExtraProps = new LinkedHashMap(map.size());
        for (Map.Entry entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = correctKeys(entry.getValue());
            mapWithExtraProps.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), value);
        }
        return mapWithExtraProps;
    }

    private List correctKeys(List list) {
        List newList = new ArrayList(list.size());
        for (Object o : list) {
            newList.add(correctKeys(o));
        }
        return newList;
    }

    private Object correctKeys(Object o) {
        if (o instanceof List) {
            return correctKeys((List) o);
        } else if (o instanceof Map) {
            return correctKeys((Map) o);
        }
        return o;
    }

    /**
     * @return A converter that converts an object to a json node
     */
    protected TypeConverter<Object, TreeNode> objectToJsonNodeConverter() {
        return (object, targetType, context) -> {
            try {
                return Optional.of(objectCodec.get().valueToTree(object));
            } catch (IllegalArgumentException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }

    /**
     * @return The JSON node to object converter
     */
    protected TypeConverter<TreeNode, Object> jsonNodeToObjectConverter() {
        return (node, targetType, context) -> {
            try {
                if (CharSequence.class.isAssignableFrom(targetType) && node.isObject()) {
                    return Optional.of(node.toString());
                } else {
                    Argument<?> argument = null;
                    // todo: the old converter checks node instanceof ContainerNode here, but this breaks tests.
                    if (context instanceof ArgumentConversionContext && targetType.getTypeParameters().length != 0) {
                        argument = ((ArgumentConversionContext<?>) context).getArgument();
                    }
                    MicronautObjectCodec om = this.objectCodec.get();
                    JsonParser parser = node.traverse(om.getObjectCodec());
                    Object result;
                    if (argument != null) {
                        result = om.readValue(parser, argument);
                    } else {
                        result = om.readValue(parser, targetType);
                    }
                    return Optional.ofNullable(result);
                }
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        };
    }
}
