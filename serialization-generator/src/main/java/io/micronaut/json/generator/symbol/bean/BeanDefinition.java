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
package io.micronaut.json.generator.symbol.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.*;
import io.micronaut.json.generator.symbol.GeneratorType;

import java.util.*;
import java.util.function.Function;

class BeanDefinition {
    boolean ignoreUnknownProperties;

    MethodElement creator;
    List<Property> creatorProps;
    /**
     * If the creator is delegating, the property for the single parameter.
     */
    Property creatorDelegatingProperty;

    /**
     * If serialization should be delegating (@JsonValue), the property to use as the value.
     */
    Property valueProperty;

    /**
     * {@link com.fasterxml.jackson.annotation.JsonTypeName}
     */
    String subTypeName;

    Subtyping subtyping;

    List<Property> props;

    final static class Property {
        final String name;

        // exactly one of these is not null
        final FieldElement field;
        final MethodElement getter;
        final MethodElement setter;
        final ParameterElement creatorParameter;

        boolean permitRecursiveSerialization = false;
        @Nullable
        Boolean nullable = null;
        boolean unwrapped = false;

        @NonNull
        Set<String> aliases = Collections.emptySet();

        @NonNull
        JsonInclude.Include valueInclusionPolicy = JsonInclude.Include.ALWAYS;

        private Property(String name, FieldElement field, MethodElement getter, MethodElement setter, ParameterElement creatorParameter) {
            this.name = name;

            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.creatorParameter = creatorParameter;
        }

        public GeneratorType getType(Function<MnType, MnType> fold) {
            if (getter != null) {
                return GeneratorType.methodReturnType(getter, fold);
            } else if (setter != null) {
                return GeneratorType.parameterType(setter.getParameters()[0], fold);
            } else if (field != null) {
                return GeneratorType.fieldType(field, fold);
            } else if (creatorParameter != null) {
                return GeneratorType.parameterType(creatorParameter, fold);
            } else {
                throw new AssertionError();
            }
        }

        /**
         * The element corresponding to this property. Used for warning messages.
         */
        public Element getElement() {
            if (getter != null) {
                return getter;
            } else if (setter != null) {
                return setter;
            } else if (field != null) {
                return field;
            } else if (creatorParameter != null) {
                return creatorParameter;
            } else {
                throw new AssertionError();
            }
        }

        public boolean isRequired() {
            // todo: only require when required=true is set
            return creatorParameter != null;
        }

        static Property field(String name, FieldElement field) {
            Objects.requireNonNull(field, "field");
            return new Property(name, field, null, null, null);
        }

        static Property getter(String name, MethodElement getter) {
            Objects.requireNonNull(getter, "getter");
            return new Property(name, null, getter, null, null);
        }

        static Property setter(String name, MethodElement setter) {
            Objects.requireNonNull(setter, "setter");
            return new Property(name, null, null, setter, null);
        }

        static Property creatorParameter(String name, ParameterElement creatorParameter) {
            Objects.requireNonNull(creatorParameter, "creatorParameter");
            return new Property(name, null, null, null, creatorParameter);
        }
    }

    public static class Subtyping {
        Collection<GeneratorType> subTypes;
        Map<GeneratorType, Collection<String>> subTypeNames;
        /**
         * {@link com.fasterxml.jackson.annotation.JsonTypeInfo.Id#DEDUCTION}
         */
        boolean deduce;
        JsonTypeInfo.As as;
        @Nullable
        String propertyName;
        @Nullable
        GeneratorType defaultImpl;
    }
}
