package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonSalesPageQueryPullServiceTest {

    private InMemoryNoonPullRepository repository;
    private NoonSalesPageQueryPullService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-23T02:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService =
                new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        service = new NoonSalesPageQueryPullService(
                foundationService,
                new NoonInterfacePuller(foundationService)
        );
    }

    @Test
    void shouldCreatePageQueryTaskAndReturnRowsWithoutSalesFactImport() {
        NoonInterfacePullResult result = service.pullWindow(command(), (request, pageNumber) ->
                NoonInterfacePullPage.builder()
                        .items(List.of(
                                Map.of("item_nr", "NAEI50000000001-1", "partner_sku", "PSKU-1"),
                                Map.of("item_nr", "NAEI50000000002-1", "partner_sku", "PSKU-2")
                        ))
                        .pageNumber(pageNumber)
                        .totalItems(2)
                        .hasNextPage(false)
                        .requestCount(1)
                        .build()
        );

        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(2, result.getItems().size());
        assertEquals(1, result.getRequestCount());
        assertTrue(result.getSourceBatchId().startsWith("noon-interface-sales-"));
        assertEquals(NoonPullType.PAGE_QUERY, task.getPullType());
        assertEquals(NoonPullDataDomain.SALES, task.getDataDomain());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, task.getTriggerMode());
        assertEquals("sales-page-query:2026-05-01..2026-05-22", task.getTargetIdentity());
        assertEquals(LocalDate.of(2026, 5, 1), task.getTargetDateFrom());
        assertEquals(LocalDate.of(2026, 5, 22), task.getTargetDateTo());
        assertEquals("ready", task.getReadinessState());
    }

    private NoonSalesPageQueryPullCommand command() {
        return NoonSalesPageQueryPullCommand.builder()
                .ownerUserId(10002L)
                .storeCode("STR245027-NAE")
                .siteCode("AE")
                .dateFrom(LocalDate.of(2026, 5, 1))
                .dateTo(LocalDate.of(2026, 5, 22))
                .requestSummary("page query smoke")
                .build();
    }
}
