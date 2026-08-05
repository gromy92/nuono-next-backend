package com.nuono.next.officialwarehouse.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp07InventorySnapshotProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void explicitTwoPageMetadataRoutesToTheNextPageWithoutAnyMaximum() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", 7)
                .put("inventory_type", "saleable")
                .put("partner_sku", "PAPERSAYSB422"));
        StubProvider rawProvider = new StubProvider(
                objectMapper, paged(1, true, 2, 2L, List.of(valid))
        );
        Dp07InventorySnapshotProvider provider = provider(rawProvider);

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result =
                provider.fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getNextPage()).hasValue(2);
        assertThat(result.getValue().getTotalPages()).hasValue(2);
        assertThat(result.getValue().getItems()).hasSize(1);
        assertThat(result.getValue().getSourceItemCount()).isEqualTo(1);
        assertThat(result.getValue().getBusinessSkippedItemCount()).isZero();
        assertThat(result.getValue().getAuthority()).hasValueSatisfying(authority -> {
            assertThat(authority.getKind())
                    .isEqualTo(SnapshotCollectionAuthority.Kind.PAGED_GENERATION);
            assertThat(authority.getDeclaredCollectionCount()).isEqualTo(2L);
        });
        assertThat(rawProvider.requestedPages).containsExactly(1);
    }

    @Test
    void missingLastPageEvidenceFailsClosedInsteadOfAssumingOnePage() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", 7)
                .put("inventory_type", "saleable")
                .put("partner_sku", "PAPERSAYSB422"));
        StubProvider rawProvider = new StubProvider(
                objectMapper, paged(1, null, null, 1L, List.of(valid))
        );

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isNotEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue()).isNull();
    }

    @Test
    void declaredTotalPagesAloneIsSufficientPaginationEvidence() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", 7)
                .put("inventory_type", "saleable")
                .put("partner_sku", "PAPERSAYSB422"));
        StubProvider rawProvider = new StubProvider(
                objectMapper, paged(1, null, 2, 2L, List.of(valid))
        );

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getNextPage()).hasValue(2);
        assertThat(result.getValue().getTotalPages()).hasValue(2);
    }

    @Test
    void invalidRequiredRowStructureFailsTheWholePage() {
        InventoryItem bad = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", "not-a-number")
                .put("inventory_type", "saleable")
                .put("partner_sku", "BAD"));
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", 3)
                .put("inventory_type", "saleable")
                .put("partner_sku", "GOOD"));
        StubProvider rawProvider = new StubProvider(
                objectMapper, paged(1, false, 1, 2L, List.of(bad, valid))
        );

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(result.getSanitizedCode()).isEqualTo("DP07A_INVENTORY_ROW_CONTRACT_ERROR");
        assertThat(result.getValue()).isNull();
    }

    @Test
    void missingRequiredInventoryFieldsFailTheWholePage() {
        List<InventoryItem> malformedRows = List.of(
                InventoryItem.from(objectMapper.createObjectNode()
                        .put("warehouse_code", "RUH01")
                        .put("inventory_type", "saleable")
                        .put("partner_sku", "MISSING-QTY")),
                InventoryItem.from(objectMapper.createObjectNode()
                        .put("qty", 1)
                        .put("inventory_type", "saleable")
                        .put("partner_sku", "MISSING-WAREHOUSE")),
                InventoryItem.from(objectMapper.createObjectNode()
                        .put("warehouse_code", "RUH01")
                        .put("qty", 1)
                        .put("partner_sku", "MISSING-INVENTORY-TYPE"))
        );

        for (InventoryItem malformed : malformedRows) {
            ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = provider(
                    new StubProvider(
                            objectMapper, paged(1, false, 1, 1L, List.of(malformed))
                    )
            ).fetchPage(request(1));

            assertThat(result.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
            assertThat(result.getSanitizedCode()).isEqualTo("DP07A_INVENTORY_ROW_CONTRACT_ERROR");
            assertThat(result.getValue()).isNull();
        }
    }

    @Test
    void localCompleteExportFlagCannotSelfAuthorizeTheSnapshot() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01")
                .put("qty", 1)
                .put("inventory_type", "saleable")
                .put("partner_sku", "GOOD")
                .put("inventory_snapshot_at", "2026-08-02 23:00:00"));
        StubProvider rawProvider = new StubProvider(objectMapper, new InventoryPage(
                1, false, 1, true, List.of(valid), objectMapper.createObjectNode()
        ));
        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(result.getValue()).isNull();
    }

    @Test
    void completeCsvResponseBytesAndExactRowsAuthorizeOnePass() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 1)
                .put("inventory_type", "saleable").put("partner_sku", "GOOD"));
        InventoryPage page = new InventoryPage(
                1, false, 1, true, null, null, 1L,
                List.of(valid), objectMapper.createObjectNode(),
                "warehouse_code,qty\nRUH01,1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = provider(
                new StubProvider(objectMapper, page)
        ).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getAuthority()).hasValueSatisfying(authority ->
                assertThat(authority.getKind())
                        .isEqualTo(SnapshotCollectionAuthority.Kind.COMPLETE_RESPONSE));
        assertThat(result.getValue().getAuthorityMode())
                .isEqualTo(SnapshotPage.AuthorityMode.PROVIDER_AUTHORITY);
    }

    @Test
    void pagedJsonWithoutNativeGenerationRequiresTwoPass() {
        InventoryItem valid = InventoryItem.from(objectMapper.createObjectNode()
                .put("warehouse_code", "RUH01").put("qty", 1)
                .put("inventory_type", "saleable").put("partner_sku", "GOOD"));
        InventoryPage page = new InventoryPage(
                1, false, 1, false, null, null, null,
                List.of(valid), objectMapper.createObjectNode()
        );

        ProviderOutcome<SnapshotPage<Dp07InventorySnapshotItem>> result = provider(
                new StubProvider(objectMapper, page)
        ).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getAuthority()).isEmpty();
        assertThat(result.getValue().getAuthorityMode())
                .isEqualTo(SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED);
    }

    private InventoryPage paged(
            int page, Boolean hasNext, Integer totalPages,
            long declaredCollectionCount, List<InventoryItem> items
    ) {
        return new InventoryPage(
                page, hasNext, totalPages, false,
                "fbn-generation-20260802", null, declaredCollectionCount,
                items, objectMapper.createObjectNode()
        );
    }

    private Dp07InventorySnapshotProvider provider(StubProvider rawProvider) {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        when(resolver.resolve(any(com.nuono.next.noonpull.NoonInterfacePullRequest.class)))
                .thenReturn(new NoonPullStoreBinding(
                        307L, "PRJ108065", "STR108065-NSA", "SA", "108065",
                        "user", "session=redacted"
                ));
        return new Dp07InventorySnapshotProvider(rawProvider, resolver, objectMapper);
    }

    private SnapshotPageRequest request(int pageNo) {
        return SnapshotPageRequest.from(task(), pageNo);
    }

    private DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                7001L,
                OperationCode.DP07A,
                "NOON_FBN_INVENTORY",
                307L,
                8001L,
                "PRJ108065",
                null,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-1",
                LocalDateTime.of(2026, 8, 2, 15, 0),
                "complete-snapshot:2026-08-02",
                "SNAPSHOT_FETCH",
                LocalDateTime.of(2026, 8, 2, 15, 0)
        );
        task.setFenceEpoch(1L);
        return task;
    }

    private static final class StubProvider extends OfficialWarehouseFbnInventoryProvider {
        private final InventoryPage page;
        private final java.util.List<Integer> requestedPages = new java.util.ArrayList<>();

        private StubProvider(ObjectMapper objectMapper, InventoryPage page) {
            super(objectMapper, null, null);
            this.page = page;
        }

        @Override
        public InventoryPage fetchPage(PullRequest request, int pageNo) {
            requestedPages.add(pageNo);
            return page;
        }
    }
}
