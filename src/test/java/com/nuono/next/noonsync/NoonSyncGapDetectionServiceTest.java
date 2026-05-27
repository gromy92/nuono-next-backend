package com.nuono.next.noonsync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonSyncGapDetectionServiceTest {

    @Test
    void newUserWithMissingProductAndSalesFactsNeedsProductInitializationAndSalesBackfill() {
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(new NoonSyncFoundationService());

        NoonSyncReadinessView readiness = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.NEW_USER,
                NoonSyncScope.of(10002L, 245027L, "STR245027-SAU", "SA"),
                true,
                true,
                NoonProductWorkspaceState.MISSING,
                NoonSalesCoverageState.MISSING,
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2026, 5, 19)
        ));

        List<String> planKeys = readiness.getRequiredWork()
                .stream()
                .map(NoonSyncRequiredWork::getPlanKey)
                .collect(Collectors.toList());

        assertEquals(NoonSyncReadinessState.INITIALIZATION_NEEDED, readiness.getState());
        assertTrue(planKeys.containsAll(List.of("product_initialization", "sales_backfill")));
    }

    @Test
    void legacyImportedAccountWithoutNewSystemFactsIsTreatedAsInitializationNeeded() {
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(new NoonSyncFoundationService());

        NoonSyncReadinessView readiness = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.LEGACY_IMPORTED,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                true,
                true,
                NoonProductWorkspaceState.MISSING,
                NoonSalesCoverageState.MISSING,
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2026, 5, 19)
        ));

        assertEquals(NoonSyncReadinessState.INITIALIZATION_NEEDED, readiness.getState());
        assertEquals(2, readiness.getRequiredWork().size());
    }

    @Test
    void existingCompleteUserNeedsNoSyncWork() {
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(new NoonSyncFoundationService());

        NoonSyncReadinessView readiness = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.EXISTING,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                true,
                true,
                NoonProductWorkspaceState.READY,
                NoonSalesCoverageState.COMPLETE,
                null,
                null
        ));

        assertEquals(NoonSyncReadinessState.NO_SYNC_NEEDED, readiness.getState());
        assertTrue(readiness.getRequiredWork().isEmpty());
    }

    @Test
    void existingUserWithWorkspaceEmptyAndSalesGapsNeedsRecoveryAndBackfill() {
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(new NoonSyncFoundationService());

        NoonSyncReadinessView readiness = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.EXISTING,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                true,
                true,
                NoonProductWorkspaceState.WORKSPACE_EMPTY,
                NoonSalesCoverageState.GAPS,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 7)
        ));

        List<String> planKeys = readiness.getRequiredWork()
                .stream()
                .map(NoonSyncRequiredWork::getPlanKey)
                .collect(Collectors.toList());
        assertEquals(NoonSyncReadinessState.BACKFILL_NEEDED, readiness.getState());
        assertTrue(planKeys.containsAll(List.of("product_initialization", "sales_backfill")));
    }

    @Test
    void missingBindingAndMissingProviderConfigurationAreTypedBlockedStates() {
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(new NoonSyncFoundationService());

        NoonSyncReadinessView noBinding = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.EXISTING,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                false,
                true,
                NoonProductWorkspaceState.MISSING,
                NoonSalesCoverageState.MISSING,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 7)
        ));
        NoonSyncReadinessView noProvider = service.preview(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.EXISTING,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                true,
                false,
                NoonProductWorkspaceState.MISSING,
                NoonSalesCoverageState.MISSING,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 7)
        ));

        assertEquals(NoonSyncReadinessState.BLOCKED, noBinding.getState());
        assertEquals(NoonSyncBlockedReason.NOON_BINDING_MISSING, noBinding.getBlockedReason());
        assertEquals(NoonSyncReadinessState.BLOCKED, noProvider.getState());
        assertEquals(NoonSyncBlockedReason.PROVIDER_NOT_CONFIGURED, noProvider.getBlockedReason());
    }

    @Test
    void authorizedGapFillCreatesTasksThroughSharedFoundation() {
        NoonSyncFoundationService foundationService = new NoonSyncFoundationService();
        NoonSyncGapDetectionService service = new NoonSyncGapDetectionService(foundationService);

        List<NoonSyncTask> tasks = service.createRequiredTasks(new NoonSyncGapDetectionInput(
                NoonSyncAccountOrigin.LEGACY_IMPORTED,
                NoonSyncScope.of(307L, 108065L, "STR108065-NSA", "SA"),
                true,
                true,
                NoonProductWorkspaceState.MISSING,
                NoonSalesCoverageState.MISSING,
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2026, 5, 19)
        ));

        assertEquals(2, tasks.size());
        assertEquals(2, foundationService.listTasks().size());
        assertTrue(foundationService.listTasks()
                .stream()
                .map(NoonSyncTask::getStatus)
                .allMatch(NoonSyncTaskStatus.QUEUED::equals));
    }
}
