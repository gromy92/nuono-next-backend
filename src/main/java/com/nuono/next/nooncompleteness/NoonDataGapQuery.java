package com.nuono.next.nooncompleteness;

import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class NoonDataGapQuery {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonDataCategory category;
    private NoonDataGapStatus status;
    private String failureType;
    private Boolean retryable;

    public static NoonDataGapQuery fromRequest(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String category,
            String status,
            String failureType,
            Boolean retryable
    ) {
        NoonDataGapQuery query = new NoonDataGapQuery();
        query.setOwnerUserId(ownerUserId);
        query.setStoreCode(storeCode);
        query.setSiteCode(siteCode);
        query.setCategory(NoonDataCompletenessQuery.parseEnum(NoonDataCategory.class, category));
        query.setStatus(NoonDataCompletenessQuery.parseEnum(NoonDataGapStatus.class, status));
        query.setFailureType(StringUtils.hasText(failureType) ? failureType.trim().toLowerCase(Locale.ROOT) : null);
        query.setRetryable(retryable);
        return query;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = normalize(storeCode); }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = normalize(siteCode); }
    public NoonDataCategory getCategory() { return category; }
    public void setCategory(NoonDataCategory category) { this.category = category; }
    public NoonDataGapStatus getStatus() { return status; }
    public void setStatus(NoonDataGapStatus status) { this.status = status; }
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }
    public Boolean getRetryable() { return retryable; }
    public void setRetryable(Boolean retryable) { this.retryable = retryable; }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NoonDataGapQuery)) {
            return false;
        }
        NoonDataGapQuery that = (NoonDataGapQuery) o;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && category == that.category
                && status == that.status
                && Objects.equals(failureType, that.failureType)
                && Objects.equals(retryable, that.retryable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, storeCode, siteCode, category, status, failureType, retryable);
    }
}

