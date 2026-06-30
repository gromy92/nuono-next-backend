package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonOrderReportPullServiceTest {

    private InMemoryNoonPullRepository repository;
    private NoonPullFoundationService foundationService;
    private NoonOrderReportPullService service;
    private InMemoryOrderFactWriter writer;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T01:00:00Z"), ZoneOffset.UTC);
        repository = new InMemoryNoonPullRepository();
        foundationService = new NoonPullFoundationService(repository, clock, new NoonPullFailurePolicy(clock));
        writer = new InMemoryOrderFactWriter();
        service = new NoonOrderReportPullService(
                foundationService,
                new NoonReportPuller(foundationService),
                new NoonOrderReportAdapter(writer, clock)
        );
    }

    @Test
    void shouldPullOrderReportThroughReportPullerAndWriteFacts() {
        FakeOrderReportProvider provider = FakeOrderReportProvider.ready(
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,SA,SA,SA,,NSAI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,SAR,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-19 23:29:16,,\n"
        );

        NoonReportPullResult result = service.pullWindow(
                new NoonOrderReportPullCommand(
                        10002L,
                        "STR245027-NAE",
                        "AE",
                        LocalDate.of(2026, 5, 19),
                        LocalDate.of(2026, 5, 19),
                        NoonPullTriggerMode.SCHEDULED_DAILY
                ),
                provider
        );

        assertEquals(NoonPullTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(List.of("create:sales_dashboard_sales_export", "poll", "download"), provider.calls);
        assertEquals(1, writer.facts.size());
        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullDataDomain.ORDER, task.getDataDomain());
        assertEquals("orders:2026-05-19..2026-05-19", task.getTargetIdentity());
        assertTrue(task.getSourceBatchId().startsWith("noon-report-order-"));
    }

    @Test
    void shouldClassifyVisibleButUnconfiguredOrderExportAsProviderNotConfigured() {
        NoonReportPullResult result = service.pullWindow(
                new NoonOrderReportPullCommand(
                        10002L,
                        "STR245027-NAE",
                        "AE",
                        LocalDate.of(2026, 5, 19),
                        LocalDate.of(2026, 5, 19),
                        NoonPullTriggerMode.MANUAL_BACKFILL
                ),
                FakeOrderReportProvider.notConfigured()
        );

        NoonPullTaskRecord task = repository.listTasks().get(0);
        assertEquals(NoonPullTaskStatus.FAILED, result.getStatus());
        assertEquals("provider_not_configured", task.getFailureType());
    }

    @Test
    void shouldAllowRealNoonOrderExportsToStayPendingBeforeReady() {
        FakeOrderReportProvider provider = FakeOrderReportProvider.pendingBeforeReady(
                5,
                "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                        + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                        + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                        + "108065,AE,AE,AE,,NAEI50094671190-1,PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                        + "Processing,65.8,65.8,AED,papersay,stationery,Fulfilled by Noon (FBN),"
                        + "2026-05-21 23:29:16,,\n"
        );

        NoonReportPullResult result = service.pullWindow(
                new NoonOrderReportPullCommand(
                        10002L,
                        "STR245027-NAE",
                        "AE",
                        LocalDate.of(2026, 5, 21),
                        LocalDate.of(2026, 5, 21),
                        NoonPullTriggerMode.MANUAL_BACKFILL
                ),
                provider
        );

        assertEquals(NoonPullTaskStatus.RUNNING, result.getStatus());
        assertEquals(1, provider.pollCount);
        assertEquals(0, writer.facts.size());
    }

    private static final class InMemoryOrderFactWriter implements NoonOrderFactWriter {
        private final Map<String, NoonOrderLineFact> facts = new LinkedHashMap<>();

        @Override
        public void upsertLine(NoonOrderLineFact fact) {
            facts.put(fact.naturalKey(), fact);
        }
    }

    private static final class FakeOrderReportProvider implements NoonReportProvider {
        private final List<String> calls = new ArrayList<>();
        private final NoonReportExportStatus status;
        private final byte[] content;
        private final boolean notConfigured;
        private final int readyAfterPolls;
        private int pollCount;

        private FakeOrderReportProvider(NoonReportExportStatus status, String content, boolean notConfigured) {
            this(status, content, notConfigured, 1);
        }

        private FakeOrderReportProvider(
                NoonReportExportStatus status,
                String content,
                boolean notConfigured,
                int readyAfterPolls
        ) {
            this.status = status;
            this.content = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            this.notConfigured = notConfigured;
            this.readyAfterPolls = readyAfterPolls;
        }

        private static FakeOrderReportProvider ready(String content) {
            return new FakeOrderReportProvider(NoonReportExportStatus.ready("https://download.test/orders.csv"), content, false);
        }

        private static FakeOrderReportProvider notConfigured() {
            return new FakeOrderReportProvider(NoonReportExportStatus.pending(), "", true);
        }

        private static FakeOrderReportProvider pendingBeforeReady(int readyAfterPolls, String content) {
            return new FakeOrderReportProvider(
                    NoonReportExportStatus.ready("https://download.test/orders.csv"),
                    content,
                    false,
                    readyAfterPolls
            );
        }

        @Override
        public String createExport(NoonReportPullRequest request) {
            if (notConfigured) {
                throw new NoonInterfacePullException("provider not configured: order export category is visible but export is not configured");
            }
            calls.add("create:" + request.getReportType());
            return "EXP-ORDER-1";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            calls.add("poll");
            pollCount++;
            if (pollCount < readyAfterPolls) {
                return NoonReportExportStatus.pending();
            }
            return status;
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            calls.add("download");
            return content;
        }
    }
}
