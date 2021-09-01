package io.micronaut.json.tree;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Internal
public abstract class JsonNode {
    JsonNode() {}

    public static JsonNode nullNode() {
        return JsonNull.INSTANCE;
    }

    public static JsonNode createArrayNode(List<JsonNode> nodes) {
        return new JsonArray(nodes);
    }

    public static JsonNode createObjectNode(Map<String, JsonNode> nodes) {
        return new JsonObject(nodes);
    }

    public static JsonNode createBooleanNode(boolean value) {
        return JsonBoolean.valueOf(value);
    }

    public static JsonNode createStringNode(@NonNull String value) {
        Objects.requireNonNull(value, "value");
        return new JsonString(value);
    }

    /**
     * Hidden, so that we don't have to check that the number type is supported
     */
    @Internal
    public static JsonNode createNumberNodeImpl(Number value) {
        Objects.requireNonNull(value, "value");
        return new JsonNumber(value);
    }

    public static JsonNode createNumberNode(int value) {
        return createNumberNodeImpl(value);
    }

    public static JsonNode createNumberNode(long value) {
        return createNumberNodeImpl(value);
    }

    public static JsonNode createNumberNode(@NonNull BigDecimal value) {
        return createNumberNodeImpl(value);
    }

    public static JsonNode createNumberNode(float value) {
        return createNumberNodeImpl(value);
    }

    public static JsonNode createNumberNode(double value) {
        return createNumberNodeImpl(value);
    }

    public static JsonNode createNumberNode(@NonNull BigInteger value) {
        return createNumberNodeImpl(value);
    }

    public boolean isNumber() {
        return false;
    }

    @NonNull
    public Number getNumberValue() {
        throw new IllegalStateException("Not a number");
    }

    public final int getIntValue() {
        return getNumberValue().intValue();
    }

    public final long getLongValue() {
        return getNumberValue().longValue();
    }

    public final float getFloatValue() {
        return getNumberValue().floatValue();
    }

    public final double getDoubleValue() {
        return getNumberValue().doubleValue();
    }

    @NonNull
    public final BigInteger getBigIntegerValue() {
        Number numberValue = getNumberValue();
        if (numberValue instanceof BigInteger) {
            return (BigInteger) numberValue;
        } else if (numberValue instanceof BigDecimal) {
            return ((BigDecimal) numberValue).toBigInteger();
        } else {
            return BigInteger.valueOf(numberValue.longValue());
        }
    }

    @NonNull
    public final BigDecimal getBigDecimalValue() {
        Number numberValue = getNumberValue();
        if (numberValue instanceof BigInteger) {
            return new BigDecimal((BigInteger) numberValue);
        } else if (numberValue instanceof BigDecimal) {
            return (BigDecimal) numberValue;
        } else if (numberValue instanceof Long) {
            return BigDecimal.valueOf(numberValue.longValue());
        } else {
            // all other types, including the int types, fit into double
            return BigDecimal.valueOf(numberValue.doubleValue());
        }
    }

    public boolean isString() {
        return false;
    }

    @NonNull
    public String getStringValue() {
        throw new IllegalStateException("Not a string");
    }

    /**
     * Attempt to coerce this node to a string.
     *
     * @throws IllegalStateException if this node is not a scalar value
     */
    @NonNull
    public String coerceStringValue() {
        throw new IllegalStateException("Not a scalar value");
    }

    public boolean isBoolean() {
        return false;
    }

    public boolean getBooleanValue() {
        throw new IllegalStateException("Not a boolean");
    }

    public boolean isNull() {
        return false;
    }

    public abstract int size();

    @NonNull
    public abstract Iterable<JsonNode> values();

    @NonNull
    public abstract Iterable<Map.Entry<String, JsonNode>> entries();

    public boolean isValueNode() {
        return false;
    }

    public boolean isContainerNode() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public boolean isObject() {
        return false;
    }

    public abstract JsonNode get(String fieldName);

    public abstract JsonNode get(int index);
}
