package com.nuono.next.product.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.ProductWriteAuthRecovery;
import com.nuono.next.product.ProductWriteAuthRequiredException;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.lang.reflect.Constructor;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductNoonAdapterAuthRecoveryTest {

    @Test
    void blockedProjectShouldStopBeforePersistedCookieLogin() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        when(authGate.isBlocked(307L, "PRJ-1")).thenReturn(true);
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(recoveryQueue, authGate));

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L,
                        "operator@example.com",
                        "cookie",
                        "PRJ-1",
                        "STR108065-NAE"
                )
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        verify(sessionGateway, never()).loginWithPersistedCookie(
                any(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(recoveryQueue, never()).enqueueProject(any(), anyString(), anyString());
    }

    @Test
    void freshCookieAuthFailureShouldEnterRecoveryQueueOnce() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        when(recoveryQueue.enqueueProject(307L, "PRJ-1", "STR108065-NAE"))
                .thenReturn(Optional.of(991L));
        when(sessionGateway.loginWithPersistedCookie(
                307L,
                "operator@example.com",
                "cookie",
                "PRJ-1",
                "STR108065-NAE"
        )).thenThrow(new IllegalStateException("auth_required: WHOAMI HTTP 307"));
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(recoveryQueue, authGate));

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L,
                        "operator@example.com",
                        "cookie",
                        "PRJ-1",
                        "STR108065-NAE"
                )
        );

        assertEquals(991L, exception.getRecoveryId());
        assertFalse(exception.isWriteMayHaveOccurred());
        verify(recoveryQueue).enqueueProject(307L, "PRJ-1", "STR108065-NAE");
    }

    @Test
    void rawProjectScopeFailureMentioningAuthRequiredShouldNotQueueEmailRecovery() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        when(sessionGateway.loginWithPersistedCookie(
                307L,
                "operator@example.com",
                "cookie",
                "PRJ-404",
                "STR108065-NAE"
        )).thenThrow(new IllegalStateException(
                "auth_required: account does not contain current project PRJ-404"
        ));
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(recoveryQueue, authGate));

        NoonProductException exception = assertThrows(
                NoonProductException.class,
                () -> adapter.loginWithPersistedCookie(
                        307L,
                        "operator@example.com",
                        "cookie",
                        "PRJ-404",
                        "STR108065-NAE"
                )
        );

        assertEquals(NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING, exception.getCode());
        verify(recoveryQueue, never()).enqueueProject(any(), anyString(), anyString());
    }

    @Test
    void resolvedLiveProjectShouldUseStoreMappingForCanonicalGate() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonSession session = newSession(sessionGateway, "LIVE-PRJ", "STR108065-NAE");
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("LOCAL-PRJ");
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE")).thenReturn(project);
        when(authGate.isBlocked(307L, "LOCAL-PRJ")).thenReturn(true);
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(recoveryQueue, authGate);
        recovery.setStoreSyncMapper(storeSyncMapper);
        adapter.setProductWriteAuthRecovery(recovery);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.getJson(session, "https://noon.test/readback", true)
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        verify(authGate).isBlocked(307L, "LOCAL-PRJ");
        verify(authGate, never()).isBlocked(307L, "LIVE-PRJ");
    }

    @Test
    void successfulHttpResponseBodyWithAuthRequiredShouldNotClaimCurrentWriteOccurred() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        when(recoveryQueue.enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE"))
                .thenReturn(Optional.of(991L));
        ProductNoonAdapter adapter = new ProductNoonAdapter(sessionGateway, new NoonProductGateway());
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(recoveryQueue, authGate));

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> adapter.requireNoAuthResponse(
                        307L,
                        "LOCAL-PRJ",
                        "STR108065-NAE",
                        new ObjectMapper().createObjectNode().put("error", "auth_required")
                )
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        assertEquals(991L, exception.getRecoveryId());
        verify(recoveryQueue).enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE");
    }

    @Test
    void alternateFailureEnvelopeFieldsShouldExposeAuthRequired() {
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        when(recoveryQueue.enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE"))
                .thenReturn(Optional.of(991L));
        ProductNoonAdapter adapter = new ProductNoonAdapter(
                mock(NoonSessionGateway.class),
                new NoonProductGateway()
        );
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                recoveryQueue,
                mock(NoonPullProjectAuthGate.class)
        ));
        ObjectMapper objectMapper = new ObjectMapper();

        for (String field : new String[]{"errorMessages", "errorMessage", "error_message"}) {
            assertThrows(
                    ProductWriteAuthRequiredException.class,
                    () -> adapter.requireNoAuthResponse(
                            307L,
                            "LOCAL-PRJ",
                            "STR108065-NAE",
                            objectMapper.createObjectNode().put(field, "auth_required")
                    )
            );
        }

        verify(recoveryQueue, times(3))
                .enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE");
    }

    @Test
    void normalDataContainingUnauthorizedTextShouldNotTriggerRecovery() {
        NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
        ProductNoonAdapter adapter = new ProductNoonAdapter(
                mock(NoonSessionGateway.class),
                new NoonProductGateway()
        );
        adapter.setProductWriteAuthRecovery(new ProductWriteAuthRecovery(
                recoveryQueue,
                mock(NoonPullProjectAuthGate.class)
        ));
        JsonNode response = new ObjectMapper()
                .createObjectNode()
                .set("data", new ObjectMapper().createObjectNode().put(
                        "title",
                        "Unauthorized biography"
                ));

        JsonNode actual = adapter.requireNoAuthResponse(
                307L,
                "LOCAL-PRJ",
                "STR108065-NAE",
                response
        );

        assertEquals(response, actual);
        verify(recoveryQueue, never()).enqueueProject(any(), anyString(), anyString());
    }

    private NoonSession newSession(
            NoonSessionGateway gateway,
            String projectCode,
            String storeCode
    ) {
        try {
            Class<?> stateClass =
                    Class.forName("com.nuono.next.noon.NoonSessionGateway$AuthSessionState");
            Constructor<NoonSession> constructor = NoonSession.class.getDeclaredConstructor(
                    NoonSessionGateway.class,
                    Long.class,
                    String.class,
                    String.class,
                    stateClass,
                    String.class,
                    String.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    gateway,
                    307L,
                    "operator@example.com",
                    "cookie",
                    null,
                    projectCode,
                    storeCode
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
