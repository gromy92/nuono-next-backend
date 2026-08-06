package com.nuono.next.noon;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.function.Consumer;

/** Executes and classifies one bounded binary response outside the legacy session monolith. */
final class NoonBoundedDownloadSessionCall {
    private NoonBoundedDownloadSessionCall() { }

    static void execute(
            HttpClient client,
            HttpRequest request,
            NoonBinaryDownloadSink sink,
            NoonRequestThrottle throttle,
            Consumer<String> requestRecorder,
            NoonHttpAttemptRecorder attemptRecorder,
            NoonEdgeAccessGuard edgeGuard,
            long edgeAccessHoldSeconds
    ) {
        long startedNanos = System.nanoTime();
        try {
            if (requestRecorder != null) {
                requestRecorder.accept(request.uri().toString());
            }
            NoonBoundedDownloadExchange.Result response =
                    NoonBoundedDownloadExchange.execute(client, request, sink);
            throttle.markCompleted();
            if (!response.successful()) {
                failResponse(
                        request, response, startedNanos, attemptRecorder,
                        edgeGuard, edgeAccessHoldSeconds
                );
            }
            record(attemptRecorder, request, response.statusCode(),
                    "streamed binary response bytes=" + response.streamedBytes(),
                    startedNanos, "SUCCESS", null, null);
        } catch (NoonBoundedDownloadExchange.TransportFailure failure) {
            record(attemptRecorder, request, null, null, startedNanos,
                    "FAILED", failure.code(), safeMessage(failure.getCause()));
            throw failure;
        } catch (RuntimeException failure) {
            sink.abort(failure);
            throw failure;
        }
    }

    private static void failResponse(
            HttpRequest request,
            NoonBoundedDownloadExchange.Result response,
            long startedNanos,
            NoonHttpAttemptRecorder recorder,
            NoonEdgeAccessGuard edgeGuard,
            long edgeAccessHoldSeconds
    ) {
        String body = response.responsePreview();
        NoonHttpException httpFailure = response.httpFailure(request.uri().getPath());
        if (NoonEdgeAccessGuard.matches(response.statusCode(), body)) {
            NoonEdgeAccessDeniedException failure = edgeGuard.block(edgeAccessHoldSeconds);
            failure.initCause(httpFailure);
            record(recorder, request, response.statusCode(), body, startedNanos,
                    "FAILED", "EGRESS_BLOCKED", failure.getMessage());
            throw failure;
        }
        if (NoonSessionResponseClassifier.isAuthExpiredResponse(
                response.statusCode(), body, request.uri().getPath(), response.location()
        )) {
            record(recorder, request, response.statusCode(), body, startedNanos,
                    "FAILED", "AUTH_EXPIRED", httpFailure.getMessage());
            throw new AuthExpired(
                    response.statusCode(), body, request.uri().getPath()
            );
        }
        record(recorder, request, response.statusCode(), body, startedNanos,
                "FAILED", "HTTP_STATUS", httpFailure.getMessage());
        throw httpFailure;
    }

    private static void record(
            NoonHttpAttemptRecorder recorder,
            HttpRequest request,
            Integer statusCode,
            String body,
            long startedNanos,
            String status,
            String failureType,
            String message
    ) {
        if (recorder != null) {
            long elapsed = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
            recorder.record(
                    request, statusCode, body, elapsed, status, failureType, message
            );
        }
    }

    private static String safeMessage(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    static final class AuthExpired extends IllegalStateException {
        private final int statusCode;
        private final String responseBody;
        private final String requestPath;

        private AuthExpired(int statusCode, String responseBody, String requestPath) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.requestPath = requestPath;
        }

        int statusCode() { return statusCode; }
        String responseBody() { return responseBody; }
        String requestPath() { return requestPath; }
    }
}
