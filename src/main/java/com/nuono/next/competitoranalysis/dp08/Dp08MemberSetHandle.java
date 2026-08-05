package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Fixed-size immutable task handle for an arbitrarily large DP08 member set. */
public final class Dp08MemberSetHandle {
    private final OperationCode operationCode;
    private final String memberSetId;
    private final long memberCount;
    private final String memberOrderedSha256;
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

    Dp08MemberSetHandle(
            Dp08MemberSetBase base,
            String memberSetId,
            long memberCount,
            String memberOrderedSha256
    ) {
        Dp08MemberSetBase value = Objects.requireNonNull(base, "base");
        this.operationCode = value.operationCode();
        this.memberSetId = digest(memberSetId, "memberSetId");
        if (memberCount < 1L) {
            throw new IllegalArgumentException("memberCount must be positive");
        }
        this.memberCount = memberCount;
        this.memberOrderedSha256 = digest(
                memberOrderedSha256,
                "memberOrderedSha256"
        );
        this.ownerUserId = value.ownerUserId();
        this.logicalStoreId = value.logicalStoreId();
        this.watchProductId = value.watchProductId();
        this.keywordId = value.keywordId();
        this.storeCode = value.storeCode();
        this.siteCode = value.siteCode();
        this.keyword = value.keyword();
        this.locale = value.locale();
        this.noonProductCode = value.noonProductCode();
        this.representativeWatchProductId = value.representativeWatchProductId();
        this.representativeCompetitorProductId = value.representativeCompetitorProductId();
        this.stableScopeKey = value.stableScopeKey();
    }

    Dp08MemberSetBase base() {
        return new Dp08MemberSetBase(
                operationCode,
                ownerUserId,
                logicalStoreId,
                watchProductId,
                keywordId,
                storeCode,
                siteCode,
                keyword,
                locale,
                noonProductCode,
                representativeWatchProductId,
                representativeCompetitorProductId,
                stableScopeKey
        );
    }

    public DataPullScope toDataPullScope() {
        return new DataPullScope(
                operationCode == OperationCode.DP08A ? "dp08a" : "dp08b",
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

    public Dp08KeywordScope keywordProviderScope() {
        requireOperation(OperationCode.DP08A);
        return new Dp08KeywordScope(
                ownerUserId,
                logicalStoreId,
                watchProductId,
                keywordId,
                storeCode,
                siteCode,
                keyword,
                locale,
                stableScopeKey,
                List.of(new Dp08TrackedProduct(
                        Dp08TrackedProduct.SubjectType.SELF,
                        null,
                        noonProductCode
                ))
        );
    }

    public Dp08ListTarget listProviderTarget(LocalDate factDate, boolean required) {
        requireOperation(OperationCode.DP08B);
        return listTarget(
                factDate,
                required,
                List.of(new Dp08ListTarget.Reference(
                        representativeWatchProductId,
                        representativeCompetitorProductId
                ))
        );
    }

    public Dp08ListTarget listTarget(
            LocalDate factDate,
            boolean required,
            List<Dp08ListTarget.Reference> references
    ) {
        requireOperation(OperationCode.DP08B);
        return new Dp08ListTarget(
                ownerUserId,
                logicalStoreId,
                storeCode,
                siteCode,
                noonProductCode,
                stableScopeKey,
                factDate,
                required,
                references
        );
    }

    private void requireOperation(OperationCode expected) {
        if (operationCode != expected) {
            throw new IllegalStateException("DP08 handle operation drift");
        }
    }

    private String digest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }

    public OperationCode getOperationCode() { return operationCode; }
    public String getMemberSetId() { return memberSetId; }
    public long getMemberCount() { return memberCount; }
    public String getMemberOrderedSha256() { return memberOrderedSha256; }
    public long getOwnerUserId() { return ownerUserId; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public Long getWatchProductId() { return watchProductId; }
    public Long getKeywordId() { return keywordId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getKeyword() { return keyword; }
    public String getLocale() { return locale; }
    public String getNoonProductCode() { return noonProductCode; }
    public String getStableScopeKey() { return stableScopeKey; }
}
