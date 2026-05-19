package com.nuono.next.filemanagement.parse;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiInputAttachment;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseAiOcrService {

    private final AiCapabilityService aiCapabilityService;
    private final String aiModel;
    private final String aiReasoningEffort;
    private final Integer aiMaxOutputTokens;
    private final Integer aiTimeoutSeconds;

    @Autowired
    public FileParseAiOcrService(
            AiCapabilityService aiCapabilityService,
            @Value("${nuono.file-management.parse.ai.model:gpt-5.4-mini}") String aiModel,
            @Value("${nuono.file-management.parse.ai.reasoning-effort:low}") String aiReasoningEffort,
            @Value("${nuono.file-management.parse.ocr.max-output-tokens:8000}") Integer aiMaxOutputTokens,
            @Value("${nuono.file-management.parse.ocr.timeout-seconds:120}") Integer aiTimeoutSeconds
    ) {
        this.aiCapabilityService = aiCapabilityService;
        this.aiModel = aiModel;
        this.aiReasoningEffort = aiReasoningEffort;
        this.aiMaxOutputTokens = aiMaxOutputTokens;
        this.aiTimeoutSeconds = aiTimeoutSeconds;
    }

    public Optional<FileParseInputExtraction> extractOcrRows(
            FileParseTaskInputRow input,
            FileParseInputAttachment attachment,
            String sourceType,
            String extractorType
    ) {
        if (input == null || attachment == null) {
            return Optional.empty();
        }
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode("file-management-parse");
        command.setOperationCode("source-ocr");
        command.setOperatorUserId(null);
        command.setModel(aiModel);
        command.setReasoningEffort(aiReasoningEffort);
        command.setMaxOutputTokens(aiMaxOutputTokens);
        command.setTimeoutSeconds(aiTimeoutSeconds);
        command.setSchemaName("file_management_parse_source_ocr");
        command.setSchema(buildOcrSchema());
        command.setInstructions("你是文件 OCR 抽取助手。只输出符合 schema 的 JSON，不要输出解释性文本。"
                + "\n逐行提取附件中真实可见的文字，保持阅读顺序。"
                + "\n不要翻译、不要改写、不要补全看不清的内容。"
                + "\n如果没有可读文字，返回空 lines 数组。");
        command.setPrompt("请从附件中提取文字行。文件名：" + attachment.getFileName()
                + "\n输出 lines，每行包含 pageNo、lineNo、text。图片 pageNo 填 1；PDF 按页码填写。");
        command.setInputAttachments(List.of(new AiInputAttachment(
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getContent()
        )));
        command.setMetadata(Map.of(
                "taskInputId", String.valueOf(input.getId()),
                "fileAssetId", String.valueOf(input.getFileAssetId()),
                "sourceType", sourceType
        ));

        AiStructuredTextResult result;
        try {
            result = aiCapabilityService.createStructuredText(command);
        } catch (RuntimeException error) {
            return Optional.empty();
        }
        if (!result.isSuccess() || result.getParsedJson() == null) {
            return Optional.empty();
        }
        List<FileParseSourceRowDraft> rows = toSourceRows(input, result.getParsedJson(), sourceType, extractorType);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        String text = rows.stream()
                .map(FileParseSourceRowDraft::getRawText)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));
        return Optional.of(new FileParseInputExtraction(
                extractorType,
                "extracted",
                text.length(),
                "文件已通过 AI OCR 抽取文本行并进入 AI 解析上下文。",
                text,
                false,
                List.of(),
                rows
        ));
    }

    private List<FileParseSourceRowDraft> toSourceRows(
            FileParseTaskInputRow input,
            Map<String, Object> parsedJson,
            String sourceType,
            String extractorType
    ) {
        Object linesValue = parsedJson.get("lines");
        if (!(linesValue instanceof List)) {
            return List.of();
        }
        List<FileParseSourceRowDraft> rows = new ArrayList<>();
        int fallbackLineNo = 1;
        for (Object lineValue : (List<?>) linesValue) {
            if (!(lineValue instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> line = (Map<String, Object>) lineValue;
            String text = normalizeLine(text(line.get("text")));
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Integer pageNo = integer(line.get("pageNo"));
            Integer lineNo = integer(line.get("lineNo"));
            if (lineNo == null) {
                lineNo = fallbackLineNo;
            }
            FileParseSourceRowDraft row = new FileParseSourceRowDraft();
            row.setTaskInputId(input.getId());
            row.setFileAssetId(input.getFileAssetId());
            row.setSourceType(sourceType);
            row.setSourceLocator("page=" + (pageNo == null ? 1 : pageNo) + ";line=" + lineNo);
            row.setPageNo(pageNo == null ? 1 : pageNo);
            row.setRowNo(lineNo);
            row.setRawText(text);
            row.setSourceHash(sha256Text(sourceType + "|" + row.getSourceLocator() + "|" + text));
            row.setExtractorType(extractorType);
            row.setExtractorVersion("v1");
            row.setSortNo(rows.size() + 1);
            rows.add(row);
            fallbackLineNo++;
        }
        return rows;
    }

    private Map<String, Object> buildOcrSchema() {
        Map<String, Object> lineProperties = new LinkedHashMap<>();
        lineProperties.put("pageNo", Map.of("type", List.of("integer", "null")));
        lineProperties.put("lineNo", Map.of("type", List.of("integer", "null")));
        lineProperties.put("text", Map.of("type", "string"));

        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "object");
        line.put("required", List.of("pageNo", "lineNo", "text"));
        line.put("additionalProperties", false);
        line.put("properties", lineProperties);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("lines"));
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "lines", Map.of("type", "array", "items", line)
        ));
        return schema;
    }

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        return line.replace('\u00A0', ' ')
                .replaceAll("[\\t ]+", " ")
                .trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integer(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
}
