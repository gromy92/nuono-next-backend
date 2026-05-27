package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;

public class NoonProductRefreshResult {
    private String state;
    private String sourceBatchId;
    private boolean preserveDrafts;
    private boolean publishFlowTriggered;
    private List<String> requestedDetailSkuParents = new ArrayList<>();
    private boolean blindFullStoreDetailFetch;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public boolean isPreserveDrafts() {
        return preserveDrafts;
    }

    public void setPreserveDrafts(boolean preserveDrafts) {
        this.preserveDrafts = preserveDrafts;
    }

    public boolean isPublishFlowTriggered() {
        return publishFlowTriggered;
    }

    public void setPublishFlowTriggered(boolean publishFlowTriggered) {
        this.publishFlowTriggered = publishFlowTriggered;
    }

    public List<String> getRequestedDetailSkuParents() {
        return requestedDetailSkuParents;
    }

    public void setRequestedDetailSkuParents(List<String> requestedDetailSkuParents) {
        this.requestedDetailSkuParents = requestedDetailSkuParents == null ? new ArrayList<>() : requestedDetailSkuParents;
    }

    public boolean isBlindFullStoreDetailFetch() {
        return blindFullStoreDetailFetch;
    }

    public void setBlindFullStoreDetailFetch(boolean blindFullStoreDetailFetch) {
        this.blindFullStoreDetailFetch = blindFullStoreDetailFetch;
    }
}
