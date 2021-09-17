/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.reflect

import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class GenericTypeUtilsSpec extends Specification {

    void "test resolve generic super type"() {
        expect:
        GenericTypeUtils.resolveSuperTypeGenericArguments(Baz, Bar) == [String] as Class[]
    }

    static class Foo<T> {}

    static class Bar<T> extends Foo<T> {}

    static class Baz extends Bar<String> {}

    // =======================

    // https://github.com/micronaut-projects/micronaut-openapi/issues/238
    void "test resolveInterfaceTypeArguments"() {
        when:
        Class[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(B, Iface)

        then:
        classes.length == 1
        classes[0] == [String] as Class[]
    }

    static interface Iface<T> {}

    static abstract class A<T> implements Iface<T> {}

    static class B extends A<String> implements Iface<String> {}

    void "test findParameterization base case"() {
        expect:
        GenericTypeUtils.findParameterization(String.class, Object.class) == Object.class
        GenericTypeUtils.findParameterization(CharSequence.class, Object.class) == Object.class
        GenericTypeUtils.findParameterization(int.class, Object.class) == null
        GenericTypeUtils.findParameterization(ArrayList.class, List.class) == List.class // no unresolved type arguments are returned
    }

    void "test findParameterization simple"() {
        expect:
        GenericTypeUtils.findParameterization(Simple.class, List.class).typeName == 'java.util.List<java.lang.String>'
        GenericTypeUtils.findParameterization(Simple.class, Iterable.class).typeName == 'java.lang.Iterable<java.lang.String>'
    }

    void "test isAssignable simple"() {
        expect:
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<List<String>>() {}.type, Simple.class)
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Iterable<String>>() {}.type, Simple.class)
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Iterable<? extends String>>() {}.type, Simple.class)
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Iterable<? super String>>() {}.type, Simple.class)
        !GenericTypeUtils.isAssignableFrom(new GenericTypeToken<List<Integer>>() {}.type, Simple.class)
        !GenericTypeUtils.isAssignableFrom(new GenericTypeToken<List<? super CharSequence>>() {}.type, Simple.class)
    }

    private static abstract class GenericTypeToken<T> {
        Type type

        GenericTypeToken() {
            Type parameterization = GenericTypeUtils.findParameterization(GenericTypeUtils.parameterizeWithFreeVariables(getClass()), GenericTypeToken.class)
            assert parameterization != null
            this.type = ((ParameterizedType) parameterization).getActualTypeArguments()[0]
        }
    }

    static abstract class Simple implements List<String> {}

    void "test findParameterization wildcard"() {
        expect:
        GenericTypeUtils.findParameterization(WildcardExt.class, Iterable.class).typeName == 'java.lang.Iterable<java.util.List<? extends java.lang.String>>'
    }

    static abstract class WildcardBase<T> implements List<List<? extends T>> {}
    static abstract class WildcardExt extends WildcardBase<String> {}

    void "test findParameterization wildcard super"() {
        expect:
        GenericTypeUtils.findParameterization(WildcardSuperExt.class, Iterable.class).typeName == 'java.lang.Iterable<java.util.List<? super java.lang.String>>'
    }

    static abstract class WildcardSuperBase<T> implements List<List<? super T>> {}
    static abstract class WildcardSuperExt extends WildcardSuperBase<String> {}

    void "test findParameterization inner"() {
        expect:
        // this class is in src/test/java, because it's not valid groovy
        GenericTypeUtils.findParameterization(InnerOuter.InnerExt.class, Map.class).typeName == 'java.util.Map<java.lang.String, java.lang.Integer>'
    }

    void "test isAssignable inner"() {
        expect:
        // this class is in src/test/java, because it's not valid groovy
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Map<String, Integer>>() {}.type, InnerOuter.InnerExt.class)
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Map<String, ? extends Number>>() {}.type, InnerOuter.InnerExt.class)
        GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Map<String, ? super Integer>>() {}.type, InnerOuter.InnerExt.class)
        GenericTypeUtils.isAssignableFrom(InnerOuter.COMPATIBLE_TYPE, InnerOuter.InnerExt.class)
        !GenericTypeUtils.isAssignableFrom(InnerOuter.INCOMPATIBLE_TYPE, InnerOuter.InnerExt.class)
        !GenericTypeUtils.isAssignableFrom(new GenericTypeToken<Map<String, Long>>() {}.type, InnerOuter.InnerExt.class)
    }

    void "test findParameterization generic type var array"() {
        given:
        def genericArrayType = GenericTypeFactory.makeArrayType(HasTypeVar.getTypeParameters()[0])

        expect:
        GenericTypeUtils.findParameterization(genericArrayType, Object[]) != null
        GenericTypeUtils.findParameterization(genericArrayType, CharSequence[]) != null
        GenericTypeUtils.findParameterization(genericArrayType, String[]) != null
        GenericTypeUtils.findParameterization(genericArrayType, Serializable) != null
    }

    static class HasTypeVar<T extends String> {}

    void "test findParameterization parameterized type array"() {
        expect:
        GenericTypeUtils.findParameterization(new GenericTypeToken<List<String>[]>() {}.getType(), Iterable[]).typeName == 'java.lang.Iterable<java.lang.String>[]'
    }

    void "test findParameterization normal array"() {
        expect:
        GenericTypeUtils.findParameterization(String[], CharSequence[]) == CharSequence[]
        GenericTypeUtils.findParameterization(String[], Object[]) == Object[]
        GenericTypeUtils.findParameterization(String[], Number[]) == null
        GenericTypeUtils.findParameterization(String[], Serializable) == Serializable
    }

    void "test findParameterization from normal array to generic"() {
        expect:
        GenericTypeUtils.findParameterization(String[], Comparable[]).typeName == 'java.lang.Comparable<java.lang.String>[]'
    }
}

