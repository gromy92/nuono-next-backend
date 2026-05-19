package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseLogisticsChannelActivationView {

    private Long targetPlanId;
    private String targetPlanCode;
    private String targetPlanLabel;
    private Long versionId;
    private String versionNo;
    private Long ownerUserId;
    private List<String> selectedChannelKeys = new ArrayList<>();
    private List<FileParseLogisticsChannelView> channels = new ArrayList<>();

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public String getTargetPlanCode() {
        return targetPlanCode;
    }

    public void setTargetPlanCode(String targetPlanCode) {
        this.targetPlanCode = targetPlanCode;
    }

    public String getTargetPlanLabel() {
        return targetPlanLabel;
    }

    public void setTargetPlanLabel(String targetPlanLabel) {
        this.targetPlanLabel = targetPlanLabel;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public List<String> getSelectedChannelKeys() {
        return selectedChannelKeys;
    }

    public void setSelectedChannelKeys(List<String> selectedChannelKeys) {
        this.selectedChannelKeys = selectedChannelKeys == null ? new ArrayList<>() : selectedChannelKeys;
    }

    public List<FileParseLogisticsChannelView> getChannels() {
        return channels;
    }

    public void setChannels(List<FileParseLogisticsChannelView> channels) {
        this.channels = channels == null ? new ArrayList<>() : channels;
    }
}
