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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.SourceType;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import java.util.List;
import java.util.stream.Collectors;

class SourceVariableImpl extends SourceType.Variable {
    private final JavaVisitorContext visitorContext;
    private final TypeParameterElement tpe;

    SourceVariableImpl(JavaVisitorContext visitorContext, TypeParameterElement tpe) {
        this.visitorContext = visitorContext;
        this.tpe = tpe;
    }

    @NonNull
    @Override
    public Element getDeclaringElement() {
        javax.lang.model.element.Element genericElement = tpe.getGenericElement();
        if (genericElement instanceof TypeElement) {
            return getClassElement((TypeElement) genericElement);
        } else {
            assert genericElement instanceof ExecutableElement;
            JavaClassElement enclosing = getClassElement((TypeElement) genericElement.getEnclosingElement());
            if (visitorContext.getModelUtils().resolveKind(genericElement, ElementKind.CONSTRUCTOR).isPresent()) {
                return visitorContext.getElementFactory().newConstructorElement(
                        enclosing,
                        (ExecutableElement) genericElement,
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(genericElement));
            } else {
                return visitorContext.getElementFactory().newMethodElement(
                        enclosing,
                        (ExecutableElement) genericElement,
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(genericElement));
            }
        }
    }

    @NonNull
    private JavaClassElement getClassElement(TypeElement typeElement) {
        return visitorContext.getElementFactory().newClassElement(typeElement, visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement));
    }

    @NonNull
    @Override
    public String getName() {
        return tpe.getSimpleName().toString();
    }

    @NonNull
    @Override
    public List<? extends SourceType> getBounds() {
        return tpe.getBounds().stream().map(tm -> AbstractJavaElement.typeMirrorToSourceType(visitorContext, tm)).collect(Collectors.toList());
    }
}
