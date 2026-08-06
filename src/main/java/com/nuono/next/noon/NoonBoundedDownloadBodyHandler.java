package com.nuono.next.noon;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates final-response headers before delegating bounded body consumption. */
final class NoonBoundedDownloadBodyHandler
        implements HttpResponse.BodyHandler<NoonBinaryDownloadBody> {
    static final int ERROR_PREVIEW_BYTES = 64 * 1024;
    private static final Pattern CONTENT_RANGE = Pattern.compile(
            "bytes\\s+(\\d+)-(\\d+)/(\\d+)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STRONG_ETAG = Pattern.compile("\"[^\"]*\"");
    private final NoonBinaryDownloadSink sink;

    NoonBoundedDownloadBodyHandler(NoonBinaryDownloadSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
        if (sink.preferredChunkBytes() <= 0 || sink.maximumBytes() <= 0L
                || sink.resumeByteOffset() < 0L
                || sink.resumeByteOffset() > sink.maximumBytes()) {
            throw new IllegalArgumentException("binary download bounds must be valid");
        }
    }

    @Override
    public HttpResponse.BodySubscriber<NoonBinaryDownloadBody> apply(
            HttpResponse.ResponseInfo responseInfo
    ) {
        NoonBinaryDownloadMetadata metadata;
        try {
            metadata = responseMetadata(responseInfo);
        } catch (NoonBinaryDownloadContractException rejected) {
            return new NoonBinaryRejectingSubscriber(rejected);
        }
        if (metadata == null) {
            return new NoonBinaryPreviewSubscriber(ERROR_PREVIEW_BYTES);
        }
        return new NoonBinaryStreamingSubscriber(sink, metadata);
    }

    private NoonBinaryDownloadMetadata responseMetadata(
            HttpResponse.ResponseInfo responseInfo
    ) {
        int status = responseInfo.statusCode();
        long start = sink.resumeByteOffset();
        if ((start == 0L && status != 200) || (start > 0L && status != 206)) {
            return null;
        }
        HttpHeaders headers = responseInfo.headers();
        String encoding = headers.firstValue("content-encoding")
                .orElse("identity").trim().toLowerCase(Locale.ROOT);
        if (!encoding.isEmpty() && !"identity".equals(encoding)) {
            throw contract("NOON_BINARY_CONTENT_ENCODING_UNSUPPORTED");
        }
        if (isInterceptionContentType(headers.firstValue("content-type").orElse(null))) {
            return null;
        }
        Long responseLength = positiveLong(headers.firstValue("content-length").orElse(null));
        if (responseLength == null) {
            return null;
        }
        long totalLength = start == 0L
                ? responseLength : resumedTotalLength(headers, start, responseLength);
        if (totalLength < 0L) {
            return null;
        }
        if (totalLength > sink.maximumBytes()
                || start >= totalLength
                || responseLength != totalLength - start) {
            throw contract("NOON_BINARY_DOWNLOAD_LIMIT_EXCEEDED");
        }
        return new NoonBinaryDownloadMetadata(
                start, responseLength, totalLength, responseValidator(headers)
        );
    }

    private long resumedTotalLength(HttpHeaders headers, long start, long responseLength) {
        Matcher range = CONTENT_RANGE.matcher(
                headers.firstValue("content-range").orElse("").trim()
        );
        if (!range.matches()) {
            return -1L;
        }
        try {
            long rangeStart = Long.parseLong(range.group(1));
            long rangeEnd = Long.parseLong(range.group(2));
            long totalLength = Long.parseLong(range.group(3));
            return rangeStart == start && rangeEnd >= rangeStart
                    && rangeEnd != Long.MAX_VALUE
                    && rangeEnd + 1L == totalLength
                    && responseLength == rangeEnd - rangeStart + 1L
                    ? totalLength : -1L;
        } catch (NumberFormatException invalid) {
            return -1L;
        }
    }

    private boolean isInterceptionContentType(String raw) {
        String value = normalize(raw);
        if (value == null) {
            return false;
        }
        String type = value.toLowerCase(Locale.ROOT);
        return type.contains("text/html")
                || type.contains("application/xhtml")
                || type.contains("application/json")
                || type.contains("problem+json")
                || type.contains("text/xml")
                || type.contains("application/xml");
    }

    private String responseValidator(HttpHeaders headers) {
        String etag = normalize(headers.firstValue("etag").orElse(null));
        if (etag != null && !etag.regionMatches(true, 0, "W/", 0, 2)
                && STRONG_ETAG.matcher(etag).matches()) {
            return etag;
        }
        String lastModified = normalize(
                headers.firstValue("last-modified").orElse(null)
        );
        if (lastModified == null) {
            return null;
        }
        try {
            ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME);
            return lastModified;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private Long positiveLong(String raw) {
        try {
            long value = Long.parseLong(raw == null ? "" : raw.trim());
            return value > 0L ? value : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NoonBinaryDownloadContractException contract(String code) {
        return new NoonBinaryDownloadContractException(code);
    }
}
