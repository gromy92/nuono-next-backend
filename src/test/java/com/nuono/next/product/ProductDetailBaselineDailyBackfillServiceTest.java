package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductDetailBaselineCandidateMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductDetailBaselineDailyBackfillServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldDoNothingWhenDailyBackfillIsDisabled() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        ProductDetailBaselineDailyBackfillService service = service(mapper, false);

        ProductDetailBaselineDailyBackfillService.EnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        assertFalse(result.isEnabled());
        verify(mapper, never()).listMissingMaintainedCandidates(any(), any(), any(), anyInt());
    }

    @Test
    void shouldEnqueueOneFullDetailFetchPerLogicalProductAndPreserveProductIdentity() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        ProductDetailBaselineBackfillService backfill = mock(ProductDetailBaselineBackfillService.class);
        LocalDbProductMasterService productMasterService = mock(LocalDbProductMasterService.class);
        ProductDetailBaselineCandidate first = candidate(101L, 50003L, "Z-PARENT-1", "PARTNER-1", "PSKU-1");
        ProductDetailBaselineCandidate duplicate = candidate(101L, 50003L, "Z-PARENT-1", "PARTNER-2", "PSKU-2");
        when(mapper.listMissingMaintainedCandidates(307L, "STR108065-NSA", "SA", 10))
                .thenReturn(List.of(first, duplicate));
        when(backfill.enqueue(any(), eq("daily-maintenance-audit"), any())).thenReturn("preparing");
        ProductDetailBaselineDailyBackfillService service =
                service(mapper, backfill, productMasterService, mock(OperationalTaskService.class), true);

        ProductDetailBaselineDailyBackfillService.EnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        ArgumentCaptor<ProductMasterFetchCommand> commandCaptor =
                ArgumentCaptor.forClass(ProductMasterFetchCommand.class);
        ArgumentCaptor<ProductDetailBaselineBackfillService.DetailBaselineBackfillRunner> runnerCaptor =
                ArgumentCaptor.forClass(ProductDetailBaselineBackfillService.DetailBaselineBackfillRunner.class);
        verify(backfill).enqueue(commandCaptor.capture(), eq("daily-maintenance-audit"), runnerCaptor.capture());
        ProductMasterFetchCommand command = commandCaptor.getValue();
        assertEquals(307L, command.getOwnerUserId());
        assertEquals("STR108065-NSA", command.getStoreCode());
        assertEquals("Z-PARENT-1", command.getCurrentZCode());
        assertEquals("PARTNER-1", command.getPartnerSku());
        assertEquals("PSKU-1", command.getPskuCode());
        runnerCaptor.getValue().fetch(command, "ignored");
        verify(productMasterService).fetchSnapshot(command);
        assertEquals(2, result.getCandidateCount());
        assertEquals(1, result.getEnqueuedCount());
    }

    @Test
    void shouldCancelStaleActiveTaskBeforeSchedulingTheNextDailyBatch() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        ProductDetailBaselineBackfillService backfill = mock(ProductDetailBaselineBackfillService.class);
        OperationalTaskService taskService = mock(OperationalTaskService.class);
        OperationalTask stale = new OperationalTask();
        stale.setId(9001L);
        stale.setOwnerUserId(307L);
        stale.setStoreCode("STR108065-NSA");
        stale.setStatus(OperationalTaskStatus.RUNNING);
        stale.setUpdatedAt(LocalDateTime.of(2026, 7, 22, 18, 0));
        when(taskService.listActive(ProductDetailBaselineBackfillService.TASK_TYPE, 1000))
                .thenReturn(List.of(stale));
        when(mapper.listMissingMaintainedCandidates(307L, "STR108065-NSA", "SA", 10))
                .thenReturn(List.of());
        ProductDetailBaselineDailyBackfillService service =
                service(mapper, backfill, mock(LocalDbProductMasterService.class), taskService, true);

        ProductDetailBaselineDailyBackfillService.EnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        verify(taskService).cancel(9001L, "每日详情基线巡检回收超时任务，允许后续重试。");
        assertEquals(1, result.getStaleRecoveredCount());
        assertTrue(result.isEnabled());
    }

    private ProductDetailBaselineDailyBackfillService service(
            ProductDetailBaselineCandidateMapper mapper,
            boolean enabled
    ) {
        return service(
                mapper,
                mock(ProductDetailBaselineBackfillService.class),
                mock(LocalDbProductMasterService.class),
                mock(OperationalTaskService.class),
                enabled
        );
    }

    private ProductDetailBaselineDailyBackfillService service(
            ProductDetailBaselineCandidateMapper mapper,
            ProductDetailBaselineBackfillService backfill,
            LocalDbProductMasterService productMasterService,
            OperationalTaskService taskService,
            boolean enabled
    ) {
        return new ProductDetailBaselineDailyBackfillService(
                mapper,
                backfill,
                productMasterService,
                taskService,
                enabled,
                10,
                360,
                CLOCK
        );
    }

    private ProductDetailBaselineCandidate candidate(
            Long productMasterId,
            Long logicalStoreId,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        ProductDetailBaselineCandidate candidate = new ProductDetailBaselineCandidate();
        candidate.setProductMasterId(productMasterId);
        candidate.setLogicalStoreId(logicalStoreId);
        candidate.setStoreCode("STR108065-NSA");
        candidate.setSiteCode("SA");
        candidate.setSkuParent(skuParent);
        candidate.setPartnerSku(partnerSku);
        candidate.setPskuCode(pskuCode);
        return candidate;
    }
}
