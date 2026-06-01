package com.nuono.next.productselection;

import java.util.List;
import java.util.Map;

public class Ali1688PluginExecutionAssignmentSubmitCommand {

    private String idempotencyKey;
    private String assignmentType;
    private String candidateId;
    private String sourcePageUrl;
    private String resultStatus;
    private Map<String, Object> resultSnapshot;
    private Map<String, Object> rawSnapshot;
    private List<Map<String, Object>> candidates;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getSourcePageUrl() {
        return sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public Map<String, Object> getResultSnapshot() {
        return resultSnapshot;
    }

    public void setResultSnapshot(Map<String, Object> resultSnapshot) {
        this.resultSnapshot = resultSnapshot;
    }

    public Map<String, Object> getRawSnapshot() {
        return rawSnapshot;
    }

    public void setRawSnapshot(Map<String, Object> rawSnapshot) {
        this.rawSnapshot = rawSnapshot;
    }

    public List<Map<String, Object>> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<Map<String, Object>> candidates) {
        this.candidates = candidates;
    }
}
