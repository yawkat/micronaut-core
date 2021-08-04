package io.micronaut.json.tree;

import com.fasterxml.jackson.core.*;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

public abstract class JsonNode implements TreeNode {
    JsonNode() {}

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

    @NonNull
    public abstract Iterator<JsonNode> valueIterator();

    @NonNull
    public abstract Iterator<Map.Entry<String, JsonNode>> entryIterator();

    @Override
    public boolean isValueNode() {
        return false;
    }

    @Override
    public boolean isContainerNode() {
        return false;
    }

    @Override
    public boolean isMissingNode() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public final JsonParser traverse() {
        return new TraversingParser(this);
    }

    @Override
    public final JsonParser traverse(ObjectCodec codec) {
        JsonParser parser = traverse();
        parser.setCodec(codec);
        return parser;
    }

    @Override
    public final JsonNode at(JsonPointer ptr) {
        JsonNode node = this;
        while (true) {
            if (ptr.matches()) {
                return node;
            }
            if (node.isObject()) {
                node = node.path(ptr.getMatchingProperty());
            } else if (node.isArray()) {
                node = node.path(ptr.getMatchingIndex());
            } else {
                return JsonMissing.INSTANCE;
            }
            ptr = ptr.tail();
        }
    }

    @Override
    public final JsonNode at(String jsonPointerExpression) throws IllegalArgumentException {
        return at(JsonPointer.compile(jsonPointerExpression));
    }

    @Override
    public abstract JsonNode get(String fieldName);

    @Override
    public abstract JsonNode get(int index);

    @Override
    public abstract JsonNode path(String fieldName);

    @Override
    public abstract JsonNode path(int index);

    abstract void emit(JsonGenerator generator) throws IOException;

    @Override
    public String toString() {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = new JsonFactory().createGenerator(writer)) {
            emit(generator);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }
}
