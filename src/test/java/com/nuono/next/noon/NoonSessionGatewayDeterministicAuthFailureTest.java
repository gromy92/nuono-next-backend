package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.ProductWriteAuthRecovery;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NoonSessionGatewayDeterministicAuthFailureTest {

    @Test
    void cookieOnlyWriteShouldPreserveDeterministicAuthMarkersWithoutReplay()
            throws Exception {
        for (String responseBody : List.of(
                "{\"message\":\"account does not contain current project PRJ-404\"}",
                "{\"message\":\"invalid credentials\"}"
        )) {
            assertTrue(NoonSessionResponseClassifier.isAuthExpiredResponse(
                    403,
                    responseBody,
                    "/catalog",
                    null
            ));
            AtomicInteger writeAttempts = new AtomicInteger();
            Throwable propagated = propagatedCookieOnlyWriteFailure(responseBody, writeAttempts);
            NoonAuthWaitQueue recoveryQueue = mock(NoonAuthWaitQueue.class);
            ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(
                    recoveryQueue,
                    mock(NoonPullProjectAuthGate.class)
            );

            assertTrue(propagated instanceof NoonSessionGateway.NoonCookieAuthRequiredException);
            assertEquals(1, writeAttempts.get());
            assertNull(recovery.suspendIfAuthFailure(
                    307L,
                    "LIVE-PRJ",
                    "STR108065-NAE",
                    propagated,
                    false
            ));
            verify(recoveryQueue, never()).enqueue(any());
        }
    }

    private Throwable propagatedCookieOnlyWriteFailure(
            String responseBody,
            AtomicInteger writeAttempts
    ) throws Exception {
        Class<?> sessionFailureType = Class.forName(
                "com.nuono.next.noon.NoonSessionGateway$SessionExpiredException"
        );
        Constructor<?> failureConstructor =
                sessionFailureType.getDeclaredConstructor(int.class, String.class, String.class);
        failureConstructor.setAccessible(true);
        Throwable sessionFailure =
                (Throwable) failureConstructor.newInstance(403, responseBody, "/catalog");

        Class<?> stateType = Class.forName(
                "com.nuono.next.noon.NoonSessionGateway$AuthSessionState"
        );
        Class<?> refreshModeType = Class.forName(
                "com.nuono.next.noon.NoonSessionGateway$SessionRefreshMode"
        );
        Object cookieOnly = List.of(refreshModeType.getEnumConstants()).stream()
                .filter(value -> "COOKIE_ONLY".equals(String.valueOf(value)))
                .findFirst()
                .orElseThrow();
        Constructor<NoonSessionGateway.NoonSession> sessionConstructor =
                NoonSessionGateway.NoonSession.class.getDeclaredConstructor(
                        NoonSessionGateway.class,
                        Long.class,
                        String.class,
                        String.class,
                        stateType,
                        String.class,
                        String.class,
                        refreshModeType
                );
        sessionConstructor.setAccessible(true);
        NoonSessionGateway.NoonSession session = sessionConstructor.newInstance(
                mock(NoonSessionGateway.class),
                307L,
                "operator@example.com",
                "cookie",
                null,
                "LIVE-PRJ",
                "STR108065-NAE",
                cookieOnly
        );

        Class<?> sessionCallType = Class.forName(
                "com.nuono.next.noon.NoonSessionGateway$SessionCall"
        );
        Object sessionCall = Proxy.newProxyInstance(
                sessionCallType.getClassLoader(),
                new Class<?>[]{sessionCallType},
                (proxy, method, arguments) -> {
                    writeAttempts.incrementAndGet();
                    throw sessionFailure;
                }
        );
        Method executeWrite =
                NoonSessionGateway.NoonSession.class.getDeclaredMethod(
                        "executeWriteWithoutReplay",
                        sessionCallType
                );
        executeWrite.setAccessible(true);
        try {
            executeWrite.invoke(session, sessionCall);
            throw new AssertionError("cookie-only write should stop on deterministic auth failure");
        } catch (InvocationTargetException exception) {
            return exception.getCause();
        }
    }
}
