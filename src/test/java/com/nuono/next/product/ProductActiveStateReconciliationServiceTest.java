package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductActiveStateReconciliationMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductActiveStateReconciliationServiceTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void queuesBeforeAsyncExecutionAndWritesOnlyExactTrustedOfferEvidence() {
        ProductActiveStateReconciliationMapper mapper =
                mock(ProductActiveStateReconciliationMapper.class);
        OperationalTaskService tasks = mock(OperationalTaskService.class);
        LocalDbProductMasterService productMaster = mock(LocalDbProductMasterService.class);
        ProductActiveStateReconciliationCandidate candidate = candidate(55001L, "TARGET-PSKU");
        when(mapper.listUnknownCandidates(307L, "STR108065-NSA", "SA", 10))
                .thenReturn(List.of(candidate));
        when(tasks.findActive(any(), any())).thenReturn(Optional.empty());
        OperationalTask queued = new OperationalTask();
        queued.setId(150101L);
        queued.setStatus(OperationalTaskStatus.QUEUED);
        when(tasks.queue(
                eq(ProductActiveStateReconciliationService.TASK_TYPE),
                eq("owner:307|siteOffer:55001"),
                any(OperationalTaskPayload.class)
        )).thenReturn(queued);
        when(tasks.claimQueued(150101L, "正在按店铺、站点和 PSKU 核实 Noon 在售状态。"))
                .thenReturn(true);
        when(productMaster.fetchSnapshot(any())).thenReturn(snapshot(false));
        when(mapper.resolveUnknownActiveState(
                eq(55001L),
                eq(307L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("TARGET-PSKU"),
                eq(false),
                eq("NOON_PRICING_INFO"),
                any(LocalDateTime.class)
        )).thenReturn(1);
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        ProductActiveStateReconciliationService service = service(
                mapper,
                tasks,
                productMaster,
                (accountKey, task) -> submitted.set(task)
        );

        ProductActiveStateReconciliationEnqueueResult result =
                service.enqueueScope(307L, "STR108065-NSA", "SA", 339);

        assertEquals(339, result.getUnknownCount());
        assertEquals(1, result.getQueuedCount());
        verify(productMaster, org.mockito.Mockito.never()).fetchSnapshot(any());
        submitted.get().run();
        verify(mapper).resolveUnknownActiveState(
                eq(55001L),
                eq(307L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("TARGET-PSKU"),
                eq(false),
                eq("NOON_PRICING_INFO"),
                eq(LocalDateTime.of(2026, 7, 31, 6, 0))
        );
        verify(tasks).complete(
                eq(150101L),
                any(),
                eq("已由 Noon 权威定价接口确认商品停用。")
        );
    }

    @Test
    void enumeratesEveryUnknownStoreSiteWithoutCanmanHardcoding() {
        ProductActiveStateReconciliationMapper mapper =
                mock(ProductActiveStateReconciliationMapper.class);
        ProductActiveStateReconciliationScope sa = scope(307L, "STR108065-NSA", "SA", 339);
        ProductActiveStateReconciliationScope ae = scope(901L, "OTHER-NAE", "AE", 12);
        when(mapper.listUnknownScopes(4)).thenReturn(List.of(sa, ae));
        when(mapper.listUnknownCandidates(any(), any(), any(), eq(10))).thenReturn(List.of());
        ProductActiveStateReconciliationService service = service(
                mapper,
                mock(OperationalTaskService.class),
                mock(LocalDbProductMasterService.class),
                (accountKey, task) -> task.run()
        );

        assertEquals(0, service.enqueueUnknownScopes(4));

        verify(mapper).listUnknownCandidates(307L, "STR108065-NSA", "SA", 10);
        verify(mapper).listUnknownCandidates(901L, "OTHER-NAE", "AE", 10);
    }

    @Test
    void staleQueuedOrRunningTasksAreRecoveredBeforeSelectingScopes() {
        ProductActiveStateReconciliationMapper mapper =
                mock(ProductActiveStateReconciliationMapper.class);
        OperationalTaskService tasks = mock(OperationalTaskService.class);
        OperationalTask stale = new OperationalTask();
        stale.setId(150099L);
        stale.setStatus(OperationalTaskStatus.RUNNING);
        stale.setUpdatedAt(LocalDateTime.of(2026, 7, 31, 4, 0));
        when(tasks.listActive(ProductActiveStateReconciliationService.TASK_TYPE, 1000))
                .thenReturn(List.of(stale));
        when(mapper.listUnknownScopes(4)).thenReturn(List.of());
        ProductActiveStateReconciliationService service = service(
                mapper,
                tasks,
                mock(LocalDbProductMasterService.class),
                (accountKey, task) -> task.run()
        );

        assertEquals(0, service.enqueueUnknownScopes(4));

        verify(tasks).cancel(150099L, "核实任务执行超时，已自动回收并允许后续续跑。");
    }

    private ProductActiveStateReconciliationService service(
            ProductActiveStateReconciliationMapper mapper,
            OperationalTaskService tasks,
            LocalDbProductMasterService productMaster,
            ProductActiveStateReconciliationService.TaskSubmitter submitter
    ) {
        return new ProductActiveStateReconciliationService(
                mapper,
                tasks,
                productMaster,
                submitter,
                ProductActiveStateReconciliationGuard.disabled(CLOCK),
                10,
                60,
                CLOCK
        );
    }

    private ProductMasterSnapshotView snapshot(boolean active) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.setSiteOffers(List.of(Map.of(
                "storeCode", "STR108065-NSA",
                "site", "SA",
                "partnerSku", "TARGET-PSKU",
                "isActive", active,
                "activeStateSource", "NOON_PRICING_INFO"
        )));
        return snapshot;
    }

    private ProductActiveStateReconciliationCandidate candidate(Long siteOfferId, String partnerSku) {
        ProductActiveStateReconciliationCandidate candidate =
                new ProductActiveStateReconciliationCandidate();
        candidate.setOwnerUserId(307L);
        candidate.setLogicalStoreId(50003L);
        candidate.setProductMasterId(53001L);
        candidate.setVariantId(54001L);
        candidate.setSiteOfferId(siteOfferId);
        candidate.setStoreCode("STR108065-NSA");
        candidate.setSiteCode("SA");
        candidate.setSkuParent("Z-PARENT-1");
        candidate.setPartnerSku(partnerSku);
        candidate.setPskuCode("ZSKU-1");
        return candidate;
    }

    private ProductActiveStateReconciliationScope scope(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            int unknownCount
    ) {
        ProductActiveStateReconciliationScope scope =
                new ProductActiveStateReconciliationScope();
        scope.setOwnerUserId(ownerUserId);
        scope.setStoreCode(storeCode);
        scope.setSiteCode(siteCode);
        scope.setUnknownCount(unknownCount);
        return scope;
    }
}
