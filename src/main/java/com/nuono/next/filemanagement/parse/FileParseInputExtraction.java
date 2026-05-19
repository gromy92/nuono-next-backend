package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseInputExtraction {

    private final String extractor;
    private final String status;
    private final Integer extractedTextLength;
    private final String message;
    private final String extractedText;
    private final boolean requiresFileAiAdapter;
    private final List<FileParseInputAttachment> attachments;
    private final List<FileParseSourceRowDraft> sourceRows;

    public FileParseInputExtraction(
            String extractor,
            String status,
            Integer extractedTextLength,
            String message
    ) {
        this(extractor, status, extractedTextLength, message, null, false);
    }

    public FileParseInputExtraction(
            String extractor,
            String status,
            Integer extractedTextLength,
            String message,
            String extractedText,
            boolean requiresFileAiAdapter
    ) {
        this(extractor, status, extractedTextLength, message, extractedText, requiresFileAiAdapter, List.of());
    }

    public FileParseInputExtraction(
            String extractor,
            String status,
            Integer extractedTextLength,
            String message,
            String extractedText,
            boolean requiresFileAiAdapter,
            List<FileParseInputAttachment> attachments
    ) {
        this(extractor, status, extractedTextLength, message, extractedText, requiresFileAiAdapter, attachments, List.of());
    }

    public FileParseInputExtraction(
            String extractor,
            String status,
            Integer extractedTextLength,
            String message,
            String extractedText,
            boolean requiresFileAiAdapter,
            List<FileParseInputAttachment> attachments,
            List<FileParseSourceRowDraft> sourceRows
    ) {
        this.extractor = extractor;
        this.status = status;
        this.extractedTextLength = extractedTextLength;
        this.message = message;
        this.extractedText = extractedText;
        this.requiresFileAiAdapter = requiresFileAiAdapter;
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
        this.sourceRows = sourceRows == null ? new ArrayList<>() : new ArrayList<>(sourceRows);
    }

    public String getExtractor() {
        return extractor;
    }

    public String getStatus() {
        return status;
    }

    public Integer getExtractedTextLength() {
        return extractedTextLength;
    }

    public String getMessage() {
        return message;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public boolean isRequiresFileAiAdapter() {
        return requiresFileAiAdapter;
    }

    public List<FileParseInputAttachment> getAttachments() {
        return new ArrayList<>(attachments);
    }

    public List<FileParseSourceRowDraft> getSourceRows() {
        return new ArrayList<>(sourceRows);
    }
}
