package com.nuono.next.filemanagement.parse;

class FileParseValidationResult {

    final String status;
    final String message;

    FileParseValidationResult(String status, String message) {
        this.status = status;
        this.message = message;
    }

    boolean isPass() {
        return "pass".equals(status);
    }
}
