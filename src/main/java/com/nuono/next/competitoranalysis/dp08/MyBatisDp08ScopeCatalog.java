package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeKey;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingStore;
import com.nuono.next.infrastructure.mapper.Dp08ScopeMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Groups current mapper rows into opaque, deterministic DP-08 runtime scopes. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisDp08ScopeCatalog implements Dp08ScopeCatalog {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final Dp08ScopeMapper mapper;
    private final DataPullScopeBindingStore bindingStore;
    private final Dp08ScopeSnapshotCodec snapshotCodec;

    public MyBatisDp08ScopeCatalog(
            Dp08ScopeMapper mapper,
            DataPullScopeBindingStore bindingStore,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.bindingStore = Objects.requireNonNull(bindingStore, "bindingStore");
        this.snapshotCodec = new Dp08ScopeSnapshotCodec(objectMapper);
    }

    @Override
    public List<DataPullScope> listKeywordScopes() {
        ScopeCohort<Dp08KeywordScope> cohort = keywordScopes();
        return keywordScopeValues(cohort);
    }

    @Override
    public DataPullScopePreparation prepareKeywordScopesForEnqueue() {
        ScopeCohort<Dp08KeywordScope> cohort = keywordScopes();
        return DataPullScopePreparation.deferred(
                keywordScopeValues(cohort),
                ignored -> bindingStore.reconcileCurrent(OperationCode.DP08A, cohort.bindings)
        );
    }

    private List<DataPullScope> keywordScopeValues(ScopeCohort<Dp08KeywordScope> cohort) {
        List<DataPullScope> scopes = new ArrayList<>();
        for (Dp08KeywordScope scope : cohort.scopes.values()) {
            scopes.add(scope.toDataPullScope());
        }
        return List.copyOf(scopes);
    }

    @Override
    public List<DataPullScope> listListTargetScopes(LocalDate factDate) {
        ScopeCohort<Dp08ListTarget> cohort = listTargets(factDate);
        return listTargetScopeValues(cohort);
    }

    @Override
    public Dp08ListTargetPreparation prepareListTargetScopesForEnqueue(LocalDate factDate) {
        ScopeCohort<Dp08ListTarget> cohort = listTargets(factDate);
        DataPullScopePreparation preparation = DataPullScopePreparation.deferred(
                listTargetScopeValues(cohort),
                ignored -> bindingStore.reconcileCurrent(OperationCode.DP08B, cohort.bindings)
        );
        return Dp08ListTargetPreparation.binding(preparation, cohort.bindings);
    }

    private List<DataPullScope> listTargetScopeValues(ScopeCohort<Dp08ListTarget> cohort) {
        List<DataPullScope> scopes = new ArrayList<>();
        for (Dp08ListTarget target : cohort.scopes.values()) {
            // Reconcile every current stable target identity. Whether the exact list call is
            // required is evaluated against each task's own fact date during advance; filtering
            // on today's evidence here would permanently hide an older missed window.
            scopes.add(target.toDataPullScope());
        }
        return List.copyOf(scopes);
    }

    private ScopeCohort<Dp08KeywordScope> keywordScopes() {
        Map<String, Dp08KeywordScopeBuilder> grouped = new LinkedHashMap<>();
        for (Dp08KeywordScopeRow row : mapper.listActiveKeywordScopes()) {
            Dp08KeywordScopeRow source = Objects.requireNonNull(row, "DP-08-A source row");
            long owner = positive(source.getOwnerUserId(), "ownerUserId");
            long watch = positive(source.getWatchProductId(), "watchProductId");
            long keywordId = positive(source.getKeywordId(), "keywordId");
            String store = normalizeUpper(source.getStoreCode(), "storeCode");
            String site = normalizeUpper(source.getSiteCode(), "siteCode");
            String key = DataPullScopeKey.from(
                    "dp08a", Long.toString(owner), store, site,
                    Long.toString(watch), Long.toString(keywordId)
            );
            grouped.computeIfAbsent(key, ignored -> new Dp08KeywordScopeBuilder(
                    owner, source.getLogicalStoreId(), watch, keywordId, store, site,
                    requireText(source.getKeyword(), "keyword"),
                    locale(source.getLocale(), site), key
            )).add(source);
        }
        Map<String, Dp08KeywordScope> result = new LinkedHashMap<>();
        List<DataPullScopeBindingCandidate> bindings = new ArrayList<>();
        grouped.forEach((key, builder) -> {
            Dp08KeywordScope scope = builder.build();
            result.put(key, scope);
            bindings.add(new DataPullScopeBindingCandidate(
                    OperationCode.DP08A, key, Dp08ScopeSnapshotCodec.KEYWORD_V1,
                    snapshotCodec.encode(scope), builder.effectiveFromUtc()
            ));
        });
        return new ScopeCohort<>(result, bindings);
    }

    private ScopeCohort<Dp08ListTarget> listTargets(LocalDate factDate) {
        LocalDate date = Objects.requireNonNull(factDate, "factDate");
        Map<String, TargetBuilder> grouped = new LinkedHashMap<>();
        for (Dp08ListTargetRow row : mapper.listActiveListTargetRows(date)) {
            Dp08ListTargetRow source = Objects.requireNonNull(row, "DP-08-B source row");
            long owner = positive(source.getOwnerUserId(), "ownerUserId");
            String store = normalizeUpper(source.getStoreCode(), "storeCode");
            String site = normalizeUpper(source.getSiteCode(), "siteCode");
            String code = normalizeUpper(source.getNoonProductCode(), "noonProductCode");
            String key = DataPullScopeKey.from(
                    "dp08b", Long.toString(owner), store, site, code
            );
            TargetBuilder builder = grouped.computeIfAbsent(
                    key,
                    ignored -> new TargetBuilder(
                            owner, source.getLogicalStoreId(), store, site, code, key, date
                    )
            );
            builder.add(source);
        }
        Map<String, Dp08ListTarget> targets = new LinkedHashMap<>();
        List<DataPullScopeBindingCandidate> bindings = new ArrayList<>();
        grouped.forEach((key, builder) -> {
            Dp08ListTarget target = builder.build();
            targets.put(key, target);
            bindings.add(new DataPullScopeBindingCandidate(
                    OperationCode.DP08B, key, Dp08ScopeSnapshotCodec.LIST_TARGET_V1,
                    snapshotCodec.encode(target), builder.effectiveFromUtc()
            ));
        });
        return new ScopeCohort<>(targets, bindings);
    }

    static String locale(String value, String site) {
        return value == null || value.trim().isEmpty()
                ? "en-" + site
                : value.trim();
    }

    static String normalizeUpper(String value, String field) {
        return requireText(value, field).toUpperCase(Locale.ROOT);
    }

    static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("DP-08 scope row has blank " + field);
        }
        return value.trim();
    }

    static long positive(Long value, String field) {
        if (value == null || value < 1L) {
            throw new IllegalStateException("DP-08 scope row has invalid " + field);
        }
        return value;
    }

    static LocalDateTime requireTime(LocalDateTime value) {
        return Objects.requireNonNull(value, "DP-08 source effective time");
    }

    private static final class TargetBuilder {
        private final long owner;
        private final Long logicalStoreId;
        private final String store;
        private final String site;
        private final String code;
        private final String key;
        private final LocalDate factDate;
        private final Map<String, Dp08ListTarget.Reference> references = new LinkedHashMap<>();
        private boolean exactSearchRequired;
        private LocalDateTime latestSourceAtUtc;

        private TargetBuilder(
                long owner, Long logicalStoreId, String store, String site,
                String code, String key, LocalDate factDate
        ) {
            this.owner = owner;
            this.logicalStoreId = logicalStoreId;
            this.store = store;
            this.site = site;
            this.code = code;
            this.key = key;
            this.factDate = factDate;
        }

        private void add(Dp08ListTargetRow row) {
            if (!Objects.equals(logicalStoreId, row.getLogicalStoreId())) {
                throw new IllegalStateException("DP-08-B target identity spans logical stores");
            }
            long watch = positive(row.getWatchProductId(), "watchProductId");
            Long competitor = row.getCompetitorProductId();
            String referenceKey = watch + ":" + (competitor == null ? "SELF" : competitor);
            if (references.putIfAbsent(
                    referenceKey,
                    new Dp08ListTarget.Reference(watch, competitor)
            ) != null) {
                throw new IllegalStateException(
                        "DP08B_DUPLICATE_ACTIVE_REFERENCE:" + key + ":" + referenceKey
                );
            }
            exactSearchRequired |= !Boolean.TRUE.equals(row.getRankedToday())
                    || !Boolean.TRUE.equals(row.getCompleteTitlesToday());
            latestSourceAtUtc = latest(
                    latestSourceAtUtc,
                    row.getSourceUpdatedAtUtc(),
                    row.getRankEvidenceUpdatedAtUtc(),
                    row.getTitleEvidenceUpdatedAtUtc()
            );
        }

        private Dp08ListTarget build() {
            return new Dp08ListTarget(
                    owner, logicalStoreId, store, site, code, key, factDate,
                    exactSearchRequired, new ArrayList<>(references.values())
            );
        }

        private LocalDateTime effectiveFromUtc() {
            LocalDateTime scheduledAtUtc = factDate.atTime(2, 0)
                    .atZone(SHANGHAI).withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
            LocalDateTime sourceAt = requireTime(latestSourceAtUtc);
            return sourceAt.isAfter(scheduledAtUtc) ? sourceAt : scheduledAtUtc;
        }
    }

    static LocalDateTime latest(LocalDateTime current, LocalDateTime... values) {
        LocalDateTime result = current;
        for (LocalDateTime value : values) {
            if (value != null && (result == null || value.isAfter(result))) {
                result = value;
            }
        }
        return result;
    }

    private static final class ScopeCohort<T> {
        private final Map<String, T> scopes;
        private final List<DataPullScopeBindingCandidate> bindings;

        private ScopeCohort(
                Map<String, T> scopes,
                List<DataPullScopeBindingCandidate> bindings
        ) {
            this.scopes = Collections.unmodifiableMap(new LinkedHashMap<>(scopes));
            this.bindings = List.copyOf(bindings);
        }
    }
}
