package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

public class AliUnpaidOrderCreateProbeCommand {

    private Long taskId;
    private Long operatorUserId;
    private List<String> offerUrls = new ArrayList<>();
    private String inquiryMessage;
    private Integer quantity;
    private Boolean dryRun;
    private Boolean confirmCreate;
    private Boolean persistPlan;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public List<String> getOfferUrls() {
        return offerUrls;
    }

    public void setOfferUrls(List<String> offerUrls) {
        this.offerUrls = offerUrls;
    }

    public String getInquiryMessage() {
        return inquiryMessage;
    }

    public void setInquiryMessage(String inquiryMessage) {
        this.inquiryMessage = inquiryMessage;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Boolean getConfirmCreate() {
        return confirmCreate;
    }

    public void setConfirmCreate(Boolean confirmCreate) {
        this.confirmCreate = confirmCreate;
    }

    public Boolean getPersistPlan() {
        return persistPlan;
    }

    public void setPersistPlan(Boolean persistPlan) {
        this.persistPlan = persistPlan;
    }
}
