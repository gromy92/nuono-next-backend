package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseTargetPlanSummary {

    private Long id;
    private String code;
    private String label;
    private String documentType;
    private String documentName;
    private String standardVersion;
    private String currentVersion;
    private String description;
    private FileParseAvailableActions availableActions;
    private List<FileParseTargetPlanItemTypeView> itemTypes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public void setStandardVersion(String standardVersion) {
        this.standardVersion = standardVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FileParseAvailableActions getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(FileParseAvailableActions availableActions) {
        this.availableActions = availableActions;
    }

    public List<FileParseTargetPlanItemTypeView> getItemTypes() {
        return itemTypes;
    }

    public void setItemTypes(List<FileParseTargetPlanItemTypeView> itemTypes) {
        this.itemTypes = itemTypes == null ? new ArrayList<>() : itemTypes;
    }
}
