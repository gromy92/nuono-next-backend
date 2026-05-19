package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(40)
public class PdfTextFileParseInputExtractor implements FileParseInputExtractor {

    private static final int MAX_TEXT_LENGTH = 120_000;

    private final FileParseAiOcrService aiOcrService;

    public PdfTextFileParseInputExtractor() {
        this(null);
    }

    @Autowired
    public PdfTextFileParseInputExtractor(FileParseAiOcrService aiOcrService) {
        this.aiOcrService = aiOcrService;
    }

    @Override
    public boolean supports(FileParseTaskInputRow input) {
        if (input == null) {
            return false;
        }
        return "pdf".equals(normalize(input.getFileExtension())) || "pdf".equals(normalize(input.getInputType()));
    }

    @Override
    public FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) throws IOException {
        Path filePath = resolveInputFile(input, storageRoot);
        String fileName = resolveFileName(input);
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            ExtractionPayload payload = extractTextRows(input, document);
            if (StringUtils.hasText(payload.text)) {
                return new FileParseInputExtraction(
                        "pdf-text-file",
                        "extracted",
                        payload.text.length(),
                        "PDF 已抽取文本行并进入 AI 解析上下文。",
                        payload.text,
                        false,
                        List.of(),
                        payload.sourceRows
                );
            }
        } catch (IOException | RuntimeException error) {
            return attachmentFallback(input, filePath, fileName);
        }
        return attachmentFallback(input, filePath, fileName);
    }

    private ExtractionPayload extractTextRows(FileParseTaskInputRow input, PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        BoundedTextBuilder builder = new BoundedTextBuilder(MAX_TEXT_LENGTH);
        List<FileParseSourceRowDraft> sourceRows = new ArrayList<>();
        int pageCount = Math.max(document.getNumberOfPages(), 1);
        for (int pageNo = 1; pageNo <= pageCount && !builder.isFull(); pageNo++) {
            stripper.setStartPage(pageNo);
            stripper.setEndPage(pageNo);
            String pageText = stripper.getText(document);
            if (!StringUtils.hasText(pageText)) {
                continue;
            }
            String[] lines = pageText.split("\\R");
            int rowNo = 1;
            for (String line : lines) {
                if (builder.isFull()) {
                    break;
                }
                String rawText = normalizeLine(line);
                if (!StringUtils.hasText(rawText)) {
                    rowNo++;
                    continue;
                }
                builder.appendLine(rawText);
                sourceRows.add(sourceRow(
                        input,
                        pageNo,
                        rowNo,
                        rawText,
                        sourceRows.size() + 1
                ));
                rowNo++;
            }
        }
        return new ExtractionPayload(builder.toString(), sourceRows);
    }

    private FileParseInputExtraction attachmentFallback(
            FileParseTaskInputRow input,
            Path filePath,
            String fileName
    ) throws IOException {
        byte[] content = Files.readAllBytes(filePath);
        FileParseInputAttachment attachment = new FileParseInputAttachment(
                fileName,
                "application/pdf",
                content,
                input.getId(),
                input.getFileAssetId()
        );
        if (aiOcrService != null) {
            java.util.Optional<FileParseInputExtraction> extraction = aiOcrService.extractOcrRows(
                    input,
                    attachment,
                    "pdf_ocr_line",
                    "ai-pdf-ocr"
            );
            if (extraction.isPresent()) {
                return extraction.get();
            }
        }
        String text = "附件：" + fileName + "，文件类型：application/pdf。PDF 未抽取到可复制文本，请读取附件内容并参与结构化解析。";
        FileParseSourceRowDraft sourceRow = new FileParseSourceRowDraft();
        sourceRow.setTaskInputId(input.getId());
        sourceRow.setFileAssetId(input.getFileAssetId());
        sourceRow.setSourceType("pdf_attachment");
        sourceRow.setSourceLocator("attachment=" + fileName);
        sourceRow.setRawText(text);
        sourceRow.setSourceHash(sha256Text("pdf_attachment|" + fileName + "|" + content.length));
        sourceRow.setExtractorType("pdf-text-file");
        sourceRow.setExtractorVersion("v1");
        sourceRow.setSortNo(1);
        return new FileParseInputExtraction(
                "pdf-text-file",
                "attached",
                text.length(),
                "PDF 未抽取到可复制文本，已作为 AI 附件进入结构化解析上下文。",
                text,
                false,
                List.of(attachment),
                List.of(sourceRow)
        );
    }

    private FileParseSourceRowDraft sourceRow(
            FileParseTaskInputRow input,
            Integer pageNo,
            Integer rowNo,
            String rawText,
            Integer sortNo
    ) {
        FileParseSourceRowDraft row = new FileParseSourceRowDraft();
        row.setTaskInputId(input.getId());
        row.setFileAssetId(input.getFileAssetId());
        row.setSourceType("pdf_text_line");
        row.setSourceLocator("page=" + pageNo + ";line=" + rowNo);
        row.setPageNo(pageNo);
        row.setRowNo(rowNo);
        row.setRawText(rawText);
        row.setRawCellsJson(rawCellsJson(rawText));
        row.setSourceHash(sha256Text("pdf_text_line|" + pageNo + "|" + rowNo + "|" + rawText));
        row.setExtractorType("pdf-text-file");
        row.setExtractorVersion("v1");
        row.setSortNo(sortNo);
        return row;
    }

    private String rawCellsJson(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        List<String> cells = java.util.Arrays.stream(rawText.split("\\s{2,}"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (cells.size() <= 1) {
            return null;
        }
        return "[" + cells.stream()
                .map(cell -> "\"" + jsonEscape(cell) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        return line.replace('\u00A0', ' ')
                .replaceAll("[\\t ]+", " ")
                .trim();
    }

    private Path resolveInputFile(FileParseTaskInputRow input, Path storageRoot) {
        if (!StringUtils.hasText(input.getStorageKey())) {
            throw new IllegalArgumentException("文件输入缺少归档路径。");
        }
        Path root = storageRoot.toAbsolutePath().normalize();
        Path filePath = root.resolve(input.getStorageKey()).normalize();
        if (!filePath.startsWith(root)) {
            throw new IllegalArgumentException("文件路径不合法。");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("归档文件不存在。");
        }
        return filePath;
    }

    private String resolveFileName(FileParseTaskInputRow input) {
        if (StringUtils.hasText(input.getOriginalFileName())) {
            return input.getOriginalFileName();
        }
        if (StringUtils.hasText(input.getDisplayName())) {
            return input.getDisplayName();
        }
        return "input-file.pdf";
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
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static class ExtractionPayload {

        private final String text;
        private final List<FileParseSourceRowDraft> sourceRows;

        private ExtractionPayload(String text, List<FileParseSourceRowDraft> sourceRows) {
            this.text = text;
            this.sourceRows = sourceRows == null ? List.of() : sourceRows;
        }
    }

    private static class BoundedTextBuilder {

        private final int maxLength;
        private final StringBuilder builder = new StringBuilder();

        private BoundedTextBuilder(int maxLength) {
            this.maxLength = maxLength;
        }

        private void appendLine(String value) {
            if (!StringUtils.hasText(value) || isFull()) {
                return;
            }
            int remaining = maxLength - builder.length();
            if (remaining <= 0) {
                return;
            }
            String line = value + "\n";
            builder.append(line, 0, Math.min(remaining, line.length()));
        }

        private boolean isFull() {
            return builder.length() >= maxLength;
        }

        @Override
        public String toString() {
            return builder.toString().trim();
        }
    }
}
