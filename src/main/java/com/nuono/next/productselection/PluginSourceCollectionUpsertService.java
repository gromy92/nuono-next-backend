package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class PluginSourceCollectionUpsertService {

    private static final int BATCH_ID_MAX_LENGTH = 100;
    private static final int ITEM_KEY_MAX_LENGTH = 120;
    private static final int EXTRACTOR_VERSION_MAX_LENGTH = 160;

    private final ProductSelectionMapper sourceMapper;
    private final ProductSelectionPluginIngestMapper pluginMapper;
    private final SourceCollectionCompletenessCalculator completenessCalculator;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectMapper objectMapper;

    public PluginSourceCollectionUpsertService(
            ProductSelectionMapper sourceMapper,
            ProductSelectionPluginIngestMapper pluginMapper,
            SourceCollectionCompletenessCalculator completenessCalculator,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectMapper objectMapper
    ) {
        this.sourceMapper = sourceMapper;
        this.pluginMapper = pluginMapper;
        this.completenessCalculator = completenessCalculator;
        this.ali1688CollectionService = ali1688CollectionService;
        this.objectMapper = objectMapper;
    }

    public Result upsert(
            ProductSelectionStoreScope scope,
            String siteCode,
            ProductSelectionPluginIngestCommand source,
            String sourcePlatform,
            String sourceUrl,
            String pageUrl,
            List<String> imageUrls,
            List<String> specHints,
            List<ProductSelectionCompetitorCategoryLink> categoryLinks,
            List<String> sellingPointsEn,
            List<String> sellingPointsAr,
            String warningNotes
    ) {
        String pluginBatchId = boundedText(
                source.getCollectionBatchId(), BATCH_ID_MAX_LENGTH, "采集批次标识过长。"
        );
        String pluginItemKey = boundedText(
                source.getCollectionItemKey(), ITEM_KEY_MAX_LENGTH, "采集批次条目标识过长。"
        );
        if ((pluginBatchId == null) != (pluginItemKey == null)) {
            throw new IllegalArgumentException("采集批次标识和批次条目标识必须同时提供。");
        }

        ProductSelectionSourceCollectionRow retry = selectRetry(scope, pluginBatchId, pluginItemKey);
        if (retry != null) {
            return new Result(retry, true);
        }

        ProductSelectionSourceCollectionRow row = buildRow(
                scope,
                siteCode,
                source,
                sourcePlatform,
                sourceUrl,
                pageUrl,
                imageUrls,
                specHints,
                categoryLinks,
                sellingPointsEn,
                sellingPointsAr,
                warningNotes,
                pluginBatchId,
                pluginItemKey
        );
        try {
            sourceMapper.insertSourceCollection(row);
        } catch (DuplicateKeyException exception) {
            ProductSelectionSourceCollectionRow concurrentRetry = selectRetry(scope, pluginBatchId, pluginItemKey);
            if (concurrentRetry != null) {
                return new Result(concurrentRetry, true);
            }
            throw exception;
        }

        ProductSelectionSourceCollectionRow inserted = sourceMapper.selectSourceCollectionById(row.getId());
        ProductSelectionSourceCollectionRow effectiveRow = inserted == null ? row : inserted;
        ali1688CollectionService.ensureTaskForSourceCollection(effectiveRow, source.getOperatorUserId());
        return new Result(effectiveRow, false);
    }

    private ProductSelectionSourceCollectionRow selectRetry(
            ProductSelectionStoreScope scope,
            String pluginBatchId,
            String pluginItemKey
    ) {
        if (pluginBatchId == null || pluginItemKey == null) {
            return null;
        }
        return pluginMapper.selectByBatchItem(
                scope.getOwnerUserId(),
                scope.getLogicalStoreId(),
                pluginBatchId,
                pluginItemKey
        );
    }

    private ProductSelectionSourceCollectionRow buildRow(
            ProductSelectionStoreScope scope,
            String siteCode,
            ProductSelectionPluginIngestCommand source,
            String platform,
            String sourceUrl,
            String pageUrl,
            List<String> images,
            List<String> specs,
            List<ProductSelectionCompetitorCategoryLink> categories,
            List<String> pointsEn,
            List<String> pointsAr,
            String warningNotes,
            String pluginBatchId,
            String pluginItemKey
    ) {
        Long id = sourceMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(scope.getOwnerUserId());
        row.setLogicalStoreId(scope.getLogicalStoreId());
        row.setSiteCode(siteCode);
        row.setCollectionNo("PSC-" + id);
        row.setSourceType("marketplace-url");
        row.setCollectionSource("plugin");
        row.setPluginBatchId(pluginBatchId);
        row.setPluginItemKey(pluginItemKey);
        row.setExtractorVersion(boundedText(
                source.getExtractorVersion(), EXTRACTOR_VERSION_MAX_LENGTH, "插件采集器版本标识过长。"
        ));
        row.setSourceUrl(sourceUrl);
        row.setPageUrl(pageUrl);
        row.setSourcePlatform(platform);
        row.setSourceTitle(text(
                source.getSourceTitle(),
                text(source.getSourceTitleCn(), text(source.getSourceTitleAr(), "插件采集源头商品"))
        ));
        row.setSourceTitleCn(text(source.getSourceTitleCn(), text(source.getSelectedText(), "")));
        row.setSourceTitleAr(text(source.getSourceTitleAr(), ""));
        row.setSourceImageUrl(images.isEmpty() ? "" : images.get(0));
        row.setImageUrlsJson(json(images));
        row.setPriceSummary(text(source.getPriceSummary(), ""));
        row.setMoqHint(text(source.getMoqHint(), ""));
        row.setShippingFrom(text(source.getShippingFrom(), ""));
        row.setBrandName(completenessCalculator.firstSpecValue(specs, Set.of("brand", "brand name")));
        row.setUnitCount(completenessCalculator.firstSpecValue(
                specs, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS
        ));
        row.setColorName(completenessCalculator.firstSpecValue(
                specs, Set.of("color", "colour", "color name", "colour name")
        ));
        row.setSpecHintsJson(json(specs));
        row.setCategoryLinksJson(json(categories));
        row.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specs));
        row.setSourceDescriptionEn(MarketplaceProductDescriptionSanitizer.preferIncoming(
                source.getSourceDescriptionEn(), ""
        ));
        row.setSourceDescriptionAr(MarketplaceProductDescriptionSanitizer.preferIncoming(
                source.getSourceDescriptionAr(), text(source.getSelectedTextAr(), "")
        ));
        row.setSourceSellingPointsEnJson(json(pointsEn));
        row.setSourceSellingPointsArJson(json(pointsAr));
        row.setSelectedText(text(source.getSelectedText(), ""));
        row.setSelectedTextAr(text(source.getSelectedTextAr(), ""));
        row.setNotes(text(source.getNotes(), text(warningNotes, "")));
        row.setStatus("success");
        row.setCreatedBy(source.getOperatorUserId());
        row.setUpdatedBy(source.getOperatorUserId());
        return row;
    }

    private String json(List<?> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("插件采集数据序列化失败。", error);
        }
    }

    private static String boundedText(String value, int maxLength, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public static final class Result {
        private final ProductSelectionSourceCollectionRow row;
        private final boolean deduped;

        Result(ProductSelectionSourceCollectionRow row, boolean deduped) {
            this.row = row;
            this.deduped = deduped;
        }

        public ProductSelectionSourceCollectionRow row() {
            return row;
        }

        public boolean deduped() {
            return deduped;
        }
    }
}
