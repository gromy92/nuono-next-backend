package com.nuono.next.productkeyword;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductKeywordService {
    private final ProductKeywordMapper mapper;
    private final ProductKeywordNormalizer normalizer;

    public ProductKeywordService(ProductKeywordMapper mapper, ProductKeywordNormalizer normalizer) {
        this.mapper = mapper;
        this.normalizer = normalizer;
    }

    public ProductKeywordRecord addManualKeyword(BusinessAccessContext context, ProductKeywordCommand command) {
        return upsertKeywordWithEvent(
                context,
                command,
                ProductKeywordStatus.ACTIVE,
                ProductKeywordSourceType.MANUAL,
                ProductKeywordEventStatus.ADDED
        );
    }

    public ProductKeywordRecord recordObservedKeyword(
            BusinessAccessContext context,
            ProductKeywordCommand command,
            ProductKeywordSourceType sourceType
    ) {
        if (sourceType == null || sourceType == ProductKeywordSourceType.MANUAL) {
            throw new IllegalArgumentException("观察型关键词必须提供非手动来源");
        }
        return upsertKeywordWithEvent(
                context,
                command,
                ProductKeywordStatus.OBSERVED,
                sourceType,
                ProductKeywordEventStatus.OBSERVED
        );
    }

    private ProductKeywordRecord upsertKeywordWithEvent(
            BusinessAccessContext context,
            ProductKeywordCommand command,
            ProductKeywordStatus targetStatus,
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus
    ) {
        ProductKeywordScope scope = resolveScope(context, command);
        String displayKeyword = requireText(command.getKeyword(), "关键词不能为空");
        String keywordNorm = normalizer.normalize(displayKeyword);
        if (!StringUtils.hasText(keywordNorm)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<String> tags = normalizeTags(command.getIntentTags());
        validateWildcardScope(scope, tags);

        LocalDateTime now = LocalDateTime.now();
        ProductKeywordRecord existing = mapper.selectByScopeAndNorm(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode(),
                scope.getPartnerSku(),
                keywordNorm
        );

        ProductKeywordRecord record = existing == null ? new ProductKeywordRecord() : existing;
        boolean observedExistingAsset = existing != null && targetStatus == ProductKeywordStatus.OBSERVED;
        if (record.getId() == null) {
            record.setId(mapper.nextKeywordId());
            record.setFirstSeenAt(now);
            record.setCreatedBy(context.getSessionUserId());
        }
        record.setOwnerUserId(scope.getOwnerUserId());
        record.setStoreCode(scope.getStoreCode());
        record.setSiteCode(scope.getSiteCode());
        record.setPartnerSku(scope.getPartnerSku());
        if (!observedExistingAsset) {
            record.setKeyword(displayKeyword);
        }
        record.setKeywordNorm(keywordNorm);
        if (!observedExistingAsset) {
            record.setLocale(trimToNull(command.getLocale()));
            record.setStatus(targetStatus.name());
            record.setIntentTagsJson(tagsJson(tags));
        }
        record.setLastSeenAt(now);
        record.setUpdatedBy(context.getSessionUserId());
        mapper.upsertKeyword(record);

        ProductKeywordUsageEventRecord event = buildUsageEvent(
                context,
                record,
                sourceType,
                eventStatus,
                now,
                tags,
                displayKeyword,
                keywordNorm
        );
        mapper.upsertUsageEvent(event);
        return record;
    }

    private ProductKeywordUsageEventRecord buildUsageEvent(
            BusinessAccessContext context,
            ProductKeywordRecord keyword,
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus,
            LocalDateTime occurredAt,
            List<String> tags,
            String eventKeyword,
            String eventKeywordNorm
    ) {
        ProductKeywordUsageEventRecord event = new ProductKeywordUsageEventRecord();
        event.setId(mapper.nextUsageEventId());
        event.setKeywordId(keyword.getId());
        event.setOwnerUserId(keyword.getOwnerUserId());
        event.setStoreCode(keyword.getStoreCode());
        event.setSiteCode(keyword.getSiteCode());
        event.setPartnerSku(keyword.getPartnerSku());
        event.setKeyword(eventKeyword);
        event.setKeywordNorm(eventKeywordNorm);
        event.setSourceType(sourceType.name());
        event.setSourceRefType("product_keyword");
        event.setSourceRefId(keyword.getId());
        event.setSourceRefKey(keyword.getOwnerUserId() + ":" + keyword.getStoreCode() + ":"
                + keyword.getSiteCode() + ":" + keyword.getPartnerSku() + ":" + keyword.getKeywordNorm());
        event.setEventStatus(eventStatus.name());
        event.setOccurredAt(occurredAt);
        event.setPayloadJson(payloadJson(keyword, tags));
        event.setMetricsJson("{}");
        event.setEventNaturalKey(naturalKey(sourceType, eventStatus, keyword));
        event.setCreatedBy(context.getSessionUserId());
        event.setUpdatedBy(context.getSessionUserId());
        return event;
    }

    private ProductKeywordScope resolveScope(BusinessAccessContext context, ProductKeywordCommand command) {
        if (context == null) {
            throw new IllegalArgumentException("缺少业务上下文");
        }
        String storeCode = normalizeStoreCode(command.getStoreCode());
        String siteCode = normalizeSiteCode(command.getSiteCode());
        String partnerSku = requireText(command.getPartnerSku(), "PSKU 不能为空");
        if (!context.canAccessStore(storeCode)) {
            throw new IllegalArgumentException("无权访问店铺：" + storeCode);
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法解析业务 owner");
        }
        return new ProductKeywordScope(ownerUserId, storeCode, siteCode, partnerSku);
    }

    private void validateWildcardScope(ProductKeywordScope scope, List<String> tags) {
        if (!"*".equals(scope.getSiteCode())) {
            return;
        }
        if (!tags.contains("TITLE_TARGET") && !tags.contains("CORE")) {
            throw new IllegalArgumentException("产品级关键词范围只允许标题目标关键词");
        }
    }

    private String naturalKey(
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus,
            ProductKeywordRecord keyword
    ) {
        return String.join(
                "|",
                sourceType.name(),
                eventStatus.name(),
                String.valueOf(keyword.getOwnerUserId()),
                keyword.getStoreCode(),
                keyword.getSiteCode(),
                keyword.getPartnerSku(),
                keyword.getKeywordNorm()
        );
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String normalizeStoreCode(String value) {
        return requireText(value, "店铺不能为空").toUpperCase(Locale.ROOT);
    }

    private String normalizeSiteCode(String value) {
        String siteCode = requireText(value, "站点不能为空");
        return "*".equals(siteCode) ? "*" : siteCode.toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String tagsJson(List<String> tags) {
        return "[" + tags.stream()
                .map(this::quoteJson)
                .collect(Collectors.joining(",")) + "]";
    }

    private String payloadJson(ProductKeywordRecord keyword, List<String> tags) {
        return "{"
                + "\"keyword\":" + quoteJson(keyword.getKeyword()) + ","
                + "\"keywordNorm\":" + quoteJson(keyword.getKeywordNorm()) + ","
                + "\"intentTags\":" + tagsJson(tags)
                + "}";
    }

    private String quoteJson(String value) {
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
}
