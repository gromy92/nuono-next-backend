package com.nuono.next.filemanagement.parse;

public class FileParseExportFile {

    private final String fileName;
    private final String contentType;
    private final byte[] content;

    public FileParseExportFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : content.clone();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
