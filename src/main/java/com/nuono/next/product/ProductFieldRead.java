package com.nuono.next.product;

import org.springframework.util.StringUtils;

public final class ProductFieldRead<T> {

    private final ProductFieldReadState state;
    private final T value;
    private final String errorMessage;

    private ProductFieldRead(ProductFieldReadState state, T value, String errorMessage) {
        this.state = state;
        this.value = value;
        this.errorMessage = errorMessage;
    }

    public static <T> ProductFieldRead<T> absent() {
        return new ProductFieldRead<>(ProductFieldReadState.ABSENT, null, null);
    }

    public static <T> ProductFieldRead<T> value(T value) {
        if (value == null) {
            return empty();
        }
        if (value instanceof String && !StringUtils.hasText((String) value)) {
            return empty();
        }
        return new ProductFieldRead<>(ProductFieldReadState.READ_VALUE, value, null);
    }

    public static <T> ProductFieldRead<T> empty() {
        return new ProductFieldRead<>(ProductFieldReadState.READ_EMPTY, null, null);
    }

    public static <T> ProductFieldRead<T> error(String errorMessage) {
        return new ProductFieldRead<>(ProductFieldReadState.READ_ERROR, null, errorMessage);
    }

    public ProductFieldReadState getState() {
        return state;
    }

    public T getValue() {
        return value;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasValue() {
        return state == ProductFieldReadState.READ_VALUE;
    }

    public boolean canUpdateProjection() {
        return state == ProductFieldReadState.READ_VALUE;
    }
}
