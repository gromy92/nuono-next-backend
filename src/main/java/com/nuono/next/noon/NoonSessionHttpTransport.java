package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import org.springframework.util.StringUtils;

/** Owns retry, response classification, audit recording, and safe response decoding. */
final class NoonSessionHttpTransport {
    private static final NoonReadRetryPolicy READ_RETRY = new NoonReadRetryPolicy();
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final NoonRequestThrottle requestThrottle;
    private final Consumer<String> requestRecorder;
    private final NoonHttpAttemptRecorder httpCallRecorder;
    private final NoonEdgeAccessGuard edgeAccessGuard;
    private final long edgeAccessHoldSeconds;
    private final NoonCatalogAuthCookieExport authCookieExport;
    private final AuthExpiredFactory authExpiredFactory;

    NoonSessionHttpTransport(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            NoonRequestThrottle requestThrottle,
            Consumer<String> requestRecorder,
            NoonHttpAttemptRecorder httpCallRecorder,
            NoonEdgeAccessGuard edgeAccessGuard,
            long edgeAccessHoldSeconds,
            NoonCatalogAuthCookieExport authCookieExport,
            AuthExpiredFactory authExpiredFactory
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestThrottle = requestThrottle;
        this.requestRecorder = requestRecorder;
        this.httpCallRecorder = httpCallRecorder;
        this.edgeAccessGuard = edgeAccessGuard;
        this.edgeAccessHoldSeconds = edgeAccessHoldSeconds;
        this.authCookieExport = authCookieExport;
        this.authExpiredFactory = authExpiredFactory;
    }

    JsonNode json(HttpRequest request, boolean retryTransientFailures) {
        return execute(request, retryTransientFailures, true, false, responseBody -> {
            String text = NoonResponseBodyDecoder.text(responseBody);
            return StringUtils.hasText(text) ? objectMapper.readTree(text) : MissingNode.getInstance();
        });
    }

    String text(HttpRequest request, boolean retryTransientFailures) {
        return execute(
                request, retryTransientFailures, false, false,
                NoonResponseBodyDecoder::text
        );
    }

    byte[] bytes(HttpRequest request, boolean retryTransientFailures) {
        return execute(
                request, retryTransientFailures, false, true,
                NoonResponseBodyDecoder::bytes
        );
    }

    private <T> T execute(
            HttpRequest request,
            boolean retryTransientFailures,
            boolean captureAuthCookie,
            boolean binarySuccess,
            ResponseDecoder<T> decoder
    ) {
        int attempt = 0;
        while (true) {
            long startedNanos = System.nanoTime();
            try {
                if (requestRecorder != null) {
                    requestRecorder.accept(request.uri().toString());
                }
                HttpResponse<byte[]> response = NoonHardDeadlineHttpClient.send(
                        httpClient, request, HttpResponse.BodyHandlers.ofByteArray()
                );
                if (captureAuthCookie) {
                    authCookieExport.captureRequestCookieHeader(request.uri());
                }
                requestThrottle.markCompleted();
                String responseText = NoonResponseBodyDecoder.text(response);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    attempt++;
                    rejectEdgeAccess(request, response, responseText, startedNanos);
                    if (retryTransientFailures
                            && READ_RETRY.shouldRetryRateLimit(
                                    response.statusCode(), responseText, attempt
                            )) {
                        READ_RETRY.sleepForRateLimit(attempt);
                        continue;
                    }
                    if (NoonSessionResponseClassifier.isAuthExpiredResponse(
                            response.statusCode(), responseText, request.uri().getPath(),
                            response.headers().firstValue("location").orElse(null)
                    )) {
                        record(request, response.statusCode(), responseText, startedNanos,
                                "FAILED", "AUTH_EXPIRED",
                                "HTTP " + response.statusCode() + " " + shrink(responseText));
                        throw authExpiredFactory.create(
                                response.statusCode(), responseText, request.uri().getPath()
                        );
                    }
                    if (READ_RETRY.shouldRetryTransientResponse(
                            retryTransientFailures, response.statusCode(), attempt
                    )) {
                        READ_RETRY.sleepForTransientFailure(attempt);
                        continue;
                    }
                    record(request, response.statusCode(), responseText, startedNanos,
                            "FAILED", "HTTP_STATUS",
                            "HTTP " + response.statusCode() + " " + shrink(responseText));
                    throw NoonHttpFailureFactory.from(
                            response, responseText, request.uri().getPath()
                    );
                }
                String successBody = binarySuccess
                        ? "binary response bytes=" + response.body().length
                        : responseText;
                record(request, response.statusCode(), successBody, startedNanos,
                        "SUCCESS", null, null);
                return decoder.decode(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                record(request, null, null, startedNanos, "FAILED", "INTERRUPTED",
                        message(exception));
                throw new IllegalStateException("请求 Noon 失败：" + message(exception), exception);
            } catch (IOException exception) {
                attempt++;
                if (READ_RETRY.shouldRetryTransientException(retryTransientFailures, attempt)) {
                    try {
                        READ_RETRY.sleepForTransientFailure(attempt);
                        continue;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "请求 Noon 失败：" + message(interrupted), interrupted
                        );
                    }
                }
                record(request, null, null, startedNanos, "FAILED", "IO_EXCEPTION",
                        message(exception));
                throw new IllegalStateException("请求 Noon 失败：" + message(exception), exception);
            }
        }
    }

    private void rejectEdgeAccess(
            HttpRequest request,
            HttpResponse<?> response,
            String responseBody,
            long startedNanos
    ) {
        if (!NoonEdgeAccessGuard.matches(response.statusCode(), responseBody)) {
            return;
        }
        NoonEdgeAccessDeniedException failure = edgeAccessGuard.block(edgeAccessHoldSeconds);
        failure.initCause(NoonHttpFailureFactory.from(
                response, responseBody, request.uri().getPath()
        ));
        record(request, response.statusCode(), responseBody, startedNanos,
                "FAILED", "EGRESS_BLOCKED", failure.getMessage());
        throw failure;
    }

    private void record(
            HttpRequest request,
            Integer statusCode,
            String responseBody,
            long startedNanos,
            String status,
            String failureType,
            String errorMessage
    ) {
        if (httpCallRecorder == null) {
            return;
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        httpCallRecorder.record(
                request, statusCode, responseBody, elapsedMs, status, failureType, errorMessage
        );
    }

    private static String shrink(String body) {
        if (!StringUtils.hasText(body)) {
            return "empty response";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }

    private static String message(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        return StringUtils.hasText(throwable.getMessage())
                ? throwable.getMessage()
                : throwable.getClass().getSimpleName();
    }

    @FunctionalInterface
    interface AuthExpiredFactory {
        RuntimeException create(int statusCode, String responseBody, String requestPath);
    }

    @FunctionalInterface
    private interface ResponseDecoder<T> {
        T decode(HttpResponse<byte[]> response) throws IOException;
    }
}
