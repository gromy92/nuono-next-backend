package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonStaleOrderExportConfirmationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRejectRowsOutsideTheRequestedWindowWithoutWritingFacts() {
        List<NoonOrderLineFact> written = new ArrayList<>();
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(written::add, CLOCK);

        NoonReportProcessResult result = adapter.process(file(
                LocalDate.of(2026, 5, 20),
                staleOrderCsv("NSAI50094671190-1", "2026-05-19 23:29:16")
        ));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(1, result.getExceptionCount());
        assertTrue(written.isEmpty());
        assertTrue(result.getDiagnosticMessage().contains("row_outside_container"));
    }

    @Test
    void shouldRejectStrictlyStaleOrderReportWhenAnyRowsCannotBeParsed() {
        List<NoonOrderLineFact> written = new ArrayList<>();
        NoonOrderReportAdapter adapter = new NoonOrderReportAdapter(written::add, CLOCK);
        String malformedTargetRow = "108065,AE,AE,AE,,,PAPERSAYSB360,Z02AD5F198C0C2E813C31Z-1,"
                + "Processing,65.8,65.8,AED,papersay,stationery,Fulfilled by Noon (FBN),"
                + "2026-05-20 00:01:00,,\n";

        NoonReportProcessResult result = adapter.process(file(
                LocalDate.of(2026, 5, 20),
                staleOrderCsv("NSAI50094671190-1", "2026-05-19 23:29:16") + malformedTargetRow
        ));

        assertEquals(NoonReportProcessResult.Code.MAPPING_FAILED, result.getCode());
        assertEquals(1, result.getExceptionCount());
        assertTrue(written.isEmpty());
    }

    @Test
    void shouldKeepPollingTheSameHandleWhenAStaleExportRepeats() {
        PullerFixture fixture = new PullerFixture("orders:repeated-stale-export");
        StaticProvider provider = new StaticProvider(staleOrderCsv("NSAI50094671190-1", "2026-05-20 23:29:16"));

        NoonReportPullResult first = fixture.execute(provider);
        NoonPullTaskRecord pending = fixture.task();

        assertEquals(
                NoonPullTaskStatus.RUNNING,
                first.getStatus(),
                pending.getFailureType() + " | " + pending.getDiagnosticSummary()
        );
        assertEquals("provider_unavailable", pending.getFailureType());
        assertEquals("EXP-1", pending.getReportExportId());

        NoonReportPullResult second = fixture.execute(provider);
        NoonPullTaskRecord stillPending = fixture.task();

        assertEquals(NoonPullTaskStatus.RUNNING, second.getStatus());
        assertEquals("provider_unavailable", stillPending.getFailureType());
        assertEquals(Boolean.TRUE, stillPending.getRetryable());
        assertEquals("EXP-1", stillPending.getReportExportId());
        assertTrue(stillPending.getDiagnosticSummary().contains(
                "report_payload_contract_rejected"
        ));
    }

    @Test
    void shouldNotInferEmptyWhenTheStrictlyStaleExportDigestChanges() {
        PullerFixture fixture = new PullerFixture("orders:changed-stale-export");

        NoonReportPullResult first = fixture.execute(
                new StaticProvider(staleOrderCsv("NSAI50094671190-1", "2026-05-20 23:29:16"))
        );
        NoonReportPullResult second = fixture.execute(
                new StaticProvider(staleOrderCsv("NSAI50094671191-1", "2026-05-20 23:29:16"))
        );
        NoonPullTaskRecord pending = fixture.task();

        assertEquals(NoonPullTaskStatus.RUNNING, first.getStatus());
        assertEquals(NoonPullTaskStatus.RUNNING, second.getStatus());
        assertEquals("provider_unavailable", pending.getFailureType());
        assertEquals(Boolean.TRUE, pending.getRetryable());
        assertEquals("EXP-1", pending.getReportExportId());
    }

    private NoonReportDownloadedFile file(LocalDate date, String content) {
        return new NoonReportDownloadedFile(
                orderRequest(date),
                "EXP-ORDER-1",
                "noon-report-order-1001-abcdef12",
                "abcdef123456",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static NoonReportPullRequest orderRequest(LocalDate date) {
        return NoonReportPullRequest.builder()
                .ownerUserId(307L)
                .storeCode("STR245027")
                .siteCode("AE")
                .dataDomain(NoonPullDataDomain.ORDER)
                .reportType(NoonOrderReportDescriptor.REPORT_TYPE)
                .dateFrom(date)
                .dateTo(date)
                .maxPollAttempts(2)
                .build();
    }

    private static String staleOrderCsv(String itemNumber, String orderTimestamp) {
        return "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,sku,status,"
                + "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,"
                + "order_timestamp,shipment_timestamp,delivered_timestamp\n"
                + "108065,AE,AE,AE,," + itemNumber + ",PAPERSAYSB359,Z02AD5F198C0C2E813C30Z-1,"
                + "Processing,65.8,65.8,AED,papersay,stationery,Fulfilled by Noon (FBN),"
                + orderTimestamp + ",,\n";
    }

    private static final class PullerFixture {
        private final InMemoryNoonPullRepository repository = new InMemoryNoonPullRepository();
        private final NoonPullFoundationService foundationService =
                new NoonPullFoundationService(repository, CLOCK, new NoonPullFailurePolicy(CLOCK));
        private final NoonReportPuller puller = new NoonReportPuller(foundationService);
        private final NoonOrderReportAdapter adapter = new NoonOrderReportAdapter((fact) -> {
        }, CLOCK);
        private final NoonPullTaskRecord task;

        private PullerFixture(String targetIdentity) {
            NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                    .ownerUserId(307L)
                    .storeCode("STR245027")
                    .siteCode("AE")
                    .pullType(NoonPullType.REPORT)
                    .dataDomain(NoonPullDataDomain.ORDER)
                    .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                    .scheduleExpression("daily")
                    .build());
            task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                    .ownerUserId(307L)
                    .storeCode("STR245027")
                    .siteCode("AE")
                    .pullType(NoonPullType.REPORT)
                    .dataDomain(NoonPullDataDomain.ORDER)
                    .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                    .targetIdentity(targetIdentity)
                    .targetDateFrom(LocalDate.of(2026, 5, 21))
                    .targetDateTo(LocalDate.of(2026, 5, 21))
                    .build()).orElseThrow();
        }

        private NoonReportPullResult execute(NoonReportProvider provider) {
            return puller.execute(
                    task.getId(),
                    orderRequest(LocalDate.of(2026, 5, 21)),
                    provider,
                    adapter::process
            );
        }

        private NoonPullTaskRecord task() {
            return repository.selectTask(task.getId());
        }
    }

    private static final class StaticProvider implements NoonReportProvider {
        private final byte[] content;

        private StaticProvider(String content) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String createExport(NoonReportPullRequest request) {
            return "EXP-1";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            return NoonReportExportStatus.ready("https://download.test/orders.csv");
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            return content;
        }
    }
}
