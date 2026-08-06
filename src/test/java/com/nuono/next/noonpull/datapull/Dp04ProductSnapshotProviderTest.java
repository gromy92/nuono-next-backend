package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotPageRequest;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonpull.NoonInterfacePullPage;
import com.nuono.next.noonpull.NoonProductInterfaceSmokeProvider;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Dp04ProductSnapshotProviderTest {

    @Test
    void validRowsContinueWhenOneDeterministicBusinessRowIsInvalid() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(100)
                        .totalItems(2)
                        .hasNextPage(false)
                        .requestCount(1)
                        .providerGenerationToken("offer-generation-20260802")
                        .items(List.of(
                                Map.of("partner_sku", "BAD-WITHOUT-PRODUCT"),
                                Map.of(
                                        "partner_sku", "PAPERSAYSB422",
                                        "csku_parent", "Z422",
                                        "provider_internal", "discard"
                                )
                        ))
                        .build();
        Dp04ProductSnapshotProvider provider = provider(rawProvider);

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider.fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getLastPage()).contains(true);
        assertThat(result.getValue().getItems()).hasSize(2);
        assertThat(result.getValue().getSourceItemCount()).isEqualTo(2);
        assertThat(result.getValue().getBusinessSkippedItemCount()).isZero();
        assertThat(result.getValue().getAuthority()).hasValueSatisfying(authority -> {
            assertThat(authority.getKind())
                    .isEqualTo(SnapshotCollectionAuthority.Kind.PAGED_GENERATION);
            assertThat(authority.getDeclaredCollectionCount()).isEqualTo(2L);
            assertThat(authority.getGenerationTokenSha256()).hasSize(64);
        });
        assertThat(result.getValue().getItems().get(0).isWritableProjection()).isFalse();
        assertThat(result.getValue().getItems().get(0).getPresencePartnerSku())
                .isEqualTo("BAD-WITHOUT-PRODUCT");
        assertThat(result.getValue().getItems().get(1).toProjectionPayload())
                .doesNotContainKey("provider_internal");
    }

    @Test
    void declaredTotalWithoutProviderGenerationRequiresTwoPassObservation() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(100)
                        .totalItems(1)
                        .hasNextPage(false)
                        .requestCount(1)
                        .items(List.of(Map.of(
                                "partner_sku", "PAPERSAYSB422",
                                "csku_parent", "Z422"
                        )))
                        .build();

        Dp04ProductSnapshotProvider adapter = provider(rawProvider);
        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result = adapter.fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getAuthority()).isEmpty();
        assertThat(result.getValue().getAuthorityMode())
                .isEqualTo(SnapshotPage.AuthorityMode.TWO_PASS_REQUIRED);
    }

    @Test
    void riskControlNeverBecomesAnEmptySuccessfulPage() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) -> {
            throw new NoonHttpException(429, "too many requests", "/offer/list/noon");
        };

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.RISK_CONTROL);
        assertThat(result.getValue()).isNull();
    }

    @Test
    void conflictingEmptyMetadataFailsTheWholePage() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(100)
                        .totalItems(0)
                        .hasNextPage(false)
                        .requestCount(1)
                        .providerGenerationToken("offer-generation-20260802")
                        .items(List.of(Map.of(
                                "partner_sku", "PAPERSAYSB422",
                                "csku_parent", "Z422"
                        )))
                        .build();

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isNotEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue()).isNull();
    }

    @Test
    void shortNonLastPageFailsClosedInsteadOfApplyingAPartialSnapshot() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(2)
                        .totalItems(3)
                        .hasNextPage(true)
                        .requestCount(1)
                        .providerGenerationToken("offer-generation-20260802")
                        .items(List.of(Map.of(
                                "partner_sku", "PAPERSAYSB422",
                                "csku_parent", "Z422"
                        )))
                        .build();

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isNotEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue()).isNull();
    }

    @Test
    void unpersistableFieldShapeBecomesPresenceOnlyAndDoesNotBlockTheGoodRow() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(100)
                        .totalItems(2)
                        .hasNextPage(false)
                        .requestCount(1)
                        .providerGenerationToken("offer-generation-20260802")
                        .items(List.of(
                                Map.of(
                                        "partner_sku", "BAD-SHAPE",
                                        "csku_parent", "Z-BAD",
                                        "title", Map.of("unexpected", "shape")
                                ),
                                Map.of(
                                        "partner_sku", "GOOD",
                                        "csku_parent", "Z-GOOD"
                                )
                        ))
                        .build();

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getItems()).hasSize(2);
        assertThat(result.getValue().getItems().get(0).isWritableProjection()).isFalse();
        assertThat(result.getValue().getItems().get(0).getPresencePartnerSku())
                .isEqualTo("BAD-SHAPE");
        assertThat(result.getValue().getItems().get(0).isAbsenceReconciliationSafe()).isTrue();
        assertThat(result.getValue().getItems().get(1).isWritableProjection()).isTrue();
    }

    @Test
    void unparseableNumericFieldBecomesPresenceOnlyAndDoesNotBlockTheGoodRow() {
        NoonProductInterfaceSmokeProvider rawProvider = (request, page) ->
                NoonInterfacePullPage.builder()
                        .pageNumber(1)
                        .pageSize(100)
                        .totalItems(2)
                        .hasNextPage(false)
                        .requestCount(1)
                        .providerGenerationToken("offer-generation-20260802")
                        .items(List.of(
                                Map.of(
                                        "partner_sku", "BAD-NUMERIC",
                                        "csku_parent", "Z-BAD",
                                        "price", "not-a-number"
                                ),
                                Map.of(
                                        "partner_sku", "GOOD",
                                        "csku_parent", "Z-GOOD",
                                        "price", "12.50"
                                )
                        ))
                        .build();

        ProviderOutcome<SnapshotPage<Dp04ProductSnapshotItem>> result =
                provider(rawProvider).fetchPage(request(1));

        assertThat(result.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(result.getValue().getItems()).hasSize(2);
        assertThat(result.getValue().getItems().get(0).isWritableProjection()).isFalse();
        assertThat(result.getValue().getItems().get(0).getPresencePartnerSku())
                .isEqualTo("BAD-NUMERIC");
        assertThat(result.getValue().getItems().get(0).isAbsenceReconciliationSafe()).isTrue();
        assertThat(result.getValue().getItems().get(1).isWritableProjection()).isTrue();
    }

    private Dp04ProductSnapshotProvider provider(NoonProductInterfaceSmokeProvider rawProvider) {
        NoonPullStoreBindingResolver resolver = mock(NoonPullStoreBindingResolver.class);
        when(resolver.resolve(any(com.nuono.next.noonpull.NoonInterfacePullRequest.class)))
                .thenReturn(new NoonPullStoreBinding(
                        307L, "PRJ108065", "STR108065-NSA", "SA", "108065",
                        "user", "session=redacted"
                ));
        return new Dp04ProductSnapshotProvider(rawProvider, resolver);
    }

    private SnapshotPageRequest request(int pageNo) {
        return SnapshotPageRequest.from(task(), pageNo);
    }

    private DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                4001L,
                OperationCode.DP04,
                "NOON_PARTNER_PRODUCT_LIST",
                307L,
                8001L,
                "PRJ108065",
                null,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-1",
                LocalDateTime.of(2026, 8, 2, 3, 0),
                "complete-snapshot:2026-08-02",
                "SNAPSHOT_FETCH",
                LocalDateTime.of(2026, 8, 2, 3, 0)
        );
        task.setFenceEpoch(1L);
        return task;
    }
}
