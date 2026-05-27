package com.nuono.next.nooncompleteness;

import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class NoonDataCompletenessQuery {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonDataCategory category;
    private NoonDataLatestStatus latestStatus;
    private NoonDataHistoryStatus historyStatus;

    public static NoonDataCompletenessQuery fromRequest(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String category,
            String latestStatus,
            String historyStatus
    ) {
        NoonDataCompletenessQuery query = new NoonDataCompletenessQuery();
        query.setOwnerUserId(ownerUserId);
        query.setStoreCode(normalizeStore(storeCode));
        query.setSiteCode(normalizeSite(siteCode));
        query.setCategory(parseEnum(NoonDataCategory.class, category));
        query.setLatestStatus(parseEnum(NoonDataLatestStatus.class, latestStatus));
        query.setHistoryStatus(parseEnum(NoonDataHistoryStatus.class, historyStatus));
        return query;
    }

    private static String normalizeStore(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String normalizeSite(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = normalizeStore(storeCode); }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = normalizeSite(siteCode); }
    public NoonDataCategory getCategory() { return category; }
    public void setCategory(NoonDataCategory category) { this.category = category; }
    public NoonDataLatestStatus getLatestStatus() { return latestStatus; }
    public void setLatestStatus(NoonDataLatestStatus latestStatus) { this.latestStatus = latestStatus; }
    public NoonDataHistoryStatus getHistoryStatus() { return historyStatus; }
    public void setHistoryStatus(NoonDataHistoryStatus historyStatus) { this.historyStatus = historyStatus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NoonDataCompletenessQuery)) {
            return false;
        }
        NoonDataCompletenessQuery that = (NoonDataCompletenessQuery) o;
        return Objects.equals(ownerUserId, that.ownerUserId)
                && Objects.equals(storeCode, that.storeCode)
                && Objects.equals(siteCode, that.siteCode)
                && category == that.category
                && latestStatus == that.latestStatus
                && historyStatus == that.historyStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerUserId, storeCode, siteCode, category, latestStatus, historyStatus);
    }
}

