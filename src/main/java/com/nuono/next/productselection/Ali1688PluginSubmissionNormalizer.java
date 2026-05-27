package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class Ali1688PluginSubmissionNormalizer {

    private static final int MAX_CANDIDATES = 10;
    private static final Pattern OFFER_ID_PATTERN = Pattern.compile("(?:offer/|offerId=|offer_id=)(\\d{6,})");
    private static final Set<String> SECRET_KEY_PARTS = Set.of(
            "password",
            "passwd",
            "token",
            "cookie",
            "authorization",
            "bearer",
            "credential",
            "secret"
    );

    private final ObjectMapper objectMapper;

    public Ali1688PluginSubmissionNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result normalize(Ali1688PluginSubmissionCommand command) {
        Ali1688PluginSubmissionCommand safeCommand = command == null ? new Ali1688PluginSubmissionCommand() : command;
        List<Ali1688PluginSubmissionCommand.Candidate> submitted = safeCommand.getCandidates();
        Set<String> seen = new LinkedHashSet<>();
        List<NormalizedCandidate> accepted = new ArrayList<>();
        int rejected = 0;
        for (Ali1688PluginSubmissionCommand.Candidate raw : submitted) {
            if (raw == null) {
                rejected++;
                continue;
            }
            String offerId = trim(raw.getOfferId());
            if (!StringUtils.hasText(offerId)) {
                offerId = extractOfferId(raw.getCandidateUrl());
            }
            String candidateUrl = normalizeUrl(offerId, raw.getCandidateUrl());
            String urlHash = sha256(defaultText(candidateUrl, ""));
            if (!StringUtils.hasText(offerId) && !StringUtils.hasText(candidateUrl)) {
                rejected++;
                continue;
            }
            String dedupeKey = StringUtils.hasText(offerId) ? "offer:" + offerId : "url:" + urlHash;
            if (!seen.add(dedupeKey)) {
                rejected++;
                continue;
            }
            if (accepted.size() >= MAX_CANDIDATES) {
                rejected++;
                continue;
            }
            NormalizedCandidate candidate = new NormalizedCandidate();
            candidate.offerId = offerId;
            candidate.candidateUrl = candidateUrl;
            candidate.candidateUrlHash = urlHash;
            candidate.title = trim(raw.getTitle());
            candidate.supplierName = trim(raw.getSupplierName());
            candidate.priceText = trim(raw.getPriceText());
            candidate.priceMin = raw.getPriceMin();
            candidate.priceMax = raw.getPriceMax();
            candidate.moqText = trim(raw.getMoqText());
            candidate.moqValue = raw.getMoqValue();
            candidate.locationText = trim(raw.getLocationText());
            candidate.mainImageUrl = trim(raw.getMainImageUrl());
            candidate.imageUrls = normalizeImageUrls(raw.getImageUrls(), candidate.mainImageUrl);
            candidate.badges = raw.getBadges();
            candidate.skuSnapshot = raw.getSkuSnapshot();
            candidate.supplierSnapshot = raw.getSupplierSnapshot();
            candidate.logisticsSnapshot = raw.getLogisticsSnapshot();
            accepted.add(candidate);
        }
        String auditSnapshotJson = auditSnapshotJson(safeCommand, submitted.size(), accepted, rejected);
        return new Result(submitted.size(), accepted, rejected, auditSnapshotJson);
    }

    private String auditSnapshotJson(
            Ali1688PluginSubmissionCommand command,
            int submittedCount,
            List<NormalizedCandidate> accepted,
            int rejectedCount
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "nuono-procurement-extension");
        snapshot.put("idempotencyKey", trim(command.getIdempotencyKey()));
        snapshot.put("sourcePageUrl", trim(command.getSourcePageUrl()));
        snapshot.put("submittedCandidateCount", submittedCount);
        snapshot.put("acceptedCandidateCount", accepted.size());
        snapshot.put("rejectedCandidateCount", rejectedCount);
        snapshot.put("sanitizedRawSnapshot", sanitize(command.getRawSnapshot()));
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (NormalizedCandidate candidate : accepted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("offerId", candidate.offerId);
            item.put("candidateUrl", candidate.candidateUrl);
            item.put("title", candidate.title);
            item.put("supplierName", candidate.supplierName);
            item.put("priceText", candidate.priceText);
            item.put("moqText", candidate.moqText);
            item.put("locationText", candidate.locationText);
            item.put("mainImageUrl", candidate.mainImageUrl);
            normalized.add(item);
        }
        snapshot.put("normalizedCandidates", normalized);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, rawValue) -> {
                String stringKey = key == null ? "" : String.valueOf(key);
                if (!isSecretKey(stringKey)) {
                    sanitized.put(stringKey, sanitize(rawValue));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?>) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : (List<?>) value) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        return value;
    }

    private boolean isSecretKey(String key) {
        String normalized = defaultText(key, "").toLowerCase(Locale.ROOT);
        for (String secretPart : SECRET_KEY_PARTS) {
            if (normalized.contains(secretPart)) {
                return true;
            }
        }
        return false;
    }

    private String extractOfferId(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = OFFER_ID_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeUrl(String offerId, String candidateUrl) {
        if (StringUtils.hasText(offerId)) {
            return "https://detail.1688.com/offer/" + offerId + ".html";
        }
        String text = trim(candidateUrl);
        return StringUtils.hasText(text) ? text : null;
    }

    private List<String> normalizeImageUrls(List<String> imageUrls, String mainImageUrl) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(mainImageUrl)) {
            result.add(mainImageUrl.trim());
        }
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (StringUtils.hasText(imageUrl)) {
                    result.add(imageUrl.trim());
                }
            }
        }
        return new ArrayList<>(result);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成 1688 候选 URL 摘要。", exception);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public static class Result {
        private final int submittedCandidateCount;
        private final List<NormalizedCandidate> candidates;
        private final int rejectedCandidateCount;
        private final String auditSnapshotJson;

        private Result(
                int submittedCandidateCount,
                List<NormalizedCandidate> candidates,
                int rejectedCandidateCount,
                String auditSnapshotJson
        ) {
            this.submittedCandidateCount = submittedCandidateCount;
            this.candidates = candidates;
            this.rejectedCandidateCount = rejectedCandidateCount;
            this.auditSnapshotJson = auditSnapshotJson;
        }

        public int getSubmittedCandidateCount() {
            return submittedCandidateCount;
        }

        public int getAcceptedCandidateCount() {
            return candidates.size();
        }

        public List<NormalizedCandidate> getCandidates() {
            return candidates;
        }

        public int getRejectedCandidateCount() {
            return rejectedCandidateCount;
        }

        public String getAuditSnapshotJson() {
            return auditSnapshotJson;
        }
    }

    public static class NormalizedCandidate {
        public String offerId;
        public String candidateUrl;
        public String candidateUrlHash;
        public String title;
        public String supplierName;
        public String priceText;
        public BigDecimal priceMin;
        public BigDecimal priceMax;
        public String moqText;
        public Integer moqValue;
        public String locationText;
        public String mainImageUrl;
        public List<String> imageUrls = new ArrayList<>();
        public Map<String, Object> badges;
        public Map<String, Object> skuSnapshot;
        public Map<String, Object> supplierSnapshot;
        public Map<String, Object> logisticsSnapshot;
    }
}
