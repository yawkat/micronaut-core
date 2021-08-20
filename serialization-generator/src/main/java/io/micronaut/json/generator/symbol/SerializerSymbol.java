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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.json.Serializer;

@Internal
public interface SerializerSymbol {
    boolean canSerialize(GeneratorType type);

    /**
     * @return a symbol equivalent to this one, but with the capability of dealing with recursive / circular serialization issues.
     */
    default SerializerSymbol withRecursiveSerialization() {
        return this;
    }

    default boolean supportsNullDeserialization() {
        return false;
    }

    void visitDependencies(DependencyVisitor visitor, GeneratorType type);

    /**
     * Generate code that writes the value returned by {@code readExpression} into {@link Names#ENCODER}.
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param type The type of the value being serialized.
     * @param readExpression The expression that reads the value. Must only be evaluated once.
     * @return The code block containing statements that perform the serialization.
     */
    CodeBlock serialize(GeneratorContext generatorContext, GeneratorType type, CodeBlock readExpression);

    /**
     * Generate code that reads a value from {@link Names#DECODER}.
     * <p>
     * Decoder should be positioned at the first token of the value (as specified by
     * {@link Serializer#deserialize})
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param type The type of the value being deserialized.
     * @param setter The setter to use to build the final return value.
     * @return The code that performs the deserialization.
     */
    CodeBlock deserialize(GeneratorContext generatorContext, GeneratorType type, Setter setter);

    /**
     * Get an expression giving a default value for this type. Used when deserializing a bean and a property is missing.
     */
    default CodeBlock getDefaultExpression(GeneratorType type) {
        return CodeBlock.of("null");
    }

    @FunctionalInterface
    interface Setter {
        /**
         * Create a statement that assigns the given expression using this setter. The given expression must only be evaluated once.
         */
        CodeBlock createSetStatement(CodeBlock expression);
    }

    interface DependencyVisitor {
        /**
         * @return Whether to visit the elements of this structure
         */
        boolean visitStructure();

        void visitStructureElement(SerializerSymbol dependencySymbol, GeneratorType dependencyType, @Nullable Element element);

        void visitInjected(GeneratorType dependencyType, boolean provider);
    }
}
