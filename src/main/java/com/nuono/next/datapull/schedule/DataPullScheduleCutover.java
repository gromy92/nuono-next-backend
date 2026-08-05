package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Persisted seal proving that the pre-cutover scope cohort has explicit anchors. */
public final class DataPullScheduleCutover {

    private OperationCode operationCode;
    private String cutoverKey;
    private String state;
    private Integer expectedScopeCount;
    private String anchorManifestSha256;
    private LocalDateTime activatedAtUtc;

    public DataPullScheduleCutover() {
        // MyBatis bean constructor.
    }

    public static DataPullScheduleCutover active(
            OperationCode operationCode,
            String cutoverKey,
            int expectedScopeCount,
            String anchorManifestSha256,
            LocalDateTime activatedAtUtc
    ) {
        DataPullScheduleCutover cutover = new DataPullScheduleCutover();
        cutover.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        cutover.cutoverKey = DataPullScheduleAnchor.requireIdentity(
                cutoverKey, "cutoverKey", 96
        );
        cutover.state = "ACTIVE";
        cutover.expectedScopeCount = expectedScopeCount;
        cutover.anchorManifestSha256 = requireDigest(anchorManifestSha256);
        cutover.activatedAtUtc = Objects.requireNonNull(activatedAtUtc, "activatedAtUtc");
        cutover.validateActive();
        return cutover;
    }

    public void validateActive() {
        Objects.requireNonNull(operationCode, "operationCode");
        DataPullScheduleAnchor.requireIdentity(cutoverKey, "cutoverKey", 96);
        if (!"ACTIVE".equals(state)) {
            throw new IllegalStateException("schedule cutover is not active");
        }
        if (expectedScopeCount == null || expectedScopeCount < 0) {
            throw new IllegalStateException("expectedScopeCount must be non-negative");
        }
        requireDigest(anchorManifestSha256);
        Objects.requireNonNull(activatedAtUtc, "activatedAtUtc");
    }

    private static String requireDigest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("anchor manifest must be a lowercase SHA-256 digest");
        }
        return value;
    }

    public OperationCode getOperationCode() { return operationCode; }
    public void setOperationCode(OperationCode value) { operationCode = value; }
    public String getCutoverKey() { return cutoverKey; }
    public void setCutoverKey(String value) { cutoverKey = value; }
    public String getState() { return state; }
    public void setState(String value) { state = value; }
    public Integer getExpectedScopeCount() { return expectedScopeCount; }
    public void setExpectedScopeCount(Integer value) { expectedScopeCount = value; }
    public String getAnchorManifestSha256() { return anchorManifestSha256; }
    public void setAnchorManifestSha256(String value) { anchorManifestSha256 = value; }
    public LocalDateTime getActivatedAtUtc() { return activatedAtUtc; }
    public void setActivatedAtUtc(LocalDateTime value) { activatedAtUtc = value; }
}
