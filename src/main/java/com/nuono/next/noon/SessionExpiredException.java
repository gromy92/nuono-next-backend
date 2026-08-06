package com.nuono.next.noon;

/** Internal authenticated-session response used by refresh/recovery policy. */
class SessionExpiredException extends IllegalStateException {
    private final int statusCode;
    private final String responseBody;
    private final String requestPath;

    SessionExpiredException(int statusCode, String responseBody, String requestPath) {
        super("Noon session expired with HTTP " + statusCode
                + NoonTransientTransportFailurePolicy.safeDeterministicAuthMarker(responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestPath = requestPath;
    }

    NoonHttpException toHttpException() {
        NoonHttpException exception = new NoonHttpException(
                statusCode, responseBody, requestPath
        );
        exception.initCause(this);
        return exception;
    }
}
