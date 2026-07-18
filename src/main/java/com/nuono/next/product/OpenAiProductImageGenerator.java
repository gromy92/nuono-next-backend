package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class OpenAiProductImageGenerator implements ProductImageGenerator {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    OpenAiProductImageGenerator(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public GeneratedProductImage generate(String prompt, List<String> referenceImageUrls) {
        AiProperties.OpenAi config = properties.getOpenai();
        if (!properties.isOpenAiConfigured()) {
            throw new IllegalStateException("OpenAI 图片生成未配置，请先配置 OPENAI_API_KEY。");
        }
        List<String> publicReferences = new ArrayList<>();
        for (String value : referenceImageUrls == null ? List.<String>of() : referenceImageUrls) {
            if (StringUtils.hasText(value) && (value.startsWith("https://") || value.startsWith("http://"))) {
                publicReferences.add(value.trim());
            }
            if (publicReferences.size() >= 4) break;
        }
        boolean edit = !publicReferences.isEmpty();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.getDefaultImageModel());
        payload.put("prompt", prompt);
        payload.put("size", "1024x1024");
        payload.put("quality", config.getImageQuality());
        payload.put("output_format", "png");
        if (edit) {
            List<Map<String, String>> images = new ArrayList<>();
            for (String url : publicReferences) images.add(Map.of("image_url", url));
            payload.put("images", images);
        }
        String path = edit ? config.getImageEditPath() : config.getImageGenerationPath();
        URI uri = URI.create(trimSlash(config.getBaseUrl()) + normalizePath(path));
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(60, config.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("图片生成服务返回 HTTP " + response.statusCode() + "：" + abbreviate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String encoded = root.path("data").path(0).path("b64_json").asText();
            if (!StringUtils.hasText(encoded)) throw new IllegalStateException("图片生成服务未返回图片内容。");
            return new GeneratedProductImage(Base64.getDecoder().decode(encoded), "image/png");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片生成请求已中断。", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("图片生成请求失败：" + exception.getMessage(), exception);
        }
    }

    private String trimSlash(String value) { return value == null ? "" : value.replaceAll("/+$", ""); }
    private String normalizePath(String value) { return value != null && value.startsWith("/") ? value : "/" + value; }
    private String abbreviate(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= 400 ? text : text.substring(0, 400) + "...";
    }
}
