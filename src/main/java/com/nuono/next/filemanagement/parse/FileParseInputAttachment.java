package com.nuono.next.filemanagement.parse;

import java.util.Arrays;
import org.springframework.util.StringUtils;

public class FileParseInputAttachment {

    private final String fileName;
    private final String contentType;
    private final byte[] content;
    private final Long taskInputId;
    private final Long fileAssetId;

    public FileParseInputAttachment(String fileName, String contentType, byte[] content) {
        this(fileName, contentType, content, null, null);
    }

    public FileParseInputAttachment(
            String fileName,
            String contentType,
            byte[] content,
            Long taskInputId,
            Long fileAssetId
    ) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("解析附件缺少文件名。");
        }
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("解析附件缺少文件类型。");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("解析附件内容为空。");
        }
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = Arrays.copyOf(content, content.length);
        this.taskInputId = taskInputId;
        this.fileAssetId = fileAssetId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    public Long getTaskInputId() {
        return taskInputId;
    }

    public Long getFileAssetId() {
        return fileAssetId;
    }
}
