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
package io.micronaut.json.generator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.*;
import io.micronaut.json.generator.symbol.bean.DependencyGraphChecker;
import io.micronaut.json.generator.symbol.bean.InlineBeanSerializerSymbol;

import java.util.Collections;
import java.util.Set;

@Internal
public class MapperVisitor extends AbstractGeneratorVisitor<Object> implements TypeElementVisitor<Object, Object> {
    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(SerializableBean.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        GeneratorType generatorType = GeneratorType.ofClass(element);
        SerializerLinker linker = new SerializerLinker(context);
        if (!linker.inlineBean.canSerializeStandalone(generatorType)) {
            return;
        }
        SerializerSymbol symbol;
        if (element.isEnum()) {
            // todo: maybe we want a separate annotation for this case?
            symbol = InlineEnumSerializerSymbol.INSTANCE;
        } else {
            symbol = linker.inlineBean;
            DependencyGraphChecker depChecker = new DependencyGraphChecker(context, linker);
            depChecker.checkCircularDependencies(linker.inlineBean, generatorType, element);
            if (depChecker.hasAnyFailures()) {
                return;
            }
        }
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(generatorType)
                .originatingElement(element)
                .problemReporter(problemReporter)
                .symbol(symbol)
                .generateSerializer(linker.inlineBean.supportsDirection(generatorType, true))
                .generateDeserializer(linker.inlineBean.supportsDirection(generatorType, false))
                .generateMulti());
    }
}
