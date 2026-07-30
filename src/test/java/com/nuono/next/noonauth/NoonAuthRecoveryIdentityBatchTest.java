package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryProjectBatchMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryIdentityBatchTest {
    private NoonAuthRecoveryRepository recoveryRepository;
    private NoonPullRepository pullRepository;
    private StoreSyncMapper storeSyncMapper;
    private NoonAuthRecoveryProjectBatchCatalog batchCatalog;
    private NoonAuthRecoveryProperties properties;

    @BeforeEach
    void setUp() {
        recoveryRepository = mock(NoonAuthRecoveryRepository.class);
        pullRepository = mock(NoonPullRepository.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        batchCatalog = mock(NoonAuthRecoveryProjectBatchCatalog.class);
        properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setCoalesceSeconds(15);
        properties.setProjectAllowlist("PRJ-A, PRJ-B");
        properties.setTrustedSenderDomains("noon.com");
        when(recoveryRepository.selectProjectBindingFingerprint(anyLong(), anyString()))
                .thenReturn("binding-fingerprint");
    }

    @Test
    void firstExpiredProjectStagesEveryEligibleProjectInOneIdentityRecovery() {
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-A")).thenReturn(project("PRJ-A"));
        when(batchCatalog.listEligibleProjects(Set.of("PRJ-A", "PRJ-B"))).thenReturn(List.of(
                new NoonAuthRecoveryProjectCandidate(307L, "PRJ-A", "PRJ-A"),
                new NoonAuthRecoveryProjectCandidate(308L, "PRJ-B", "PRJ-B")
        ));
        NoonAuthIdentityRecoveryRecord active = recovery(91L, NoonAuthRecoveryStatus.COALESCING);
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString())).thenReturn(active);
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ-A"))
                .thenReturn(null, requiredState(91L, 7L));
        when(recoveryRepository.selectProjectAuthStateForUpdate(308L, "PRJ-B"))
                .thenReturn(null, requiredState(91L, 3L));
        when(pullRepository.blockTaskForAuth(eq(41L), eq(91L), anyString(), any())).thenReturn(1);

        Optional<Long> recoveryId = coordinator().blockAndEnqueue(
                task(41L, 307L, "STORE-A"),
                "auth_required: cookie expired"
        );

        assertEquals(Optional.of(91L), recoveryId);
        verify(recoveryRepository).coalesceRecoveryItem(anyItem(
                91L, 307L, "PRJ-A", 41L, "ORDER", 7L
        ));
        verify(recoveryRepository).coalesceRecoveryItem(anyItem(
                91L, 308L, "PRJ-B", null, "IDENTITY_BATCH", 3L
        ));
        verify(recoveryRepository, never()).coalesceSuccessorRecovery(any());
    }

    @Test
    void unchangedManualHoldProjectIsNotPulledIntoProactiveIdentityBatch() {
        when(storeSyncMapper.selectOwnerProject(307L, "STORE-A")).thenReturn(project("PRJ-A"));
        when(batchCatalog.listEligibleProjects(Set.of("PRJ-A", "PRJ-B"))).thenReturn(List.of(
                new NoonAuthRecoveryProjectCandidate(307L, "PRJ-A", "PRJ-A"),
                new NoonAuthRecoveryProjectCandidate(308L, "PRJ-B", "PRJ-B")
        ));
        when(recoveryRepository.coalesceActiveRecovery(any())).thenReturn(91L);
        when(recoveryRepository.selectActiveRecoveryForUpdate(anyString()))
                .thenReturn(recovery(91L, NoonAuthRecoveryStatus.COALESCING));
        when(recoveryRepository.selectProjectAuthStateForUpdate(307L, "PRJ-A"))
                .thenReturn(null, requiredState(91L, 7L));
        when(recoveryRepository.selectProjectAuthState(308L, "PRJ-B"))
                .thenReturn(heldState());
        when(pullRepository.blockTaskForAuth(eq(42L), eq(91L), anyString(), any())).thenReturn(1);

        assertEquals(Optional.of(91L), coordinator().blockAndEnqueue(
                task(42L, 307L, "STORE-A"),
                "auth_required: cookie expired"
        ));

        verify(recoveryRepository, never()).upsertProjectAuthRequired(
                eq(308L), eq("PRJ-B"), anyString(), anyLong(), anyString(), anyString(),
                anyString(), eq(null), any()
        );
    }

    @Test
    void catalogKeepsOnlyAllowedCompleteProjectsAndDeduplicatesOwnerBoundary() {
        NoonAuthRecoveryProjectBatchMapper mapper =
                mock(NoonAuthRecoveryProjectBatchMapper.class);
        when(mapper.listEligibleIdentityProjects()).thenReturn(List.of(
                new NoonAuthRecoveryProjectCandidate(307L, " PRJ-A ", null),
                new NoonAuthRecoveryProjectCandidate(307L, "PRJ-A", "DUPLICATE"),
                new NoonAuthRecoveryProjectCandidate(308L, "prj-b", "STORE-B"),
                new NoonAuthRecoveryProjectCandidate(309L, "PRJ-C", "STORE-C"),
                new NoonAuthRecoveryProjectCandidate(null, "PRJ-A", "INVALID")
        ));
        MyBatisNoonAuthRecoveryProjectBatchCatalog catalog =
                new MyBatisNoonAuthRecoveryProjectBatchCatalog(mapper);

        List<NoonAuthRecoveryProjectCandidate> selected =
                catalog.listEligibleProjects(Set.of("PRJ-A", "PRJ-B"));

        assertEquals(2, selected.size());
        assertEquals(307L, selected.get(0).getOwnerUserId());
        assertEquals("PRJ-A", selected.get(0).getProjectCode());
        assertEquals("PRJ-A", selected.get(0).getStoreCode());
        assertEquals(308L, selected.get(1).getOwnerUserId());
        assertEquals("prj-b", selected.get(1).getProjectCode());
        assertTrue(selected.stream().noneMatch(candidate ->
                "PRJ-C".equals(candidate.getProjectCode())
        ));
    }

    @Test
    void projectBatchQueryExcludesDeletedUnboundAndUnauthorizedRows() throws Exception {
        Method method = NoonAuthRecoveryProjectBatchMapper.class.getDeclaredMethod(
                "listEligibleIdentityProjects"
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .toLowerCase(java.util.Locale.ROOT);

        assertTrue(sql.contains("up.is_deleted = 0"));
        assertTrue(sql.contains("up.bind_status = 1"));
        assertTrue(sql.contains("up.is_authorized = 1"));
        assertTrue(sql.contains("up.user_id as owneruserid"));
        assertTrue(sql.contains("up.project_code as projectcode"));
    }

    private NoonAuthRecoveryCoordinator coordinator() {
        return new NoonAuthRecoveryCoordinator(
                recoveryRepository,
                pullRepository,
                storeSyncMapper,
                batchCatalog,
                properties,
                "shared@example.com",
                "imap-secret",
                Clock.fixed(Instant.parse("2026-07-30T04:00:00Z"), ZoneOffset.UTC)
        );
    }

    private NoonAuthRecoveryItemRecord anyItem(
            Long recoveryId,
            Long ownerUserId,
            String projectCode,
            Long taskId,
            String sourceDomain,
            Long authVersion
    ) {
        return org.mockito.ArgumentMatchers.argThat(item ->
                recoveryId.equals(item.getRecoveryId())
                        && ownerUserId.equals(item.getOwnerUserId())
                        && projectCode.equals(item.getProjectCode())
                        && java.util.Objects.equals(taskId, item.getSourceTaskId())
                        && sourceDomain.equals(item.getSourceDomain())
                        && authVersion.equals(item.getExpectedAuthVersion())
        );
    }

    private NoonProjectAuthStateRecord requiredState(Long recoveryId, Long authVersion) {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setActiveRecoveryId(recoveryId);
        state.setAuthVersion(authVersion);
        state.setStatus(NoonProjectAuthStatus.REAUTH_REQUIRED);
        return state;
    }

    private NoonProjectAuthStateRecord heldState() {
        NoonProjectAuthStateRecord state = new NoonProjectAuthStateRecord();
        state.setStatus(NoonProjectAuthStatus.MANUAL_HOLD);
        state.setActiveRecoveryId(80L);
        state.setAuthVersion(2L);
        state.setBindingFingerprint("binding-fingerprint");
        state.setConfigFingerprint(NoonAuthIdentityKey.configFingerprint(
                "shared@example.com",
                "imap-secret",
                properties.normalizedTrustedSenderDomains()
        ));
        return state;
    }

    private NoonAuthIdentityRecoveryRecord recovery(Long id, NoonAuthRecoveryStatus status) {
        NoonAuthIdentityRecoveryRecord recovery = new NoonAuthIdentityRecoveryRecord();
        recovery.setId(id);
        recovery.setStatus(status);
        return recovery;
    }

    private StoreSyncStoreRecord project(String projectCode) {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setProjectCode(projectCode);
        return project;
    }

    private NoonPullTaskRecord task(Long id, Long ownerUserId, String storeCode) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(id);
        task.setOwnerUserId(ownerUserId);
        task.setStoreCode(storeCode);
        task.setSiteCode("AE");
        task.setDataDomain(NoonPullDataDomain.ORDER);
        task.setStatus(NoonPullTaskStatus.RUNNING);
        return task;
    }
}
