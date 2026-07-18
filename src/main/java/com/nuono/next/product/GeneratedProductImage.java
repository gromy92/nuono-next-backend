package com.nuono.next.product;

final class GeneratedProductImage {
    private final byte[] content;
    private final String contentType;

    GeneratedProductImage(byte[] content, String contentType) {
        this.content = content;
        this.contentType = contentType;
    }

    byte[] content() { return content; }
    String contentType() { return contentType; }
}
