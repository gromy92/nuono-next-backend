package com.nuono.next.nooncompleteness;

public class NoonDataCompletenessBootstrapResult {
    private int scopeCount;
    private int completenessCategoryCount;
    private String message;

    public NoonDataCompletenessBootstrapResult() {
    }

    public NoonDataCompletenessBootstrapResult(int scopeCount, int completenessCategoryCount, String message) {
        this.scopeCount = scopeCount;
        this.completenessCategoryCount = completenessCategoryCount;
        this.message = message;
    }

    public int getScopeCount() {
        return scopeCount;
    }

    public void setScopeCount(int scopeCount) {
        this.scopeCount = scopeCount;
    }

    public int getCompletenessCategoryCount() {
        return completenessCategoryCount;
    }

    public void setCompletenessCategoryCount(int completenessCategoryCount) {
        this.completenessCategoryCount = completenessCategoryCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
