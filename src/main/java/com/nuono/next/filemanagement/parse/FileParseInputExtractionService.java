package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class FileParseInputExtractionService {

    private final List<FileParseInputExtractor> extractors;

    public FileParseInputExtractionService(List<FileParseInputExtractor> extractors) {
        this.extractors = extractors == null ? List.of() : new ArrayList<>(extractors);
        AnnotationAwareOrderComparator.sort(this.extractors);
    }

    public static FileParseInputExtractionService withDefaultExtractors() {
        return new FileParseInputExtractionService(List.of(
                new ManualTextFileParseInputExtractor(),
                new PlainTextFileParseInputExtractor(),
                new OfficeFileParseInputExtractor(),
                new PdfTextFileParseInputExtractor(),
                new ImageOcrFileParseInputExtractor(),
                new AiFileAttachmentParseInputExtractor()
        ));
    }

    public FileParseExtractionResult extract(List<FileParseTaskInputRow> inputs, Path storageRoot) throws IOException {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("解析文档没有输入项。");
        }
        List<FileParseTaskInputRow> sortedInputs = inputs.stream()
                .sorted(Comparator.comparing(row -> row.getSortNo() == null ? 0 : row.getSortNo()))
                .collect(Collectors.toList());
        List<FileParseExtractionSummary> summaries = new ArrayList<>();
        List<FileParseInputAttachment> attachments = new ArrayList<>();
        List<FileParseSourceRowDraft> sourceRows = new ArrayList<>();
        StringBuilder combinedText = new StringBuilder();
        boolean requiresFileAiAdapter = false;
        int sourceSortNo = 1;
        for (FileParseTaskInputRow input : sortedInputs) {
            FileParseInputExtractor extractor = resolveExtractor(input);
            FileParseInputExtraction extraction = extractor.extract(input, storageRoot);
            summaries.add(toSummary(input, extraction));
            if (extraction.isRequiresFileAiAdapter()) {
                requiresFileAiAdapter = true;
            }
            attachments.addAll(extraction.getAttachments());
            List<FileParseSourceRowDraft> extractedRows = extraction.getSourceRows();
            if (!extractedRows.isEmpty()) {
                sourceRows.addAll(withGlobalSortNo(extractedRows, sourceSortNo));
                sourceSortNo += extractedRows.size();
            }
            if (extraction.getExtractedText() != null && !extraction.getExtractedText().isBlank()) {
                if (extractedRows.isEmpty()) {
                    List<FileParseSourceRowDraft> fallbackRows = toSourceRows(input, extraction, sourceSortNo);
                    sourceRows.addAll(fallbackRows);
                    sourceSortNo += fallbackRows.size();
                }
                combinedText
                        .append("\n\n")
                        .append("### ")
                        .append(input.getDisplayName() == null ? input.getInputType() : input.getDisplayName())
                        .append("\n")
                        .append(extraction.getExtractedText());
            }
        }
        return new FileParseExtractionResult(
                summaries,
                combinedText.toString().trim(),
                requiresFileAiAdapter,
                attachments,
                sourceRows
        );
    }

    private List<FileParseSourceRowDraft> withGlobalSortNo(List<FileParseSourceRowDraft> rows, int startSortNo) {
        List<FileParseSourceRowDraft> updatedRows = new ArrayList<>();
        int sortNo = startSortNo;
        for (FileParseSourceRowDraft sourceRow : rows) {
            sourceRow.setSortNo(sortNo++);
            updatedRows.add(sourceRow);
        }
        return updatedRows;
    }

    private FileParseInputExtractor resolveExtractor(FileParseTaskInputRow input) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(input))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有可用的输入抽取器：" + input.getInputType()));
    }

    private FileParseExtractionSummary toSummary(FileParseTaskInputRow input, FileParseInputExtraction extraction) {
        FileParseExtractionSummary summary = new FileParseExtractionSummary();
        summary.setInputId(input.getId());
        summary.setInputType(input.getInputType());
        summary.setDisplayName(input.getDisplayName());
        summary.setExtractor(extraction.getExtractor());
        summary.setStatus(extraction.getStatus());
        summary.setExtractedTextLength(extraction.getExtractedTextLength());
        summary.setMessage(extraction.getMessage());
        return summary;
    }

    private List<FileParseSourceRowDraft> toSourceRows(
            FileParseTaskInputRow input,
            FileParseInputExtraction extraction,
            int startSortNo
    ) {
        String text = extraction.getExtractedText();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] lines = text.split("\\R");
        List<FileParseSourceRowDraft> rows = new ArrayList<>();
        int rowNo = 1;
        int sortNo = startSortNo;
        for (String line : lines) {
            String rawText = line == null ? "" : line.trim();
            if (rawText.isEmpty()) {
                rowNo++;
                continue;
            }
            FileParseSourceRowDraft row = new FileParseSourceRowDraft();
            row.setTaskInputId(input.getId());
            row.setFileAssetId(input.getFileAssetId());
            row.setSourceType(resolveSourceType(input));
            row.setSourceLocator("line=" + rowNo);
            row.setRowNo(rowNo);
            row.setRawText(rawText);
            row.setRawCellsJson(rawCellsJson(rawText));
            row.setSourceHash(sha256Text(row.getSourceType() + "|" + row.getSourceLocator() + "|" + rawText));
            row.setExtractorType(extraction.getExtractor());
            row.setExtractorVersion("v1");
            row.setSortNo(sortNo++);
            rows.add(row);
            rowNo++;
        }
        return rows;
    }

    private String resolveSourceType(FileParseTaskInputRow input) {
        String inputType = normalize(input.getInputType());
        String extension = normalize(input.getFileExtension());
        if ("manual_text".equals(inputType)) {
            return "manual_text_block";
        }
        if ("ocr_text".equals(inputType)) {
            return "image_ocr_block";
        }
        if ("xlsx".equals(extension) || "xls".equals(extension) || "csv".equals(extension) || "excel".equals(inputType)) {
            return "excel_row";
        }
        if ("pdf".equals(extension) || "pdf".equals(inputType)) {
            return "pdf_attachment";
        }
        if ("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension) || "webp".equals(extension) || "image".equals(inputType)) {
            return "image_attachment";
        }
        if ("doc".equals(extension) || "docx".equals(extension)) {
            return "word_paragraph";
        }
        return "text_block";
    }

    private String rawCellsJson(String rawText) {
        if (rawText == null || !rawText.contains("\t")) {
            return null;
        }
        String[] cells = rawText.split("\\t");
        return "[" + java.util.Arrays.stream(cells)
                .map(cell -> "\"" + jsonEscape(cell.trim()) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String sha256Text(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : hashed) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", error);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
