package com.nuono.next.product;

public final class ProductActiveStateReconciliationEnqueueResult {
    private final int unknownCount;
    private final int queuedCount;
    private final boolean held;

    ProductActiveStateReconciliationEnqueueResult(int unknownCount, int queuedCount, boolean held) {
        this.unknownCount = Math.max(0, unknownCount);
        this.queuedCount = Math.max(0, queuedCount);
        this.held = held;
    }

    public int getUnknownCount() {
        return unknownCount;
    }

    public int getQueuedCount() {
        return queuedCount;
    }

    public boolean isHeld() {
        return held;
    }
}
