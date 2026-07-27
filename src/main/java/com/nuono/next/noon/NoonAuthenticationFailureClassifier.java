package com.nuono.next.noon;

/** Structured authentication-failure detection for callers that must not parse provider messages. */
public final class NoonAuthenticationFailureClassifier {

    private NoonAuthenticationFailureClassifier() {
    }

    public static boolean isAuthenticationFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonSessionGateway.NoonCookieAuthRequiredException) {
                return true;
            }
            if (current instanceof NoonAuthenticationRequiredException) {
                return true;
            }
            if (current instanceof NoonHttpException
                    && isAuthenticationStatus(((NoonHttpException) current).getStatusCode())) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    /**
     * Returns true only when Noon explicitly rejected the HTTP request at its authentication
     * boundary. Unlike a timeout or connection reset, this response proves that the protected
     * operation was not accepted for processing.
     */
    public static boolean isExplicitAuthenticationRejection(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException
                    && isAuthenticationStatus(
                    ((NoonHttpException) current).getStatusCode())) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    private static boolean isAuthenticationStatus(int statusCode) {
        return statusCode == 401
                || statusCode == 403
                || statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }
}
