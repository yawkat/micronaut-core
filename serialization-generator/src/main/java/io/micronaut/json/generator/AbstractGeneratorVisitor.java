package io.micronaut.json.generator;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.generator.symbol.ProblemReporter;
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class AbstractGeneratorVisitor<ANN> implements TypeElementVisitor<ANN, ANN> {
    private final List<SingletonSerializerGenerator.GenerationResult> generated = new ArrayList<>();

    @Override
    public abstract void visitClass(ClassElement element, VisitorContext context);

    protected final void generateFromSymbol(VisitorContext context, Function<ProblemReporter, Collection<SingletonSerializerGenerator.GenerationResult>> generate) {
        ProblemReporter problemReporter = new ProblemReporter();
        Collection<SingletonSerializerGenerator.GenerationResult> generationResult = generate.apply(problemReporter);

        problemReporter.reportTo(context);
        if (!problemReporter.isFailed()) {
            generated.addAll(generationResult);
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
        // todo: pass in originating element -> then make ISOLATING
        return VisitorKind.AGGREGATING;
    }
}
