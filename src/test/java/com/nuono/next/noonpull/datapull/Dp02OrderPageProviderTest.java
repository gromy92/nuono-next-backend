package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonInterfacePullPage;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonOrderLineFact;
import com.nuono.next.noonpull.NoonOrderReportRowClassifier;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.noonpull.NoonSalesPageQueryProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Dp02OrderPageProviderTest {

    @Test
    void exactWindowPageProducesPersistableFactsAndRequiresTwoPassProof() {
        Dp02OrderPageProvider provider = provider(page(1, 100, 1, false, List.of(validRow())));

        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> outcome = provider.fetchPage(request());

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        SnapshotPage<NoonOrderLineFact> page = outcome.getValue();
        assertThat(page.getAuthorityMode()).isEqualTo(SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED);
        assertThat(page.getLastPage()).contains(true);
        assertThat(page.getSourceItemCount()).isEqualTo(1);
        assertThat(page.getItems()).singleElement().satisfies(fact -> {
            assertThat(fact.getOrderLineIdentity()).isEqualTo("NAEI50000000001-1");
            assertThat(fact.getOrderTimestamp())
                    .isEqualTo(LocalDateTime.of(2026, 7, 10, 12, 30));
            assertThat(fact.getReportDateFrom().toString()).isEqualTo("2026-07-10");
        });
    }

    @Test
    void authoritativeEmptyWindowIsACompleteTwoPassPage() {
        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> outcome =
                provider(page(1, 100, 0, false, List.of())).fetchPage(request());

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(outcome.getValue().getItems()).isEmpty();
        assertThat(outcome.getValue().getLastPage()).contains(true);
        assertThat(outcome.getValue().getAuthorityMode())
                .isEqualTo(SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED);
    }

    @Test
    void rowOutsideRequestedDateFailsTheWholeContainer() {
        Map<String, Object> row = validRow();
        row.put("order_timestamp", "2026-07-09T23:59:59");

        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> outcome =
                provider(page(1, 100, 1, false, List.of(row))).fetchPage(request());

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(outcome.getSanitizedCode()).isEqualTo("DP02_PAGE_ROW_OUTSIDE_CONTAINER");
        assertThat(outcome.getValue()).isNull();
    }

    @Test
    void businessInvalidRowUsesFrameworkSkipFingerprintWithoutAWrapperItem() {
        Map<String, Object> row = validRow();
        row.put("status", "");

        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> outcome =
                provider(page(1, 100, 1, false, List.of(row))).fetchPage(request());
        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> repeated =
                provider(page(1, 100, 1, false, List.of(row))).fetchPage(request());

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(outcome.getValue().getItems()).isEmpty();
        assertThat(outcome.getValue().getBusinessSkippedItemCount()).isEqualTo(1);
        assertThat(outcome.getValue().getBusinessSkippedComparisonFingerprints())
                .singleElement().satisfies(fingerprint -> assertThat(fingerprint).hasSize(64));
        assertThat(repeated.getValue().getBusinessSkippedComparisonFingerprints())
                .isEqualTo(outcome.getValue().getBusinessSkippedComparisonFingerprints());
    }

    @Test
    void incompleteRowSchemaAndInconsistentExtentFailClosed() {
        Map<String, Object> incomplete = validRow();
        incomplete.remove("sku");
        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> schemaOutcome =
                provider(page(1, 100, 1, false, List.of(incomplete))).fetchPage(request());
        ProviderOutcome<SnapshotPage<NoonOrderLineFact>> extentOutcome =
                provider(page(1, 2, 3, true, List.of(validRow()))).fetchPage(request());

        assertThat(schemaOutcome.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(extentOutcome.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(schemaOutcome.getValue()).isNull();
        assertThat(extentOutcome.getValue()).isNull();
    }

    private Dp02OrderPageProvider provider(NoonInterfacePullPage response) {
        NoonSalesPageQueryProvider rawProvider = (request, page) -> response;
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        when(resolver.resolve(any(NoonInterfacePullRequest.class))).thenReturn(new NoonPullStoreBinding(
                307L, "PRJ244978", "STR244978-NAE", "AE", "244978",
                "user", "session=redacted"
        ));
        return new Dp02OrderPageProvider(
                rawProvider,
                resolver,
                new NoonOrderReportRowClassifier(Clock.fixed(
                        Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC
                )),
                new ObjectMapper()
        );
    }

    private NoonInterfacePullPage page(
            int pageNo,
            int pageSize,
            int total,
            boolean hasNext,
            List<Map<String, Object>> rows
    ) {
        return NoonInterfacePullPage.builder()
                .pageNumber(pageNo)
                .pageSize(pageSize)
                .totalItems(total)
                .hasNextPage(hasNext)
                .requestCount(1)
                .items(rows)
                .build();
    }

    private SnapshotPageRequest request() {
        return SnapshotPageRequest.from(task(), 1);
    }

    private DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                2001L,
                OperationCode.DP02,
                Dp02OrderPageProvider.CHANNEL,
                307L,
                8001L,
                "PRJ244978",
                null,
                "PRJ244978",
                "STR244978-NAE",
                "AE",
                "scope-dp02",
                LocalDateTime.of(2026, 7, 11, 1, 0),
                "date-range:2026-07-10..2026-07-10",
                "SNAPSHOT_FETCH",
                LocalDateTime.of(2026, 7, 11, 1, 0)
        );
        task.setFenceEpoch(1L);
        return task;
    }

    private Map<String, Object> validRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id_partner", "244978");
        row.put("src_country", "AE");
        row.put("country_code", "AE");
        row.put("dest_country", "AE");
        row.put("item_nr", "NAEI50000000001-1");
        row.put("partner_sku", "PAPERSAYSB422");
        row.put("sku", "Z422");
        row.put("status", "Shipped");
        row.put("offer_price", "49.50");
        row.put("gmv_lcy", "49.50");
        row.put("currency_code", "AED");
        row.put("brand_code", "PAPERSAY");
        row.put("family", "Stationery");
        row.put("fulfillment_model", "FBN");
        row.put("order_timestamp", "2026-07-10T12:30:00");
        row.put("shipment_timestamp", "2026-07-10T14:00:00");
        row.put("delivered_timestamp", "");
        row.put("bayan_nr", "");
        return row;
    }
}
