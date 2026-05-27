package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonProductListInitializationServiceTest {

    private InMemoryNoonPullRepository repository;
    private CapturingProjectionWriter writer;
    private NoonProductListInitializationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T06:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        NoonPullFoundationService foundationService =
                new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new CapturingProjectionWriter();
        service = new NoonProductListInitializationService(
                foundationService,
                new NoonInterfacePuller(foundationService),
                new NoonProductListPullAdapter(writer)
        );
    }

    @Test
    void shouldCreateInterfacePullTaskExecuteProviderAndApplyProductProjection() {
        NoonProductListInitializationResult result = service.initialize(
                NoonProductListInitializationCommand.builder()
                        .ownerUserId(307L)
                        .projectCode("PRJ245027")
                        .projectName("Xingyao")
                        .storeCode("STR245027-NAE")
                        .siteCode("AE")
                        .requestSummary("POST /offer/list/noon")
                        .build(),
                (request, pageNumber) -> NoonInterfacePullPage.builder()
                        .items(List.of(Map.of(
                                "sku_parent", "ZPARENT-1",
                                "sku", "ZCHILD-1",
                                "content", Map.of("title", "Milkyway bottle")
                        )))
                        .pageNumber(pageNumber)
                        .totalItems(1)
                        .hasNextPage(false)
                        .requestCount(1)
                        .build()
        );

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getTaskStatus());
        assertEquals(1, result.getAcceptedProductCount());
        assertNotNull(result.getSourceBatchId());
        assertEquals(1, repository.listTasks().size());
        assertEquals(NoonPullTaskStatus.SUCCEEDED, repository.listTasks().get(0).getStatus());
        assertEquals(result.getSourceBatchId(), writer.command.getSourceBatchId());
        assertEquals(1, writer.command.getProductSeeds().size());
    }

    private static final class CapturingProjectionWriter implements NoonProductProjectionWriter {
        private NoonProductProjectionWriteCommand command;

        @Override
        public void write(NoonProductProjectionWriteCommand command) {
            this.command = command;
            this.command.setWarnings(new ArrayList<>(command.getWarnings()));
        }
    }
}
