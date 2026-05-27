package com.nuono.next.product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductPublishValidationResult {

    private final List<String> errors;

    public ProductPublishValidationResult(List<String> errors) {
        this.errors = errors == null ? List.of() : new ArrayList<>(errors);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
