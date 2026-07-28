package com.nuono.next.product;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class ProductImagePublishExecutionLocks {
    private static final int STRIPE_COUNT = 64;
    private final ReentrantLock[] locks = new ReentrantLock[STRIPE_COUNT];

    ProductImagePublishExecutionLocks() {
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    Lock lockFor(Long suiteId) {
        int hash = suiteId == null ? 0 : Long.hashCode(suiteId);
        return locks[Math.floorMod(hash, locks.length)];
    }
}
