package com.nuono.next.filemanagement.parse;

import java.util.List;

public class FileParseLogisticsChannelActivationCommand {

    private Long targetPlanId;
    private Long versionId;
    private List<String> selectedChannelKeys;

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public List<String> getSelectedChannelKeys() {
        return selectedChannelKeys;
    }

    public void setSelectedChannelKeys(List<String> selectedChannelKeys) {
        this.selectedChannelKeys = selectedChannelKeys;
    }
}
