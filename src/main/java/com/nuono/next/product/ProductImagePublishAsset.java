package com.nuono.next.product;

final class ProductImagePublishAsset {
    final String sourceUrl;
    final String fileName;
    final String contentType;
    final byte[] content;
    final String sha256;

    ProductImagePublishAsset(
            String sourceUrl,
            String fileName,
            String contentType,
            byte[] content,
            String sha256
    ) {
        this.sourceUrl = sourceUrl;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
        this.sha256 = sha256;
    }
}
