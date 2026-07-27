package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.io.EOFException;
import java.io.IOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoonProjectTransientFailureClassifierTest {

    @Test
    void classifiesOnlyTheSevenApprovedTransientErrorTypes() {
        Map<Throwable, NoonTransientErrorType> cases = new LinkedHashMap<>();
        cases.put(new EOFException("stream ended"), NoonTransientErrorType.NETWORK_EOF);
        cases.put(
                new IOException("HTTP/1.1 header parser received no bytes"),
                NoonTransientErrorType.NETWORK_EOF
        );
        cases.put(new IOException("EOF"), NoonTransientErrorType.NETWORK_EOF);
        cases.put(
                new HttpConnectTimeoutException("connect timed out"),
                NoonTransientErrorType.CONNECT_TIMEOUT
        );
        cases.put(new java.net.ConnectException("connect timeout"), NoonTransientErrorType.CONNECT_TIMEOUT);
        cases.put(new NoonHttpException(408, "", "/catalog"), NoonTransientErrorType.HTTP_408);
        cases.put(new NoonHttpException(500, "", "/catalog"), NoonTransientErrorType.HTTP_500);
        cases.put(new NoonHttpException(502, "", "/catalog"), NoonTransientErrorType.HTTP_502);
        cases.put(new NoonHttpException(503, "", "/catalog"), NoonTransientErrorType.HTTP_503);
        cases.put(new NoonHttpException(504, "", "/catalog"), NoonTransientErrorType.HTTP_504);

        cases.forEach((failure, expected) -> assertEquals(
                expected,
                NoonProjectTransientFailureClassifier.classify(failure).orElse(null)
        ));
    }

    @Test
    void rejectsAuthRiskAndOtherTransportFailures() {
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new NoonHttpException(401, "", "/catalog")
        ).isEmpty());
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new NoonHttpException(403, "", "/catalog")
        ).isEmpty());
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new NoonHttpException(429, "", "/catalog")
        ).isEmpty());
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new NoonHttpException(501, "", "/catalog")
        ).isEmpty());
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new IOException("connection reset")
        ).isEmpty());
        assertTrue(NoonProjectTransientFailureClassifier.classify(
                new HttpTimeoutException("request timed out")
        ).isEmpty());
    }
}
