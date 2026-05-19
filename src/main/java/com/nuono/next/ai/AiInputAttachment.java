package com.nuono.next.ai;

import java.util.Arrays;
import org.springframework.util.StringUtils;

public class AiInputAttachment {

    private final String fileName;
    private final String contentType;
    private final byte[] content;

    public AiInputAttachment(String fileName, String contentType, byte[] content) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("AI input attachment fileName is required");
        }
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalArgumentException("AI input attachment contentType is required");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("AI input attachment content is required");
        }
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = Arrays.copyOf(content, content.length);
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

    public boolean isImage() {
        return contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/");
    }
}
