package com.nuono.next.product;

/**
 * Signals that a product-domain Noon operation must stop until Project authorization is restored.
 *
 * <p>The exception is deliberately distinct from transient provider failures: callers must never
 * put an external write back onto an automatic retry queue when this exception is observed.</p>
 */
public class ProductWriteAuthRequiredException extends IllegalStateException {
    private final Long recoveryId;
    private final boolean writeMayHaveOccurred;

    ProductWriteAuthRequiredException(
            Long recoveryId,
            boolean writeMayHaveOccurred,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.recoveryId = recoveryId;
        this.writeMayHaveOccurred = writeMayHaveOccurred;
    }

    public Long getRecoveryId() {
        return recoveryId;
    }

    public boolean isWriteMayHaveOccurred() {
        return writeMayHaveOccurred;
    }

    ProductWriteAuthRequiredException withWriteMayHaveOccurred() {
        if (writeMayHaveOccurred) {
            return this;
        }
        return new ProductWriteAuthRequiredException(recoveryId, true, getMessage(), this);
    }

    public static ProductWriteAuthRequiredException find(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ProductWriteAuthRequiredException) {
                return (ProductWriteAuthRequiredException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    static void rethrowIfPresent(Throwable throwable) {
        ProductWriteAuthRequiredException authRequired = find(throwable);
        if (authRequired != null) {
            throw authRequired;
        }
    }
}
