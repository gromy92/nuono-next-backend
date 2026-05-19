package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseWorkflowView {

    private Long taskId;
    private String status;
    private List<FileParseWorkflowStepView> steps = new ArrayList<>();
    private FileParseWorkflowCoverageView coverage = new FileParseWorkflowCoverageView();

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<FileParseWorkflowStepView> getSteps() {
        return steps;
    }

    public void setSteps(List<FileParseWorkflowStepView> steps) {
        this.steps = steps == null ? new ArrayList<>() : steps;
    }

    public FileParseWorkflowCoverageView getCoverage() {
        return coverage;
    }

    public void setCoverage(FileParseWorkflowCoverageView coverage) {
        this.coverage = coverage == null ? new FileParseWorkflowCoverageView() : coverage;
    }
}
