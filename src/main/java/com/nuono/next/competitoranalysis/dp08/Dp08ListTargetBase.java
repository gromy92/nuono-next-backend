package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Date-neutral DP08B cohort payload; daily evidence is joined only for a bounded task batch. */
final class Dp08ListTargetBase {
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final String storeCode;
    private final String siteCode;
    private final String noonProductCode;
    private final String stableScopeKey;
    private final List<Dp08ListTarget.Reference> references;
    private final LocalDateTime effectiveFromUtc;

    Dp08ListTargetBase(
            long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            String siteCode,
            String noonProductCode,
            String stableScopeKey,
            List<Dp08ListTarget.Reference> references,
            LocalDateTime effectiveFromUtc
    ) {
        if (ownerUserId < 1L || (logicalStoreId != null && logicalStoreId < 1L)) {
            throw new IllegalArgumentException("DP08B source identities must be positive");
        }
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.storeCode = text(storeCode, "storeCode");
        this.siteCode = text(siteCode, "siteCode");
        this.noonProductCode = text(noonProductCode, "noonProductCode");
        this.stableScopeKey = text(stableScopeKey, "stableScopeKey");
        this.references = List.copyOf(Objects.requireNonNull(references, "references"));
        this.effectiveFromUtc = Objects.requireNonNull(effectiveFromUtc, "effectiveFromUtc");
        Set<String> unique = new HashSet<>();
        for (Dp08ListTarget.Reference reference : this.references) {
            Dp08ListTarget.Reference value = Objects.requireNonNull(reference, "reference");
            if (!unique.add(value.getWatchProductId() + ":" + value.getCompetitorProductId())) {
                throw new IllegalArgumentException("DP08B base contains a duplicate reference");
            }
        }
        if (this.references.isEmpty()) throw new IllegalArgumentException("DP08B base is empty");
    }

    DataPullScope toScope() {
        return new DataPullScope(
                "dp08b", ownerUserId, logicalStoreId,
                ownerUserId + ":" + storeCode + ":" + siteCode,
                null, null, storeCode, siteCode, stableScopeKey
        );
    }

    long ownerUserId() { return ownerUserId; }
    Long logicalStoreId() { return logicalStoreId; }
    String storeCode() { return storeCode; }
    String siteCode() { return siteCode; }
    String noonProductCode() { return noonProductCode; }
    String stableScopeKey() { return stableScopeKey; }
    List<Dp08ListTarget.Reference> references() { return references; }
    LocalDateTime effectiveFromUtc() { return effectiveFromUtc; }

    private static String text(String value, String field) {
        String text = Objects.requireNonNull(value, field);
        if (text.isEmpty() || !text.equals(text.trim())) {
            throw new IllegalArgumentException(field + " must be stable non-blank text");
        }
        return text;
    }
}
