package com.nuono.next.filemanagement.parse;

public class FileParsePersistedResult {

    private final Long resultId;
    private final String resultNo;
    private final int itemCount;

    public FileParsePersistedResult(Long resultId, String resultNo, int itemCount) {
        this.resultId = resultId;
        this.resultNo = resultNo;
        this.itemCount = itemCount;
    }

    public Long getResultId() {
        return resultId;
    }

    public String getResultNo() {
        return resultNo;
    }

    public int getItemCount() {
        return itemCount;
    }
}
