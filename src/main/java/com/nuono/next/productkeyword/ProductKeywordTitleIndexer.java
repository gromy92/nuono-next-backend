package com.nuono.next.productkeyword;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductKeywordTitleIndexer {
    private final ProductKeywordMapper mapper;
    private final ProductKeywordMatcher matcher;

    public ProductKeywordTitleIndexer(ProductKeywordMapper mapper, ProductKeywordMatcher matcher) {
        this.mapper = mapper;
        this.matcher = matcher;
    }

    public void indexPublishedHistory(TitleHistoryIndexCommand command) {
        if (command == null
                || command.ownerUserId == null
                || command.productMasterId == null
                || command.historyId == null
                || !StringUtils.hasText(command.storeCode)
                || !StringUtils.hasText(command.partnerSku)) {
            return;
        }
        Map<String, String> titles = extractAfterTitles(command.summary);
        if (titles.isEmpty()) {
            return;
        }
        String siteCode = StringUtils.hasText(command.siteCode) ? command.siteCode.trim().toUpperCase(Locale.ROOT) : "*";
        String titleHash = titleHash(titles);
        List<ProductKeywordRecord> keywords = mapper.listActiveTitleTargetKeywords(
                command.ownerUserId,
                command.storeCode.trim().toUpperCase(Locale.ROOT),
                siteCode,
                command.partnerSku.trim()
        );
        for (ProductKeywordRecord keyword : keywords) {
            if (!isActiveTitleTarget(keyword) || !matchesAnyTitle(keyword, titles)) {
                continue;
            }
            ProductKeywordUsageEventRecord event = new ProductKeywordUsageEventRecord();
            event.setId(mapper.nextUsageEventId());
            event.setKeywordId(keyword.getId());
            event.setOwnerUserId(command.ownerUserId);
            event.setStoreCode(keyword.getStoreCode());
            event.setSiteCode(keyword.getSiteCode());
            event.setPartnerSku(keyword.getPartnerSku());
            event.setKeyword(keyword.getKeyword());
            event.setKeywordNorm(keyword.getKeywordNorm());
            event.setSourceType(ProductKeywordSourceType.TITLE_HISTORY.name());
            event.setSourceRefType("product_key_content_history");
            event.setSourceRefId(command.historyId);
            event.setSourceRefKey(command.productMasterId + ":" + command.historyId);
            event.setEventStatus(ProductKeywordEventStatus.MATCHED.name());
            event.setOccurredAt(command.occurredAt == null ? LocalDateTime.now() : command.occurredAt);
            event.setPayloadJson(payloadJson(command, titles, titleHash, matchedFields(keyword, titles)));
            event.setMetricsJson("{\"coverage\":true}");
            event.setEventNaturalKey(naturalKey(command, keyword, titleHash));
            event.setCreatedBy(command.actorUserId);
            event.setUpdatedBy(command.actorUserId);
            mapper.upsertUsageEvent(event);
        }
    }

    private Map<String, String> extractAfterTitles(Map<String, Object> summary) {
        Map<String, String> titles = new LinkedHashMap<>();
        if (summary == null) {
            return titles;
        }
        Object titleNode = summary.get("title");
        if (!(titleNode instanceof Map<?, ?>)) {
            return titles;
        }
        Map<?, ?> titleMap = (Map<?, ?>) titleNode;
        Object afterNode = titleMap.get("after");
        if (!(afterNode instanceof Map<?, ?>)) {
            return titles;
        }
        Map<?, ?> afterMap = (Map<?, ?>) afterNode;
        putTitle(titles, "titleEn", afterMap.get("titleEn"));
        putTitle(titles, "titleAr", afterMap.get("titleAr"));
        putTitle(titles, "titleCn", afterMap.get("titleCn"));
        return titles;
    }

    private void putTitle(Map<String, String> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private boolean isActiveTitleTarget(ProductKeywordRecord keyword) {
        if (keyword == null || !"ACTIVE".equalsIgnoreCase(keyword.getStatus())) {
            return false;
        }
        String tags = keyword.getIntentTagsJson();
        if (!StringUtils.hasText(tags)) {
            return false;
        }
        return tags.contains("\"CORE\"") || tags.contains("\"TITLE_TARGET\"");
    }

    private boolean matchesAnyTitle(ProductKeywordRecord keyword, Map<String, String> titles) {
        return titles.values().stream().anyMatch(title -> matcher.matches(keyword.getKeyword(), title));
    }

    private List<String> matchedFields(ProductKeywordRecord keyword, Map<String, String> titles) {
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            if (matcher.matches(keyword.getKeyword(), entry.getValue())) {
                fields.add(entry.getKey());
            }
        }
        return fields;
    }

    private String naturalKey(TitleHistoryIndexCommand command, ProductKeywordRecord keyword, String titleHash) {
        return String.join(
                "|",
                ProductKeywordSourceType.TITLE_HISTORY.name(),
                "product_key_content_history",
                String.valueOf(command.historyId),
                keyword.getSiteCode(),
                keyword.getPartnerSku(),
                keyword.getKeywordNorm(),
                ProductKeywordEventStatus.MATCHED.name(),
                titleHash
        );
    }

    private String payloadJson(
            TitleHistoryIndexCommand command,
            Map<String, String> titles,
            String titleHash,
            List<String> matchedFields
    ) {
        List<String> entries = new ArrayList<>();
        entries.add("\"productMasterId\":" + command.productMasterId);
        entries.add("\"historyId\":" + command.historyId);
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            entries.add("\"" + entry.getKey() + "\":" + quote(entry.getValue()));
        }
        entries.add("\"title_hash\":" + quote(titleHash));
        entries.add("\"matchedFields\":" + stringArray(matchedFields));
        return "{" + String.join(",", entries) + "}";
    }

    private String stringArray(List<String> values) {
        return "[" + values.stream().map(this::quote).reduce((left, right) -> left + "," + right).orElse("") + "]";
    }

    private String titleHash(Map<String, String> titles) {
        String raw = String.join(
                "\u001F",
                titles.getOrDefault("titleEn", ""),
                titles.getOrDefault("titleAr", ""),
                titles.getOrDefault("titleCn", "")
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    public static class TitleHistoryIndexCommand {
        private final Long ownerUserId;
        private final Long productMasterId;
        private final Long historyId;
        private final String storeCode;
        private final String siteCode;
        private final String partnerSku;
        private final Map<String, Object> summary;
        private final LocalDateTime occurredAt;
        private final Long actorUserId;

        public TitleHistoryIndexCommand(
                Long ownerUserId,
                Long productMasterId,
                Long historyId,
                String storeCode,
                String siteCode,
                String partnerSku,
                Map<String, Object> summary,
                LocalDateTime occurredAt,
                Long actorUserId
        ) {
            this.ownerUserId = ownerUserId;
            this.productMasterId = productMasterId;
            this.historyId = historyId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.partnerSku = partnerSku;
            this.summary = summary;
            this.occurredAt = occurredAt;
            this.actorUserId = actorUserId;
        }
    }
}
