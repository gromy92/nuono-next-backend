package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

public class ProductPublishHistoryAssembler {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public ProductPublishHistoryAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildLastPublishTaskSummary(ProductPublishTaskRecord task, List<String> warnings) {
        if (task == null) {
            return null;
        }
        String statusLabel = publishTaskListStatusLabel(task.getStatus());
        if (!StringUtils.hasText(statusLabel)) {
            return null;
        }
        ProductMasterSnapshotView baseline = readHistorySnapshot(task.getBaselineJson(), warnings);
        ProductMasterSnapshotView draft = readHistorySnapshot(task.getDraftJson(), warnings);
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfNotNull(summary, "taskId", task.getId());
        putIfNotBlank(summary, "status", normalize(task.getStatus()));
        putIfNotBlank(summary, "statusLabel", statusLabel);
        putIfNotBlank(summary, "resultText", publishTaskHistoryMessage(task));
        putIfNotBlank(summary, "submittedAt", formatDateTime(task.getSubmittedAt()));
        putIfNotBlank(summary, "finishedAt", taskHistoryTime(task));
        putIfNotBlank(summary, "targetSiteCode", task.getCurrentSiteCode());
        putIfNotBlank(summary, "pskuCode", task.getPskuCode());
        putIfNotBlank(summary, "partnerSku", task.getPartnerSku());
        putIfNotNull(summary, "changes", buildProductModificationChanges(baseline, draft, task.getCurrentSiteCode()));
        return summary;
    }

    public List<Map<String, Object>> buildPublishTaskHistoryItems(
            List<ProductPublishTaskRecord> tasks,
            List<String> warnings
    ) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            return history;
        }
        for (ProductPublishTaskRecord task : tasks) {
            ProductMasterSnapshotView baseline = readHistorySnapshot(task.getBaselineJson(), warnings);
            ProductMasterSnapshotView draft = readHistorySnapshot(task.getDraftJson(), warnings);
            List<Map<String, Object>> changes = buildProductModificationChanges(
                    baseline,
                    draft,
                    task.getCurrentSiteCode()
            );
            if (changes.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            putIfNotBlank(item, "historyKind", "modification");
            putIfNotBlank(item, "source", "publish_task");
            putIfNotNull(item, "taskId", task.getId());
            putIfNotBlank(item, "actionType", "publish-current");
            putIfNotBlank(item, "resultStatus", normalize(task.getStatus()));
            putIfNotBlank(item, "statusLabel", publishTaskStatusLabel(task.getStatus()));
            putIfNotBlank(item, "message", publishTaskHistoryMessage(task));
            putIfNotBlank(item, "targetSiteCode", task.getCurrentSiteCode());
            putIfNotBlank(item, "pskuCode", task.getPskuCode());
            putIfNotBlank(item, "partnerSku", task.getPartnerSku());
            putIfNotBlank(item, "publishedAt", taskHistoryTime(task));
            putIfNotBlank(item, "visibilityStatus", "modification");
            putIfNotNull(item, "changes", changes);
            putIfNotNull(item, "changeTypes", changeTypes(changes));
            history.add(item);
        }
        return history;
    }

    public String publishTaskListStatusLabel(String status) {
        String normalized = normalize(status);
        if ("synced".equalsIgnoreCase(normalized)) {
            return "发布成功";
        }
        if ("failed".equalsIgnoreCase(normalized)) {
            return "发布失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "待人工核对";
        }
        if ("cancelled".equalsIgnoreCase(normalized)) {
            return "已取消";
        }
        if ("queued".equalsIgnoreCase(normalized)
                || "running".equalsIgnoreCase(normalized)
                || "submitted".equalsIgnoreCase(normalized)
                || "verifying".equalsIgnoreCase(normalized)
                || "pending_effective".equalsIgnoreCase(normalized)
                || "write_unknown".equalsIgnoreCase(normalized)
                || "verify_timeout".equalsIgnoreCase(normalized)) {
            return "发布中";
        }
        return null;
    }

    public String publishTaskStatusLabel(String status) {
        String normalized = normalize(status);
        if ("synced".equalsIgnoreCase(normalized)) {
            return "发布成功";
        }
        if ("failed".equalsIgnoreCase(normalized)) {
            return "发布失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "待人工核对";
        }
        if ("pending_effective".equalsIgnoreCase(normalized)) {
            return "等待生效";
        }
        if ("write_unknown".equalsIgnoreCase(normalized) || "verify_timeout".equalsIgnoreCase(normalized)) {
            return "结果待确认";
        }
        if ("queued".equalsIgnoreCase(normalized)) {
            return "排队中";
        }
        if ("running".equalsIgnoreCase(normalized)
                || "submitted".equalsIgnoreCase(normalized)
                || "verifying".equalsIgnoreCase(normalized)) {
            return "发布中";
        }
        if ("cancelled".equalsIgnoreCase(normalized)) {
            return "已取消";
        }
        return StringUtils.hasText(normalized) ? normalized : "已记录";
    }

    public String publishTaskHistoryMessage(ProductPublishTaskRecord task) {
        String status = task == null ? null : normalize(task.getStatus());
        if ("synced".equalsIgnoreCase(status)) {
            return "发布已完成。";
        }
        if ("failed".equalsIgnoreCase(status)) {
            return firstNonBlank(task.getErrorMessage(), "发布失败，诺诺草稿已保留。");
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            String changedDomainText = publishTaskChangedDomainText(task);
            String targetText = StringUtils.hasText(changedDomainText)
                    ? "【" + changedDomainText + "】"
                    : "本次修改";
            return "Noon 多轮回读仍未确认" + targetText + "已生效。诺诺草稿已保留，请在官方后台核对。";
        }
        if ("pending_effective".equalsIgnoreCase(status)) {
            return "Noon 可能延迟生效，系统仍在回读校验。";
        }
        if ("write_unknown".equalsIgnoreCase(status) || "verify_timeout".equalsIgnoreCase(status)) {
            return "发布结果暂未确认，系统只回读校验。";
        }
        if ("queued".equalsIgnoreCase(status)) {
            return "发布已排队。";
        }
        if ("running".equalsIgnoreCase(status) || "submitted".equalsIgnoreCase(status) || "verifying".equalsIgnoreCase(status)) {
            return "发布处理中。";
        }
        return "发布任务已记录。";
    }

    public List<Map<String, Object>> buildProductModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (before == null || after == null) {
            return changes;
        }

        addModificationChange(changes, "content", "title_en", "英文标题",
                safeMap(before.getContent()).get("titleEn"), safeMap(after.getContent()).get("titleEn"));
        addModificationChange(changes, "content", "title_ar", "阿语标题",
                safeMap(before.getContent()).get("titleAr"), safeMap(after.getContent()).get("titleAr"));
        addModificationChange(changes, "content", "long_description_en", "英文长描述",
                safeMap(before.getContent()).get("descriptionEn"), safeMap(after.getContent()).get("descriptionEn"));
        addModificationChange(changes, "content", "long_description_ar", "阿语长描述",
                safeMap(before.getContent()).get("descriptionAr"), safeMap(after.getContent()).get("descriptionAr"));
        addModificationChange(changes, "content", "feature_bullet_en", "英文卖点",
                safeMap(before.getContent()).get("highlightsEn"), safeMap(after.getContent()).get("highlightsEn"));
        addModificationChange(changes, "content", "feature_bullet_ar", "阿语卖点",
                safeMap(before.getContent()).get("highlightsAr"), safeMap(after.getContent()).get("highlightsAr"));
        addModificationChange(changes, "content", "images", "图片",
                imageHistoryValue(safeMap(before.getContent()).get("images")),
                imageHistoryValue(safeMap(after.getContent()).get("images")));
        addModificationChange(changes, "content", "brand", "品牌",
                safeMap(before.getIdentity()).get("brand"), safeMap(after.getIdentity()).get("brand"));
        addModificationChange(changes, "content", "product_fulltype", "类目",
                safeMap(before.getTaxonomy()).get("productFulltype"), safeMap(after.getTaxonomy()).get("productFulltype"));

        changes.addAll(buildOfferModificationChanges(before, after, currentSiteCode));
        addVariantSizeChanges(changes, before.getVariants(), after.getVariants());
        addAttributeChanges(changes, before.getKeyAttributes(), after.getKeyAttributes());
        return changes;
    }

    public List<String> changeTypes(List<Map<String, Object>> changes) {
        List<String> types = new ArrayList<>();
        if (changes == null) {
            return types;
        }
        for (Map<String, Object> change : changes) {
            String domain = text(change.get("domain"));
            String type = StringUtils.hasText(domain) ? domain : text(change.get("field"));
            if (StringUtils.hasText(type) && !types.contains(type)) {
                types.add(type);
            }
        }
        return types;
    }

    private List<Map<String, Object>> buildOfferModificationChanges(
            ProductMasterSnapshotView before,
            ProductMasterSnapshotView after,
            String currentSiteCode
    ) {
        List<Map<String, Object>> changes = new ArrayList<>();
        Map<String, Object> beforeOffer = siteOfferForHistory(before, currentSiteCode);
        Map<String, Object> afterOffer = siteOfferForHistory(after, currentSiteCode);
        addModificationChange(changes, "offer", "price", "价格",
                offerValue(before, beforeOffer, "price"), offerValue(after, afterOffer, "price"));
        addModificationChange(changes, "offer", "salePrice", "活动价",
                offerValue(before, beforeOffer, "salePrice"), offerValue(after, afterOffer, "salePrice"));
        addModificationChange(changes, "offer", "saleStart", "活动开始",
                offerValue(before, beforeOffer, "saleStart"), offerValue(after, afterOffer, "saleStart"));
        addModificationChange(changes, "offer", "saleEnd", "活动结束",
                offerValue(before, beforeOffer, "saleEnd"), offerValue(after, afterOffer, "saleEnd"));
        addModificationChange(changes, "offer", "priceMin", "价格下限",
                offerValue(before, beforeOffer, "priceMin"), offerValue(after, afterOffer, "priceMin"));
        addModificationChange(changes, "offer", "priceMax", "价格上限",
                offerValue(before, beforeOffer, "priceMax"), offerValue(after, afterOffer, "priceMax"));
        addModificationChange(changes, "offer", "isActive", "在线状态",
                offerValue(before, beforeOffer, "isActive"), offerValue(after, afterOffer, "isActive"));
        addModificationChange(changes, "offer", "idWarranty", "质保",
                offerValue(before, beforeOffer, "idWarranty"), offerValue(after, afterOffer, "idWarranty"));
        addModificationChange(changes, "offer", "offerNote", "经营备注",
                offerValue(before, beforeOffer, "offerNote"), offerValue(after, afterOffer, "offerNote"));
        return changes;
    }

    private void addVariantSizeChanges(
            List<Map<String, Object>> changes,
            List<Map<String, Object>> beforeVariants,
            List<Map<String, Object>> afterVariants
    ) {
        Map<String, Map<String, Object>> beforeBySku = variantHistoryMap(beforeVariants);
        Map<String, Map<String, Object>> afterBySku = variantHistoryMap(afterVariants);
        List<String> childSkus = new ArrayList<>(beforeBySku.keySet());
        for (String childSku : afterBySku.keySet()) {
            if (!childSkus.contains(childSku)) {
                childSkus.add(childSku);
            }
        }
        for (String childSku : childSkus) {
            addModificationChange(
                    changes,
                    "sizes",
                    "variant_size",
                    variantSizeHistoryLabel(childSku),
                    variantSizeHistoryValue(beforeBySku.get(childSku)),
                    variantSizeHistoryValue(afterBySku.get(childSku))
            );
        }
    }

    private void addAttributeChanges(
            List<Map<String, Object>> changes,
            List<Map<String, Object>> beforeKeyAttributes,
            List<Map<String, Object>> afterKeyAttributes
    ) {
        Map<String, Map<String, Object>> beforeAttributes = keyAttributeMap(beforeKeyAttributes);
        Map<String, Map<String, Object>> afterAttributes = keyAttributeMap(afterKeyAttributes);
        List<String> attributeCodes = new ArrayList<>(beforeAttributes.keySet());
        for (String code : afterAttributes.keySet()) {
            if (!attributeCodes.contains(code)) {
                attributeCodes.add(code);
            }
        }
        for (String code : attributeCodes) {
            if (isDuplicatedHistoryAttribute(code)) {
                continue;
            }
            Map<String, Object> beforeAttribute = beforeAttributes.get(code);
            Map<String, Object> afterAttribute = afterAttributes.get(code);
            addModificationChange(
                    changes,
                    "attribute",
                    code,
                    attributeHistoryLabel(code, beforeAttribute, afterAttribute),
                    keyAttributeHistoryValue(beforeAttribute),
                    keyAttributeHistoryValue(afterAttribute)
            );
        }
    }

    private ProductMasterSnapshotView readHistorySnapshot(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductMasterSnapshotView.class);
        } catch (JsonProcessingException exception) {
            addWarningOnce(warnings, "读取发布任务快照失败，部分商品修改历史已跳过。");
            return null;
        }
    }

    private String taskHistoryTime(ProductPublishTaskRecord task) {
        if (task == null) {
            return null;
        }
        return firstNonBlank(
                formatDateTime(task.getFinishedAt()),
                formatDateTime(task.getVerifyFinishedAt()),
                formatDateTime(task.getSubmittedAt()),
                formatDateTime(task.getLockedAt())
        );
    }

    private List<String> resolvePublishTaskChangedDomains(ProductPublishTaskRecord task) {
        List<String> domains = parseStringList(task == null ? null : task.getChangedDomainsJson());
        boolean needsRecompute = domains.isEmpty()
                || domains.stream().anyMatch((domain) -> "unknown".equalsIgnoreCase(normalize(domain)));
        if (!needsRecompute || task == null) {
            return domains;
        }
        ProductMasterSnapshotView baseline = readHistorySnapshot(task.getBaselineJson(), null);
        ProductMasterSnapshotView draft = readHistorySnapshot(task.getDraftJson(), null);
        List<Map<String, Object>> changes = buildProductModificationChanges(
                baseline,
                draft,
                task.getCurrentSiteCode()
        );
        List<String> recomputedDomains = changeTypes(changes);
        return recomputedDomains.isEmpty() ? domains : recomputedDomains;
    }

    private String publishTaskChangedDomainText(ProductPublishTaskRecord task) {
        Set<String> labels = new LinkedHashSet<>();
        for (String domain : resolvePublishTaskChangedDomains(task)) {
            String label = publishTaskChangedDomainLabel(domain);
            if (StringUtils.hasText(label)) {
                labels.add(label);
            }
        }
        return String.join("、", labels);
    }

    private String publishTaskChangedDomainLabel(String domain) {
        String normalized = normalize(domain);
        if ("main".equalsIgnoreCase(normalized)) {
            return "商品主档";
        }
        if ("content".equalsIgnoreCase(normalized)) {
            return "图文内容";
        }
        if ("attributes".equalsIgnoreCase(normalized) || "attribute".equalsIgnoreCase(normalized)) {
            return "关键属性";
        }
        if ("site".equalsIgnoreCase(normalized) || "site_offer".equalsIgnoreCase(normalized) || "offer".equalsIgnoreCase(normalized)) {
            return "当前站点经营";
        }
        if ("grouping".equalsIgnoreCase(normalized)) {
            return "Group 与变体";
        }
        if ("sizes".equalsIgnoreCase(normalized)) {
            return "尺码";
        }
        if ("unknown".equalsIgnoreCase(normalized)) {
            return "未识别字段";
        }
        return normalized;
    }

    private List<String> parseStringList(String json) {
        List<String> values = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return values;
        }
        try {
            for (String value : objectMapper.readValue(json, new TypeReference<List<String>>() {
            })) {
                if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                }
            }
        } catch (JsonProcessingException exception) {
            String value = normalize(json);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, Map<String, Object>> variantHistoryMap(List<Map<String, Object>> variants) {
        Map<String, Map<String, Object>> byChildSku = new LinkedHashMap<>();
        if (variants == null) {
            return byChildSku;
        }
        int fallbackIndex = 1;
        for (Map<String, Object> variant : variants) {
            if (variant == null) {
                continue;
            }
            String childSku = firstNonBlank(
                    text(variant.get("childSku")),
                    text(variant.get("sku")),
                    text(variant.get("partnerSku")),
                    text(variant.get("pskuCode"))
            );
            if (!StringUtils.hasText(childSku)) {
                childSku = "variant-" + fallbackIndex;
            }
            byChildSku.put(childSku, variant);
            fallbackIndex++;
        }
        return byChildSku;
    }

    private String variantSizeHistoryLabel(String childSku) {
        return StringUtils.hasText(childSku) && !childSku.startsWith("variant-")
                ? "尺码（" + childSku + "）"
                : "尺码";
    }

    private Object variantSizeHistoryValue(Map<String, Object> variant) {
        if (variant == null) {
            return null;
        }
        String sizeEn = firstNonBlank(
                text(variant.get("sizeEn")),
                text(variant.get("size_en")),
                text(variant.get("size")),
                text(variant.get("value"))
        );
        String sizeAr = firstNonBlank(
                text(variant.get("sizeAr")),
                text(variant.get("size_ar")),
                text(variant.get("valueAr"))
        );
        if (StringUtils.hasText(sizeEn) && StringUtils.hasText(sizeAr) && !sizeEn.equals(sizeAr)) {
            return sizeEn + " / " + sizeAr;
        }
        return firstNonBlank(sizeEn, sizeAr);
    }

    private Map<String, Map<String, Object>> keyAttributeMap(List<Map<String, Object>> attributes) {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        if (attributes == null) {
            return byCode;
        }
        for (Map<String, Object> attribute : attributes) {
            String code = normalize(text(attribute.get("code")));
            if (StringUtils.hasText(code)) {
                byCode.put(code, attribute);
            }
        }
        return byCode;
    }

    private boolean isDuplicatedHistoryAttribute(String code) {
        String normalized = normalize(code);
        return "product_title".equalsIgnoreCase(normalized)
                || "long_description".equalsIgnoreCase(normalized)
                || "feature_bullet".equalsIgnoreCase(normalized)
                || "brand".equalsIgnoreCase(normalized)
                || "family".equalsIgnoreCase(normalized)
                || "product_type".equalsIgnoreCase(normalized)
                || "product_subtype".equalsIgnoreCase(normalized)
                || "product_fulltype".equalsIgnoreCase(normalized);
    }

    private Object keyAttributeHistoryValue(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        String value = firstNonBlank(
                text(attribute.get("commonValue")),
                text(attribute.get("enValue")),
                text(attribute.get("value")),
                text(attribute.get("valueEn"))
        );
        String unit = firstNonBlank(text(attribute.get("unit")), text(attribute.get("unitValue")));
        if (StringUtils.hasText(value) && StringUtils.hasText(unit)) {
            return value + " " + unit;
        }
        return value;
    }

    private String attributeHistoryLabel(
            String code,
            Map<String, Object> beforeAttribute,
            Map<String, Object> afterAttribute
    ) {
        Map<String, Object> source = afterAttribute != null ? afterAttribute : beforeAttribute;
        String englishLabel = source == null ? null : text(source.get("labelEn"));
        String mappedLabel = attributeHistoryChineseLabel(code);
        return StringUtils.hasText(mappedLabel)
                ? mappedLabel + (StringUtils.hasText(englishLabel) ? " / " + englishLabel : "")
                : firstNonBlank(englishLabel, code);
    }

    private String attributeHistoryChineseLabel(String code) {
        String normalized = normalize(code);
        if ("base_material".equalsIgnoreCase(normalized)) {
            return "基础材质";
        }
        if ("colour_name".equalsIgnoreCase(normalized)) {
            return "颜色名称";
        }
        if ("colour_family".equalsIgnoreCase(normalized)) {
            return "颜色系列";
        }
        if ("number_of_pieces".equalsIgnoreCase(normalized)) {
            return "件数";
        }
        if ("set_includes".equalsIgnoreCase(normalized)) {
            return "套装包含";
        }
        if ("control_method".equalsIgnoreCase(normalized)) {
            return "控制方式";
        }
        if ("model_number".equalsIgnoreCase(normalized)) {
            return "型号";
        }
        if ("model_name".equalsIgnoreCase(normalized)) {
            return "型号名称";
        }
        if ("product_length".equalsIgnoreCase(normalized)) {
            return "商品长度";
        }
        if ("product_height".equalsIgnoreCase(normalized)) {
            return "商品高度";
        }
        if ("product_width_depth".equalsIgnoreCase(normalized)) {
            return "商品宽度/深度";
        }
        if ("product_weight".equalsIgnoreCase(normalized)) {
            return "商品重量";
        }
        return null;
    }

    private void addModificationChange(
            List<Map<String, Object>> changes,
            String domain,
            String field,
            String label,
            Object beforeValue,
            Object afterValue
    ) {
        if (sameHistoryValue(field, beforeValue, afterValue)) {
            return;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        putIfNotBlank(change, "domain", domain);
        putIfNotBlank(change, "field", field);
        putIfNotBlank(change, "label", label);
        putIfNotBlank(change, "before", historyDisplayValue(field, beforeValue));
        putIfNotBlank(change, "after", historyDisplayValue(field, afterValue));
        changes.add(change);
    }

    private boolean sameHistoryValue(Object beforeValue, Object afterValue) {
        if (beforeValue == null && afterValue == null) {
            return true;
        }
        BigDecimal beforeNumber = asBigDecimal(beforeValue);
        BigDecimal afterNumber = asBigDecimal(afterValue);
        if (beforeNumber != null && afterNumber != null) {
            return beforeNumber.compareTo(afterNumber) == 0;
        }
        return normalizeHistoryText(beforeValue).equals(normalizeHistoryText(afterValue));
    }

    private boolean sameHistoryValue(String field, Object beforeValue, Object afterValue) {
        if (isOfferDateHistoryField(field)) {
            String beforeDate = normalizeHistoryDate(beforeValue);
            String afterDate = normalizeHistoryDate(afterValue);
            if (StringUtils.hasText(beforeDate) || StringUtils.hasText(afterDate)) {
                return Objects.equals(beforeDate, afterDate);
            }
        }
        return sameHistoryValue(beforeValue, afterValue);
    }

    private boolean isOfferDateHistoryField(String field) {
        return "saleStart".equals(field) || "saleEnd".equals(field);
    }

    private String normalizeHistoryDate(Object value) {
        String rawText = text(value);
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(rawText).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(rawText, TIME_FORMATTER).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(rawText).toString();
        } catch (DateTimeParseException ignored) {
            return rawText;
        }
    }

    private String normalizeHistoryText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List || value instanceof Map) {
            String json = toJson(value);
            return json == null ? String.valueOf(value).trim() : json;
        }
        return String.valueOf(value).trim();
    }

    private String historyDisplayValue(String field, Object value) {
        if (value == null) {
            return "空";
        }
        if ("isActive".equals(field)) {
            Boolean active = asBoolean(value);
            if (active != null) {
                return active ? "在线" : "不在线";
            }
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? "空" : list.size() + " 项";
        }
        String valueText = text(value);
        return StringUtils.hasText(valueText) ? valueText : "空";
    }

    private Object imageHistoryValue(Object value) {
        if (value instanceof List) {
            return ((List<?>) value).size() + " 张";
        }
        List<String> images = stringList(value);
        return images.isEmpty() ? null : images.size() + " 张";
    }

    private Object offerValue(ProductMasterSnapshotView snapshot, Map<String, Object> offer, String field) {
        if (offer != null && offer.containsKey(field)) {
            return offer.get(field);
        }
        return safeMap(snapshot == null ? null : snapshot.getPricing()).get(field);
    }

    private Map<String, Object> siteOfferForHistory(ProductMasterSnapshotView snapshot, String currentSiteCode) {
        if (snapshot == null || snapshot.getSiteOffers() == null || snapshot.getSiteOffers().isEmpty()) {
            return null;
        }
        String normalizedSiteCode = normalize(currentSiteCode);
        if (StringUtils.hasText(normalizedSiteCode)) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                if (normalizedSiteCode.equalsIgnoreCase(text(siteOffer.get("storeCode")))) {
                    return siteOffer;
                }
            }
        }
        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            if (Boolean.TRUE.equals(siteOffer.get("reference"))) {
                return siteOffer;
            }
        }
        return snapshot.getSiteOffers().get(0);
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value == null) {
            return values;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String itemText = text(item);
                if (StringUtils.hasText(itemText)) {
                    values.add(itemText);
                }
            }
            return values;
        }
        String text = text(value);
        if (StringUtils.hasText(text)) {
            values.add(text);
        }
        return values;
    }

    private Map<String, Object> safeMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void addWarningOnce(List<String> warnings, String warning) {
        if (warnings == null || !StringUtils.hasText(warning) || warnings.contains(warning)) {
            return;
        }
        warnings.add(warning);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key, value);
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        target.put(key, value);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : TIME_FORMATTER.format(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
