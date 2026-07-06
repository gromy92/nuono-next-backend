package com.nuono.next.intransit.autosync;

import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;

public class LogisticsProviderFetchResult {
    private boolean success;
    private String failureCode;
    private String failureMessage;
    private PluginSyncCommand command;
    private int batchCount;
    private int packageCount;
    private int lineCount;
    private int nodeCount;

    public static LogisticsProviderFetchResult success(PluginSyncCommand command) {
        LogisticsProviderFetchResult result = new LogisticsProviderFetchResult();
        result.setSuccess(true);
        result.setCommand(command);
        if (command != null && command.getBatches() != null) {
            result.setBatchCount(command.getBatches().size());
        }
        return result;
    }

    public static LogisticsProviderFetchResult failure(String failureCode, String failureMessage) {
        LogisticsProviderFetchResult result = new LogisticsProviderFetchResult();
        result.setSuccess(false);
        result.setFailureCode(failureCode);
        result.setFailureMessage(failureMessage);
        return result;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public PluginSyncCommand getCommand() { return command; }
    public void setCommand(PluginSyncCommand command) { this.command = command; }
    public int getBatchCount() { return batchCount; }
    public void setBatchCount(int batchCount) { this.batchCount = batchCount; }
    public int getPackageCount() { return packageCount; }
    public void setPackageCount(int packageCount) { this.packageCount = packageCount; }
    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }
    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
}
