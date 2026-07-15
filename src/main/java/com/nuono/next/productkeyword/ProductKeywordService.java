package com.nuono.next.productkeyword;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public ProductKeywordViews.KeywordListView addCompetitorKeywords(
            BusinessAccessContext context,
            ProductKeywordCompetitorKeywordCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        List<ProductKeywordRecord> records = new ArrayList<>();
        Set<String> seenKeywordNorms = new LinkedHashSet<>();
        if (command.getKeywords() != null) {
            for (String keyword : command.getKeywords()) {
                String displayKeyword = trimToNull(keyword);
                String keywordNorm = normalizer.normalize(displayKeyword);
                if (!StringUtils.hasText(keywordNorm) || !seenKeywordNorms.add(keywordNorm)) {
                    continue;
                }
                ProductKeywordCommand item = new ProductKeywordCommand();
                item.setStoreCode(command.getStoreCode());
                item.setSiteCode(command.getSiteCode());
                item.setPartnerSku(command.getPartnerSku());
                item.setKeyword(displayKeyword);
                item.setIntentTags(List.of("COMPETITOR_TRACK"));
                records.add(upsertKeywordWithEvent(
                        context,
                        item,
                        ProductKeywordStatus.OBSERVED,
                        ProductKeywordSourceType.COMPETITOR_KEYWORD,
                        ProductKeywordEventStatus.OBSERVED,
                        command.getCompetitorSources()
                ));
            }
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        return new ProductKeywordViews.KeywordListView(records.stream()
                .map(ProductKeywordViews::keyword)
                .collect(Collectors.toList()));
    }

    public ProductKeywordViews.KeywordListView listKeywords(
            BusinessAccessContext context,
            ProductKeywordListQuery query
    ) {
        ProductKeywordListQuery normalized = normalizeQuery(context, query);
        List<ProductKeywordViews.KeywordItemView> items = mapper.listKeywords(normalized).stream()
                .map(ProductKeywordViews::keyword)
                .collect(Collectors.toList());
        return new ProductKeywordViews.KeywordListView(items);
    }

    public ProductKeywordViews.ProductKeywordPanelView productKeywords(
            BusinessAccessContext context,
            String storeCode,
            String siteCode,
            String partnerSku
    ) {
        ProductKeywordListQuery query = new ProductKeywordListQuery();
        query.setStoreCode(storeCode);
        query.setSiteCode(siteCode);
        query.setPartnerSku(partnerSku);
        query.setLimit(100);
        ProductKeywordListQuery normalized = normalizeQuery(context, query);
        List<ProductKeywordViews.KeywordItemView> keywords = mapper.listKeywords(normalized).stream()
                .map(ProductKeywordViews::keyword)
                .collect(Collectors.toList());
        List<ProductKeywordViews.EventItemView> events = mapper.listEvents(
                        normalized.getOwnerUserId(),
                        normalized.getStoreCode(),
                        normalized.getSiteCode(),
                        normalized.getPartnerSku(),
                        100
                ).stream()
                .map(ProductKeywordViews::event)
                .collect(Collectors.toList());
        return new ProductKeywordViews.ProductKeywordPanelView(
                normalized.getStoreCode(),
                normalized.getSiteCode(),
                normalized.getPartnerSku(),
                keywords,
                events
        );
    }

    public ProductKeywordRecord updateKeyword(
            BusinessAccessContext context,
            Long keywordId,
            ProductKeywordCommand command
    ) {
        if (keywordId == null) {
            throw new IllegalArgumentException("关键词 ID 不能为空");
        }
        ProductKeywordScope scope = resolveScope(context, command);
        ProductKeywordRecord existing = mapper.selectById(scope.getOwnerUserId(), keywordId);
        if (existing == null) {
            throw new IllegalArgumentException("关键词不存在或无权访问");
        }
        if (!existing.getStoreCode().equals(scope.getStoreCode())
                || !existing.getSiteCode().equals(scope.getSiteCode())
                || !existing.getPartnerSku().equals(scope.getPartnerSku())) {
            throw new IllegalArgumentException("关键词所属商品范围不能通过编辑修改");
        }
        String displayKeyword = StringUtils.hasText(command.getKeyword()) ? command.getKeyword().trim() : existing.getKeyword();
        String keywordNorm = normalizer.normalize(displayKeyword);
        if (!StringUtils.hasText(keywordNorm)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<String> tags = normalizeTags(command.getIntentTags());
        validateWildcardScope(scope, tags);

        existing.setStoreCode(scope.getStoreCode());
        existing.setSiteCode(scope.getSiteCode());
        existing.setPartnerSku(scope.getPartnerSku());
        existing.setKeyword(displayKeyword);
        existing.setKeywordNorm(keywordNorm);
        existing.setLocale(trimToNull(command.getLocale()));
        existing.setIntentTagsJson(tagsJson(tags));
        existing.setLastSeenAt(LocalDateTime.now());
        existing.setUpdatedBy(context.getSessionUserId());
        mapper.upsertKeyword(existing);
        ProductKeywordUsageEventRecord event = buildUsageEvent(
                context,
                existing,
                ProductKeywordSourceType.MANUAL,
                ProductKeywordEventStatus.ADDED,
                existing.getLastSeenAt(),
                tags,
                displayKeyword,
                keywordNorm
        );
        mapper.upsertUsageEvent(event);
        return existing;
    }

    public ProductKeywordViews.RebuildIndexResultView rebuildIndex(
            BusinessAccessContext context,
            ProductKeywordListQuery query
    ) {
        ProductKeywordListQuery normalized = normalizeQuery(context, query);
        return new ProductKeywordViews.RebuildIndexResultView(
                normalized.getStoreCode(),
                normalized.getSiteCode(),
                normalized.getPartnerSku(),
                "QUEUED"
        );
    }

    private ProductKeywordRecord upsertKeywordWithEvent(
            BusinessAccessContext context,
            ProductKeywordCommand command,
            ProductKeywordStatus targetStatus,
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus
    ) {
        return upsertKeywordWithEvent(context, command, targetStatus, sourceType, eventStatus, List.of());
    }

    private ProductKeywordRecord upsertKeywordWithEvent(
            BusinessAccessContext context,
            ProductKeywordCommand command,
            ProductKeywordStatus targetStatus,
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus,
            List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources
    ) {
        ProductKeywordScope scope = resolveScope(context, command);
        String displayKeyword = requireText(command.getKeyword(), "关键词不能为空");
        String keywordNorm = normalizer.normalize(displayKeyword);
        if (!StringUtils.hasText(keywordNorm)) {
            throw new IllegalArgumentException("关键词不能为空");
        }
        List<String> tags = normalizeTags(command.getIntentTags());
        if (sourceType == ProductKeywordSourceType.COMPETITOR_KEYWORD && !tags.contains("COMPETITOR_TRACK")) {
            tags = new ArrayList<>(tags);
            tags.add("COMPETITOR_TRACK");
        }
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
        } else if (sourceType == ProductKeywordSourceType.COMPETITOR_KEYWORD) {
            record.setIntentTagsJson(ProductKeywordTagJson.merge(
                    record.getIntentTagsJson(),
                    List.of("COMPETITOR_TRACK")
            ));
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
                keywordNorm,
                competitorSources
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
        return buildUsageEvent(context, keyword, sourceType, eventStatus, occurredAt, tags, eventKeyword, eventKeywordNorm, List.of());
    }

    private ProductKeywordUsageEventRecord buildUsageEvent(
            BusinessAccessContext context,
            ProductKeywordRecord keyword,
            ProductKeywordSourceType sourceType,
            ProductKeywordEventStatus eventStatus,
            LocalDateTime occurredAt,
            List<String> tags,
            String eventKeyword,
            String eventKeywordNorm,
            List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources
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
        event.setPayloadJson(payloadJson(keyword, tags, competitorSources));
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

    private ProductKeywordListQuery normalizeQuery(BusinessAccessContext context, ProductKeywordListQuery query) {
        if (context == null) {
            throw new IllegalArgumentException("缺少业务上下文");
        }
        ProductKeywordListQuery normalized = new ProductKeywordListQuery();
        normalized.setStoreCode(normalizeStoreCode(query == null ? null : query.getStoreCode()));
        normalized.setSiteCode(normalizeSiteCode(query == null ? null : query.getSiteCode()));
        normalized.setPartnerSku(trimToNull(query == null ? null : query.getPartnerSku()));
        if (!context.canAccessStore(normalized.getStoreCode())) {
            throw new IllegalArgumentException("无权访问店铺：" + normalized.getStoreCode());
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(normalized.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法解析业务 owner");
        }
        normalized.setOwnerUserId(ownerUserId);
        normalized.setKeywordNorm(trimToNull(query == null ? null : query.getKeywordNorm()));
        normalized.setStatus(normalizeOptionalUpper(query == null ? null : query.getStatus()));
        normalized.setLimit(limitOrDefault(query == null ? null : query.getLimit()));
        return normalized;
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

    private String normalizeOptionalUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private Integer limitOrDefault(Integer limit) {
        if (limit == null || limit <= 0) {
            return 100;
        }
        return Math.min(limit, 5000);
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
        return payloadJson(keyword, tags, List.of());
    }

    private String payloadJson(
            ProductKeywordRecord keyword,
            List<String> tags,
            List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources
    ) {
        return "{"
                + "\"keyword\":" + quoteJson(keyword.getKeyword()) + ","
                + "\"keywordNorm\":" + quoteJson(keyword.getKeywordNorm()) + ","
                + "\"intentTags\":" + tagsJson(tags)
                + competitorSourcesJsonField(competitorSources)
                + "}";
    }

    private String competitorSourcesJsonField(List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources) {
        if (competitorSources == null || competitorSources.isEmpty()) {
            return "";
        }
        List<String> entries = competitorSources.stream()
                .filter(source -> source != null)
                .map(this::competitorSourceJson)
                .filter(StringUtils::hasText)
                .limit(20)
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            return "";
        }
        return ",\"competitorSources\":[" + String.join(",", entries) + "]";
    }

    private String competitorSourceJson(ProductKeywordCompetitorKeywordCommand.CompetitorSource source) {
        String label = trimToNull(source.getLabel());
        String url = trimToNull(source.getUrl());
        String sourceText = trimToNull(source.getSourceText());
        if (label == null && url == null && sourceText == null) {
            return "";
        }
        return "{"
                + "\"label\":" + quoteJson(label) + ","
                + "\"url\":" + quoteJson(url) + ","
                + "\"sourceText\":" + quoteJson(truncate(sourceText, 1000))
                + "}";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
