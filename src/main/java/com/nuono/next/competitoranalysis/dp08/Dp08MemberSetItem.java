package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDateTime;
import java.util.Objects;

/** One immutable, keyset-ordered member shared by schedule staging and task consumption. */
public class Dp08MemberSetItem {
    private String memberSetId;
    private String memberKey;
    private String memberKind;
    private Long watchProductId;
    private Long competitorProductId;
    private String noonProductCode;
    private LocalDateTime sourceUpdatedAtUtc;

    public static Dp08MemberSetItem keyword(Dp08KeywordScopeRow row) {
        Dp08KeywordScopeRow value = Objects.requireNonNull(row, "row");
        boolean self = "SELF".equals(value.getTrackedProductType());
        Dp08MemberSetItem item = new Dp08MemberSetItem();
        item.memberKind = self ? "SELF" : "COMPETITOR";
        item.watchProductId = value.getWatchProductId();
        item.competitorProductId = value.getCompetitorProductId();
        item.noonProductCode = value.getTrackedNoonProductCode();
        item.sourceUpdatedAtUtc = value.getSourceUpdatedAtUtc();
        item.memberKey = (self ? "0:" : "1:") + fixed(
                self ? 0L : Objects.requireNonNull(item.competitorProductId, "competitorProductId")
        );
        item.validate();
        return item;
    }

    public static Dp08MemberSetItem list(Dp08ListTargetRow row) {
        Dp08ListTargetRow value = Objects.requireNonNull(row, "row");
        Dp08MemberSetItem item = new Dp08MemberSetItem();
        item.memberKind = value.getCompetitorProductId() == null ? "SELF" : "COMPETITOR";
        item.watchProductId = value.getWatchProductId();
        item.competitorProductId = value.getCompetitorProductId();
        item.noonProductCode = value.getNoonProductCode();
        item.sourceUpdatedAtUtc = value.getSourceUpdatedAtUtc();
        item.memberKey = fixed(item.watchProductId) + ":" + fixed(
                item.competitorProductId == null ? 0L : item.competitorProductId
        );
        item.validate();
        return item;
    }

    public Dp08TrackedProduct trackedProduct() {
        return new Dp08TrackedProduct(
                Dp08TrackedProduct.SubjectType.valueOf(memberKind),
                competitorProductId, noonProductCode
        );
    }

    public Dp08ListTarget.Reference reference() {
        return new Dp08ListTarget.Reference(watchProductId, competitorProductId);
    }

    public void validate() {
        if (memberKey == null || memberKey.isEmpty() || memberKey.length() > 64
                || watchProductId == null || watchProductId < 1
                || !("SELF".equals(memberKind) || "COMPETITOR".equals(memberKind))
                || ("SELF".equals(memberKind) && competitorProductId != null)
                || ("COMPETITOR".equals(memberKind)
                    && (competitorProductId == null || competitorProductId < 1))
                || noonProductCode == null || noonProductCode.trim().isEmpty()
                || sourceUpdatedAtUtc == null) {
            throw new IllegalStateException("invalid DP08 member-set item");
        }
    }

    private static String fixed(long value) {
        if (value < 0) throw new IllegalArgumentException("member identity must be non-negative");
        return String.format("%020d", value);
    }

    public String getMemberSetId() { return memberSetId; }
    public void setMemberSetId(String value) { memberSetId = value; }
    public String getMemberKey() { return memberKey; }
    public void setMemberKey(String value) { memberKey = value; }
    public String getMemberKind() { return memberKind; }
    public void setMemberKind(String value) { memberKind = value; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long value) { watchProductId = value; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public void setCompetitorProductId(Long value) { competitorProductId = value; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String value) { noonProductCode = value; }
    public LocalDateTime getSourceUpdatedAtUtc() { return sourceUpdatedAtUtc; }
    public void setSourceUpdatedAtUtc(LocalDateTime value) { sourceUpdatedAtUtc = value; }
}
