package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.Objects;

/** Canonical non-member identity retained while a nested source scan is incomplete. */
final class Dp08MemberSetBase {
    private final OperationCode operationCode;
    private final long ownerUserId;
    private final Long logicalStoreId;
    private final Long watchProductId;
    private final Long keywordId;
    private final String storeCode;
    private final String siteCode;
    private final String keyword;
    private final String locale;
    private final String noonProductCode;
    private final long representativeWatchProductId;
    private final Long representativeCompetitorProductId;
    private final String stableScopeKey;

    Dp08MemberSetBase(
            OperationCode operationCode,
            long ownerUserId,
            Long logicalStoreId,
            Long watchProductId,
            Long keywordId,
            String storeCode,
            String siteCode,
            String keyword,
            String locale,
            String noonProductCode,
            long representativeWatchProductId,
            Long representativeCompetitorProductId,
            String stableScopeKey
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        if (ownerUserId < 1L || representativeWatchProductId < 1L) {
            throw new IllegalArgumentException("bad DP08 base identity");
        }
        this.ownerUserId = ownerUserId;
        this.logicalStoreId = logicalStoreId;
        this.watchProductId = watchProductId;
        this.keywordId = keywordId;
        this.storeCode = text(storeCode, "storeCode");
        this.siteCode = text(siteCode, "siteCode");
        this.keyword = keyword;
        this.locale = locale;
        this.noonProductCode = text(noonProductCode, "noonProductCode");
        this.representativeWatchProductId = representativeWatchProductId;
        this.representativeCompetitorProductId = representativeCompetitorProductId;
        this.stableScopeKey = text(stableScopeKey, "stableScopeKey");
        requireOperationShape();
    }

    private void requireOperationShape() {
        if (operationCode == OperationCode.DP08A
                && (watchProductId == null
                || keywordId == null
                || keyword == null
                || locale == null
                || representativeCompetitorProductId != null)) {
            throw new IllegalArgumentException("invalid DP08A base");
        }
        if (operationCode == OperationCode.DP08B
                && (watchProductId != null
                || keywordId != null
                || keyword != null
                || locale != null)) {
            throw new IllegalArgumentException("invalid DP08B base");
        }
    }

    private String text(String value, String field) {
        String required = Objects.requireNonNull(value, field);
        if (required.isEmpty() || !required.equals(required.trim())) {
            throw new IllegalArgumentException(field);
        }
        return required;
    }

    OperationCode operationCode() { return operationCode; }
    long ownerUserId() { return ownerUserId; }
    Long logicalStoreId() { return logicalStoreId; }
    Long watchProductId() { return watchProductId; }
    Long keywordId() { return keywordId; }
    String storeCode() { return storeCode; }
    String siteCode() { return siteCode; }
    String keyword() { return keyword; }
    String locale() { return locale; }
    String noonProductCode() { return noonProductCode; }
    long representativeWatchProductId() { return representativeWatchProductId; }
    Long representativeCompetitorProductId() { return representativeCompetitorProductId; }
    String stableScopeKey() { return stableScopeKey; }
}
