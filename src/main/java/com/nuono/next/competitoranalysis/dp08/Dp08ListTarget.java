package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One owner/store/site/code union target and every current watch-product reference. */
public final class Dp08ListTarget {
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final String noonProductCode;
    private final String stableScopeKey;
    private final LocalDate factDate;
    private final boolean exactSearchRequired;
    private final List<Reference> references;

    public Dp08ListTarget(
            long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            String noonProductCode,
            String stableScopeKey,
            LocalDate factDate,
            boolean exactSearchRequired,
            List<Reference> references
    ) {
        if (ownerUserId < 1L) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = requireText(storeCode, "storeCode");
        this.siteCode = requireText(siteCode, "siteCode");
        this.noonProductCode = requireText(noonProductCode, "noonProductCode");
        this.stableScopeKey = requireText(stableScopeKey, "stableScopeKey");
        this.factDate = Objects.requireNonNull(factDate, "factDate");
        this.exactSearchRequired = exactSearchRequired;
        this.references = List.copyOf(Objects.requireNonNull(references, "references"));
        if (this.references.isEmpty()) {
            throw new IllegalArgumentException("a list target requires at least one current reference");
        }
        Set<String> identities = new HashSet<>();
        for (Reference reference : this.references) {
            Reference value = Objects.requireNonNull(reference, "reference");
            String identity = value.watchProductId + ":" + value.competitorProductId;
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("a list target has duplicate immutable references");
            }
        }
    }

    public DataPullScope toDataPullScope() {
        return new DataPullScope(
                "dp08b",
                ownerUserId,
                logicalStoreId,
                ownerUserId + ":" + storeCode + ":" + siteCode,
                null,
                null,
                storeCode,
                siteCode,
                stableScopeKey
        );
    }

    private static String requireText(String value, String field) {
        String nonNull = Objects.requireNonNull(value, field);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(field + " must be a stable non-blank value");
        }
        return nonNull;
    }

    public long getOwnerUserId() { return ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getNoonProductCode() { return noonProductCode; }
    public String getStableScopeKey() { return stableScopeKey; }
    public LocalDate getFactDate() { return factDate; }
    public boolean isExactSearchRequired() { return exactSearchRequired; }
    public List<Reference> getReferences() { return references; }

    public static final class Reference {
        private final long watchProductId;
        private final Long competitorProductId;

        public Reference(long watchProductId, Long competitorProductId) {
            if (watchProductId < 1L || (competitorProductId != null && competitorProductId < 1L)) {
                throw new IllegalArgumentException("DP-08-B reference identities must be positive");
            }
            this.watchProductId = watchProductId;
            this.competitorProductId = competitorProductId;
        }

        public long getWatchProductId() { return watchProductId; }
        public Long getCompetitorProductId() { return competitorProductId; }
    }
}
