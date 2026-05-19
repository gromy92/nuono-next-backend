package com.nuono.next.logisticsquote;

import java.nio.file.Path;

public class LogisticsQuoteArchivedFile {

    private final Path path;

    private final String fileName;

    public LogisticsQuoteArchivedFile(Path path, String fileName) {
        this.path = path;
        this.fileName = fileName;
    }

    public Path getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }
}
