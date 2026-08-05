package com.nuono.next.noon;

import java.net.http.HttpRequest;

/** Package seam for recording a sanitized Noon HTTP attempt. */
@FunctionalInterface
interface NoonHttpAttemptRecorder {
    void record(
            HttpRequest request,
            Integer responseStatusCode,
            String responseBody,
            Long elapsedMs,
            String status,
            String failureType,
            String errorMessage
    );
}
