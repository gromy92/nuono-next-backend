package com.nuono.next.filemanagement.parse;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.file-management.parse.storage")
public class FileParseStorageProperties {

    private Path rootDir = Paths.get(System.getProperty("java.io.tmpdir"), "nuono-next-file-management-parse");
    private long maxFileBytes = 30L * 1024L * 1024L;
    private long uploadExpiresHours = 24L;

    public Path getRootDir() {
        return rootDir;
    }

    public void setRootDir(Path rootDir) {
        this.rootDir = rootDir;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public long getUploadExpiresHours() {
        return uploadExpiresHours;
    }

    public void setUploadExpiresHours(long uploadExpiresHours) {
        this.uploadExpiresHours = uploadExpiresHours;
    }
}
