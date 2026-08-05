package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import com.nuono.next.datapull.scope.DataPullScopeBindingStore;
import com.nuono.next.infrastructure.mapper.Dp08ScopeMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisDp08ScopeCatalogTest {

    @Test
    void reconcilesAStableTargetEvenWhenTodayNeedsNoExactCall() {
        LocalDate factDate = LocalDate.of(2026, 8, 2);
        Dp08ListTargetRow completeToday = new Dp08ListTargetRow();
        completeToday.setOwnerUserId(307L);
        completeToday.setLogicalStoreId(108065L);
        completeToday.setStoreCode("STR108065-NSA");
        completeToday.setSiteCode("SA");
        completeToday.setNoonProductCode("N700001");
        completeToday.setWatchProductId(81L);
        completeToday.setRankedToday(true);
        completeToday.setCompleteTitlesToday(true);
        completeToday.setSourceUpdatedAtUtc(LocalDateTime.of(2026, 8, 1, 0, 0));
        RecordingBindingStore bindings = new RecordingBindingStore();
        MyBatisDp08ScopeCatalog catalog = new MyBatisDp08ScopeCatalog(
                new FixedScopeMapper(List.of(), List.of(completeToday)),
                bindings,
                new ObjectMapper().findAndRegisterModules()
        );

        List<DataPullScope> scopes = catalog.listListTargetScopes(factDate);

        assertThat(scopes).hasSize(1);
        assertThat(bindings.operation).isNull();
        Dp08ListTargetPreparation preparation =
                catalog.prepareListTargetScopesForEnqueue(factDate);
        preparation.completeAfterAdmission(admit(preparation.getScopes()));
        assertThat(bindings.operation).isEqualTo(OperationCode.DP08B);
        JsonNode payload = read(bindings.candidates.get(0).getPayload());
        assertThat(payload.path("exactSearchRequired").booleanValue()).isFalse();
        assertThat(payload.path("factDate").textValue()).isEqualTo(factDate.toString());
    }

    @Test
    void duplicateActiveListReferenceFailsClosed() {
        Dp08ListTargetRow row = targetRow();
        MyBatisDp08ScopeCatalog catalog = new MyBatisDp08ScopeCatalog(
                new FixedScopeMapper(List.of(), List.of(row, row)),
                new RecordingBindingStore(),
                new ObjectMapper().findAndRegisterModules()
        );

        assertThatThrownBy(() -> catalog.listListTargetScopes(LocalDate.of(2026, 8, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DP08B_DUPLICATE_ACTIVE_REFERENCE");
    }

    @Test
    void keywordScopePayloadFreezesTheSelfAndConfirmedCompetitorCohort() {
        Dp08KeywordScopeRow self = keywordRow("SELF", null, "N700001");
        Dp08KeywordScopeRow competitor = keywordRow("COMPETITOR", 201L, "N700002");
        RecordingBindingStore bindings = new RecordingBindingStore();
        MyBatisDp08ScopeCatalog catalog = new MyBatisDp08ScopeCatalog(
                new FixedScopeMapper(List.of(self, competitor), List.of()),
                bindings,
                new ObjectMapper().findAndRegisterModules()
        );

        assertThat(catalog.listKeywordScopes()).hasSize(1);
        assertThat(bindings.operation).isNull();
        com.nuono.next.datapull.orchestration.DataPullScopePreparation preparation =
                catalog.prepareKeywordScopesForEnqueue();
        preparation.completeAfterAdmission(admit(preparation.getScopes()));
        JsonNode tracked = read(bindings.candidates.get(0).getPayload()).path("trackedProducts");
        assertThat(tracked.size()).isEqualTo(2);
        assertThat(tracked.toString()).contains("N700001", "N700002", "201");
    }

    @Test
    void duplicateTrackedProductInsideOneKeywordScopeFailsClosed() {
        Dp08KeywordScopeRow row = keywordRow("SELF", null, "N700001");
        MyBatisDp08ScopeCatalog catalog = new MyBatisDp08ScopeCatalog(
                new FixedScopeMapper(List.of(row, row), List.of()),
                new RecordingBindingStore(),
                new ObjectMapper().findAndRegisterModules()
        );

        assertThatThrownBy(catalog::listKeywordScopes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DP08A_DUPLICATE_TRACKED_PRODUCT");
    }

    @Test
    void listTargetObservedAfterTheSlotIsBoundButNotEligibleForThatHistoricalTask() {
        Dp08ListTargetRow row = targetRow();
        row.setSourceUpdatedAtUtc(LocalDateTime.of(2026, 8, 2, 0, 30));
        RecordingBindingStore bindings = new RecordingBindingStore();
        MyBatisDp08ScopeCatalog catalog = new MyBatisDp08ScopeCatalog(
                new FixedScopeMapper(List.of(), List.of(row)),
                bindings,
                new ObjectMapper().findAndRegisterModules()
        );

        Dp08ListTargetPreparation preparation = catalog.prepareListTargetScopesForEnqueue(
                LocalDate.of(2026, 8, 2)
        );
        preparation.completeAfterAdmission(admit(preparation.getScopes()));

        assertThat(preparation.isEligibleAt(
                preparation.getScopes().get(0).getStableScopeKey(),
                LocalDateTime.of(2026, 8, 1, 22, 0)
        )).isFalse();
        assertThat(bindings.candidates.get(0).getEffectiveFromUtc())
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 0, 30));
    }

    private Dp08KeywordScopeRow keywordRow(
            String subjectType,
            Long competitorProductId,
            String noonProductCode
    ) {
        Dp08KeywordScopeRow row = new Dp08KeywordScopeRow();
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(108065L);
        row.setWatchProductId(81L);
        row.setKeywordId(91L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setKeyword("paper");
        row.setLocale("en-SA");
        row.setTrackedProductType(subjectType);
        row.setCompetitorProductId(competitorProductId);
        row.setTrackedNoonProductCode(noonProductCode);
        row.setSourceUpdatedAtUtc(LocalDateTime.of(2026, 8, 1, 0, 0));
        return row;
    }

    private Dp08ListTargetRow targetRow() {
        Dp08ListTargetRow row = new Dp08ListTargetRow();
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(108065L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setNoonProductCode("N700001");
        row.setWatchProductId(81L);
        row.setRankedToday(false);
        row.setCompleteTitlesToday(false);
        row.setSourceUpdatedAtUtc(LocalDateTime.of(2026, 8, 1, 0, 0));
        return row;
    }

    private static JsonNode read(String payload) {
        try {
            return new ObjectMapper().readTree(payload);
        } catch (Exception invalid) {
            throw new AssertionError(invalid);
        }
    }

    private static List<AdmittedDataPullScope> admit(List<DataPullScope> scopes) {
        LocalDateTime admittedAt = LocalDateTime.of(2026, 8, 1, 0, 0);
        return scopes.stream()
                .map(scope -> new AdmittedDataPullScope(
                        scope,
                        DataPullScopeAdmission.cutoverExisting(
                                scope, "cutover-20260801", admittedAt
                        )
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    private static final class RecordingBindingStore implements DataPullScopeBindingStore {
        private OperationCode operation;
        private List<DataPullScopeBindingCandidate> candidates = List.of();

        @Override
        public List<DataPullScopeBindingEpoch> reconcileCurrent(
                OperationCode operationCode,
                List<DataPullScopeBindingCandidate> currentBindings
        ) {
            operation = operationCode;
            candidates = List.copyOf(currentBindings);
            List<DataPullScopeBindingEpoch> epochs = new ArrayList<>();
            for (DataPullScopeBindingCandidate candidate : candidates) {
                epochs.add(DataPullScopeBindingEpoch.open(
                        candidate, candidate.getEffectiveFromUtc()
                ));
            }
            return List.copyOf(epochs);
        }
    }

    private static final class FixedScopeMapper implements Dp08ScopeMapper {
        private final List<Dp08KeywordScopeRow> keywords;
        private final List<Dp08ListTargetRow> targets;

        private FixedScopeMapper(
                List<Dp08KeywordScopeRow> keywords,
                List<Dp08ListTargetRow> targets
        ) {
            this.keywords = List.copyOf(keywords);
            this.targets = List.copyOf(targets);
        }

        @Override
        public List<Dp08KeywordScopeRow> listActiveKeywordScopes() {
            return keywords;
        }

        @Override
        public List<Dp08ListTargetRow> listActiveListTargetRows(LocalDate factDate) {
            return targets;
        }
    }
}
