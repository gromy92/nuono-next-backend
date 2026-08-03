package com.nuono.next.noon;

import java.util.List;

public final class NoonEgressUnavailableException extends IllegalStateException {
    public static final String FAILURE_CODE = "NOON_EGRESS_UNAVAILABLE";
    public static final String BLOCKED_FAILURE_CODE = "NOON_EGRESS_BLOCKED";

    private final int attempts;
    private final List<String> evidenceCodes;

    public NoonEgressUnavailableException(int attempts, List<String> evidenceCodes) {
        super(failureCode(evidenceCodes) + " attempts=" + attempts + " stages=" + List.copyOf(evidenceCodes));
        this.attempts = attempts;
        this.evidenceCodes = List.copyOf(evidenceCodes);
    }

    public String getFailureCode() {
        return failureCode(evidenceCodes);
    }

    public int getAttempts() {
        return attempts;
    }

    public List<String> getEvidenceCodes() {
        return evidenceCodes;
    }

    private static String failureCode(List<String> evidenceCodes) {
        return !evidenceCodes.isEmpty() && evidenceCodes.stream().allMatch(NoonEgressUnavailableException::isBlocked)
                ? BLOCKED_FAILURE_CODE
                : FAILURE_CODE;
    }

    private static boolean isBlocked(String evidenceCode) {
        return "CONNECT_STATUS_407".equals(evidenceCode)
                || "SESSION_HTTP_407".equals(evidenceCode);
    }
}
