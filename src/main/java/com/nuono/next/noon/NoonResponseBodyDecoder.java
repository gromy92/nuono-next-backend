package com.nuono.next.noon;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/** Decodes raw or gzip-encoded Noon response bodies without lossy binary conversion. */
final class NoonResponseBodyDecoder {

    private NoonResponseBodyDecoder() {
    }

    static String text(HttpResponse<byte[]> response) throws IOException {
        byte[] body = bytes(response);
        return body.length == 0 ? "" : new String(body, StandardCharsets.UTF_8);
    }

    static byte[] bytes(HttpResponse<byte[]> response) throws IOException {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return new byte[0];
        }
        String encoding = response.headers().firstValue("content-encoding").orElse("");
        boolean gzip = encoding.toLowerCase(Locale.ROOT).contains("gzip")
                || looksLikeGzip(body);
        if (!gzip) {
            return body;
        }
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return input.readAllBytes();
        }
    }

    private static boolean looksLikeGzip(byte[] body) {
        return body.length >= 2
                && (body[0] & 0xFF) == 0x1F
                && (body[1] & 0xFF) == 0x8B;
    }
}
