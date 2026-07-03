package com.nuono.next.productlisting;

public class ProductListingRealRunSubmittedEvent {

    private final Long taskId;

    public ProductListingRealRunSubmittedEvent(Long taskId) {
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
