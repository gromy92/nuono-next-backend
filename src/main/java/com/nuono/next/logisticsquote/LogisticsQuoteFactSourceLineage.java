package com.nuono.next.logisticsquote;

public class LogisticsQuoteFactSourceLineage {

    private final String sourceType;
    private final Long sourceTaskId;
    private final Long sourceResultId;
    private final Long sourceVersionId;
    private final Long sourceVersionItemId;
    private final String sourceFileName;
    private final String sourceLocator;

    public LogisticsQuoteFactSourceLineage(
            String sourceType,
            Long sourceTaskId,
            Long sourceResultId,
            Long sourceVersionId,
            Long sourceVersionItemId,
            String sourceFileName,
            String sourceLocator
    ) {
        this.sourceType = sourceType;
        this.sourceTaskId = sourceTaskId;
        this.sourceResultId = sourceResultId;
        this.sourceVersionId = sourceVersionId;
        this.sourceVersionItemId = sourceVersionItemId;
        this.sourceFileName = sourceFileName;
        this.sourceLocator = sourceLocator;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceTaskId() {
        return sourceTaskId;
    }

    public Long getSourceResultId() {
        return sourceResultId;
    }

    public Long getSourceVersionId() {
        return sourceVersionId;
    }

    public Long getSourceVersionItemId() {
        return sourceVersionItemId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getSourceLocator() {
        return sourceLocator;
    }
}
