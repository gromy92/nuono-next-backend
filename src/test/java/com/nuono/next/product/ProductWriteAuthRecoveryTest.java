package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.noon.NoonProductError;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductWriteAuthRecoveryTest {

    private final NoonProjectAuthRecoveryQueue recoveryQueue = mock(NoonProjectAuthRecoveryQueue.class);
    private final NoonPullProjectAuthGate authGate = mock(NoonPullProjectAuthGate.class);
    private final ProductWriteAuthRecovery recovery = new ProductWriteAuthRecovery(recoveryQueue, authGate);

    @Test
    void blockedProjectShouldStopBeforeAnyNoonCallWithoutCreatingAnotherRecovery() {
        when(authGate.isBlocked(307L, "PRJ-1")).thenReturn(true);

        ProductWriteAuthRequiredException exception = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> recovery.requireAvailable(307L, "PRJ-1")
        );

        assertFalse(exception.isWriteMayHaveOccurred());
        assertNull(exception.getRecoveryId());
        assertTrue(exception.getMessage().contains("人工重新确认"));
        verify(recoveryQueue, never()).enqueueProject(307L, "PRJ-1", "STR108065-NAE");
    }

    @Test
    void nestedNoonAuthFailureShouldEnqueueExactProjectAndPreserveWriteProgress() {
        when(recoveryQueue.enqueueProject(307L, "PRJ-1", "STR108065-NAE"))
                .thenReturn(Optional.of(991L));
        NoonProductException authFailure = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_AUTH_REQUIRED,
                        false,
                        "Noon 授权已失效，请在店铺管理中人工重新连接。"
                ),
                new IllegalStateException("auth_required: WHOAMI HTTP 307")
        );

        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "PRJ-1",
                "STR108065-NAE",
                new IllegalStateException("publish failed", authFailure),
                true
        );

        assertNotNull(exception);
        assertEquals(991L, exception.getRecoveryId());
        assertTrue(exception.isWriteMayHaveOccurred());
        assertTrue(exception.getMessage().contains("recoveryId=991"));
        verify(recoveryQueue).enqueueProject(307L, "PRJ-1", "STR108065-NAE");
    }

    @Test
    void projectScopeMismatchShouldNotStartEmailRecovery() {
        NoonProductException scopeFailure = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING,
                        false,
                        "Noon project.list 未返回目标项目"
                ),
                new IllegalStateException("auth_required: account does not contain current project PRJ-404")
        );

        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "PRJ-404",
                "STR108065-NAE",
                scopeFailure,
                false
        );

        assertNull(exception);
        verify(recoveryQueue, never()).enqueueProject(307L, "PRJ-404", "STR108065-NAE");
    }

    @Test
    void typedCredentialFailureShouldNotBecomeEmailRecoveryBecauseItsCauseMentionsHttp401() {
        NoonProductException credentialFailure = new NoonProductException(
                new NoonProductError(
                        NoonProductErrorCode.NOON_CREDENTIAL_INVALID,
                        false,
                        "Noon 账号或密码错误"
                ),
                new IllegalStateException("HTTP 401 invalid password")
        );

        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "PRJ-1",
                "STR108065-NAE",
                credentialFailure,
                false
        );

        assertNull(exception);
        verify(recoveryQueue, never()).enqueueProject(307L, "PRJ-1", "STR108065-NAE");
    }

    @Test
    void siteStoreShouldResolveCanonicalLocalProjectBeforeGateCheck() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        StoreSyncStoreRecord initialProject = new StoreSyncStoreRecord();
        initialProject.setProjectCode("LOCAL-PRJ");
        StoreSyncStoreRecord reboundProject = new StoreSyncStoreRecord();
        reboundProject.setProjectCode("REBOUND-PRJ");
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE"))
                .thenReturn(initialProject, reboundProject);
        when(authGate.isBlocked(307L, "LOCAL-PRJ")).thenReturn(true);
        recovery.setStoreSyncMapper(storeSyncMapper);

        assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> recovery.requireAvailable(307L, "LIVE-PRJ", "STR108065-NAE")
        );

        verify(authGate).isBlocked(307L, "LOCAL-PRJ");
        verify(authGate, never()).isBlocked(307L, "LIVE-PRJ");

        when(authGate.isBlocked(307L, "REBOUND-PRJ")).thenReturn(false);
        recovery.requireAvailable(307L, "ANOTHER-LIVE-PRJ", "STR108065-NAE");
        verify(authGate).isBlocked(307L, "REBOUND-PRJ");
        verify(storeSyncMapper, times(2)).selectOwnerProject(307L, "STR108065-NAE");
    }

    @Test
    void siteStoreShouldResolveCanonicalLocalProjectBeforeEnqueue() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode("LOCAL-PRJ");
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE")).thenReturn(project);
        when(recoveryQueue.enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE"))
                .thenReturn(Optional.of(992L));
        recovery.setStoreSyncMapper(storeSyncMapper);
        recovery.requireAvailable(307L, "LIVE-PRJ", "STR108065-NAE");

        ProductWriteAuthRequiredException exception = recovery.suspendIfAuthFailure(
                307L,
                "LIVE-PRJ",
                "STR108065-NAE",
                new IllegalStateException("auth_required: WHOAMI HTTP 307"),
                false
        );

        assertNotNull(exception);
        assertEquals(992L, exception.getRecoveryId());
        verify(recoveryQueue).enqueueProject(307L, "LOCAL-PRJ", "STR108065-NAE");
        verify(recoveryQueue, never()).enqueueProject(307L, "LIVE-PRJ", "STR108065-NAE");
        verify(storeSyncMapper, times(2)).selectOwnerProject(307L, "STR108065-NAE");
    }

    @Test
    void mapperFailureShouldFailClosedWithoutCheckingLiveProject() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE"))
                .thenThrow(new IllegalStateException("database unavailable"));
        recovery.setStoreSyncMapper(storeSyncMapper);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> recovery.requireAvailable(307L, "LIVE-PRJ", "STR108065-NAE")
        );

        assertTrue(exception.getMessage().contains("本地店铺映射查询失败"));
        verify(authGate, never()).isBlocked(307L, "LIVE-PRJ");
    }
}
