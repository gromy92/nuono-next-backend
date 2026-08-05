package com.nuono.next.noon;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Parsed curl response metadata; raw Retry-After text is discarded immediately. */
public final class NoonCurlResponse {
    private static final String STATUS_MARKER = "\n__NUONO_HTTP_STATUS__:";
    private static final String RETRY_AFTER_MARKER = "\n__NUONO_RETRY_AFTER__:";
    private static final String WRITE_OUT = STATUS_MARKER + "%{http_code}"
            + RETRY_AFTER_MARKER + "%header{retry-after}\n";

    private final int exitCode;
    private final int statusCode;
    private final String body;
    private final String stderr;
    private final Duration retryAfter;

    private NoonCurlResponse(
            int exitCode,
            int statusCode,
            String body,
            String stderr,
            Duration retryAfter
    ) {
        this.exitCode = exitCode;
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
        this.stderr = stderr == null ? "" : stderr;
        this.retryAfter = retryAfter;
    }

    public static NoonCurlResponse parse(int exitCode, byte[] stdout, byte[] stderr) {
        String output = new String(stdout, StandardCharsets.UTF_8);
        String errorOutput = new String(stderr, StandardCharsets.UTF_8);
        int markerIndex = output.lastIndexOf(STATUS_MARKER);
        if (markerIndex < 0) {
            return new NoonCurlResponse(exitCode, 0, output, errorOutput, null);
        }
        String metadata = output.substring(markerIndex + STATUS_MARKER.length());
        int retryIndex = metadata.indexOf(RETRY_AFTER_MARKER);
        String status = (retryIndex < 0 ? metadata : metadata.substring(0, retryIndex)).trim();
        String retryAfter = retryIndex < 0
                ? null
                : metadata.substring(retryIndex + RETRY_AFTER_MARKER.length()).trim();
        return new NoonCurlResponse(
                exitCode,
                statusCode(status),
                output.substring(0, markerIndex),
                errorOutput,
                NoonRetryAfterParser.parse(retryAfter)
        );
    }

    private static int statusCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    public static String writeOutFormat() { return WRITE_OUT; }
    public int getExitCode() { return exitCode; }
    public int getStatusCode() { return statusCode; }
    public String getBody() { return body; }
    public String getStderr() { return stderr; }
    public Duration getRetryAfter() { return retryAfter; }
}
