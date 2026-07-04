package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import com.nuono.next.infrastructure.mapper.NoonOrderPriceTrendBucketRow;
import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NoonPullSpringWiringContractTest {

    @Test
    void noonPullCoreServicesShouldHaveUnambiguousSpringConstructors() {
        new ApplicationContextRunner()
                .withBean(NoonPullRepository.class, InMemoryNoonPullRepository::new)
                .withBean(NoonProductProjectionWriter.class, () -> (command) -> {
                })
                .withBean(NoonSalesFactWriter.class, () -> (fact) -> {
                })
                .withBean(NoonOrderFactWriter.class, () -> (fact) -> {
                })
                .withBean(com.nuono.next.auth.AuthSessionTokenService.class,
                        () -> new com.nuono.next.auth.AuthSessionTokenService("test-secret", 3600))
                .withUserConfiguration(NoonPullWiringConfig.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(NoonPullFoundationService.class);
                    assertThat(context).hasSingleBean(NoonPullScheduler.class);
                    assertThat(context).hasSingleBean(NoonBusinessReadinessService.class);
                    assertThat(context).hasSingleBean(NoonRiskBackoffGuard.class);
                    assertThat(context).hasSingleBean(NoonReportPuller.class);
                    assertThat(context).hasSingleBean(NoonSalesPageQueryPullService.class);
                    assertThat(context).hasSingleBean(NoonPullScheduledExecutionService.class);
                    assertThat(context).hasSingleBean(NoonPullSmokeRunner.class);
                    assertThat(context).hasSingleBean(NoonPullSmokeController.class);
                });
    }

    @Test
    void noonSalesReportAdapterShouldUseProductionSalesFactWriterWhenPresent() {
        RecordingNoonSalesFactMapper mapper = new RecordingNoonSalesFactMapper();

        new ApplicationContextRunner()
                .withBean(NoonSalesFactMapper.class, () -> mapper)
                .withUserConfiguration(NoonSalesWriterWiringConfig.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(NoonSalesFactWriter.class);
                    NoonSalesReportAdapter adapter = context.getBean(NoonSalesReportAdapter.class);
                    NoonReportProcessResult result = adapter.process(new NoonReportDownloadedFile(
                            NoonReportPullRequest.builder()
                                    .ownerUserId(10002L)
                                    .storeCode("STR245027-NAE")
                                    .siteCode("AE")
                                    .dataDomain(NoonPullDataDomain.SALES)
                                    .reportType("productviewsandsalesdata")
                                    .dateFrom(LocalDate.of(2026, 5, 21))
                                    .dateTo(LocalDate.of(2026, 5, 21))
                                    .build(),
                            "EXP-SMOKE",
                            "noon-report-sales-130008-abcdef12",
                            "abcdef123456",
                            ("Visit_Date,Partner_SKU,SKU,Currency_Code,Shipped_Units,Revenue_Shipped\n"
                                    + "2026-05-21,PSKU-1,SKU-1,AED,7,120.50\n").getBytes(StandardCharsets.UTF_8)
                    ));

                    assertThat(result.getCode()).isEqualTo(NoonReportProcessResult.Code.SUCCEEDED);
                    assertThat(mapper.fact.getOwnerUserId()).isEqualTo(10002L);
                    assertThat(mapper.fact.getSkuParent()).isEqualTo("PSKU-1");
                    assertThat(mapper.fact.getSku()).isEqualTo("SKU-1");
                    assertThat(mapper.fact.getSourceBatchId()).isEqualTo("noon-report-sales-130008-abcdef12");
                    assertThat(mapper.ensureSequenceCalls).isEqualTo(1);
                    assertThat(mapper.ensureFactSequenceCalls).isEqualTo(1);
                    assertThat(mapper.ensureFactTableCalls).isEqualTo(1);
                });
    }

    @Test
    void noonOrderReportAdapterShouldUseProductionOrderFactWriterWhenPresent() {
        RecordingNoonOrderFactMapper mapper = new RecordingNoonOrderFactMapper();

        new ApplicationContextRunner()
                .withBean(NoonOrderFactMapper.class, () -> mapper)
                .withUserConfiguration(NoonOrderWriterWiringConfig.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(NoonOrderFactWriter.class);
                    NoonOrderReportAdapter adapter = context.getBean(NoonOrderReportAdapter.class);
                    NoonReportProcessResult result = adapter.process(new NoonReportDownloadedFile(
                            NoonReportPullRequest.builder()
                                    .ownerUserId(10002L)
                                    .storeCode("STR245027-NAE")
                                    .siteCode("AE")
                                    .dataDomain(NoonPullDataDomain.ORDER)
                                    .reportType(NoonOrderReportDescriptor.EXPORT_CATEGORY_CODE)
                                    .dateFrom(LocalDate.of(2026, 5, 21))
                                    .dateTo(LocalDate.of(2026, 5, 21))
                                    .build(),
                            "EXP-ORDER-SMOKE",
                            "noon-report-order-130016-abcdef12",
                            "abcdef123456",
                            ("id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,partner_sku,"
                                    + "sku,status,offer_price,gmv_lcy,currency_code,brand_code,family,"
                                    + "fulfillment_model,order_timestamp,shipment_timestamp,delivered_timestamp\n"
                                    + "245027,AE,AE,AE,,NAEI50094671190-1,PSKU-1,SKU-1,Processing,"
                                    + "65.8,65.8,AED,brand,family,Fulfilled by Noon (FBN),"
                                    + "2026-05-21 23:29:16,,\n").getBytes(StandardCharsets.UTF_8)
                    ));

                    assertThat(result.getCode()).isEqualTo(NoonReportProcessResult.Code.SUCCEEDED);
                    assertThat(mapper.fact.getOwnerUserId()).isEqualTo(10002L);
                    assertThat(mapper.fact.getOrderLineIdentity()).isEqualTo("NAEI50094671190-1");
                    assertThat(mapper.fact.getOrderIdentity()).isEqualTo("NAEI50094671190");
                    assertThat(mapper.fact.getSourceBatchId()).isEqualTo("noon-report-order-130016-abcdef12");
                    assertThat(mapper.ensureSequenceCalls).isEqualTo(1);
                    assertThat(mapper.ensureFactSequenceCalls).isEqualTo(1);
                    assertThat(mapper.ensureFactTableCalls).isEqualTo(1);
                });
    }

    @Configuration
    @Import({
            NoonPullFoundationService.class,
            NoonPullScheduler.class,
            NoonBusinessReadinessService.class,
            NoonRiskBackoffGuard.class,
            NoonReportPuller.class,
            NoonInterfacePuller.class,
            NoonProductListPullAdapter.class,
            NoonProductListInitializationService.class,
            NoonSalesReportAdapter.class,
            NoonSalesReportPullService.class,
            NoonSalesPageQueryPullService.class,
            NoonOrderReportAdapter.class,
            NoonOrderReportPullService.class,
            NoonPullScheduledExecutionService.class,
            NoonPullSmokeRunner.class,
            NoonPullSmokeController.class
    })
    static class NoonPullWiringConfig {
    }

    @Configuration
    @Import({
            MyBatisNoonSalesFactWriter.class,
            NoonSalesReportAdapter.class
    })
    static class NoonSalesWriterWiringConfig {
    }

    @Configuration
    @Import({
            MyBatisNoonOrderFactWriter.class,
            NoonOrderReportAdapter.class
    })
    static class NoonOrderWriterWiringConfig {
    }

    private static final class RecordingNoonSalesFactMapper implements NoonSalesFactMapper {
        private NoonSalesDailyFact fact;
        private int ensureSequenceCalls;
        private int ensureFactSequenceCalls;
        private int ensureFactTableCalls;

        @Override
        public void ensureSalesDataIdSequence() {
            ensureSequenceCalls++;
        }

        @Override
        public void ensureDailySalesFactSequence() {
            ensureFactSequenceCalls++;
        }

        @Override
        public void ensureDailySalesFactTable() {
            ensureFactTableCalls++;
        }

        @Override
        public void nextId(IdSequenceCommand command) {
            command.setAllocatedId(100001L);
        }

        @Override
        public int upsertDailySalesFact(Long id, NoonSalesDailyFact fact) {
            this.fact = fact;
            return 1;
        }

        @Override
        public com.nuono.next.nooncompleteness.NoonSalesProductViewsCompletenessAudit auditSalesProductViewsCompleteness(
                Long ownerUserId,
                String storeCode,
                String siteCode
        ) {
            return com.nuono.next.nooncompleteness.NoonSalesProductViewsCompletenessAudit.missing();
        }
    }

    private static final class RecordingNoonOrderFactMapper implements NoonOrderFactMapper {
        private NoonOrderLineFact fact;
        private int ensureSequenceCalls;
        private int ensureFactSequenceCalls;
        private int ensureFactTableCalls;

        @Override
        public void ensureNoonOrderIdSequence() {
            ensureSequenceCalls++;
        }

        @Override
        public void ensureOrderLineFactSequence() {
            ensureFactSequenceCalls++;
        }

        @Override
        public void ensureNoonOrderLineFactTable() {
            ensureFactTableCalls++;
        }

        @Override
        public void nextId(IdSequenceCommand command) {
            command.setAllocatedId(200001L);
        }

        @Override
        public int upsertOrderLineFact(Long id, NoonOrderLineFact fact) {
            this.fact = fact;
            return 1;
        }

        @Override
        public com.nuono.next.nooncompleteness.NoonSalesOrderCompletenessAudit auditSalesOrderCompleteness(
                Long ownerUserId,
                String storeCode,
                String siteCode
        ) {
            return com.nuono.next.nooncompleteness.NoonSalesOrderCompletenessAudit.notIntegrated("not_integrated");
        }

        @Override
        public List<NoonOrderPriceTrendBucketRow> selectPriceTrendBuckets(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String sku,
                LocalDateTime dateFromStart,
                LocalDateTime dateToExclusive
        ) {
            return List.of();
        }

        @Override
        public int countPriceTrendCandidateRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String partnerSku,
                String sku,
                LocalDateTime dateFromStart,
                LocalDateTime dateToExclusive,
                LocalDate reportDateFrom,
                LocalDate reportDateTo
        ) {
            return 0;
        }
    }
}
