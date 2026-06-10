package com.nuono.next.intransit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InTransitPluginSyncRecords {

    private InTransitPluginSyncRecords() {
    }

    public static class PluginSyncPreviewView {
        private String mode = "plugin-sync";
        private boolean committable;
        private String sourceSystem;
        private String forwarderName;
        private int batchCount;
        private int packageCount;
        private int lineCount;
        private int nodeCount;
        private int newBatchCount;
        private int updateBatchCount;
        private int newLineCount;
        private int updateLineCount;
        private int newNodeCount;
        private int skippedNodeCount;
        private List<PluginSyncIssueView> issues = Collections.emptyList();
        private List<PluginSyncBatchPreviewView> batches = Collections.emptyList();

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public boolean isCommittable() { return committable; }
        public void setCommittable(boolean committable) { this.committable = committable; }
        public String getSourceSystem() { return sourceSystem; }
        public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
        public String getForwarderName() { return forwarderName; }
        public void setForwarderName(String forwarderName) { this.forwarderName = forwarderName; }
        public int getBatchCount() { return batchCount; }
        public void setBatchCount(int batchCount) { this.batchCount = batchCount; }
        public int getPackageCount() { return packageCount; }
        public void setPackageCount(int packageCount) { this.packageCount = packageCount; }
        public int getLineCount() { return lineCount; }
        public void setLineCount(int lineCount) { this.lineCount = lineCount; }
        public int getNodeCount() { return nodeCount; }
        public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
        public int getNewBatchCount() { return newBatchCount; }
        public void setNewBatchCount(int newBatchCount) { this.newBatchCount = newBatchCount; }
        public int getUpdateBatchCount() { return updateBatchCount; }
        public void setUpdateBatchCount(int updateBatchCount) { this.updateBatchCount = updateBatchCount; }
        public int getNewLineCount() { return newLineCount; }
        public void setNewLineCount(int newLineCount) { this.newLineCount = newLineCount; }
        public int getUpdateLineCount() { return updateLineCount; }
        public void setUpdateLineCount(int updateLineCount) { this.updateLineCount = updateLineCount; }
        public int getNewNodeCount() { return newNodeCount; }
        public void setNewNodeCount(int newNodeCount) { this.newNodeCount = newNodeCount; }
        public int getSkippedNodeCount() { return skippedNodeCount; }
        public void setSkippedNodeCount(int skippedNodeCount) { this.skippedNodeCount = skippedNodeCount; }
        public List<PluginSyncIssueView> getIssues() { return issues; }
        public void setIssues(List<PluginSyncIssueView> issues) {
            this.issues = issues == null ? Collections.emptyList() : issues;
        }
        public List<PluginSyncBatchPreviewView> getBatches() { return batches; }
        public void setBatches(List<PluginSyncBatchPreviewView> batches) {
            this.batches = batches == null ? Collections.emptyList() : batches;
        }
    }

    public static class PluginSyncCommitView extends PluginSyncPreviewView {
        private boolean committed;

        public boolean isCommitted() { return committed; }
        public void setCommitted(boolean committed) { this.committed = committed; }
    }

    public static class PluginSyncBatchPreviewView {
        private String batchNo;
        private Long batchId;
        private String action;
        private int packageCount;
        private int lineCount;
        private int nodeCount;

        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public int getPackageCount() { return packageCount; }
        public void setPackageCount(int packageCount) { this.packageCount = packageCount; }
        public int getLineCount() { return lineCount; }
        public void setLineCount(int lineCount) { this.lineCount = lineCount; }
        public int getNodeCount() { return nodeCount; }
        public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
    }

    public static class PluginSyncIssueView {
        private String level;
        private String batchNo;
        private String boxNo;
        private String psku;
        private String field;
        private String message;

        public static PluginSyncIssueView error(String batchNo, String boxNo, String psku, String field, String message) {
            return issue("error", batchNo, boxNo, psku, field, message);
        }

        public static PluginSyncIssueView warning(String batchNo, String boxNo, String psku, String field, String message) {
            return issue("warning", batchNo, boxNo, psku, field, message);
        }

        private static PluginSyncIssueView issue(String level, String batchNo, String boxNo, String psku, String field, String message) {
            PluginSyncIssueView view = new PluginSyncIssueView();
            view.setLevel(level);
            view.setBatchNo(batchNo);
            view.setBoxNo(boxNo);
            view.setPsku(psku);
            view.setField(field);
            view.setMessage(message);
            return view;
        }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getBatchNo() { return batchNo; }
        public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
        public String getBoxNo() { return boxNo; }
        public void setBoxNo(String boxNo) { this.boxNo = boxNo; }
        public String getPsku() { return psku; }
        public void setPsku(String psku) { this.psku = psku; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    static PluginSyncCommitView committedFrom(PluginSyncPreviewView preview) {
        PluginSyncCommitView view = new PluginSyncCommitView();
        view.setMode(preview.getMode());
        view.setCommittable(preview.isCommittable());
        view.setSourceSystem(preview.getSourceSystem());
        view.setForwarderName(preview.getForwarderName());
        view.setBatchCount(preview.getBatchCount());
        view.setPackageCount(preview.getPackageCount());
        view.setLineCount(preview.getLineCount());
        view.setNodeCount(preview.getNodeCount());
        view.setNewBatchCount(preview.getNewBatchCount());
        view.setUpdateBatchCount(preview.getUpdateBatchCount());
        view.setNewLineCount(preview.getNewLineCount());
        view.setUpdateLineCount(preview.getUpdateLineCount());
        view.setNewNodeCount(preview.getNewNodeCount());
        view.setSkippedNodeCount(preview.getSkippedNodeCount());
        view.setIssues(new ArrayList<>(preview.getIssues()));
        view.setBatches(new ArrayList<>(preview.getBatches()));
        view.setCommitted(true);
        return view;
    }
}
