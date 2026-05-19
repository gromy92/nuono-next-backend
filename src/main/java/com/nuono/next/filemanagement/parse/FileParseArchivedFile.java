package com.nuono.next.filemanagement.parse;

import java.nio.file.Path;

public class FileParseArchivedFile {

    private final Path path;
    private final String fileName;
    private final String contentType;

    public FileParseArchivedFile(Path path, String fileName, String contentType) {
        this.path = path;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public Path getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }
}
