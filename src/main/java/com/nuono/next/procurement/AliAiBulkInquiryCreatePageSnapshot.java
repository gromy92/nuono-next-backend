package com.nuono.next.procurement;

import java.util.ArrayList;
import java.util.List;

class AliAiBulkInquiryCreatePageSnapshot {

    private boolean ok;
    private String url;
    private String title;
    private String text;
    private List<AliAiBulkInquiryElementProbe> elements = new ArrayList<>();
    private String failureCode;
    private String failureMessage;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<AliAiBulkInquiryElementProbe> getElements() {
        return elements;
    }

    public void setElements(List<AliAiBulkInquiryElementProbe> elements) {
        this.elements = elements;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }
}
