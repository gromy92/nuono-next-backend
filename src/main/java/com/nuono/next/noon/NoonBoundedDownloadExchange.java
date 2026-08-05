package com.nuono.next.noon;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Builds and executes one non-replayed bounded binary GET. */
final class NoonBoundedDownloadExchange {
    private NoonBoundedDownloadExchange() { }

    static HttpRequest request(
            URI uri,
            String projectCode,
            boolean withProject,
            Map<String, String> extraHeaders,
            NoonRequestHeaders headers,
            NoonBinaryDownloadSink sink,
            Duration timeout
    ) {
        requireNoTransportRangeOverride(extraHeaders);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout);
        if (withProject && StringUtils.hasText(projectCode)) {
            builder.setHeader("X-Project", projectCode);
        }
        headers.applyDefaults(builder, uri);
        headers.applyExtra(builder, extraHeaders);
        builder.setHeader("Accept", "text/csv,application/octet-stream,*/*");
        builder.setHeader("Accept-Encoding", "identity");
        long resumeOffset = sink.resumeByteOffset();
        if (resumeOffset > 0L) {
            builder.setHeader("Range", "bytes=" + resumeOffset + "-");
            if (StringUtils.hasText(sink.resumeEntityValidator())) {
                builder.setHeader("If-Range", sink.resumeEntityValidator().trim());
            }
        }
        return builder.build();
    }

    private static void requireNoTransportRangeOverride(Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        for (String name : headers.keySet()) {
            if (name != null && ("range".equalsIgnoreCase(name)
                    || "if-range".equalsIgnoreCase(name))) {
                throw new IllegalArgumentException("binary range headers are transport-owned");
            }
        }
    }

    static Result execute(
            HttpClient client,
            HttpRequest request,
            NoonBinaryDownloadSink sink
    ) {
        try {
            HttpResponse<NoonBinaryDownloadBody> response = NoonHardDeadlineHttpClient.send(
                    client, request, new NoonBoundedDownloadBodyHandler(sink)
            );
            NoonBinaryDownloadBody body = response.body();
            return new Result(
                    response.statusCode(),
                    response.headers().firstValue("location").orElse(null),
                    response.headers().firstValue("Retry-After").orElse(null),
                    new String(body.errorPreview(), StandardCharsets.UTF_8),
                    body.streamedBytes(),
                    body.acceptedFinalResponse()
            );
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            sink.abort(failure);
            throw new TransportFailure("INTERRUPTED", failure);
        } catch (IOException failure) {
            NoonBinaryDownloadContractException contractFailure = contractFailure(failure);
            if (contractFailure != null) {
                sink.abort(contractFailure);
                throw contractFailure;
            }
            sink.abort(failure);
            throw new TransportFailure("IO_EXCEPTION", failure);
        }
    }

    private static NoonBinaryDownloadContractException contractFailure(Throwable failure) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof NoonBinaryDownloadContractException) {
                return (NoonBinaryDownloadContractException) cursor;
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    static final class Result {
        private final int statusCode;
        private final String location;
        private final String retryAfter;
        private final String responsePreview;
        private final long streamedBytes;
        private final boolean acceptedFinalResponse;

        private Result(
                int statusCode,
                String location,
                String retryAfter,
                String responsePreview,
                long streamedBytes,
                boolean acceptedFinalResponse
        ) {
            this.statusCode = statusCode;
            this.location = location;
            this.retryAfter = retryAfter;
            this.responsePreview = responsePreview;
            this.streamedBytes = streamedBytes;
            this.acceptedFinalResponse = acceptedFinalResponse;
        }

        int statusCode() { return statusCode; }
        String location() { return location; }
        String responsePreview() { return responsePreview; }
        long streamedBytes() { return streamedBytes; }
        boolean successful() { return acceptedFinalResponse; }

        NoonHttpException httpFailure(String requestPath) {
            return new NoonHttpException(
                    statusCode,
                    responsePreview,
                    requestPath,
                    NoonRetryAfterParser.parse(retryAfter)
            );
        }
    }

    static final class TransportFailure extends IllegalStateException {
        private final String code;

        private TransportFailure(String code, Exception cause) {
            super("请求 Noon 失败：" + safeMessage(cause), cause);
            this.code = code;
        }

        String code() { return code; }

        private static String safeMessage(Throwable failure) {
            return StringUtils.hasText(failure.getMessage())
                    ? failure.getMessage() : failure.getClass().getSimpleName();
        }
    }
}
