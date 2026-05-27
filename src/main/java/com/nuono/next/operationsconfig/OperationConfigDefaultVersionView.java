package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationConfigDefaultVersionView {

    private final String versionNo;
    private final String displayName;
    private final String configType;
    private final String status;
    private final String publishSourceLabel;
    private final String sourceDocument;
    private final String summary;
    private final List<OperationConfigDefaultVersionItemView> items;

    public OperationConfigDefaultVersionView(
            String versionNo,
            String displayName,
            String configType,
            String status,
            String publishSourceLabel,
            String sourceDocument,
            String summary,
            List<OperationConfigDefaultVersionItemView> items
    ) {
        this.versionNo = versionNo;
        this.displayName = displayName;
        this.configType = configType;
        this.status = status;
        this.publishSourceLabel = publishSourceLabel;
        this.sourceDocument = sourceDocument;
        this.summary = summary;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigType() {
        return configType;
    }

    public String getStatus() {
        return status;
    }

    public String getPublishSourceLabel() {
        return publishSourceLabel;
    }

    public String getSourceDocument() {
        return sourceDocument;
    }

    public String getSummary() {
        return summary;
    }

    public int getItemCount() {
        return items.size();
    }

    public List<OperationConfigDefaultVersionItemView> getItems() {
        return items;
    }
}
