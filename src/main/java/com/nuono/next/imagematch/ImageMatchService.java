package com.nuono.next.imagematch;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiInputAttachment;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageMatchService {

    static final String FEATURE_CODE = "PRODUCT_IMAGE_MATCH";
    static final String OPERATION_CODE = "COMPARE_SAME_PRODUCT_SCORE";
    static final String SCHEMA_NAME = "product_image_match_score_v1";
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final AiCapabilityService aiCapabilityService;

    public ImageMatchService(AiCapabilityService aiCapabilityService) {
        this.aiCapabilityService = aiCapabilityService;
    }

    public ImageMatchView compare(ImageMatchCommand command) {
        ImageMatchCommand normalized = command == null ? new ImageMatchCommand() : command;
        ImageMatchCommand.ImageInput original = resolveImage(normalized, true);
        ImageMatchCommand.ImageInput candidate = resolveImage(normalized, false);
        AiStructuredTextResult result = aiCapabilityService.createStructuredText(buildAiCommand(original, candidate));
        if (result == null || !result.isSuccess() || result.getParsedJson() == null) {
            throw new ImageMatchException(HttpStatus.BAD_GATEWAY, "AI 图片匹配失败");
        }
        Object rawScore = result.getParsedJson().get("similarityScore");
        if (!(rawScore instanceof Number)) {
            throw new ImageMatchException(HttpStatus.BAD_GATEWAY, "AI 图片匹配结果缺少 similarityScore");
        }
        return new ImageMatchView(clamp(((Number) rawScore).intValue()));
    }

    private ImageMatchCommand.ImageInput resolveImage(ImageMatchCommand command, boolean original) {
        ImageMatchCommand.ImageInput direct = original ? command.getOriginalUpload() : command.getCandidateUpload();
        if (direct != null) {
            return validateInput(direct, label(original));
        }
        MultipartFile file = original ? command.getOriginalImageFile() : command.getCandidateImageFile();
        if (file != null && !file.isEmpty()) {
            return validateInput(readMultipart(file, label(original)), label(original));
        }
        String url = original ? command.getOriginalImageUrl() : command.getCandidateImageUrl();
        if (StringUtils.hasText(url)) {
            return validateInput(downloadUrl(url.trim(), label(original)), label(original));
        }
        throw new ImageMatchException(HttpStatus.BAD_REQUEST, label(original) + "图片不能为空");
    }

    private ImageMatchCommand.ImageInput readMultipart(MultipartFile file, String label) {
        try {
            return new ImageMatchCommand.ImageInput(
                    safeFileName(file.getOriginalFilename(), label + ".image"),
                    normalizeContentType(file.getContentType()),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片读取失败");
        }
    }

    private ImageMatchCommand.ImageInput downloadUrl(String rawUrl, String label) {
        URI uri = parseAndValidateUrl(rawUrl, label);
        try {
            URLConnection rawConnection = uri.toURL().openConnection();
            if (!(rawConnection instanceof HttpURLConnection)) {
                throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接协议不支持");
            }
            HttpURLConnection connection = (HttpURLConnection) rawConnection;
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片下载失败");
            }
            String contentType = normalizeContentType(connection.getContentType());
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_IMAGE_BYTES) {
                throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片不能超过 8MB");
            }
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] bytes = readLimited(inputStream, label);
                return new ImageMatchCommand.ImageInput(fileNameFromUrl(uri, label), contentType, bytes);
            } finally {
                connection.disconnect();
            }
        } catch (ImageMatchException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片下载失败");
        }
    }

    private URI parseAndValidateUrl(String rawUrl, String label) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接不合法");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接只支持 http/https");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接缺少 host");
        }
        rejectInternalHost(uri.getHost(), label);
        return uri;
    }

    private void rejectInternalHost(String host, String label) {
        String normalizedHost = host.trim().toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接不能指向内网地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接不能指向内网地址");
                }
            }
        } catch (ImageMatchException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片链接 host 无法解析");
        }
    }

    private ImageMatchCommand.ImageInput validateInput(ImageMatchCommand.ImageInput input, String label) {
        if (input == null || input.getBytes().length == 0) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片不能为空");
        }
        if (input.getBytes().length > MAX_IMAGE_BYTES) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片不能超过 8MB");
        }
        String contentType = normalizeContentType(input.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片格式不支持");
        }
        return new ImageMatchCommand.ImageInput(
                safeFileName(input.getFileName(), label + ".image"),
                contentType,
                input.getBytes()
        );
    }

    private byte[] readLimited(InputStream inputStream, String label) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_IMAGE_BYTES) {
                throw new ImageMatchException(HttpStatus.BAD_REQUEST, label + "图片不能超过 8MB");
            }
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private String normalizeContentType(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int separatorIndex = value.indexOf(';');
        String normalized = separatorIndex >= 0 ? value.substring(0, separatorIndex) : value;
        return normalized.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String safeFileName(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String fileName = value.trim();
        int slashIndex = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return slashIndex >= 0 ? fileName.substring(slashIndex + 1) : fileName;
    }

    private String fileNameFromUrl(URI uri, String label) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            return label + ".image";
        }
        return safeFileName(path, label + ".image");
    }

    private String label(boolean original) {
        return original ? "原图" : "匹配图";
    }

    private AiStructuredTextCommand buildAiCommand(
            ImageMatchCommand.ImageInput original,
            ImageMatchCommand.ImageInput candidate
    ) {
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode(FEATURE_CODE);
        command.setOperationCode(OPERATION_CODE);
        command.setSchemaName(SCHEMA_NAME);
        command.setSchema(scoreSchema());
        command.setMaxOutputTokens(120);
        command.setInstructions("Only output JSON matching the schema.");
        command.setPrompt(String.join("\n",
                "Compare two ecommerce product images for same-item matching.",
                "Return similarityScore from 0 to 100.",
                "High score means the visible product is effectively the same item.",
                "Similar category or style alone must not receive a high score.",
                "Output only JSON."
        ));
        command.setInputAttachments(List.of(
                new AiInputAttachment(original.getFileName(), original.getContentType(), original.getBytes()),
                new AiInputAttachment(candidate.getFileName(), candidate.getContentType(), candidate.getBytes())
        ));
        return command;
    }

    private Map<String, Object> scoreSchema() {
        return object(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("similarityScore"),
                "properties", object(
                        "similarityScore", object(
                                "type", "integer",
                                "minimum", 0,
                                "maximum", 100
                        )
                )
        );
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
