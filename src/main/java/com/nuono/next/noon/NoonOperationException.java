package com.nuono.next.noon;

public class NoonOperationException extends IllegalStateException {

    private final NoonResponseClassification classification;

    public NoonOperationException(NoonResponseClassification classification, NoonHttpException cause) {
        super(classification.getCode() + ": " + classification.getUserMessage(), cause);
        this.classification = classification;
    }

    public NoonResponseClassification getClassification() {
        return classification;
    }
}
