package com.nuono.next.competitoranalysis;

import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class CompetitorProductDetailTarget {
    public static final String SELF = "SELF";
    public static final String COMPETITOR = "COMPETITOR";

    private String subjectType;
    private Long competitorProductId;
    private String noonProductCode;
    private String canonicalUrl;

    public CompetitorProductDetailTarget() {
    }

    public static CompetitorProductDetailTarget self(String noonProductCode) {
        CompetitorProductDetailTarget target = new CompetitorProductDetailTarget();
        target.setSubjectType(SELF);
        target.setNoonProductCode(noonProductCode);
        return target;
    }

    public static CompetitorProductDetailTarget competitor(
            Long competitorProductId,
            String noonProductCode,
            String canonicalUrl
    ) {
        CompetitorProductDetailTarget target = new CompetitorProductDetailTarget();
        target.setSubjectType(COMPETITOR);
        target.setCompetitorProductId(competitorProductId);
        target.setNoonProductCode(noonProductCode);
        target.setCanonicalUrl(canonicalUrl);
        return target;
    }

    public boolean isSelf() {
        return SELF.equals(subjectType);
    }

    String identityKey() {
        return text(subjectType) + "|" + text(noonProductCode);
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = upper(subjectType);
    }

    public Long getCompetitorProductId() {
        return competitorProductId;
    }

    public void setCompetitorProductId(Long competitorProductId) {
        this.competitorProductId = competitorProductId;
    }

    public String getNoonProductCode() {
        return noonProductCode;
    }

    public void setNoonProductCode(String noonProductCode) {
        this.noonProductCode = upper(noonProductCode);
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = trim(canonicalUrl);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CompetitorProductDetailTarget)) {
            return false;
        }
        CompetitorProductDetailTarget other = (CompetitorProductDetailTarget) object;
        return Objects.equals(identityKey(), other.identityKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityKey());
    }

    private static String upper(String value) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
