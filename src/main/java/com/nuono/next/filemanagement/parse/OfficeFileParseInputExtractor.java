package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(30)
public class OfficeFileParseInputExtractor implements FileParseInputExtractor {

    private static final int MAX_TEXT_LENGTH = 120_000;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("xlsx", "xls", "csv", "docx", "doc");

    @Override
    public boolean supports(FileParseTaskInputRow input) {
        if (input == null) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(normalize(input.getFileExtension()));
    }

    @Override
    public FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) throws IOException {
        Path filePath = resolveInputFile(input, storageRoot);
        String extension = normalize(input.getFileExtension());
        ExtractionPayload payload;
        String message;
        if ("csv".equals(extension)) {
            payload = readDelimitedRows(input, filePath);
            message = "CSV 文件已读取并进入 AI 解析上下文。";
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            payload = readWorkbook(input, filePath);
            message = "Excel 文件已抽取表格文本并进入 AI 解析上下文。";
        } else if ("docx".equals(extension)) {
            payload = new ExtractionPayload(readDocx(filePath), List.of());
            message = "Word 文件已抽取文本并进入 AI 解析上下文。";
        } else {
            payload = new ExtractionPayload(readDoc(filePath), List.of());
            message = "Word 文件已抽取文本并进入 AI 解析上下文。";
        }
        if (!StringUtils.hasText(payload.text)) {
            throw new IllegalArgumentException("文件未抽取到可解析文本。");
        }
        return new FileParseInputExtraction(
                "office-file",
                "extracted",
                payload.text.length(),
                message,
                payload.text,
                false,
                List.of(),
                payload.sourceRows
        );
    }

    private ExtractionPayload readDelimitedRows(FileParseTaskInputRow input, Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        String[] lines = content.split("\\R");
        BoundedTextBuilder builder = new BoundedTextBuilder(MAX_TEXT_LENGTH);
        List<FileParseSourceRowDraft> rows = new ArrayList<>();
        for (int index = 0; index < lines.length && !builder.isFull(); index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            builder.appendLine(line);
            List<String> cells = splitDelimitedLine(line);
            rows.add(sourceRow(
                    input,
                    "csv_row",
                    "row=" + (index + 1),
                    null,
                    null,
                    index + 1,
                    "A:" + columnName(cells.size()),
                    line,
                    cells,
                    rows.size() + 1
            ));
        }
        return new ExtractionPayload(builder.toString(), rows);
    }

    private ExtractionPayload readWorkbook(FileParseTaskInputRow input, Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            BoundedTextBuilder builder = new BoundedTextBuilder(MAX_TEXT_LENGTH);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<FileParseSourceRowDraft> sourceRows = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets() && !builder.isFull(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                builder.appendLine("## Sheet: " + sheet.getSheetName());
                for (Row row : sheet) {
                    if (builder.isFull()) {
                        break;
                    }
                    StringJoiner joiner = new StringJoiner("\t");
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell);
                        if (StringUtils.hasText(value)) {
                            String trimmed = value.trim();
                            joiner.add(trimmed);
                            cells.add(trimmed);
                        }
                    }
                    String rowText = joiner.toString();
                    if (StringUtils.hasText(rowText)) {
                        builder.appendLine(rowText);
                        sourceRows.add(sourceRow(
                                input,
                                "excel_row",
                                "sheet=" + sheet.getSheetName() + ";row=" + (row.getRowNum() + 1),
                                sheet.getSheetName(),
                                null,
                                row.getRowNum() + 1,
                                "A:" + columnName(cells.size()),
                                rowText,
                                cells,
                                sourceRows.size() + 1
                        ));
                    }
                }
            }
            return new ExtractionPayload(builder.toString(), sourceRows);
        } catch (RuntimeException exception) {
            throw new IOException("Excel 文件读取失败。", exception);
        }
    }

    private String readDocx(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            BoundedTextBuilder builder = new BoundedTextBuilder(MAX_TEXT_LENGTH);
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (builder.isFull()) {
                    break;
                }
                String text = paragraph.getText();
                if (StringUtils.hasText(text)) {
                    builder.appendLine(text.trim());
                }
            }
            for (XWPFTable table : document.getTables()) {
                if (builder.isFull()) {
                    break;
                }
                appendTable(builder, table);
            }
            return builder.toString();
        } catch (RuntimeException exception) {
            throw new IOException("Word 文件读取失败。", exception);
        }
    }

    private String readDoc(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return truncate(extractor.getText());
        } catch (RuntimeException exception) {
            throw new IOException("Word 文件读取失败。", exception);
        }
    }

    private void appendTable(BoundedTextBuilder builder, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            if (builder.isFull()) {
                break;
            }
            StringJoiner joiner = new StringJoiner("\t");
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText();
                if (StringUtils.hasText(text)) {
                    joiner.add(text.trim());
                }
            }
            String rowText = joiner.toString();
            if (StringUtils.hasText(rowText)) {
                builder.appendLine(rowText);
            }
        }
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

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    private FileParseSourceRowDraft sourceRow(
            FileParseTaskInputRow input,
            String sourceType,
            String sourceLocator,
            String sheetName,
            Integer tableNo,
            Integer rowNo,
            String columnRange,
            String rawText,
            List<String> cells,
            Integer sortNo
    ) {
        FileParseSourceRowDraft row = new FileParseSourceRowDraft();
        row.setTaskInputId(input.getId());
        row.setFileAssetId(input.getFileAssetId());
        row.setSourceType(sourceType);
        row.setSourceLocator(sourceLocator);
        row.setSheetName(sheetName);
        row.setTableNo(tableNo);
        row.setRowNo(rowNo);
        row.setColumnRange(columnRange);
        row.setRawText(rawText);
        row.setRawCellsJson(cellsJson(cells));
        row.setSourceHash(sha256Text(sourceType + "|" + sourceLocator + "|" + rawText));
        row.setExtractorType("office-file");
        row.setExtractorVersion("v2");
        row.setSortNo(sortNo);
        return row;
    }

    private List<String> splitDelimitedLine(String line) {
        if (line.contains("\t")) {
            return java.util.Arrays.stream(line.split("\\t"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }
        return java.util.Arrays.stream(line.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private String cellsJson(List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return null;
        }
        return "[" + cells.stream()
                .map(cell -> "\"" + jsonEscape(cell) + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private String columnName(int size) {
        if (size <= 0) {
            return "A";
        }
        int current = size;
        StringBuilder builder = new StringBuilder();
        while (current > 0) {
            current--;
            builder.insert(0, (char) ('A' + (current % 26)));
            current /= 26;
        }
        return builder.toString();
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
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
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

    private static class ExtractionPayload {

        private final String text;
        private final List<FileParseSourceRowDraft> sourceRows;

        private ExtractionPayload(String text, List<FileParseSourceRowDraft> sourceRows) {
            this.text = text;
            this.sourceRows = sourceRows == null ? List.of() : sourceRows;
        }
    }
}
