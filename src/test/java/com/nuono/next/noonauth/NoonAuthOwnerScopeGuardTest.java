package com.nuono.next.noonauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoonAuthOwnerScopeGuardTest {
    @Test
    void driftedScopedManifestStopsBeforeTheWorkerLeaseClaim() throws Exception {
        NoonAuthRecoveryRepository repository = mock(NoonAuthRecoveryRepository.class);
        NoonAuthIdentityRecoveryRecord candidate = new NoonAuthIdentityRecoveryRecord();
        candidate.setId(744L);
        candidate.setScopeOwnerUserId(307L);
        when(repository.isOwnerScopeManifestValid(744L)).thenReturn(false);

        assertThatThrownBy(() -> NoonAuthOwnerScopeGuard.requireValid(repository, candidate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manifest drifted before lease claim");

        String worker = Files.readString(Path.of(
                "src/main/java/com/nuono/next/noonauth/NoonAuthRecoveryWorker.java"
        ));
        int guard = worker.indexOf("NoonAuthOwnerScopeGuard.requireValid(repository, candidate)");
        int claim = worker.indexOf("repository.tryClaimRecovery(");
        org.assertj.core.api.Assertions.assertThat(guard).isGreaterThan(0).isLessThan(claim);
    }

    @Test
    void ordinaryRecoveryDoesNotReadAnOwnerManifest() {
        NoonAuthRecoveryRepository repository = mock(NoonAuthRecoveryRepository.class);
        NoonAuthIdentityRecoveryRecord candidate = new NoonAuthIdentityRecoveryRecord();
        candidate.setId(741L);

        NoonAuthOwnerScopeGuard.requireValid(repository, candidate);

        verify(repository, never()).isOwnerScopeManifestValid(741L);
    }
}
