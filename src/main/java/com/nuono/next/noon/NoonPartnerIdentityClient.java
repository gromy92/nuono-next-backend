package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.util.StringUtils;

/** Hides the Noon partner-identity OTP/PKCE protocol behind one cohesive client. */
final class NoonPartnerIdentityClient {
    private static final String WEB_CLIENT_CODE = "web";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ObjectMapper objectMapper;
    private final String userLookupUrl;
    private final String pkceUrl;
    private final String generateUrl;
    private final String validateUrl;
    private final String projectListUrl;
    private final String sessionCreateUrl;

    NoonPartnerIdentityClient(
            ObjectMapper objectMapper,
            String userLookupUrl,
            String pkceUrl,
            String generateUrl,
            String validateUrl,
            String projectListUrl,
            String sessionCreateUrl
    ) {
        this.objectMapper = objectMapper;
        this.userLookupUrl = userLookupUrl;
        this.pkceUrl = pkceUrl;
        this.generateUrl = generateUrl;
        this.validateUrl = validateUrl;
        this.projectListUrl = projectListUrl;
        this.sessionCreateUrl = sessionCreateUrl;
    }

    NoonSessionGateway.PartnerIdentityUser lookupEmailOtpUser(
            NoonSessionGateway.AuthSessionState state,
            String email
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelIdentifier", email);
        body.put("client_code", WEB_CLIENT_CODE);
        return extractEmailOtpUser(state.postJson(null, null, userLookupUrl, body, false, null));
    }

    NoonSessionGateway.PkcePair createPkce(NoonSessionGateway.AuthSessionState state) {
        String verifier = generateCodeVerifier();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code_challenge", generateCodeChallenge(verifier));
        body.put("client_code", WEB_CLIENT_CODE);
        JsonNode root = state.postJson(null, null, pkceUrl, body, false, null);
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon PKCE 初始化失败：" + providerError(root));
        }
        String key = text(root, "pkce_key");
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException("Noon PKCE 初始化失败：缺少 pkce_key。");
        }
        return new NoonSessionGateway.PkcePair(verifier, key);
    }

    void sendEmailOtp(
            NoonSessionGateway.AuthSessionState state,
            String userCode,
            NoonSessionGateway.PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channelCode", "emailotp");
        body.put("client_code", WEB_CLIENT_CODE);
        body.put("userCode", userCode);
        body.put("code_verifier", pkce.getCodeVerifier());
        body.put("pkce_key", pkce.getPkceKey());
        JsonNode root = state.postJson(
                null, null, generateUrl, body, false, null,
                NoonJsonRequestPolicy.ONE_SHOT_AFTER_PACING
        );
        if (root == null || !"ok".equalsIgnoreCase(root.path("emailotp").asText(null))) {
            throw new IllegalStateException("Noon emailotp 发送失败：" + providerError(root));
        }
    }

    String validateEmailOtp(
            NoonSessionGateway.AuthSessionState state,
            String userCode,
            String email,
            String otpCode,
            NoonSessionGateway.PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel_code", "emailotp");
        body.put("client_code", WEB_CLIENT_CODE);
        body.put("user_code", userCode);
        body.put("channel_identifier", email);
        body.put("channel_credential", otpCode);
        body.put("code_verifier", pkce.getCodeVerifier());
        body.put("pkce_key", pkce.getPkceKey());
        final JsonNode root;
        try {
            root = state.postJson(
                    null, null, validateUrl, body, false, null,
                    NoonJsonRequestPolicy.ONE_SHOT_AFTER_PACING
            );
        } catch (SessionExpiredException exception) {
            throw exception.toHttpException();
        }
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new IllegalStateException("Noon emailotp validate 失败：" + providerError(root));
        }
        String accessToken = text(root, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new IllegalStateException("Noon emailotp validate 失败：缺少 access_token。");
        }
        return accessToken;
    }

    List<NoonSessionGateway.MerchantProject> listProjects(
            NoonSessionGateway.AuthSessionState state,
            String userCode,
            String accessToken
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userCode", userCode);
        body.put("accessToken", accessToken);
        return extractProjects(state.postJson(null, null, projectListUrl, body, false, null));
    }

    void createSession(
            NoonSessionGateway.AuthSessionState state,
            String userCode,
            String accessToken,
            String projectCode,
            NoonSessionGateway.PkcePair pkce
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("userCode", userCode);
        body.put("accessToken", accessToken);
        body.put("pkce_key", pkce.getPkceKey());
        body.put("projectCode", projectCode);
        body.put("clientCode", WEB_CLIENT_CODE);
        body.put("code_verifier", pkce.getCodeVerifier());
        state.postJson(
                projectCode, null, sessionCreateUrl, body, false, null,
                NoonJsonRequestPolicy.ONE_SHOT_AFTER_PACING
        );
        if (!StringUtils.hasText(state.exportAuthCookieHeader())) {
            throw new IllegalStateException("Noon session/create 未返回有效 Cookie。");
        }
    }

    static NoonSessionGateway.PartnerIdentityUser extractEmailOtpUser(JsonNode root) {
        if (root == null || !root.isArray() || root.size() == 0) {
            throw new IllegalStateException("Noon 账号不存在或 lookup 响应为空。");
        }
        JsonNode user = root.get(0);
        String userCode = firstText(user, "userCode", "user_code");
        if (!StringUtils.hasText(userCode)) {
            throw new IllegalStateException("Noon lookup 响应缺少 userCode。");
        }
        JsonNode channels = user.path("channels");
        if (channels.isArray()) {
            for (JsonNode channel : channels) {
                if ("emailotp".equalsIgnoreCase(firstText(
                        channel, "channelCode", "channel_code"
                ))) {
                    return new NoonSessionGateway.PartnerIdentityUser(userCode);
                }
            }
        }
        throw new IllegalStateException("该 Noon 商家后台账号未启用邮箱验证码登录。");
    }

    static List<NoonSessionGateway.MerchantProject> extractProjects(JsonNode root) {
        JsonNode nodes = root == null ? MissingNode.getInstance() : root.path("projects");
        if (!nodes.isArray() || nodes.size() == 0) {
            throw new IllegalStateException("Noon 账号没有可用 Project。");
        }
        List<NoonSessionGateway.MerchantProject> projects = new ArrayList<>();
        for (JsonNode node : nodes) {
            String projectCode = firstText(node, "projectCode", "project_code");
            if (!StringUtils.hasText(projectCode)) {
                throw new IllegalStateException("Noon project/list 响应缺少 projectCode。");
            }
            projects.add(new NoonSessionGateway.MerchantProject(
                    projectCode,
                    firstText(node, "projectName", "project_name"),
                    firstText(node, "orgCode", "org_code"),
                    firstText(node, "orgName", "org_name")
            ));
        }
        return projects;
    }

    static NoonSessionGateway.MerchantProject selectProject(
            List<NoonSessionGateway.MerchantProject> projects,
            String requestedProjectCode
    ) {
        String requested = normalize(requestedProjectCode);
        for (NoonSessionGateway.MerchantProject project : projects) {
            if (StringUtils.hasText(project.getProjectCode())
                    && project.getProjectCode().equalsIgnoreCase(requested)) {
                return project;
            }
        }
        throw new NoonAccountProjectExcludedException(requested);
    }

    static String generateCodeVerifier() {
        byte[] randomBytes = new byte[96];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    static String generateCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "生成 Noon PKCE challenge 失败：" + exception.getMessage(), exception
            );
        }
    }

    private static String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "empty response";
        }
        JsonNode error = root.path("err");
        if (error.isArray() && error.size() > 0) {
            String value = normalize(error.get(0).asText(null));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        String value = firstText(
                root, "err", "error", "message", "errorMessage", "error_message"
        );
        return StringUtils.hasText(value) ? value : "provider response indicated failure";
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : normalize(value.asText(null));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
