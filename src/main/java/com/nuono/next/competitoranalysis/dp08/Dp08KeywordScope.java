package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable active keyword identity used by one DP-08-A task. */
public final class Dp08KeywordScope {
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final long watchProductId;
    private final long keywordId;
    private final String storeCode;
    private final String siteCode;
    private final String keyword;
    private final String locale;
    private final String stableScopeKey;
    private final List<Dp08TrackedProduct> trackedProducts;

    public Dp08KeywordScope(
            long ownerUserId,
            Long logicalStoreId,
            long watchProductId,
            long keywordId,
            String storeCode,
            String siteCode,
            String keyword,
            String locale,
            String stableScopeKey,
            List<Dp08TrackedProduct> trackedProducts
    ) {
        if (ownerUserId < 1L || watchProductId < 1L || keywordId < 1L) {
            throw new IllegalArgumentException("DP-08-A identities must be positive");
        }
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.watchProductId = watchProductId;
        this.keywordId = keywordId;
        this.storeCode = requireText(storeCode, "storeCode");
        this.siteCode = requireText(siteCode, "siteCode");
        this.keyword = requireText(keyword, "keyword");
        this.locale = requireText(locale, "locale");
        this.stableScopeKey = requireText(stableScopeKey, "stableScopeKey");
        this.trackedProducts = requireTrackedProducts(trackedProducts);
    }

    public DataPullScope toDataPullScope() {
        return new DataPullScope(
                "dp08a",
                ownerUserId,
                logicalStoreId,
                accountKey(),
                null,
                null,
                storeCode,
                siteCode,
                stableScopeKey
        );
    }

    private String accountKey() {
        return ownerUserId + ":" + storeCode + ":" + siteCode;
    }

    private static String requireText(String value, String field) {
        String nonNull = Objects.requireNonNull(value, field);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(field + " must be a stable non-blank value");
        }
        return nonNull;
    }

    private static List<Dp08TrackedProduct> requireTrackedProducts(
            List<Dp08TrackedProduct> values
    ) {
        List<Dp08TrackedProduct> products = List.copyOf(
                Objects.requireNonNull(values, "trackedProducts")
        );
        Set<String> identities = new HashSet<>();
        int selfCount = 0;
        for (Dp08TrackedProduct product : products) {
            Dp08TrackedProduct value = Objects.requireNonNull(product, "trackedProduct");
            String identity = value.getSubjectType() + ":" + value.getNoonProductCode();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate DP-08-A tracked product identity");
            }
            if (value.getSubjectType() == Dp08TrackedProduct.SubjectType.SELF) {
                selfCount++;
            }
        }
        if (selfCount != 1) {
            throw new IllegalArgumentException("DP-08-A requires exactly one SELF product");
        }
        return products;
    }

    public long getOwnerUserId() { return ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public long getWatchProductId() { return watchProductId; }
    public long getKeywordId() { return keywordId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getKeyword() { return keyword; }
    public String getLocale() { return locale; }
    public String getStableScopeKey() { return stableScopeKey; }
    public List<Dp08TrackedProduct> getTrackedProducts() { return trackedProducts; }
}
