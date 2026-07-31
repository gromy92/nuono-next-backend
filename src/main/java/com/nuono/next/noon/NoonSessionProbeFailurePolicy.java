package com.nuono.next.noon;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

final class NoonSessionProbeFailurePolicy {

    private NoonSessionProbeFailurePolicy() {
    }

    static String classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                return classifyHttpStatus(((NoonHttpException) current).getStatusCode());
            }
            if (current instanceof ConnectException) {
                return "SESSION_CONNECT_REFUSED";
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                return "SESSION_TIMEOUT";
            }
            if (current instanceof EOFException) {
                return "SESSION_EOF";
            }
            if (current instanceof IOException) {
                return "SESSION_IO";
            }
            current = current.getCause();
        }
        return null;
    }

    private static String classifyHttpStatus(int statusCode) {
        if (statusCode == 407) {
            return "SESSION_HTTP_407";
        }
        if (statusCode == 408 || statusCode == 500 || statusCode == 502
                || statusCode == 503 || statusCode == 504) {
            return "SESSION_HTTP_" + statusCode;
        }
        return null;
    }
}
