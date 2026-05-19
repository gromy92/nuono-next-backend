package com.nuono.next.filemanagement.parse;

public class FileParseItemReviewRow {

    private Long id;
    private Long resultItemId;
    private Long resultId;
    private Long taskId;
    private String reviewAction;
    private String reviewStatus;
    private String overridePayloadJson;
    private String effectivePayloadJson;
    private String validationStatus;
    private String validationMessage;
    private String reviewNote;
    private Long expectedResultId;
    private String idempotencyKey;
    private String requestHash;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getResultItemId() {
        return resultItemId;
    }

    public void setResultItemId(Long resultItemId) {
        this.resultItemId = resultItemId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getReviewAction() {
        return reviewAction;
    }

    public void setReviewAction(String reviewAction) {
        this.reviewAction = reviewAction;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getOverridePayloadJson() {
        return overridePayloadJson;
    }

    public void setOverridePayloadJson(String overridePayloadJson) {
        this.overridePayloadJson = overridePayloadJson;
    }

    public String getEffectivePayloadJson() {
        return effectivePayloadJson;
    }

    public void setEffectivePayloadJson(String effectivePayloadJson) {
        this.effectivePayloadJson = effectivePayloadJson;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getExpectedResultId() {
        return expectedResultId;
    }

    public void setExpectedResultId(Long expectedResultId) {
        this.expectedResultId = expectedResultId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }
}
