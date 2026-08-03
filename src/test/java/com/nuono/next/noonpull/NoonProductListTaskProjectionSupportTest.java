package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class NoonProductListTaskProjectionSupportTest {

    @Test
    void projectionFailureReplacesPrematureSuccessWithExactFailedTaskEvidence() {
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), ZoneOffset.UTC);
        NoonPullFoundationService foundation = new NoonPullFoundationService(repository, clock);
        NoonPullPlanRecord plan = foundation.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .scheduleExpression("test")
                .build());
        NoonPullTaskRecord task = foundation.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .targetIdentity("product-list:test")
                .build()).orElseThrow();
        foundation.markSucceeded(task.getId(), "batch-1", "list fetched");
        NoonProductListPullAdapter adapter = new NoonProductListPullAdapter(command -> {
            throw new DuplicateKeyException(
                    "projection duplicate",
                    new IllegalStateException("Duplicate entry for key uk_product_master_store_sku_parent")
            );
        });

        boolean projected = NoonProductListTaskProjectionSupport.apply(
                adapter,
                task,
                NoonInterfacePullResult.succeeded("batch-1", List.of(Map.of("partner_sku", "PAPERSAYSB446")), 1, 1),
                foundation
        );

        NoonPullTaskRecord failed = repository.selectTask(task.getId());
        assertFalse(projected);
        assertEquals(NoonPullTaskStatus.FAILED, failed.getStatus());
        assertEquals("product_projection_failed", failed.getFailureType());
        assertTrue(failed.getDiagnosticSummary().contains("uk_product_master_store_sku_parent"));
    }
}
