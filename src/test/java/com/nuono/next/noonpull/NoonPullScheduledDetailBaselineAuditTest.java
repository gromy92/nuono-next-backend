package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NoonPullScheduledDetailBaselineAuditTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void scheduledProductListShouldRequestAutomaticDetailBaselineAudit() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T00:30:00Z"), SHANGHAI);
        InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundation =
                new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        NoonProductListPullAdapter productListAdapter = mock(NoonProductListPullAdapter.class);
        NoonPullScheduledExecutionService service = new NoonPullScheduledExecutionService(
                new NoonPullScheduler(
                        foundation,
                        clock,
                        new NoonOrderReportSchedulePolicy(clock),
                        new NoonOrderBackfillPlanner(),
                        new NoonSalesRetentionPolicy(clock),
                        (plan) -> true
                ),
                foundation,
                new NoonReportPuller(foundation),
                new NoonInterfacePuller(foundation),
                productListAdapter,
                mock(NoonSalesReportAdapter.class),
                mock(NoonOrderReportAdapter.class),
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonReportProvider>) () -> null,
                (Supplier<NoonSalesPageQueryProvider>) () -> null,
                (Supplier<NoonProductInterfaceSmokeProvider>) () -> (request, pageNumber) ->
                        NoonInterfacePullPage.builder()
                                .items(List.of(Map.of("sku_parent", "Z-PRODUCT-1")))
                                .pageNumber(pageNumber)
                                .totalItems(1)
                                .requestCount(1)
                                .hasNextPage(false)
                                .build(),
                true
        );
        foundation.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("daily product offer list")
                .build());

        service.runOnce();

        ArgumentCaptor<NoonProductListApplyCommand> command =
                ArgumentCaptor.forClass(NoonProductListApplyCommand.class);
        verify(productListAdapter).apply(command.capture());
        assertTrue(command.getValue().isAutomaticDetailBackfill());
    }
}
