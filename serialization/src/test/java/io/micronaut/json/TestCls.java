package io.micronaut.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
public class TestCls {
    // todo: move to groovy

    public final String foo;

    @JsonCreator
    public TestCls(@JsonProperty("foo") String foo) {
        this.foo = foo;
    }
}
