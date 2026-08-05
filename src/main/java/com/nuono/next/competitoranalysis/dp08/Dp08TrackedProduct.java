package com.nuono.next.competitoranalysis.dp08;

import java.util.Locale;
import java.util.Objects;

/** Immutable product identity whose ranking facts belong to one DP-08-A task. */
public final class Dp08TrackedProduct {
    public enum SubjectType {
        SELF,
        COMPETITOR
    }

    private final SubjectType subjectType;
    private final Long competitorProductId;
    private final String noonProductCode;

    public Dp08TrackedProduct(
            SubjectType subjectType,
            Long competitorProductId,
            String noonProductCode
    ) {
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        if (subjectType == SubjectType.SELF && competitorProductId != null) {
            throw new IllegalArgumentException("a SELF tracked product has no competitor id");
        }
        if (subjectType == SubjectType.COMPETITOR
                && (competitorProductId == null || competitorProductId < 1L)) {
            throw new IllegalArgumentException("a COMPETITOR tracked product needs its immutable id");
        }
        this.competitorProductId = competitorProductId;
        String code = Objects.requireNonNull(noonProductCode, "noonProductCode").trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("tracked Noon product code is blank");
        }
        this.noonProductCode = code.toUpperCase(Locale.ROOT);
    }

    public SubjectType getSubjectType() { return subjectType; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public String getNoonProductCode() { return noonProductCode; }
}
