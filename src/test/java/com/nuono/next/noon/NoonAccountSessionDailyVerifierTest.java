package com.nuono.next.noon;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonauth.NoonAuthRecoveryProperties;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAccountSessionDailyVerifierTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validExistingProjectSessionsBecomeActiveWithoutSendingOrValidatingOtp() throws Exception {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAuthWaitQueue authWaitQueue = mock(NoonAuthWaitQueue.class);
        NoonAccountSessionProjectTarget target = target("PRJ307", "STR307", "sid=existing");
        when(mapper.listBoundProjects()).thenReturn(List.of(target));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");
        when(gateway.whoamiWithCookie(anyString(), anyString(), anyString())).thenReturn(
                objectMapper.readTree("{\"projectCode\":\"PRJ307\"}")
        );

        NoonAccountSessionAuditResult result = verifier(mapper, gateway, authWaitQueue, allProjects())
                .verifyNow();

        org.junit.jupiter.api.Assertions.assertTrue(result.isReady());
        verify(gateway).validateCatalogSessionWithCookie(
                "sid=existing", "PRJ307", "STR307", "merchant@example.com"
        );
        verify(authWaitQueue, never()).enqueue(any());
    }

    @Test
    void invalidSessionJoinsAutomaticRecoveryQueueWithoutSendingOtpDirectly() throws Exception {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAuthWaitQueue authWaitQueue = mock(NoonAuthWaitQueue.class);
        when(mapper.listBoundProjects()).thenReturn(List.of(target("PRJ307", "STR307", "sid=expired")));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");
        when(gateway.whoamiWithCookie(anyString(), anyString(), anyString())).thenReturn(
                objectMapper.readTree("{\"projectCode\":\"PRJ307\"}")
        );
        doThrow(new IllegalStateException("expired")).when(gateway).validateCatalogSessionWithCookie(
                anyString(), anyString(), anyString(), anyString()
        );

        verifier(mapper, gateway, authWaitQueue, allProjects()).verifyNow();

        verify(authWaitQueue).enqueue(NoonAuthWaitRequest.binding(307L, "PRJ307", "STR307"));
    }

    @Test
    void rejectedProjectDoesNotPreventLaterAllowedProjectFromBeingVerified() throws Exception {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAuthWaitQueue authWaitQueue = mock(NoonAuthWaitQueue.class);
        NoonAccountSessionProjectTarget rejected = target("PRJ100", "STR100", null);
        NoonAccountSessionProjectTarget allowed = target("PRJ200", "STR200", "sid=valid");
        when(mapper.listBoundProjects()).thenReturn(List.of(rejected, allowed));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");
        when(gateway.whoamiWithCookie("sid=valid", "PRJ200", "STR200")).thenReturn(
                objectMapper.readTree("{\"projectCode\":\"PRJ200\"}")
        );

        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setProjectAllowlist("PRJ200");

        NoonAccountSessionAuditResult result = verifier(mapper, gateway, authWaitQueue, properties)
                .verifyNow();

        org.junit.jupiter.api.Assertions.assertTrue(result.isReady());
        verify(gateway).validateCatalogSessionWithCookie(
                "sid=valid", "PRJ200", "STR200", "merchant@example.com"
        );
    }

    @Test
    void completedSharedRecoveryPublishesFullProjectReadiness() {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAuthWaitQueue authWaitQueue = mock(NoonAuthWaitQueue.class);
        when(mapper.listBoundProjects()).thenReturn(List.of(
                target("PRJ100", "STR100", "sid=one"),
                target("PRJ200", "STR200", "sid=two")
        ));
        NoonAccountSessionDailyVerifier verifier = verifier(
                mapper, gateway, authWaitQueue, allProjects()
        );

        NoonAccountSessionAuditResult result = verifier.recordRecoveryCompletion(List.of(
                recoveredItem("PRJ100"),
                recoveredItem("PRJ200")
        ));

        org.junit.jupiter.api.Assertions.assertTrue(result.isReady());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getVerifiedProjects());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getUnverifiedProjects());
    }

    private static NoonAccountSessionDailyVerifier verifier(
            NoonAccountSessionMapper mapper,
            NoonSessionGateway gateway,
            NoonAuthWaitQueue authWaitQueue,
            NoonAuthRecoveryProperties properties
    ) {
        return new NoonAccountSessionDailyVerifier(mapper, gateway, authWaitQueue, properties);
    }

    private static NoonAuthRecoveryProperties allProjects() {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setAllProjectsEnabled(true);
        return properties;
    }

    private static NoonAccountSessionProjectTarget target(String projectCode, String storeCode, String cookie) {
        NoonAccountSessionProjectTarget target = new NoonAccountSessionProjectTarget();
        target.setOwnerUserId(307L);
        target.setProjectCode(projectCode);
        target.setStoreCode(storeCode);
        target.setSessionCookie(cookie);
        return target;
    }

    private static NoonAuthRecoveryItemRecord recoveredItem(String projectCode) {
        NoonAuthRecoveryItemRecord item = new NoonAuthRecoveryItemRecord();
        item.setOwnerUserId(307L);
        item.setProjectCode(projectCode);
        item.setStatus(NoonAuthRecoveryItemStatus.RECOVERED);
        return item;
    }
}
