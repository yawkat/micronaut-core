package io.micronaut.json;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public interface Decoder {
    @NonNull
    Decoder decodeArray() throws IOException;

    boolean hasNextArrayValue() throws IOException;

    @NonNull
    Decoder decodeObject() throws IOException;

    @Nullable
    String decodeKey() throws IOException;

    @NonNull
    String decodeString() throws IOException;

    boolean decodeBoolean() throws IOException;

    byte decodeByte() throws IOException;

    short decodeShort() throws IOException;

    char decodeChar() throws IOException;

    int decodeInt() throws IOException;

    long decodeLong() throws IOException;

    float decodeFloat() throws IOException;

    double decodeDouble() throws IOException;

    @NonNull
    BigInteger decodeBigInteger() throws IOException;

    @NonNull
    BigDecimal decodeBigDecimal() throws IOException;

    /**
     * Attempt to decode a null value. Returns {@code false} if this value is not null, and another method should be
     * used for decoding. Returns {@code true} if this value was null, and the cursor has been advanced to the next
     * value.
     */
    boolean decodeNull() throws IOException;

    @Nullable
    Object decodeArbitrary() throws IOException;

    void skipValue() throws IOException;

    void finishStructure() throws IOException;
}
