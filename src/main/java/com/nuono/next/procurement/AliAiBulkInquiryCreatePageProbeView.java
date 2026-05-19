package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

public class AliAiBulkInquiryCreatePageProbeView {

    private String mode = "local-db";
    private boolean ready;
    private boolean readable;
    private boolean loginRequired;
    private boolean createPageLikely;
    private String source;
    private String pageUrl;
    private String pageTitle;
    private String failureCode;
    private String message;
    private Integer elementCount;
    private Integer buttonCount;
    private Integer inputCount;
    private Integer textareaCount;
    private Integer submitCandidateCount;
    private Integer offerInputCandidateCount;
    private Integer messageInputCandidateCount;
    private Integer quantityInputCandidateCount;
    private String textPreview;
    private List<AliAiBulkInquiryElementProbe> elements = new ArrayList<>();

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

    public boolean isCreatePageLikely() {
        return createPageLikely;
    }

    public void setCreatePageLikely(boolean createPageLikely) {
        this.createPageLikely = createPageLikely;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getElementCount() {
        return elementCount;
    }

    public void setElementCount(Integer elementCount) {
        this.elementCount = elementCount;
    }

    public Integer getButtonCount() {
        return buttonCount;
    }

    public void setButtonCount(Integer buttonCount) {
        this.buttonCount = buttonCount;
    }

    public Integer getInputCount() {
        return inputCount;
    }

    public void setInputCount(Integer inputCount) {
        this.inputCount = inputCount;
    }

    public Integer getTextareaCount() {
        return textareaCount;
    }

    public void setTextareaCount(Integer textareaCount) {
        this.textareaCount = textareaCount;
    }

    public Integer getSubmitCandidateCount() {
        return submitCandidateCount;
    }

    public void setSubmitCandidateCount(Integer submitCandidateCount) {
        this.submitCandidateCount = submitCandidateCount;
    }

    public Integer getOfferInputCandidateCount() {
        return offerInputCandidateCount;
    }

    public void setOfferInputCandidateCount(Integer offerInputCandidateCount) {
        this.offerInputCandidateCount = offerInputCandidateCount;
    }

    public Integer getMessageInputCandidateCount() {
        return messageInputCandidateCount;
    }

    public void setMessageInputCandidateCount(Integer messageInputCandidateCount) {
        this.messageInputCandidateCount = messageInputCandidateCount;
    }

    public Integer getQuantityInputCandidateCount() {
        return quantityInputCandidateCount;
    }

    public void setQuantityInputCandidateCount(Integer quantityInputCandidateCount) {
        this.quantityInputCandidateCount = quantityInputCandidateCount;
    }

    public String getTextPreview() {
        return textPreview;
    }

    public void setTextPreview(String textPreview) {
        this.textPreview = textPreview;
    }

    public List<AliAiBulkInquiryElementProbe> getElements() {
        return elements;
    }

    public void setElements(List<AliAiBulkInquiryElementProbe> elements) {
        this.elements = elements;
    }
}
