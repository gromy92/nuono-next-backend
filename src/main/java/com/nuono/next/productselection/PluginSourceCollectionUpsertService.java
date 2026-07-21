package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionPluginIngestMapper;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class PluginSourceCollectionUpsertService {

    private static final int LOOKBACK_LIMIT = 500;
    private static final Pattern AMAZON_ASIN = Pattern.compile("(?i)/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})(?:/|$)");
    private static final Pattern NOON_SKU = Pattern.compile("(?i)/([NPZ][A-Z0-9]{8,})/p(?:/|$)");
    private static final Pattern SHEIN_PRODUCT_ID = Pattern.compile("(?i)-p-(\\d+)(?:\\.html|/|$)");
    private static final Pattern TEMU_GOODS_ID = Pattern.compile("(?i)-g-(\\d+)(?:\\.html|/|$)");

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
        ProductSelectionSourceCollectionRow duplicate = findDuplicate(scope, siteCode, sourcePlatform, pageUrl, sourceUrl);
        List<String> effectiveImages = preferIncoming(imageUrls, duplicate == null ? null : duplicate.getImageUrlsJson(), STRING_LIST);
        List<String> effectiveSpecs = preferIncoming(specHints, duplicate == null ? null : duplicate.getSpecHintsJson(), STRING_LIST);
        List<ProductSelectionCompetitorCategoryLink> effectiveCategories =
                preferIncoming(categoryLinks, duplicate == null ? null : duplicate.getCategoryLinksJson(), CATEGORY_LIST);
        List<String> effectivePointsEn = preferIncoming(
                sellingPointsEn,
                duplicate == null ? null : duplicate.getSourceSellingPointsEnJson(),
                STRING_LIST
        );
        List<String> effectivePointsAr = preferIncoming(
                sellingPointsAr,
                duplicate == null ? null : duplicate.getSourceSellingPointsArJson(),
                STRING_LIST
        );

        ProductSelectionSourceCollectionRow row = buildRow(
                scope, siteCode, source, sourcePlatform, sourceUrl, pageUrl, duplicate,
                effectiveImages, effectiveSpecs, effectiveCategories, effectivePointsEn, effectivePointsAr, warningNotes
        );
        if (duplicate == null) {
            sourceMapper.insertSourceCollection(row);
        } else if (pluginMapper.update(row) != 1) {
            throw new IllegalArgumentException("已有插件采集记录更新失败，请刷新后重试。");
        }
        ProductSelectionSourceCollectionRow inserted = duplicate == null
                ? sourceMapper.selectSourceCollectionById(row.getId())
                : null;
        ProductSelectionSourceCollectionRow effectiveRow = inserted == null ? row : inserted;
        ali1688CollectionService.ensureTaskForSourceCollection(effectiveRow, source.getOperatorUserId());
        return new Result(effectiveRow, duplicate != null);
    }

    private ProductSelectionSourceCollectionRow buildRow(
            ProductSelectionStoreScope scope,
            String siteCode,
            ProductSelectionPluginIngestCommand source,
            String platform,
            String sourceUrl,
            String pageUrl,
            ProductSelectionSourceCollectionRow old,
            List<String> images,
            List<String> specs,
            List<ProductSelectionCompetitorCategoryLink> categories,
            List<String> pointsEn,
            List<String> pointsAr,
            String warningNotes
    ) {
        boolean insert = old == null;
        Long id = insert ? sourceMapper.nextSourceCollectionId() : old.getId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(scope.getOwnerUserId());
        row.setLogicalStoreId(scope.getLogicalStoreId());
        row.setSiteCode(siteCode);
        row.setCollectionNo(insert ? "PSC-" + id : old.getCollectionNo());
        row.setSourceType("marketplace-url");
        row.setCollectionSource("plugin");
        row.setSourceUrl(sourceUrl);
        row.setPageUrl(pageUrl);
        row.setSourcePlatform(platform);
        row.setSourceTitle(text(source.getSourceTitle(), insert
                ? text(source.getSourceTitleCn(), text(source.getSourceTitleAr(), "插件采集源头商品"))
                : old.getSourceTitle()));
        row.setSourceTitleCn(text(source.getSourceTitleCn(), insert ? source.getSelectedText() : old.getSourceTitleCn()));
        row.setSourceTitleAr(text(source.getSourceTitleAr(), insert ? "" : old.getSourceTitleAr()));
        row.setSourceImageUrl(images.isEmpty() ? text(insert ? "" : old.getSourceImageUrl(), "") : images.get(0));
        row.setImageUrlsJson(json(images));
        row.setPriceSummary(text(source.getPriceSummary(), insert ? "" : old.getPriceSummary()));
        row.setMoqHint(text(source.getMoqHint(), insert ? "" : old.getMoqHint()));
        row.setShippingFrom(text(source.getShippingFrom(), insert ? "" : old.getShippingFrom()));
        row.setBrandName(completenessCalculator.firstSpecValue(specs, Set.of("brand", "brand name")));
        row.setUnitCount(completenessCalculator.firstSpecValue(specs, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS));
        row.setColorName(completenessCalculator.firstSpecValue(specs, Set.of("color", "colour", "color name", "colour name")));
        row.setSpecHintsJson(json(specs));
        row.setCategoryLinksJson(json(categories));
        row.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specs));
        row.setSourceDescriptionEn(MarketplaceProductDescriptionSanitizer.preferIncoming(
                source.getSourceDescriptionEn(),
                insert ? "" : old.getSourceDescriptionEn()
        ));
        row.setSourceDescriptionAr(MarketplaceProductDescriptionSanitizer.preferIncoming(
                source.getSourceDescriptionAr(),
                insert ? source.getSelectedTextAr() : old.getSourceDescriptionAr()
        ));
        row.setSourceSellingPointsEnJson(json(pointsEn));
        row.setSourceSellingPointsArJson(json(pointsAr));
        row.setSelectedText(text(source.getSelectedText(), insert ? "" : old.getSelectedText()));
        row.setSelectedTextAr(text(source.getSelectedTextAr(), insert ? "" : old.getSelectedTextAr()));
        row.setNotes(text(source.getNotes(), text(warningNotes, insert ? "" : old.getNotes())));
        row.setStatus("success");
        row.setCreatedBy(insert ? source.getOperatorUserId() : old.getCreatedBy());
        row.setUpdatedBy(source.getOperatorUserId());
        return row;
    }

    private ProductSelectionSourceCollectionRow findDuplicate(
            ProductSelectionStoreScope scope, String siteCode, String platform, String pageUrl, String sourceUrl
    ) {
        Set<String> incoming = keys(platform, pageUrl, sourceUrl);
        if (incoming.isEmpty()) {
            return null;
        }
        List<ProductSelectionSourceCollectionRow> candidates = pluginMapper.listRecentForDedupe(
                scope.getOwnerUserId(), scope.getLogicalStoreId(), siteCode, platform, LOOKBACK_LIMIT
        );
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> keys(platform, candidate.getPageUrl(), candidate.getSourceUrl())
                        .stream().anyMatch(incoming::contains))
                .findFirst()
                .orElse(null);
    }

    private Set<String> keys(String platform, String... urls) {
        Set<String> result = new LinkedHashSet<>();
        for (String rawUrl : urls) {
            URI uri = uri(rawUrl);
            if (uri == null) {
                continue;
            }
            String identity = productIdentity(platform, uri);
            String host = host(uri);
            result.add(StringUtils.hasText(identity)
                    ? identity
                    : "url:" + host + text(uri.getPath(), "/").toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private String productIdentity(String platform, URI uri) {
        String path = text(uri.getPath(), "");
        String host = host(uri);
        if ("Amazon".equals(platform)) {
            return keyed("amazon:" + host, AMAZON_ASIN, path, true);
        }
        if ("Noon".equals(platform)) {
            return keyed("noon:" + noonMarket(path), NOON_SKU, path, true);
        }
        if ("SHEIN".equals(platform)) {
            return keyed("shein:" + host, SHEIN_PRODUCT_ID, path, false);
        }
        if ("Temu".equals(platform)) {
            String id = text(queryParam(uri, "goods_id"), match(TEMU_GOODS_ID, path));
            return StringUtils.hasText(id) ? "temu:" + host + ":" + id : "";
        }
        return "";
    }

    private String keyed(String prefix, Pattern pattern, String path, boolean uppercase) {
        String id = match(pattern, path);
        return StringUtils.hasText(id) ? prefix + ":" + (uppercase ? id.toUpperCase(Locale.ROOT) : id) : "";
    }

    private String noonMarket(String path) {
        for (String part : text(path, "").split("/")) {
            if (StringUtils.hasText(part)) {
                return part.toLowerCase(Locale.ROOT).replaceFirst("-(?:en|ar)$", "");
            }
        }
        return "noon";
    }

    private String queryParam(URI uri, String name) {
        for (String pair : text(uri.getRawQuery(), "").split("&")) {
            int separator = pair.indexOf('=');
            if (name.equalsIgnoreCase(separator < 0 ? pair : pair.substring(0, separator))) {
                return separator < 0 ? "" : pair.substring(separator + 1);
            }
        }
        return "";
    }

    private URI uri(String value) {
        try {
            return StringUtils.hasText(value) ? URI.create(value) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String host(URI uri) {
        String host = text(uri.getHost(), "").toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String match(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(text(value, ""));
        return matcher.find() ? matcher.group(1) : "";
    }

    private <T> List<T> preferIncoming(List<T> values, String previousJson, TypeReference<List<T>> type) {
        if (values != null && !values.isEmpty()) {
            return values;
        }
        try {
            return StringUtils.hasText(previousJson) ? objectMapper.readValue(previousJson, type) : List.of();
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String json(List<?> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("插件采集数据序列化失败。", error);
        }
    }

    private static String text(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ProductSelectionCompetitorCategoryLink>> CATEGORY_LIST = new TypeReference<>() { };

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
