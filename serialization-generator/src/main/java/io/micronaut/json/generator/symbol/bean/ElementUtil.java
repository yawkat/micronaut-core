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

    static boolean equals(ClassElement a, ClassElement b) {
        return equals(a, b, 0);
    }

    private static boolean equals(ClassElement a, ClassElement b, int depth) {
        // todo: mn3 .equals

        // todo: hack: some ClassElements will recursively return the same types, e.g. class X<T extends X>.
        //  there does not seem to be a way to handle this case properly with current API – we can't check the "path"
        //  to the current call, because that would require an equals impl, which we are implementing here :)
        if (depth > 10) {
            return true;
        }

        if (!a.getName().equals(b.getName())) {
            return false;
        }
        Map<String, ClassElement> aArgs = a.getTypeArguments();
        Map<String, ClassElement> bArgs = b.getTypeArguments();
        if (!aArgs.keySet().equals(bArgs.keySet())) {
            return false;
        }
        for (String argument : aArgs.keySet()) {
            if (!equals(aArgs.get(argument), bArgs.get(argument), depth + 1)) {
                return false;
            }
        }
        return true;
    }

    static boolean equals(MethodElement a, MethodElement b) {
        if (!equals(a.getDeclaringType(), b.getDeclaringType())) {
            return false;
        }
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        if (!equals(a.getGenericReturnType(), b.getGenericReturnType())) {
            return false;
        }
        ParameterElement[] paramsA = a.getParameters();
        ParameterElement[] paramsB = b.getParameters();
        if (paramsA.length != paramsB.length) {
            return false;
        }
        for (int i = 0; i < paramsA.length; i++) {
            if (!equals(paramsA[i].getGenericType(), paramsB[i].getGenericType())) {
                return false;
            }
        }
        return true;
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
