package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(100)
public class AiFileAttachmentParseInputExtractor implements FileParseInputExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "image", "file");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp");

    @Override
    public boolean supports(FileParseTaskInputRow input) {
        if (input == null) {
            return false;
        }
        String inputType = normalize(input.getInputType());
        String extension = normalize(input.getFileExtension());
        return SUPPORTED_TYPES.contains(inputType) || SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) throws IOException {
        Path filePath = resolveInputFile(input, storageRoot);
        byte[] content = Files.readAllBytes(filePath);
        String fileName = resolveFileName(input);
        String contentType = resolveContentType(input);
        FileParseInputAttachment attachment = new FileParseInputAttachment(
                fileName,
                contentType,
                content,
                input.getId(),
                input.getFileAssetId()
        );
        String text = "附件：" + fileName + "，文件类型：" + contentType + "。请读取附件内容并参与结构化解析。";
        FileParseSourceRowDraft sourceRow = new FileParseSourceRowDraft();
        sourceRow.setTaskInputId(input.getId());
        sourceRow.setFileAssetId(input.getFileAssetId());
        sourceRow.setSourceType(resolveSourceType(input));
        sourceRow.setSourceLocator("attachment=" + fileName);
        sourceRow.setRawText(text);
        sourceRow.setSourceHash(sha256Text(sourceRow.getSourceType() + "|" + fileName + "|" + content.length));
        sourceRow.setExtractorType("ai-file-attachment");
        sourceRow.setExtractorVersion("v2");
        sourceRow.setSortNo(1);
        return new FileParseInputExtraction(
                "ai-file-attachment",
                "attached",
                text.length(),
                "文件已作为 AI 附件进入结构化解析上下文。",
                text,
                false,
                List.of(attachment),
                List.of(sourceRow)
        );
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
        return "input-file." + normalize(input.getFileExtension());
    }

    private String resolveContentType(FileParseTaskInputRow input) {
        String extension = normalize(input.getFileExtension());
        if ("pdf".equals(extension)) {
            return "application/pdf";
        }
        if ("png".equals(extension)) {
            return "image/png";
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return "image/jpeg";
        }
        if ("webp".equals(extension)) {
            return "image/webp";
        }
        if (StringUtils.hasText(input.getContentType())) {
            return input.getContentType();
        }
        return "application/octet-stream";
    }

    private String resolveSourceType(FileParseTaskInputRow input) {
        String extension = normalize(input.getFileExtension());
        if ("pdf".equals(extension)) {
            return "pdf_attachment";
        }
        if ("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension) || "webp".equals(extension)) {
            return "image_attachment";
        }
        return "file_attachment";
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
}
