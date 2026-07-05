package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionAnalysisView {

    private String status;
    private String sourceCollectionId;
    private String recommendationLevel;
    private Integer recommendationScore;
    private String conclusion;
    private String summary;
    private String model;
    private String errorCode;
    private String errorMessage;
    private Long durationMillis;
    private List<String> profitRisks = new ArrayList<>();
    private List<String> competitorRisks = new ArrayList<>();
    private List<String> procurementRisks = new ArrayList<>();
    private List<String> logisticsRisks = new ArrayList<>();
    private List<String> missingInformation = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceCollectionId() {
        return sourceCollectionId;
    }

    public void setSourceCollectionId(String sourceCollectionId) {
        this.sourceCollectionId = sourceCollectionId;
    }

    public String getRecommendationLevel() {
        return recommendationLevel;
    }

    public void setRecommendationLevel(String recommendationLevel) {
        this.recommendationLevel = recommendationLevel;
    }

    public Integer getRecommendationScore() {
        return recommendationScore;
    }

    public void setRecommendationScore(Integer recommendationScore) {
        this.recommendationScore = recommendationScore;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public List<String> getProfitRisks() {
        return profitRisks;
    }

    public void setProfitRisks(List<String> profitRisks) {
        this.profitRisks = profitRisks == null ? new ArrayList<>() : new ArrayList<>(profitRisks);
    }

    public List<String> getCompetitorRisks() {
        return competitorRisks;
    }

    public void setCompetitorRisks(List<String> competitorRisks) {
        this.competitorRisks = competitorRisks == null ? new ArrayList<>() : new ArrayList<>(competitorRisks);
    }

    public List<String> getProcurementRisks() {
        return procurementRisks;
    }

    public void setProcurementRisks(List<String> procurementRisks) {
        this.procurementRisks = procurementRisks == null ? new ArrayList<>() : new ArrayList<>(procurementRisks);
    }

    public List<String> getLogisticsRisks() {
        return logisticsRisks;
    }

    public void setLogisticsRisks(List<String> logisticsRisks) {
        this.logisticsRisks = logisticsRisks == null ? new ArrayList<>() : new ArrayList<>(logisticsRisks);
    }

    public List<String> getMissingInformation() {
        return missingInformation;
    }

    public void setMissingInformation(List<String> missingInformation) {
        this.missingInformation = missingInformation == null ? new ArrayList<>() : new ArrayList<>(missingInformation);
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions == null ? new ArrayList<>() : new ArrayList<>(nextActions);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }
}
