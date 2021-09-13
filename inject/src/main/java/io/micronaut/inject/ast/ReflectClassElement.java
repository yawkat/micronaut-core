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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ClassElement} backed by reflection.
 *
 * @author graemerocher
 * @since 2.3
 */
@Internal
class ReflectClassElement implements ClassElement {
    private final Class<?> type;

    /**
     * Default constructor.
     *
     * @param type The type
     */
    ReflectClassElement(Class<?> type) {
        this.type = type;
    }

    @Override
    public MnType getRawMnType() {
        if (type.isArray()) {
            return fromArray().getRawMnType().getArrayType();
        }
        return new MnType.RawClass() {
            @Override
            public ClassElement getClassElement() {
                return ReflectClassElement.this;
            }

            @Override
            public List<? extends Variable> getTypeVariables() {
                return Arrays.stream(type.getTypeParameters())
                        .map(tp -> new Variable() {
                            @NonNull
                            @Override
                            public Element getDeclaringElement() {
                                return ReflectClassElement.this;
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return tp.getName();
                            }

                            @NonNull
                            @Override
                            public List<? extends MnType> getBounds() {
                                return Arrays.stream(tp.getBounds()).map(bound -> {
                                    if (bound instanceof Class) {
                                        return ClassElement.of((Class<?>) bound).getRawMnType();
                                    } else {
                                        throw new UnsupportedOperationException();
                                    }
                                }).collect(Collectors.toList());
                            }
                        })
                        .collect(Collectors.toList());
            }

            @Nullable
            @Override
            public MnType getSupertype() {
                Type genericSuperclass = type.getGenericSuperclass();
                return genericSuperclass == null ? null : toMnType(genericSuperclass);
            }

            @Override
            public List<? extends MnType> getInterfaces() {
                return Arrays.stream(type.getGenericInterfaces()).map(ReflectClassElement::toMnType).collect(Collectors.toList());
            }
        };
    }

    private static MnType toMnType(Type type) {
        if (type instanceof GenericArrayType) {
            return toMnType(((GenericArrayType) type).getGenericComponentType()).getArrayType();
        } else if (type instanceof ParameterizedType) {
            return new MnType.Parameterized() {
                @Nullable
                @Override
                public MnType getOuter() {
                    Type ownerType = ((ParameterizedType) type).getOwnerType();
                    return ownerType == null ? null : toMnType(ownerType);
                }

                @NonNull
                @Override
                public RawClass getRaw() {
                    return (RawClass) toMnType(((ParameterizedType) type).getRawType());
                }

                @NonNull
                @Override
                public List<? extends MnType> getParameters() {
                    return Arrays.stream(((ParameterizedType) type).getActualTypeArguments()).map(ReflectClassElement::toMnType).collect(Collectors.toList());
                }
            };
        } else if (type instanceof TypeVariable<?>) {
            return new MnType.Variable() {
                @NonNull
                @Override
                public Element getDeclaringElement() {
                    throw new UnsupportedOperationException();
                }

                @NonNull
                @Override
                public String getName() {
                    return ((TypeVariable<?>) type).getName();
                }

                @NonNull
                @Override
                public List<? extends MnType> getBounds() {
                    return Arrays.stream(((TypeVariable<?>) type).getBounds()).map(ReflectClassElement::toMnType).collect(Collectors.toList());
                }
            };
        } else if (type instanceof WildcardType) {
            return new MnType.Wildcard() {
                @Override
                public List<? extends MnType> getUpperBounds() {
                    return Arrays.stream(((WildcardType) type).getUpperBounds()).map(ReflectClassElement::toMnType).collect(Collectors.toList());
                }

                @Override
                public List<? extends MnType> getLowerBounds() {
                    return Arrays.stream(((WildcardType) type).getLowerBounds()).map(ReflectClassElement::toMnType).collect(Collectors.toList());
                }
            };
        } else if (type instanceof Class) {
            return ClassElement.of((Class<?>) type).getRawMnType();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
    }

    @Override
    public String toString() {
        return type.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReflectClassElement that = (ReflectClassElement) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public boolean isPrimitive() {
        return type.isPrimitive();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public int getArrayDimensions() {
        return computeDimensions(type);
    }

    private int computeDimensions(Class<?> type) {
        int i = 0;
        while (type.isArray()) {
            i++;
            type = type.getComponentType();
        }
        return i;
    }

    @Override
    public boolean isAssignable(Class<?> type) {
        return type.isAssignableFrom(this.type);
    }

    @Override
    public boolean isAssignable(String type) {
        // unsupported by this impl
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        // unsupported by this impl
        return false;
    }

    @Override
    public ClassElement toArray() {
        Class<?> arrayType = Array.newInstance(type, 0).getClass();
        return ClassElement.of(arrayType);
    }

    @Override
    public ClassElement fromArray() {
        return new ReflectClassElement(type.getComponentType());
    }

    @NonNull
    @Override
    public String getName() {
        return type.getName();
    }

    @Override
    public boolean isPackagePrivate() {
        int modifiers = type.getModifiers();
        return !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
    }

    @Override
    public boolean isProtected() {
        return !isPublic();
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(type.getModifiers());
    }

    @NonNull
    @Override
    public Object getNativeType() {
        return type;
    }
}
