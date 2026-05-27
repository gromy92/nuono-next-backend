package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NoonInterfacePullResult {
    private NoonPullTaskStatus status;
    private String sourceBatchId;
    private String failureType;
    private List<Map<String, Object>> items = new ArrayList<>();
    private int pageCount;
    private int requestCount;

    public static NoonInterfacePullResult succeeded(
            String sourceBatchId,
            List<Map<String, Object>> items,
            int pageCount,
            int requestCount
    ) {
        return completed(NoonPullTaskStatus.SUCCEEDED, sourceBatchId, items, pageCount, requestCount);
    }

    public static NoonInterfacePullResult completed(
            NoonPullTaskStatus status,
            String sourceBatchId,
            List<Map<String, Object>> items,
            int pageCount,
            int requestCount
    ) {
        NoonInterfacePullResult result = new NoonInterfacePullResult();
        result.status = status;
        result.sourceBatchId = sourceBatchId;
        result.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        result.pageCount = pageCount;
        result.requestCount = requestCount;
        return result;
    }

    public static NoonInterfacePullResult failed(NoonPullTaskRecord task) {
        NoonInterfacePullResult result = new NoonInterfacePullResult();
        result.status = task.getStatus();
        result.sourceBatchId = task.getSourceBatchId();
        result.failureType = task.getFailureType();
        return result;
    }

    public NoonPullTaskStatus getStatus() {
        return status;
    }

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public String getFailureType() {
        return failureType;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getRequestCount() {
        return requestCount;
    }
}
