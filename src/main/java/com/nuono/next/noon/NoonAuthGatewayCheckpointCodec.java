package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class NoonAuthGatewayCheckpointCodec {
    private final ObjectMapper objectMapper;

    NoonAuthGatewayCheckpointCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String encodeChallenge(
            GenerationSnapshot generation,
            NoonEmailOtpReader.MailboxCursor cursor,
            Instant sentAt
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("generation", generation(generation));
        root.put("uidValidity", cursor.getUidValidity());
        root.put("lastUid", cursor.getLastUid());
        root.put("capturedAt", cursor.getCapturedAt().toString());
        root.put("sentAt", sentAt.toString());
        return write(root);
    }

    Challenge decodeChallenge(String payload) {
        JsonNode root = read(payload);
        return new Challenge(
                generation(root.path("generation")),
                new NoonEmailOtpReader.MailboxCursor(
                        root.path("uidValidity").asLong(),
                        root.path("lastUid").asLong(),
                        Instant.parse(root.path("capturedAt").asText())
                ),
                Instant.parse(root.path("sentAt").asText())
        );
    }

    String encodeGrant(GrantSnapshot grant) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("generation", generation(grant.getGeneration()));
        root.put("accessToken", grant.getAccessToken());
        ArrayNode projects = root.putArray("projectCodes");
        grant.getProjectCodes().forEach(projects::add);
        return write(root);
    }

    GrantSnapshot decodeGrant(String payload) {
        JsonNode root = read(payload);
        List<String> projects = new ArrayList<>();
        root.path("projectCodes").forEach(value -> projects.add(value.asText()));
        return new GrantSnapshot(
                generation(root.path("generation")),
                requiredText(root, "accessToken"),
                projects
        );
    }

    private ObjectNode generation(GenerationSnapshot value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("email", value.getEmail());
        node.put("userCode", value.getUserCode());
        node.put("codeVerifier", value.getCodeVerifier());
        node.put("pkceKey", value.getPkceKey());
        node.put("cookieHeader", value.getCookieHeader());
        return node;
    }

    private GenerationSnapshot generation(JsonNode node) {
        return new GenerationSnapshot(
                requiredText(node, "email"),
                requiredText(node, "userCode"),
                requiredText(node, "codeVerifier"),
                requiredText(node, "pkceKey"),
                node.path("cookieHeader").asText("")
        );
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid Noon auth checkpoint", exception);
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode Noon auth checkpoint", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Noon auth checkpoint missing " + field);
        }
        return value;
    }

    static final class Challenge {
        final GenerationSnapshot generation;
        final NoonEmailOtpReader.MailboxCursor cursor;
        final Instant sentAt;

        Challenge(
                GenerationSnapshot generation,
                NoonEmailOtpReader.MailboxCursor cursor,
                Instant sentAt
        ) {
            this.generation = generation;
            this.cursor = cursor;
            this.sentAt = sentAt;
        }
    }

    static final class GenerationSnapshot {
        private final String email;
        private final String userCode;
        private final String codeVerifier;
        private final String pkceKey;
        private final String cookieHeader;

        GenerationSnapshot(
                String email, String userCode, String codeVerifier, String pkceKey, String cookieHeader
        ) {
            this.email = email;
            this.userCode = userCode;
            this.codeVerifier = codeVerifier;
            this.pkceKey = pkceKey;
            this.cookieHeader = cookieHeader;
        }

        String getEmail() { return email; }
        String getUserCode() { return userCode; }
        String getCodeVerifier() { return codeVerifier; }
        String getPkceKey() { return pkceKey; }
        String getCookieHeader() { return cookieHeader; }
    }

    static final class GrantSnapshot {
        private final GenerationSnapshot generation;
        private final String accessToken;
        private final List<String> projectCodes;

        GrantSnapshot(GenerationSnapshot generation, String accessToken, List<String> projectCodes) {
            this.generation = generation;
            this.accessToken = accessToken;
            this.projectCodes = List.copyOf(projectCodes);
        }

        GenerationSnapshot getGeneration() { return generation; }
        String getAccessToken() { return accessToken; }
        List<String> getProjectCodes() { return projectCodes; }
    }
}
