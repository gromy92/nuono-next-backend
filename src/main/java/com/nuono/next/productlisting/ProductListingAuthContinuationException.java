package com.nuono.next.productlisting;

public class ProductListingAuthContinuationException extends IllegalStateException {
    public ProductListingAuthContinuationException(String message) {
        super(message);
    }

    public ProductListingAuthContinuationException(String message, Throwable cause) {
        super(message, cause);
    }
}
