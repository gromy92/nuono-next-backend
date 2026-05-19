package com.nuono.next.procurement;

public class AliAiBulkInquiryResultView {

    private String mode = "local-db";
    private boolean ready;
    private boolean readable;
    private boolean loginRequired;
    private String source;
    private String resultUrl;
    private String pageTitle;
    private String externalInquiryId;
    private String externalResultStatus;
    private String replySource;
    private String replyParseStatus;
    private String replyParseError;
    private Integer supplierCount;
    private Integer repliedCount;
    private Integer noReplyCount;
    private Integer priceMentionCount;
    private Integer moqMentionCount;
    private Integer deliveryMentionCount;
    private String rawTextDigest;
    private String textPreview;
    private String message;
    private Long persistedTaskId;

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

    public boolean isReadable() {
        return readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }

    public void setLoginRequired(boolean loginRequired) {
        this.loginRequired = loginRequired;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getResultUrl() {
        return resultUrl;
    }

    public void setResultUrl(String resultUrl) {
        this.resultUrl = resultUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getExternalInquiryId() {
        return externalInquiryId;
    }

    public void setExternalInquiryId(String externalInquiryId) {
        this.externalInquiryId = externalInquiryId;
    }

    public String getExternalResultStatus() {
        return externalResultStatus;
    }

    public void setExternalResultStatus(String externalResultStatus) {
        this.externalResultStatus = externalResultStatus;
    }

    public String getReplySource() {
        return replySource;
    }

    public void setReplySource(String replySource) {
        this.replySource = replySource;
    }

    public String getReplyParseStatus() {
        return replyParseStatus;
    }

    public void setReplyParseStatus(String replyParseStatus) {
        this.replyParseStatus = replyParseStatus;
    }

    public String getReplyParseError() {
        return replyParseError;
    }

    public void setReplyParseError(String replyParseError) {
        this.replyParseError = replyParseError;
    }

    public Integer getSupplierCount() {
        return supplierCount;
    }

    public void setSupplierCount(Integer supplierCount) {
        this.supplierCount = supplierCount;
    }

    public Integer getRepliedCount() {
        return repliedCount;
    }

    public void setRepliedCount(Integer repliedCount) {
        this.repliedCount = repliedCount;
    }

    public Integer getNoReplyCount() {
        return noReplyCount;
    }

    public void setNoReplyCount(Integer noReplyCount) {
        this.noReplyCount = noReplyCount;
    }

    public Integer getPriceMentionCount() {
        return priceMentionCount;
    }

    public void setPriceMentionCount(Integer priceMentionCount) {
        this.priceMentionCount = priceMentionCount;
    }

    public Integer getMoqMentionCount() {
        return moqMentionCount;
    }

    public void setMoqMentionCount(Integer moqMentionCount) {
        this.moqMentionCount = moqMentionCount;
    }

    public Integer getDeliveryMentionCount() {
        return deliveryMentionCount;
    }

    public void setDeliveryMentionCount(Integer deliveryMentionCount) {
        this.deliveryMentionCount = deliveryMentionCount;
    }

    public String getRawTextDigest() {
        return rawTextDigest;
    }

    public void setRawTextDigest(String rawTextDigest) {
        this.rawTextDigest = rawTextDigest;
    }

    public String getTextPreview() {
        return textPreview;
    }

    public void setTextPreview(String textPreview) {
        this.textPreview = textPreview;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getPersistedTaskId() {
        return persistedTaskId;
    }

    public void setPersistedTaskId(Long persistedTaskId) {
        this.persistedTaskId = persistedTaskId;
    }
}
