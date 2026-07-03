package com.nuono.next.productlisting;

import org.springframework.util.StringUtils;

final class ProductListingImageDownload {
    final String fileName;
    final String contentType;
    final byte[] content;

    ProductListingImageDownload(String fileName, String contentType, byte[] content) {
        this.fileName = StringUtils.hasText(fileName) ? fileName : "image.jpg";
        this.contentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        this.content = content == null ? new byte[0] : content;
    }
}
