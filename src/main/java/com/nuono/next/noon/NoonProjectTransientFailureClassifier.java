package com.nuono.next.noon;

import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.io.EOFException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class NoonProjectTransientFailureClassifier {

    private NoonProjectTransientFailureClassifier() {
    }

    static Optional<NoonTransientErrorType> classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                NoonTransientErrorType httpType = classifyStatus(
                        ((NoonHttpException) current).getStatusCode()
                );
                if (httpType != null) {
                    return Optional.of(httpType);
                }
            }
            if (current instanceof HttpConnectTimeoutException) {
                return Optional.of(NoonTransientErrorType.CONNECT_TIMEOUT);
            }
            if (current instanceof EOFException) {
                return Optional.of(NoonTransientErrorType.NETWORK_EOF);
            }
            NoonTransientErrorType messageType = classifyMessage(current);
            if (messageType != null) {
                return Optional.of(messageType);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private static NoonTransientErrorType classifyStatus(int statusCode) {
        switch (statusCode) {
            case 407:
                return NoonTransientErrorType.HTTP_407;
            case 408:
                return NoonTransientErrorType.HTTP_408;
            case 500:
                return NoonTransientErrorType.HTTP_500;
            case 502:
                return NoonTransientErrorType.HTTP_502;
            case 503:
                return NoonTransientErrorType.HTTP_503;
            case 504:
                return NoonTransientErrorType.HTTP_504;
            default:
                return null;
        }
    }

    private static NoonTransientErrorType classifyMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (throwable instanceof java.io.IOException
                && (normalized.equals("eof")
                || normalized.endsWith(": eof")
                || normalized.contains("header parser received no bytes")
                || normalized.contains("eof reached")
                || normalized.contains("premature eof")
                || normalized.contains("unexpected end of file"))) {
            return NoonTransientErrorType.NETWORK_EOF;
        }
        if (throwable instanceof java.io.IOException
                && (normalized.equals("connect timeout")
                || normalized.equals("connection timeout")
                || normalized.contains("http connect timeout")
                || normalized.contains("connect timed out")
                || normalized.contains("connection timed out"))) {
            return NoonTransientErrorType.CONNECT_TIMEOUT;
        }
        return null;
    }
}
