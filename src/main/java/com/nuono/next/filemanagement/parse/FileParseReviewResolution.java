package com.nuono.next.filemanagement.parse;

class FileParseReviewResolution {

    final String reviewStatus;
    final String overridePayloadJson;
    final String effectivePayloadJson;
    final String validationStatus;
    final String validationMessage;
    final String effectivePayloadHash;
    final String note;

    FileParseReviewResolution(
            String reviewStatus,
            String overridePayloadJson,
            String effectivePayloadJson,
            String validationStatus,
            String validationMessage,
            String effectivePayloadHash,
            String note
    ) {
        this.reviewStatus = reviewStatus;
        this.overridePayloadJson = overridePayloadJson;
        this.effectivePayloadJson = effectivePayloadJson;
        this.validationStatus = validationStatus;
        this.validationMessage = validationMessage;
        this.effectivePayloadHash = effectivePayloadHash;
        this.note = note;
    }
}
