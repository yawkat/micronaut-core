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

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

final class ElementUtil {
    private ElementUtil() {
    }

    static <T extends Annotation> AnnotationValue<T> getAnnotation(Class<T> type, AnnotatedElement one, Collection<AnnotatedElement> additional) {
        AnnotationValue<T> value = one.getAnnotation(type);
        if (value != null) {
            return value;
        }
        for (AnnotatedElement other : additional) {
            value = other.getAnnotation(type);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
