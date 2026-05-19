package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

public class AliUnpaidOrderCreateProbeView {

    private String mode = "local-db";
    private boolean ready;
    private boolean dryRun;
    private boolean createEnabled;
    private boolean confirmCreate;
    private boolean creationAllowed;
    private boolean persisted;
    private Long taskId;
    private String plannedChannel;
    private String activeChannel;
    private String failureCode;
    private String blockedReason;
    private String message;
    private Integer quantity;
    private Integer offerCount;
    private List<String> offerUrls = new ArrayList<>();
    private String inquiryMessagePreview;
    private String orderPayloadDigest;
    private String unpaidOrderStatus;
    private String orderWorkbenchUrl;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isCreateEnabled() {
        return createEnabled;
    }

    public void setCreateEnabled(boolean createEnabled) {
        this.createEnabled = createEnabled;
    }

    public boolean isConfirmCreate() {
        return confirmCreate;
    }

    public void setConfirmCreate(boolean confirmCreate) {
        this.confirmCreate = confirmCreate;
    }

    public boolean isCreationAllowed() {
        return creationAllowed;
    }

    public void setCreationAllowed(boolean creationAllowed) {
        this.creationAllowed = creationAllowed;
    }

    public boolean isPersisted() {
        return persisted;
    }

    public void setPersisted(boolean persisted) {
        this.persisted = persisted;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getPlannedChannel() {
        return plannedChannel;
    }

    public void setPlannedChannel(String plannedChannel) {
        this.plannedChannel = plannedChannel;
    }

    public String getActiveChannel() {
        return activeChannel;
    }

    public void setActiveChannel(String activeChannel) {
        this.activeChannel = activeChannel;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getOfferCount() {
        return offerCount;
    }

    public void setOfferCount(Integer offerCount) {
        this.offerCount = offerCount;
    }

    public List<String> getOfferUrls() {
        return offerUrls;
    }

    public void setOfferUrls(List<String> offerUrls) {
        this.offerUrls = offerUrls;
    }

    public String getInquiryMessagePreview() {
        return inquiryMessagePreview;
    }

    public void setInquiryMessagePreview(String inquiryMessagePreview) {
        this.inquiryMessagePreview = inquiryMessagePreview;
    }

    public String getOrderPayloadDigest() {
        return orderPayloadDigest;
    }

    public void setOrderPayloadDigest(String orderPayloadDigest) {
        this.orderPayloadDigest = orderPayloadDigest;
    }

    public String getUnpaidOrderStatus() {
        return unpaidOrderStatus;
    }

    public void setUnpaidOrderStatus(String unpaidOrderStatus) {
        this.unpaidOrderStatus = unpaidOrderStatus;
    }

    public String getOrderWorkbenchUrl() {
        return orderWorkbenchUrl;
    }

    public void setOrderWorkbenchUrl(String orderWorkbenchUrl) {
        this.orderWorkbenchUrl = orderWorkbenchUrl;
    }
}
