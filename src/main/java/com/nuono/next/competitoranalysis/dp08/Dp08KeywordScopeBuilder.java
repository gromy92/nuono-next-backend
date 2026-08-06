package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Groups one mapper row per tracked product into one immutable keyword task payload. */
final class Dp08KeywordScopeBuilder {
    private final long owner;
    private final Long logicalStoreId;
    private final long watch;
    private final long keywordId;
    private final String store;
    private final String site;
    private final String keyword;
    private final String locale;
    private final String key;
    private final Map<String, Dp08TrackedProduct> products = new LinkedHashMap<>();
    private LocalDateTime latestSourceAtUtc;

    Dp08KeywordScopeBuilder(
            long owner, Long logicalStoreId, long watch, long keywordId,
            String store, String site, String keyword, String locale, String key
    ) {
        this.owner = owner;
        this.logicalStoreId = logicalStoreId;
        this.watch = watch;
        this.keywordId = keywordId;
        this.store = store;
        this.site = site;
        this.keyword = keyword;
        this.locale = locale;
        this.key = key;
    }

    void add(Dp08KeywordScopeRow row) {
        if (!Objects.equals(logicalStoreId, row.getLogicalStoreId())
                || owner != MyBatisDp08ScopeCatalog.positive(row.getOwnerUserId(), "ownerUserId")
                || watch != MyBatisDp08ScopeCatalog.positive(row.getWatchProductId(), "watchProductId")
                || keywordId != MyBatisDp08ScopeCatalog.positive(row.getKeywordId(), "keywordId")
                || !store.equals(MyBatisDp08ScopeCatalog.normalizeUpper(
                        row.getStoreCode(), "storeCode"))
                || !site.equals(MyBatisDp08ScopeCatalog.normalizeUpper(
                        row.getSiteCode(), "siteCode"))
                || !keyword.equals(MyBatisDp08ScopeCatalog.requireText(
                        row.getKeyword(), "keyword"))
                || !locale.equals(MyBatisDp08ScopeCatalog.locale(row.getLocale(), site))) {
            throw new IllegalStateException("DP08A_GROUPED_SCOPE_IDENTITY_DRIFT:" + key);
        }
        Dp08TrackedProduct.SubjectType type = subjectType(row.getTrackedProductType());
        Dp08TrackedProduct product = new Dp08TrackedProduct(
                type, row.getCompetitorProductId(),
                MyBatisDp08ScopeCatalog.normalizeUpper(
                        row.getTrackedNoonProductCode(), "trackedNoonProductCode"
                )
        );
        String productKey = type + ":" + product.getNoonProductCode();
        if (products.putIfAbsent(productKey, product) != null) {
            throw new IllegalStateException("DP08A_DUPLICATE_TRACKED_PRODUCT:" + key);
        }
        latestSourceAtUtc = MyBatisDp08ScopeCatalog.latest(
                latestSourceAtUtc, row.getSourceUpdatedAtUtc()
        );
    }

    Dp08KeywordScope build() {
        return new Dp08KeywordScope(
                owner, logicalStoreId, watch, keywordId, store, site, keyword, locale, key,
                new ArrayList<>(products.values())
        );
    }

    LocalDateTime effectiveFromUtc() {
        return MyBatisDp08ScopeCatalog.requireTime(latestSourceAtUtc);
    }

    private Dp08TrackedProduct.SubjectType subjectType(String value) {
        try {
            return Dp08TrackedProduct.SubjectType.valueOf(
                    MyBatisDp08ScopeCatalog.normalizeUpper(value, "trackedProductType")
            );
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("DP08A_TRACKED_PRODUCT_TYPE_INVALID:" + key, invalid);
        }
    }
}
