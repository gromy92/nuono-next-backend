package com.nuono.next.noon;

import java.util.function.Predicate;
import java.util.function.Supplier;

final class NoonTransportRetry {
    private NoonTransportRetry() {
    }

    static <T> T once(Supplier<T> request, Predicate<IllegalStateException> retryable) {
        try {
            return request.get();
        } catch (IllegalStateException exception) {
            if (!retryable.test(exception)) {
                throw exception;
            }
            return request.get();
        }
    }
}
