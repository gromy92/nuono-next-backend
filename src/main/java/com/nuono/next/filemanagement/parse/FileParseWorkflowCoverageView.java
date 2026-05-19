package com.nuono.next.filemanagement.parse;

public class FileParseWorkflowCoverageView {

    private int sourceRows;
    private int processedSourceRows;
    private int unprocessedSourceRows;
    private int resultItems;
    private int hardErrors;

    public int getSourceRows() {
        return sourceRows;
    }

    public void setSourceRows(int sourceRows) {
        this.sourceRows = sourceRows;
    }

    public int getProcessedSourceRows() {
        return processedSourceRows;
    }

    public void setProcessedSourceRows(int processedSourceRows) {
        this.processedSourceRows = processedSourceRows;
    }

    public int getUnprocessedSourceRows() {
        return unprocessedSourceRows;
    }

    public void setUnprocessedSourceRows(int unprocessedSourceRows) {
        this.unprocessedSourceRows = unprocessedSourceRows;
    }

    public int getResultItems() {
        return resultItems;
    }

    public void setResultItems(int resultItems) {
        this.resultItems = resultItems;
    }

    public int getHardErrors() {
        return hardErrors;
    }

    public void setHardErrors(int hardErrors) {
        this.hardErrors = hardErrors;
    }
}
