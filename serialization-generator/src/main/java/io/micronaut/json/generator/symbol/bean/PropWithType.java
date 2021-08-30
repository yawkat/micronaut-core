package io.micronaut.json.generator.symbol.bean;

import io.micronaut.json.generator.symbol.GeneratorType;

class PropWithType {
    final BeanDefinition.Property property;
    final GeneratorType type;

    private PropWithType(BeanDefinition.Property property, GeneratorType type) {
        this.property = property;
        this.type = type;
    }

    /**
     * Determine the type of a given property.
     *
     * @param context  The type the property is part of.
     * @param property The property.
     */
    static PropWithType fromContext(GeneratorType context, BeanDefinition.Property property) {
        // todo: how does this work when supertypes also have type parameters?
        return new PropWithType(property, property.getType(context.typeParametersAsFoldFunction()));
    }
}
