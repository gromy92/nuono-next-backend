package com.nuono.next.filemanagement.parse;

import java.nio.file.Path;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
@Order(10)
public class ManualTextFileParseInputExtractor implements FileParseInputExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of("manual_text", "ocr_text");

    @Override
    public boolean supports(FileParseTaskInputRow input) {
        return input != null && SUPPORTED_TYPES.contains(input.getInputType());
    }

    @Override
    public FileParseInputExtraction extract(FileParseTaskInputRow input, Path storageRoot) {
        String content = input.getTextContent();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文本输入内容不能为空。");
        }
        return new FileParseInputExtraction(
                "manual-text",
                "extracted",
                content.length(),
                "文本输入已进入 AI 解析上下文。",
                content,
                false
        );
    }
}
