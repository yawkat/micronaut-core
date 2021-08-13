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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.NameAllocator;
import com.squareup.javapoet.TypeName;
import io.micronaut.core.annotation.Internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Internal
public final class GeneratorContext {
    private final ProblemReporter problemReporter;

    /**
     * A readable path to this context, used for better error messages.
     */
    private final String readablePath;

    private final NameAllocator fields;
    private final NameAllocator localVariables;

    private final Map<Injectable, Injected> injected;

    private GeneratorContext(
            ProblemReporter problemReporter, String readablePath,
            NameAllocator fields,
            NameAllocator localVariables,
            Map<Injectable, Injected> injected) {
        this.problemReporter = problemReporter;
        this.readablePath = readablePath;
        this.fields = fields;
        this.localVariables = localVariables;
        this.injected = injected;
    }

    static GeneratorContext create(ProblemReporter problemReporter, String rootReadablePath) {
        return new GeneratorContext(problemReporter, rootReadablePath, new NameAllocator(), null, new HashMap<>());
    }

    public String getReadablePath() {
        return readablePath;
    }

    public GeneratorContext withSubPath(String element) {
        // the other variables are mutable, so we can just reuse them
        return new GeneratorContext(problemReporter, readablePath + "->" + element, fields, localVariables, injected);
    }

    public GeneratorContext newMethodContext(String... usedLocals) {
        if (this.localVariables != null) {
            throw new IllegalStateException("Nesting of local variable scopes not supported");
        }
        NameAllocator localVariables = new NameAllocator();
        for (String usedLocal : usedLocals) {
            String actual = localVariables.newName(usedLocal);
            // usually, newName will return the same name, unless there's a collision or something invalid.
            if (!actual.equals(usedLocal)) {
                throw new IllegalArgumentException("Duplicate or illegal local variable name: " + usedLocal);
            }
        }
        return new GeneratorContext(problemReporter, readablePath, fields, localVariables, injected);
    }

    /**
     * Create a new unique variable, with a name similar to the given {@code nameHint}.
     *
     * @param nameHint a readable name for this variable. Not necessarily a valid java identifier, or unique
     * @return The unique generated variable name
     */
    public String newLocalVariable(String nameHint) {
        return localVariables.newName(nameHint);
    }

    public Injected requestInjection(Injectable injectable) {
        return injected.computeIfAbsent(injectable, t -> {
            String fieldName = fields.newName(t.fieldType.toString());
            return new Injected(fieldName);
        });
    }

    Map<Injectable, Injected> getInjected() {
        return injected;
    }

    public ProblemReporter getProblemReporter() {
        return problemReporter;
    }

    public static final class Injected {
        final String fieldName;

        private final CodeBlock accessExpression;

        private Injected(String fieldName) {
            this.fieldName = fieldName;
            this.accessExpression = CodeBlock.of("this.$N", fieldName);
        }

        public CodeBlock getAccessExpression() {
            return accessExpression;
        }
    }

    /**
     * An value that can be injected.
     *
     * Subclasses must implement equals/hashCode to avoid injecting duplicates.
     */
    public static abstract class Injectable {
        final TypeName fieldType;
        final TypeName parameterType;

        /**
         * @param fieldType     The type to use for the field to store the injected value in.
         * @param parameterType The actual parameter type requested in the {@code @Inject} constructor.
         */
        public Injectable(TypeName fieldType, TypeName parameterType) {
            this.fieldType = fieldType;
            this.parameterType = parameterType;
        }

        /**
         * Build the statement initializing the field from the parameter injection.
         *
         * @param parameterExpression The expression giving the injected parameter. Of type {@link #parameterType}.
         * @param fieldSetter         The setter to write the field. Of type {@link #fieldType}.
         */
        protected abstract CodeBlock buildInitializationStatement(
                CodeBlock parameterExpression,
                SerializerSymbol.Setter fieldSetter
        );

        @Override
        public boolean equals(Object o) {
            return o instanceof Injectable &&
                    this.fieldType.equals(((Injectable) o).fieldType) &&
                    this.parameterType.equals(((Injectable) o).parameterType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldType, parameterType);
        }
    }
}
