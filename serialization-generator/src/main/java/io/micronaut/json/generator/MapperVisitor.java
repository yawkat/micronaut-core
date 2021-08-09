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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.ProblemReporter;
import io.micronaut.json.generator.symbol.SerializerLinker;
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator;
import io.micronaut.json.generator.symbol.bean.DependencyGraphChecker;
import io.micronaut.json.generator.symbol.bean.InlineBeanSerializerSymbol;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Internal
public class MapperVisitor implements TypeElementVisitor<SerializableBean, SerializableBean> {
    private final List<SingletonSerializerGenerator.GenerationResult> generated = new ArrayList<>();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        SerializerLinker linker = new SerializerLinker(context);
        InlineBeanSerializerSymbol inlineBeanSerializer = linker.inlineBean;
        if (!inlineBeanSerializer.canSerializeStandalone(element)) {
            return;
        }
        DependencyGraphChecker depChecker = new DependencyGraphChecker(context, linker);
        depChecker.checkCircularDependencies(inlineBeanSerializer, element, element);
        if (depChecker.hasAnyFailures()) {
            return;
        }
        ProblemReporter problemReporter = new ProblemReporter();
        SingletonSerializerGenerator.GenerationResult generationResult = SingletonSerializerGenerator.generate(problemReporter, element, inlineBeanSerializer);

        problemReporter.reportTo(context);
        if (!problemReporter.isFailed()) {
            generated.add(generationResult);
        }
    }

    @Override
    public void finish(VisitorContext context) {
        if (!generated.isEmpty()) {
            if (context instanceof JavaVisitorContext) {
                Filer filer = ((JavaVisitorContext) context).getProcessingEnv().getFiler();
                try {
                    for (SingletonSerializerGenerator.GenerationResult generationResult : generated) {
                        generationResult.getGeneratedFile().writeTo(filer);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                boolean groovyContext;
                try {
                    groovyContext = context instanceof GroovyVisitorContext;
                } catch (NoClassDefFoundError e) {
                    groovyContext = false;
                }
                if (groovyContext) {
                    try {
                        GroovyAuxCompiler.compile(
                                (GroovyVisitorContext) context,
                                generated.stream().map(SingletonSerializerGenerator.GenerationResult::getGeneratedFile).collect(Collectors.toList())
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            generated.clear();
        }
    }

    @Override
    @NonNull
    public VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }
}
