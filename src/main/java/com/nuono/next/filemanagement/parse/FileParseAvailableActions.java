package com.nuono.next.filemanagement.parse;

public class FileParseAvailableActions {

    private boolean canCreateTask;
    private boolean canProcess;
    private boolean canPublish;
    private boolean canActivateLogisticsChannels;
    private boolean canManageStandard;

    public boolean isCanCreateTask() {
        return canCreateTask;
    }

    public void setCanCreateTask(boolean canCreateTask) {
        this.canCreateTask = canCreateTask;
    }

    public boolean isCanProcess() {
        return canProcess;
    }

    public void setCanProcess(boolean canProcess) {
        this.canProcess = canProcess;
    }

    public boolean isCanPublish() {
        return canPublish;
    }

    public void setCanPublish(boolean canPublish) {
        this.canPublish = canPublish;
    }

    public boolean isCanActivateLogisticsChannels() {
        return canActivateLogisticsChannels;
    }

    public void setCanActivateLogisticsChannels(boolean canActivateLogisticsChannels) {
        this.canActivateLogisticsChannels = canActivateLogisticsChannels;
    }

    public boolean isCanManageStandard() {
        return canManageStandard;
    }

    public void setCanManageStandard(boolean canManageStandard) {
        this.canManageStandard = canManageStandard;
    }
}
