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
package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.*;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MnType;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Internal
public final class PoetUtil {
    private PoetUtil() {
    }

    public static TypeName toTypeName(GeneratorType clazz) {
        return clazz.toPoetName();
    }

    public static TypeName toTypeName(ClassElement clazz) {
        TypeName className = toTypeNameRaw(clazz);
        Map<String, ClassElement> typeArguments = clazz.getTypeArguments();
        if (typeArguments.isEmpty() || clazz.isArray() || clazz.isPrimitive()) {
            return className;
        } else {
            // we assume the typeArguments Map is ordered by source declaration
            return ParameterizedTypeName.get(
                    (ClassName) className,
                    typeArguments.values().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new));
        }
    }

    static TypeName toTypeNameRaw(ClassElement clazz) {
        if (clazz.isArray()) {
            return ArrayTypeName.of(toTypeNameRaw(clazz.fromArray()));
        }
        if (clazz.isPrimitive()) {
            if (clazz.equals(PrimitiveElement.BYTE)) {
                return TypeName.BYTE;
            } else if (clazz.equals(PrimitiveElement.SHORT)) {
                return TypeName.SHORT;
            } else if (clazz.equals(PrimitiveElement.CHAR)) {
                return TypeName.CHAR;
            } else if (clazz.equals(PrimitiveElement.INT)) {
                return TypeName.INT;
            } else if (clazz.equals(PrimitiveElement.LONG)) {
                return TypeName.LONG;
            } else if (clazz.equals(PrimitiveElement.FLOAT)) {
                return TypeName.FLOAT;
            } else if (clazz.equals(PrimitiveElement.DOUBLE)) {
                return TypeName.DOUBLE;
            } else if (clazz.equals(PrimitiveElement.BOOLEAN)) {
                return TypeName.BOOLEAN;
            } else if (clazz.equals(PrimitiveElement.VOID)) {
                return TypeName.VOID;
            } else {
                throw new AssertionError("unknown primitive type " + clazz);
            }
        }
        // split for nested classes
        String[] simpleNameParts = clazz.getSimpleName().split("\\$");
        ClassName className = ClassName.get(clazz.getPackageName(), simpleNameParts[0], Arrays.copyOfRange(simpleNameParts, 1, simpleNameParts.length));
        if (clazz.getName().equals("<any>")) {
            // todo: investigate further. Seems to happen when the input source has unresolvable types
            throw new IllegalArgumentException("Type resolution error?");
        }
        return className;
    }

    static TypeName toTypeName(MnType type) {
        if (type instanceof MnType.Array) {
            return ArrayTypeName.of(toTypeName(((MnType.Array) type).getComponent()));
        } else if (type instanceof MnType.RawClass) {
            return toTypeNameRaw(((MnType.RawClass) type).getClassElement());
        } else if (type instanceof MnType.Parameterized) {
            ClassName raw = (ClassName) toTypeName(((MnType.Parameterized) type).getRaw());
            TypeName[] params = ((MnType.Parameterized) type).getParameters().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new);
            if (((MnType.Parameterized) type).getOuter() != null) {
                TypeName outer = toTypeName(((MnType.Parameterized) type).getOuter());
                if (outer instanceof ParameterizedTypeName) {
                    return ((ParameterizedTypeName) outer).nestedClass(raw.simpleName(), Arrays.asList(params));
                }
            }
            return ParameterizedTypeName.get(raw, params);
        } else if (type instanceof MnType.Variable) {
            return TypeVariableName.get(((MnType.Variable) type).getName());
        } else if (type instanceof MnType.Wildcard) {
            List<TypeName> lower = ((MnType.Wildcard) type).getLowerBounds().stream().map(PoetUtil::toTypeName).collect(Collectors.toList());
            List<TypeName> upper = ((MnType.Wildcard) type).getUpperBounds().stream().map(PoetUtil::toTypeName).collect(Collectors.toList());
            if (!lower.isEmpty()) {
                if (lower.size() != 1) {
                    throw new UnsupportedOperationException("Cannot emit lower wildcard bound with multiple types");
                }
                if (upper.size() != 1 || !upper.get(0).equals(ClassName.OBJECT)) {
                    throw new UnsupportedOperationException("Cannot emit lower and upper wildcard bound at the same time");
                }
                return WildcardTypeName.supertypeOf(lower.get(0));
            } else {
                if (upper.size() != 1) {
                    throw new UnsupportedOperationException("Cannot emit upper wildcard bound with multiple types");
                }
                return WildcardTypeName.subtypeOf(upper.get(0));
            }
        } else {
            throw new AssertionError("Exhaustive instanceof, this should not happen");
        }
    }
}
