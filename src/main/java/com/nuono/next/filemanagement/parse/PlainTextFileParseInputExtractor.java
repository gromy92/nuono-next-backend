package com.nuono.next.filemanagement.parse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
@Order(20)
public class PlainTextFileParseInputExtractor implements FileParseInputExtractor {

    @Override
    public boolean supports(FileParseTaskInputRow input) {
        return input != null && "txt".equalsIgnoreCase(input.getFileExtension());
    }

    @Override
    public FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) throws IOException {
        Path filePath = resolveInputFile(input, storageRoot);
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return new FileParseInputExtraction(
                "plain-text-file",
                "extracted",
                content.length(),
                "文本文件已读取并进入 AI 解析上下文。",
                content,
                false
        );
    }

    private Path resolveInputFile(FileParseTaskInputRow input, Path storageRoot) {
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
}
