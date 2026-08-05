package com.nuono.next.noon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.util.StringUtils;

/** Immutable multipart file body with its generated boundary. */
final class NoonMultipartBody {

    private final String boundary;
    private final byte[] content;

    private NoonMultipartBody(String boundary, byte[] content) {
        this.boundary = boundary;
        this.content = content;
    }

    static NoonMultipartBody file(
            String fieldName,
            String fileName,
            String contentType,
            byte[] content
    ) {
        String boundary = "nuono-"
                + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 16);
        String safeField = sanitize(fieldName, "file");
        String safeFile = sanitize(fileName, "image");
        String safeType = StringUtils.hasText(contentType)
                ? contentType.trim()
                : "application/octet-stream";
        byte[] fileContent = content == null ? new byte[0] : content;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"" + safeField
                    + "\"; filename=\"" + safeFile + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + safeType + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(fileContent);
            output.write(("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            return new NoonMultipartBody(boundary, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "构造 Noon 文件上传请求失败：" + exception.getMessage(),
                    exception
            );
        }
    }

    String boundary() {
        return boundary;
    }

    byte[] content() {
        return content;
    }

    private static String sanitize(String value, String fallback) {
        String normalized = StringUtils.hasText(value) ? value.trim() : fallback;
        return normalized.replace('"', '_').replace('\r', '_').replace('\n', '_');
    }
}
