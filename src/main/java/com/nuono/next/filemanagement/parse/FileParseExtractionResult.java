package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseExtractionResult {

    private final List<FileParseExtractionSummary> summaries;
    private final String combinedText;
    private final boolean requiresFileAiAdapter;
    private final List<FileParseInputAttachment> attachments;
    private final List<FileParseSourceRowDraft> sourceRows;

    public FileParseExtractionResult(
            List<FileParseExtractionSummary> summaries,
            String combinedText,
            boolean requiresFileAiAdapter
    ) {
        this(summaries, combinedText, requiresFileAiAdapter, List.of());
    }

    public FileParseExtractionResult(
            List<FileParseExtractionSummary> summaries,
            String combinedText,
            boolean requiresFileAiAdapter,
            List<FileParseInputAttachment> attachments
    ) {
        this(summaries, combinedText, requiresFileAiAdapter, attachments, List.of());
    }

    public FileParseExtractionResult(
            List<FileParseExtractionSummary> summaries,
            String combinedText,
            boolean requiresFileAiAdapter,
            List<FileParseInputAttachment> attachments,
            List<FileParseSourceRowDraft> sourceRows
    ) {
        this.summaries = summaries == null ? new ArrayList<>() : new ArrayList<>(summaries);
        this.combinedText = combinedText;
        this.requiresFileAiAdapter = requiresFileAiAdapter;
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
        this.sourceRows = sourceRows == null ? new ArrayList<>() : new ArrayList<>(sourceRows);
    }

    public List<FileParseExtractionSummary> getSummaries() {
        return new ArrayList<>(summaries);
    }

    public String getCombinedText() {
        return combinedText;
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
