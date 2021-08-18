package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MnType;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import java.util.List;
import java.util.stream.Collectors;

class MnVariableImpl extends MnType.Variable {
    private final JavaVisitorContext visitorContext;
    private final TypeParameterElement tpe;

    MnVariableImpl(JavaVisitorContext visitorContext, TypeParameterElement tpe) {
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
    public List<? extends MnType> getBounds() {
        return tpe.getBounds().stream().map(tm -> AbstractJavaElement.typeMirrorToMnType(visitorContext, tm)).collect(Collectors.toList());
    }
}
