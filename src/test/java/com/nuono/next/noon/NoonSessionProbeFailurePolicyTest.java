package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class NoonSessionProbeFailurePolicyTest {

    @Test
    void classifiesSafePreWriteProbeTransportFailures() {
        assertEquals(
                "SESSION_CONNECT_REFUSED",
                NoonSessionProbeFailurePolicy.classify(
                        new IllegalStateException("请求 Noon 失败", new ConnectException("Connection refused"))
                )
        );
        assertEquals(
                "SESSION_TIMEOUT",
                NoonSessionProbeFailurePolicy.classify(
                        new IllegalStateException(
                                "请求 Noon 失败",
                                new HttpTimeoutException("Noon request exceeded hard timeout of 30000 ms")
                        )
                )
        );
        assertEquals(
                "SESSION_EOF",
                NoonSessionProbeFailurePolicy.classify(
                        new IllegalStateException("请求 Noon 失败", new EOFException("stream ended"))
                )
        );
        assertEquals(
                "SESSION_HTTP_407",
                NoonSessionProbeFailurePolicy.classify(new NoonHttpException(407, "", "/whoami"))
        );
        assertEquals(
                "SESSION_HTTP_503",
                NoonSessionProbeFailurePolicy.classify(new NoonHttpException(503, "", "/whoami"))
        );
    }

    @Test
    void rejectsAuthenticationProjectAndProgrammingFailures() {
        assertNull(NoonSessionProbeFailurePolicy.classify(new NoonHttpException(401, "", "/whoami")));
        assertNull(NoonSessionProbeFailurePolicy.classify(new NoonHttpException(403, "", "/whoami")));
        assertNull(NoonSessionProbeFailurePolicy.classify(new NoonHttpException(400, "", "/whoami")));
        assertNull(NoonSessionProbeFailurePolicy.classify(new IllegalArgumentException("missing project")));
    }
}
