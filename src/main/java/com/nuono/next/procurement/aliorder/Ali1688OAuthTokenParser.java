package com.nuono.next.procurement.aliorder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

final class Ali1688OAuthTokenParser {

    private final ObjectMapper objectMapper;

    Ali1688OAuthTokenParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    TokenPayload parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode payload = root.has("result") && root.get("result").isObject()
                    ? root.get("result")
                    : root;
            TokenPayload token = new TokenPayload();
            token.accessToken = firstText(payload, "access_token", "accessToken");
            token.refreshToken = firstText(payload, "refresh_token", "refreshToken");
            token.providerAccountId = firstText(
                    payload,
                    "memberId",
                    "member_id",
                    "resource_owner",
                    "loginId",
                    "login_id",
                    "accountId"
            );
            token.accountLabel = firstText(
                    payload,
                    "resource_owner",
                    "loginName",
                    "login_id",
                    "memberId",
                    "member_id"
            );
            Long expiresIn = firstLong(payload, "expires_in", "expiresIn");
            token.expiresAt = expiresIn == null
                    ? null
                    : LocalDateTime.now().plusSeconds(Math.max(0, expiresIn));
            if (!StringUtils.hasText(token.accessToken)) {
                throw new IllegalStateException("1688 OAuth token 响应缺少 access_token。");
            }
            return token;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("1688 OAuth token 响应解析失败。", exception);
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... fieldNames) {
        String value = firstText(node, fieldNames);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static final class TokenPayload {
        String accessToken;
        String refreshToken;
        String providerAccountId;
        String accountLabel;
        LocalDateTime expiresAt;
    }
}
