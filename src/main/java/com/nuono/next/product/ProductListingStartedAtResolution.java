package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductListingStartedAtResolution {

    private final LocalDateTime startedAt;
    private final String source;

    public ProductListingStartedAtResolution(LocalDateTime startedAt, String source) {
        this.startedAt = startedAt;
        this.source = source;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getSource() {
        return source;
    }
}
