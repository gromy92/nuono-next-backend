package com.nuono.next.competitoranalysis;

public class CompetitorProductDetailFailure {
    private CompetitorProductDetailTarget target;
    private String errorCode;
    private String errorMessage;
    private boolean deferred;

    public CompetitorProductDetailFailure() {
    }

    static CompetitorProductDetailFailure failed(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage
    ) {
        return of(target, errorCode, errorMessage, false);
    }

    static CompetitorProductDetailFailure deferred(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage
    ) {
        return of(target, errorCode, errorMessage, true);
    }

    private static CompetitorProductDetailFailure of(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage,
            boolean deferred
    ) {
        CompetitorProductDetailFailure failure = new CompetitorProductDetailFailure();
        failure.setTarget(target);
        failure.setErrorCode(errorCode);
        failure.setErrorMessage(errorMessage);
        failure.setDeferred(deferred);
        return failure;
    }

    public CompetitorProductDetailTarget getTarget() {
        return target;
    }

    public void setTarget(CompetitorProductDetailTarget target) {
        this.target = target;
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

    public boolean isDeferred() {
        return deferred;
    }

    public void setDeferred(boolean deferred) {
        this.deferred = deferred;
    }
}
