package com.nuono.next.productlisting;

public class ProductListingReauthenticationException extends IllegalStateException {

    public ProductListingReauthenticationException(String message) {
        super(message);
    }

    public ProductListingReauthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
