package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopeBindingDigest;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable scope-level fact that separates source eligibility from scheduler observation time. */
public final class DataPullScopeAdmission {

    public enum Kind {
        CUTOVER_EXISTING,
        POST_CUTOVER
    }

    private String scopeKey;
    private String scopeNamespace;
    private Long ownerUserId;
    private Long logicalStoreId;
    private String accountKey;
    private String egressKey;
    private String projectCode;
    private String storeCode;
    private String siteCode;
    private Kind admissionKind;
    private LocalDateTime firstEligibleAtUtc;
    private String sourceBindingSha256;
    private String cutoverKey;
    private LocalDateTime admittedAtUtc;

    public DataPullScopeAdmission() {
        // MyBatis bean constructor.
    }

    public static DataPullScopeAdmission cutoverExisting(
            DataPullScope scope,
            String cutoverKey,
            LocalDateTime admittedAtUtc
    ) {
        return create(scope, Kind.CUTOVER_EXISTING, null, cutoverKey, admittedAtUtc);
    }

    public static DataPullScopeAdmission postCutover(
            DataPullScope scope,
            LocalDateTime firstEligibleAtUtc,
            String cutoverKey,
            LocalDateTime admittedAtUtc
    ) {
        return create(
                scope,
                Kind.POST_CUTOVER,
                Objects.requireNonNull(firstEligibleAtUtc, "firstEligibleAtUtc"),
                cutoverKey,
                admittedAtUtc
        );
    }

    private static DataPullScopeAdmission create(
            DataPullScope scope,
            Kind kind,
            LocalDateTime firstEligibleAtUtc,
            String cutoverKey,
            LocalDateTime admittedAtUtc
    ) {
        DataPullScope source = Objects.requireNonNull(scope, "scope");
        DataPullScopeAdmission result = new DataPullScopeAdmission();
        result.scopeKey = source.getStableScopeKey();
        result.scopeNamespace = source.getNamespace();
        result.ownerUserId = source.getOwnerUserId();
        result.logicalStoreId = source.getLogicalStoreId();
        result.accountKey = source.getAccountKey();
        result.egressKey = source.getEgressKey();
        result.projectCode = source.getProjectCode();
        result.storeCode = source.getStoreCode();
        result.siteCode = source.getSiteCode();
        result.admissionKind = Objects.requireNonNull(kind, "kind");
        result.firstEligibleAtUtc = firstEligibleAtUtc;
        result.sourceBindingSha256 = DataPullScopeBindingDigest.sha256(source);
        result.cutoverKey = cutoverKey;
        result.admittedAtUtc = admittedAtUtc;
        result.validate();
        return result;
    }

    public void validate() {
        DataPullScope snapshot = toScope();
        Objects.requireNonNull(admissionKind, "admissionKind");
        DataPullScheduleAnchor.requireIdentity(cutoverKey, "cutoverKey", 96);
        requireDigest(sourceBindingSha256, "sourceBindingSha256");
        requireMillisecond(admittedAtUtc, "admittedAtUtc");
        if (admissionKind == Kind.CUTOVER_EXISTING) {
            if (firstEligibleAtUtc != null) {
                throw new IllegalStateException("cutover-existing admission cannot invent eligibility");
            }
        } else {
            requireMillisecond(firstEligibleAtUtc, "firstEligibleAtUtc");
            if (admittedAtUtc.isBefore(firstEligibleAtUtc)) {
                throw new IllegalStateException("admission cannot predate source eligibility");
            }
        }
        if (!DataPullScopeBindingDigest.sha256(snapshot).equals(sourceBindingSha256)) {
            throw new IllegalStateException("scope admission identity digest does not match snapshot");
        }
    }

    public boolean matchesSource(DataPullScope source) {
        DataPullScope value = Objects.requireNonNull(source, "source");
        validate();
        return scopeKey.equals(value.getStableScopeKey())
                && sourceBindingSha256.equals(DataPullScopeBindingDigest.sha256(value));
    }

    public DataPullScope toScope() {
        Long owner = Objects.requireNonNull(ownerUserId, "ownerUserId");
        return new DataPullScope(
                DataPullScheduleAnchor.requireIdentity(
                        scopeNamespace, "scopeNamespace", 32
                ),
                owner,
                logicalStoreId,
                accountKey,
                egressKey,
                projectCode,
                storeCode,
                siteCode,
                DataPullScheduleAnchor.requireIdentity(scopeKey, "scopeKey", 96)
        );
    }

    static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    static LocalDateTime requireMillisecond(LocalDateTime value, String field) {
        LocalDateTime nonNull = Objects.requireNonNull(value, field);
        if (nonNull.getNano() % 1_000_000 != 0) {
            throw new IllegalStateException(field + " must fit persisted millisecond precision");
        }
        return nonNull;
    }

    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String value) { scopeKey = value; }
    public String getScopeNamespace() { return scopeNamespace; }
    public void setScopeNamespace(String value) { scopeNamespace = value; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { ownerUserId = value; }
    public Long getLogicalStoreId() { return logicalStoreId; }
    public void setLogicalStoreId(Long value) { logicalStoreId = value; }
    public String getAccountKey() { return accountKey; }
    public void setAccountKey(String value) { accountKey = value; }
    public String getEgressKey() { return egressKey; }
    public void setEgressKey(String value) { egressKey = value; }
    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String value) { projectCode = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { storeCode = value; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String value) { siteCode = value; }
    public Kind getAdmissionKind() { return admissionKind; }
    public void setAdmissionKind(Kind value) { admissionKind = value; }
    public LocalDateTime getFirstEligibleAtUtc() { return firstEligibleAtUtc; }
    public void setFirstEligibleAtUtc(LocalDateTime value) { firstEligibleAtUtc = value; }
    public String getSourceBindingSha256() { return sourceBindingSha256; }
    public void setSourceBindingSha256(String value) { sourceBindingSha256 = value; }
    public String getCutoverKey() { return cutoverKey; }
    public void setCutoverKey(String value) { cutoverKey = value; }
    public LocalDateTime getAdmittedAtUtc() { return admittedAtUtc; }
    public void setAdmittedAtUtc(LocalDateTime value) { admittedAtUtc = value; }
}
