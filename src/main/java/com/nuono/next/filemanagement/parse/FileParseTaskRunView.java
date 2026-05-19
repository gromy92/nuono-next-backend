package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseTaskRunView {

    private Long taskId;
    private String taskNo;
    private String documentTitle;
    private String status;
    private Integer parseAttemptCount;
    private Long resultId;
    private String resultNo;
    private Integer resultItemCount;
    private String message;
    private List<FileParseExtractionSummary> extractions = new ArrayList<>();

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getParseAttemptCount() {
        return parseAttemptCount;
    }

    public void setParseAttemptCount(Integer parseAttemptCount) {
        this.parseAttemptCount = parseAttemptCount;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public String getResultNo() {
        return resultNo;
    }

    public void setResultNo(String resultNo) {
        this.resultNo = resultNo;
    }

    public Integer getResultItemCount() {
        return resultItemCount;
    }

    public void setResultItemCount(Integer resultItemCount) {
        this.resultItemCount = resultItemCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<FileParseExtractionSummary> getExtractions() {
        return extractions;
    }

    public void setExtractions(List<FileParseExtractionSummary> extractions) {
        this.extractions = extractions == null ? new ArrayList<>() : extractions;
    }
}
