package com.nuono.next.procurement;

public class AliAiBulkInquiryResultProbeCommand extends ProcurementAutoInquiryProbeCommand {

    private Long taskId;
    private String resultUrl;
    private String externalInquiryId;
    private String sampleText;
    private Boolean openIfMissing;
    private Boolean persistResult;
    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getExternalInquiryId() {
        return externalInquiryId;
    }

    public void setExternalInquiryId(String externalInquiryId) {
        this.externalInquiryId = externalInquiryId;
    }

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }

    public Boolean getOpenIfMissing() {
        return openIfMissing;
    }

    public void setOpenIfMissing(Boolean openIfMissing) {
        this.openIfMissing = openIfMissing;
    }

    public Boolean getPersistResult() {
        return persistResult;
    }

    public void setPersistResult(Boolean persistResult) {
        this.persistResult = persistResult;
    }

}
