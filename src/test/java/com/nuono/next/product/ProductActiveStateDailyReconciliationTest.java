package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductDetailBaselineCandidateMapper;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ProductActiveStateDailyReconciliationTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void unknownCandidateUsesTrustedMerchantPricingReason() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        ProductDetailBaselineBackfillService backfill = mock(ProductDetailBaselineBackfillService.class);
        ProductDetailBaselineCandidate candidate = candidate(101L, "Z-PARENT-1");
        when(mapper.listMissingMaintainedCandidates(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(candidate));
        when(backfill.enqueueInline(any(), eq(ProductActiveStateBackfillHandler.REASON), any()))
                .thenReturn("preparing");
        ProductDetailBaselineDailyBackfillService service =
                service(mapper, backfill, mock(LocalDbProductMasterService.class), NoonRiskBackoffGuard.disabled());

        ProductDetailBaselineEnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        assertEquals(1, result.getEnqueuedCount());
        verify(backfill).enqueueInline(any(), eq(ProductActiveStateBackfillHandler.REASON), any());
    }

    @Test
    void activeRiskHoldExposesCandidatesWithoutScheduling() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        when(mapper.listMissingMaintainedCandidates(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(candidate(101L, "Z-PARENT-1")));
        NoonRiskBackoffGuard guard = mock(NoonRiskBackoffGuard.class);
        when(guard.currentHold(any())).thenReturn(Optional.of(new NoonRiskBackoffHold()));
        ProductDetailBaselineDailyBackfillService.BatchSubmitter submitter =
                mock(ProductDetailBaselineDailyBackfillService.BatchSubmitter.class);
        ProductDetailBaselineDailyBackfillService service = service(
                mapper,
                mock(ProductDetailBaselineBackfillService.class),
                mock(LocalDbProductMasterService.class),
                mock(OperationalTaskService.class),
                submitter,
                guard
        );

        ProductDetailBaselineEnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        assertEquals(1, result.getCandidateCount());
        assertEquals(0, result.getEnqueuedCount());
        verify(submitter, never()).submit(any(), any());
    }

    @Test
    void rateLimitStopsRemainingStoreSiteBatch() {
        ProductDetailBaselineCandidateMapper mapper = mock(ProductDetailBaselineCandidateMapper.class);
        ProductDetailBaselineBackfillService backfill = mock(ProductDetailBaselineBackfillService.class);
        LocalDbProductMasterService productMaster = mock(LocalDbProductMasterService.class);
        when(mapper.listMissingMaintainedCandidates(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(candidate(101L, "Z-PARENT-1"), candidate(102L, "Z-PARENT-2")));
        ProductMasterSnapshotView rateLimited = new ProductMasterSnapshotView();
        rateLimited.getWarnings().add("读取价格信息失败: HTTP 429 too many requests");
        when(productMaster.fetchSnapshot(any())).thenReturn(rateLimited);
        when(backfill.enqueueInline(any(), any(), any())).thenAnswer(invocation -> {
            ProductMasterFetchCommand command = invocation.getArgument(0);
            ProductDetailBaselineBackfillService.DetailBaselineBackfillRunner runner = invocation.getArgument(2);
            runner.fetch(command, "ignored");
            return "preparing";
        });
        AtomicBoolean held = new AtomicBoolean();
        NoonRiskBackoffGuard guard = mock(NoonRiskBackoffGuard.class);
        when(guard.currentHold(any())).thenAnswer(
                ignored -> held.get() ? Optional.of(new NoonRiskBackoffHold()) : Optional.empty());
        when(guard.recordRiskSignal(any(), any(), any(), any(), any(), any())).thenAnswer(ignored -> {
            held.set(true);
            return new NoonRiskBackoffHold();
        });
        ProductDetailBaselineDailyBackfillService service =
                service(mapper, backfill, productMaster, guard);

        ProductDetailBaselineEnqueueResult result =
                service.enqueueMissingAfterDailyList(307L, "STR108065-NSA", "SA");

        assertEquals(2, result.getEnqueuedCount());
        verify(backfill, times(1)).enqueueInline(any(), any(), any());
        verify(productMaster, times(1)).fetchSnapshot(any());
        verify(guard).recordRiskSignal(any(), eq("rate_limited"), eq("PRODUCT"), any(), any(), any());
    }

    private ProductDetailBaselineDailyBackfillService service(
            ProductDetailBaselineCandidateMapper mapper,
            ProductDetailBaselineBackfillService backfill,
            LocalDbProductMasterService productMaster,
            NoonRiskBackoffGuard guard
    ) {
        return service(
                mapper, backfill, productMaster, mock(OperationalTaskService.class),
                (accountKey, task) -> task.run(), guard);
    }

    private ProductDetailBaselineDailyBackfillService service(
            ProductDetailBaselineCandidateMapper mapper,
            ProductDetailBaselineBackfillService backfill,
            LocalDbProductMasterService productMaster,
            OperationalTaskService tasks,
            ProductDetailBaselineDailyBackfillService.BatchSubmitter submitter,
            NoonRiskBackoffGuard guard
    ) {
        return new ProductDetailBaselineDailyBackfillService(
                mapper, backfill, productMaster, tasks, submitter, true, 360, 10, CLOCK,
                guard, new NoonPullFailurePolicy(CLOCK));
    }

    private ProductDetailBaselineCandidate candidate(long id, String skuParent) {
        ProductDetailBaselineCandidate candidate = new ProductDetailBaselineCandidate();
        candidate.setProductMasterId(id);
        candidate.setLogicalStoreId(50003L);
        candidate.setStoreCode("STR108065-NSA");
        candidate.setSiteCode("SA");
        candidate.setSkuParent(skuParent);
        candidate.setPartnerSku("PARTNER-" + id);
        candidate.setPskuCode("PSKU-" + id);
        candidate.setActiveStateUnknown(true);
        return candidate;
    }
}
