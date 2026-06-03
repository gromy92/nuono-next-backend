package com.nuono.next.productanalysis;

import java.time.LocalDate;

public class ProductLifecycleTimelinePointView {

    private final LocalDate date;
    private final String lifecycleCode;
    private final String lifecycleLabel;

    public ProductLifecycleTimelinePointView(
            LocalDate date,
            String lifecycleCode,
            String lifecycleLabel
    ) {
        this.date = date;
        this.lifecycleCode = lifecycleCode;
        this.lifecycleLabel = lifecycleLabel;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLifecycleCode() {
        return lifecycleCode;
    }

    public String getLifecycleLabel() {
        return lifecycleLabel;
    }
}
