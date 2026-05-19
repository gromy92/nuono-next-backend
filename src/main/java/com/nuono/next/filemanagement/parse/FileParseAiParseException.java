package com.nuono.next.filemanagement.parse;

public class FileParseAiParseException extends RuntimeException {

    private final String code;

    public FileParseAiParseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
