package com.nuono.next.procurement.aliorder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Ali1688OpenApiSigner {

    public String hmacSha1Hex(Map<String, String> params, String appSecret) {
        requireSecret(appSecret);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return uppercaseHex(mac.doFinal(canonicalParams(params).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign 1688 OAuth parameters.", exception);
        }
    }

    public String apiSignature(String path, Map<String, String> params, String appSecret) {
        requireSecret(appSecret);
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("1688 API path is required for signature.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return uppercaseHex(mac.doFinal((signaturePath(path) + canonicalParams(params)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign 1688 API request.", exception);
        }
    }

    private String signaturePath(String path) {
        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("openapi/")) {
            normalized = normalized.substring("openapi/".length());
        }
        return normalized;
    }

    private String canonicalParams(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (params != null) {
            params.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null && !"_aop_signature".equals(key)) {
                    sorted.put(key, value);
                }
            });
        }
        StringBuilder builder = new StringBuilder();
        sorted.forEach((key, value) -> builder.append(key).append(value));
        return builder.toString();
    }

    private void requireSecret(String appSecret) {
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("1688 AppSecret is required for request signing.");
        }
    }

    private String uppercaseHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }
}
