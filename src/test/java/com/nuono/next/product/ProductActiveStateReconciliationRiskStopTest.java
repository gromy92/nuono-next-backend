package com.nuono.next.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductActiveStateReconciliationMapper;
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

class ProductActiveStateReconciliationRiskStopTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void rateLimitStopsAndCancelsTheRestOfTheQueuedExactOfferBatch() {
        ProductActiveStateReconciliationMapper mapper =
                mock(ProductActiveStateReconciliationMapper.class);
        OperationalTaskService tasks = mock(OperationalTaskService.class);
        LocalDbProductMasterService productMaster = mock(LocalDbProductMasterService.class);
        ProductMasterSnapshotView rateLimited = new ProductMasterSnapshotView();
        rateLimited.getWarnings().add("读取价格信息失败: HTTP 429 too many requests");
        when(productMaster.fetchSnapshot(any())).thenReturn(rateLimited);
        when(tasks.claimQueued(any(), any())).thenReturn(true);

        AtomicBoolean held = new AtomicBoolean();
        NoonRiskBackoffGuard riskGuard = mock(NoonRiskBackoffGuard.class);
        when(riskGuard.currentHold(any())).thenAnswer(
                ignored -> held.get() ? Optional.of(new NoonRiskBackoffHold()) : Optional.empty()
        );
        when(riskGuard.recordRiskSignal(any(), any(), any(), any(), any(), any())).thenAnswer(ignored -> {
            held.set(true);
            return new NoonRiskBackoffHold();
        });
        ProductActiveStateReconciliationBatchRunner runner =
                new ProductActiveStateReconciliationBatchRunner(
                        mapper,
                        tasks,
                        productMaster,
                        new ProductActiveStateReconciliationGuard(
                                riskGuard,
                                new NoonPullFailurePolicy(CLOCK)
                        ),
                        CLOCK
                );

        runner.run(List.of(
                new ProductActiveStateReconciliationBatchRunner.WorkItem(
                        150101L,
                        candidate(55001L, "PARTNER-1")
                ),
                new ProductActiveStateReconciliationBatchRunner.WorkItem(
                        150102L,
                        candidate(55002L, "PARTNER-2")
                )
        ));

        verify(productMaster, times(1)).fetchSnapshot(any());
        verify(riskGuard).recordRiskSignal(
                any(),
                eq("rate_limited"),
                eq("PRODUCT"),
                any(),
                any(),
                any()
        );
        verify(tasks).cancel(
                150102L,
                "Noon 触发限流或风控保护，本次核实已安全停止，解除后将自动续跑。"
        );
    }

    private ProductActiveStateReconciliationCandidate candidate(
            Long siteOfferId,
            String partnerSku
    ) {
        ProductActiveStateReconciliationCandidate candidate =
                new ProductActiveStateReconciliationCandidate();
        candidate.setOwnerUserId(307L);
        candidate.setSiteOfferId(siteOfferId);
        candidate.setStoreCode("STR108065-NSA");
        candidate.setSiteCode("SA");
        candidate.setSkuParent("Z-" + siteOfferId);
        candidate.setPartnerSku(partnerSku);
        candidate.setPskuCode("PSKU-" + siteOfferId);
        return candidate;
    }
}
