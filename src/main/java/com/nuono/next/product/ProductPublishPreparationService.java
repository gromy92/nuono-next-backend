package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductPublishPreparationService {

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectMapper objectMapper;
    private final ProductDraftMergePolicy productDraftMergePolicy;
    private final ProductPublishOfferWriter productPublishOfferWriter;

    ProductPublishPreparationService(
            ObjectMapper objectMapper,
            ProductDraftMergePolicy productDraftMergePolicy,
            ProductPublishOfferWriter productPublishOfferWriter
    ) {
        this.objectMapper = objectMapper;
        this.productDraftMergePolicy = productDraftMergePolicy;
        this.productPublishOfferWriter = productPublishOfferWriter;
    }

    ProductMasterSnapshotView prepareSnapshotForPublish(
            ProductMasterSnapshotView requestedSnapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (requestedSnapshot == null || baseline == null) {
            return requestedSnapshot;
        }
        ProductMasterSnapshotView preparedSnapshot = copySnapshot(requestedSnapshot);
        hydrateWritableOfferFieldsForPublish(preparedSnapshot, baseline, currentSiteCode);
        return preparedSnapshot;
    }

    boolean sharedZskuChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
        return !objectMapper.valueToTree(sharedZskuComparableView(draft))
                .equals(objectMapper.valueToTree(sharedZskuComparableView(baseline)));
    }

    boolean groupChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
        return !objectMapper.valueToTree(publishComparableGroup(draft))
                .equals(objectMapper.valueToTree(publishComparableGroup(baseline)));
    }

    boolean siteOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
        return !objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)));
    }

    List<Map<String, Object>> targetOffers(ProductMasterSnapshotView draft, String currentSiteCode) {
        return siteOfferComparableList(draft, currentSiteCode, false);
    }

    Map<String, Map<String, Object>> baselineOffers(ProductMasterSnapshotView baseline) {
        return siteOfferMap(baseline != null ? baseline.getSiteOffers() : null);
    }

    boolean shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (draft == null || baseline == null || !StringUtils.hasText(currentSiteCode)) {
            return false;
        }
        boolean sharedChanged = !objectMapper.valueToTree(sharedComparableView(draft))
                .equals(objectMapper.valueToTree(sharedComparableView(baseline)));
        if (!sharedChanged) {
            return false;
        }
        Map<String, Object> draftOffer = siteOfferMap(draft.getSiteOffers()).get(currentSiteCode);
        Map<String, Object> baselineOffer = siteOfferMap(baseline.getSiteOffers()).get(currentSiteCode);
        return objectMapper.valueToTree(siteOfferComparable(
                draftOffer != null ? draftOffer : new LinkedHashMap<>(),
                false
        )).equals(objectMapper.valueToTree(siteOfferComparable(
                baselineOffer != null ? baselineOffer : new LinkedHashMap<>(),
                false
        )));
    }

    boolean sameBusinessSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right) {
        return toComparableBusinessJson(left).equals(toComparableBusinessJson(right));
    }

    boolean sameScopedSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right, String siteCode) {
        return toComparableScopedJson(left, siteCode).equals(toComparableScopedJson(right, siteCode));
    }

    boolean publishChangedFieldsMatch(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView noonCurrent,
            String siteCode
    ) {
        return publishChangedFieldsMatch(
                toPublishComparableScopedJson(baseline, siteCode),
                toPublishComparableScopedJson(draft, siteCode),
                toPublishComparableScopedJson(noonCurrent, siteCode)
        );
    }

    List<Map<String, Object>> detectPublishConflictFields(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView localDraft,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        collectPublishConflictFields(
                "",
                toPublishComparableScopedJson(baseline, currentSiteCode),
                toPublishComparableScopedJson(localDraft, currentSiteCode),
                toPublishComparableScopedJson(noonCurrent, currentSiteCode),
                conflicts
        );
        return conflicts;
    }

    Map<String, Object> buildPublishConflictPayload(
            List<Map<String, Object>> fields,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "blocked");
        payload.put("message", "Noon 当前内容与本地草稿存在同字段冲突，请选择使用哪边内容。");
        payload.put("currentSiteCode", currentSiteCode);
        payload.put("checkedAt", extractFetchedAt(noonCurrent));
        payload.put("fields", fields);
        return payload;
    }

    List<String> validatePublishSnapshot(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        String titleEn = textValue(snapshot.getContent().get("titleEn"));
        String brand = textValue(snapshot.getIdentity().get("brand"));
        String productFulltype = textValue(snapshot.getTaxonomy().get("productFulltype"));
        List<String> images = stringList(snapshot.getContent().get("images"));

        if (!StringUtils.hasText(titleEn)) {
            errors.add("共享主档缺少标题 EN。");
        }
        if (!StringUtils.hasText(brand)) {
            errors.add("共享主档缺少品牌。");
        }
        if (!StringUtils.hasText(productFulltype)) {
            errors.add("共享主档缺少 Fulltype。");
        }
        if (images.isEmpty()) {
            errors.add("共享主档至少需要保留 1 张图片。");
        }
        List<String> baselineImages = baseline == null ? List.of() : stringList(baseline.getContent().get("images"));
        if (!objectMapper.valueToTree(images).equals(objectMapper.valueToTree(baselineImages))
                && images.stream().anyMatch(this::isLocalProductImageAssetUrl)) {
            errors.add("本地上传图片还没有 Noon 可访问 URL，暂时不能发布；请先删除本地上传图片或等待图片外链适配。");
        }

        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline != null ? baseline.getSiteOffers() : null);
        for (Map<String, Object> siteOffer : siteOfferComparableList(snapshot, currentSiteCode, false)) {
            String siteCode = textValue(siteOffer.get("storeCode"));
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer != null
                    && objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                    .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)))) {
                continue;
            }
            String label = textValue(siteOffer.get("site")) + " / " + textValue(siteOffer.get("storeCode"));
            BigDecimal price = asBigDecimal(siteOffer.get("price"));
            BigDecimal salePrice = asBigDecimal(siteOffer.get("salePrice"));
            BigDecimal priceMin = asBigDecimal(siteOffer.get("priceMin"));
            BigDecimal priceMax = asBigDecimal(siteOffer.get("priceMax"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(label + " 缺少有效售价。");
            }
            if (salePrice != null && price != null && salePrice.compareTo(price) > 0) {
                errors.add(label + " 的促销价不能高于原价。");
            }
            if (price != null && priceMin != null && price.compareTo(priceMin) < 0) {
                errors.add(label + " 的售价低于允许范围。");
            }
            if (price != null && priceMax != null && price.compareTo(priceMax) > 0) {
                errors.add(label + " 的售价高于允许范围。");
            }
        }
        return errors;
    }

    List<String> validatePublishOperationalKeys(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Object>> draftOffers = siteOfferMap(draft.getSiteOffers());
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline.getSiteOffers());
        Set<String> relevantSiteCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(currentSiteCode)) {
            relevantSiteCodes.add(currentSiteCode);
        } else {
            relevantSiteCodes.addAll(draftOffers.keySet());
        }
        for (String siteCode : relevantSiteCodes) {
            Map<String, Object> siteOffer = draftOffers.get(siteCode);
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (siteOffer == null || baselineOffer == null) {
                continue;
            }
            if (objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                    .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)))) {
                continue;
            }
            if (!StringUtils.hasText(firstNonBlank(
                    textValue(siteOffer.get("pskuCode")),
                    textValue(baselineOffer.get("pskuCode"))
            ))) {
                errors.add(
                        textValue(siteOffer.get("site"))
                                + " / "
                                + siteCode
                                + " 缺少 pskuCode，暂时不能发布当前站点经营字段。"
                );
            }
        }
        return errors;
    }

    private void hydrateWritableOfferFieldsForPublish(
            ProductMasterSnapshotView target,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (target == null || baseline == null || target.getSiteOffers() == null) {
            return;
        }
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline.getSiteOffers());
        String normalizedCurrentSiteCode = normalize(currentSiteCode);
        for (Map<String, Object> targetOffer : target.getSiteOffers()) {
            if (targetOffer == null) {
                continue;
            }
            String siteCode = siteOfferCode(targetOffer);
            if (StringUtils.hasText(normalizedCurrentSiteCode)
                    && !normalizedCurrentSiteCode.equalsIgnoreCase(siteCode)) {
                continue;
            }
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer == null) {
                continue;
            }
            hydrateWritableOfferFieldsForPublish(targetOffer, baselineOffer);
            if (isPublishOfferChanged(targetOffer, baselineOffer)) {
                applyDefaultSaleWindowForPublish(targetOffer);
            }
            mirrorCurrentOfferToPricing(target, targetOffer, normalizedCurrentSiteCode);
        }
    }

    private void hydrateWritableOfferFieldsForPublish(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer
    ) {
        Map<String, Object> hydrated = productDraftMergePolicy.hydrateMissingOfferFieldsForPublish(
                targetOffer,
                baselineOffer,
                List.of(
                        "site",
                        "pskuCode",
                        "offerCode",
                        "currency",
                        "price",
                        "salePrice",
                        "saleStart",
                        "saleEnd",
                        "priceMin",
                        "priceMax",
                        "isActive",
                        "idWarranty"
                )
        );
        targetOffer.clear();
        targetOffer.putAll(hydrated);
        copyBaselineOfferFieldIfMissing(targetOffer, baselineOffer, "offerNote");
    }

    private void applyDefaultSaleWindowForPublish(Map<String, Object> siteOffer) {
        if (asBigDecimal(siteOffer.get("salePrice")) == null) {
            return;
        }
        boolean missingSaleStart = !StringUtils.hasText(textValue(siteOffer.get("saleStart")));
        boolean missingSaleEnd = !StringUtils.hasText(textValue(siteOffer.get("saleEnd")));
        if (!missingSaleStart && !missingSaleEnd) {
            return;
        }
        Map<String, String> saleWindow = productPublishOfferWriter.saleWindowForPublish(siteOffer);
        if (missingSaleStart && StringUtils.hasText(saleWindow.get("saleStart"))) {
            siteOffer.put("saleStart", saleWindow.get("saleStart"));
        }
        if (missingSaleEnd && StringUtils.hasText(saleWindow.get("saleEnd"))) {
            siteOffer.put("saleEnd", saleWindow.get("saleEnd"));
        }
    }

    private boolean isPublishOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
        return !objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)));
    }

    private void mirrorCurrentOfferToPricing(
            ProductMasterSnapshotView target,
            Map<String, Object> siteOffer,
            String currentSiteCode
    ) {
        String siteCode = siteOfferCode(siteOffer);
        Map<String, Object> storeContext = target.getStoreContext() == null ? Map.of() : target.getStoreContext();
        String pricingSiteCode = firstNonBlank(currentSiteCode, textValue(storeContext.get("storeCode")));
        if (!StringUtils.hasText(siteCode)
                || !StringUtils.hasText(pricingSiteCode)
                || !siteCode.equalsIgnoreCase(pricingSiteCode)) {
            return;
        }
        if (target.getPricing() == null) {
            target.setPricing(new LinkedHashMap<>());
        }
        for (String field : new String[]{
                "price",
                "salePrice",
                "saleStart",
                "saleEnd",
                "priceMin",
                "priceMax",
                "isActive",
                "idWarranty",
                "offerNote"
        }) {
            if (siteOffer.containsKey(field)) {
                target.getPricing().put(field, siteOffer.get(field));
            }
        }
    }

    private void copyBaselineOfferFieldIfMissing(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer,
            String field
    ) {
        if (!targetOffer.containsKey(field) && baselineOffer.containsKey(field)) {
            targetOffer.put(field, baselineOffer.get(field));
        }
    }

    private boolean publishChangedFieldsMatch(JsonNode baseline, JsonNode draft, JsonNode noonCurrent) {
        JsonNode baselineNode = comparableNode(baseline);
        JsonNode draftNode = comparableNode(draft);
        JsonNode noonNode = comparableNode(noonCurrent);
        if (baselineNode.equals(draftNode)) {
            return true;
        }
        if (baselineNode.isObject() || draftNode.isObject() || noonNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(baselineNode, fieldNames);
            collectFieldNames(draftNode, fieldNames);
            collectFieldNames(noonNode, fieldNames);
            for (String fieldName : fieldNames) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(fieldName),
                        draftNode.path(fieldName),
                        noonNode.path(fieldName)
                )) {
                    return false;
                }
            }
            return true;
        }
        if (baselineNode.isArray() || draftNode.isArray() || noonNode.isArray()) {
            int maxSize = Math.max(baselineNode.size(), Math.max(draftNode.size(), noonNode.size()));
            for (int index = 0; index < maxSize; index++) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(index),
                        draftNode.path(index),
                        noonNode.path(index)
                )) {
                    return false;
                }
            }
            return true;
        }
        return draftNode.equals(noonNode);
    }

    private void collectPublishConflictFields(
            String path,
            JsonNode baseline,
            JsonNode localDraft,
            JsonNode noonCurrent,
            List<Map<String, Object>> conflicts
    ) {
        JsonNode baselineNode = comparableNode(baseline);
        JsonNode localNode = comparableNode(localDraft);
        JsonNode noonNode = comparableNode(noonCurrent);
        if (baselineNode.isObject() || localNode.isObject() || noonNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(baselineNode, fieldNames);
            collectFieldNames(localNode, fieldNames);
            collectFieldNames(noonNode, fieldNames);
            for (String fieldName : fieldNames) {
                String childPath = StringUtils.hasText(path) ? path + "." + fieldName : fieldName;
                collectPublishConflictFields(
                        childPath,
                        baselineNode.path(fieldName),
                        localNode.path(fieldName),
                        noonNode.path(fieldName),
                        conflicts
                );
            }
            return;
        }
        if (baselineNode.isArray() || localNode.isArray() || noonNode.isArray()) {
            int maxSize = Math.max(baselineNode.size(), Math.max(localNode.size(), noonNode.size()));
            for (int index = 0; index < maxSize; index++) {
                collectPublishConflictFields(
                        path + "[" + index + "]",
                        baselineNode.path(index),
                        localNode.path(index),
                        noonNode.path(index),
                        conflicts
                );
            }
            return;
        }

        boolean localChanged = !baselineNode.equals(localNode);
        boolean noonChanged = !baselineNode.equals(noonNode);
        if (!localChanged || !noonChanged || localNode.equals(noonNode)) {
            return;
        }
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("path", path);
        conflict.put("label", publishConflictFieldLabel(path));
        conflict.put("baselineValue", jsonNodeValue(baselineNode));
        conflict.put("localValue", jsonNodeValue(localNode));
        conflict.put("noonValue", jsonNodeValue(noonNode));
        conflict.put("scope", path.startsWith("siteOffers") ? "site" : "shared");
        conflicts.add(conflict);
    }

    private void collectFieldNames(JsonNode node, Set<String> fieldNames) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
    }

    private JsonNode comparableNode(JsonNode node) {
        return node == null || node.isMissingNode() ? MissingNode.getInstance() : node;
    }

    private Object jsonNodeValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        return node.toString();
    }

    private String publishConflictFieldLabel(String path) {
        if (!StringUtils.hasText(path)) {
            return "商品字段";
        }
        if (path.contains("titleEn")) {
            return "英文标题";
        }
        if (path.contains("descriptionEn")) {
            return "英文长描述";
        }
        if (path.contains("highlightsEn")) {
            return "英文卖点";
        }
        if (path.contains("images")) {
            return "商品图片";
        }
        if (path.contains("priceMin")) {
            return "最低价格";
        }
        if (path.contains("priceMax")) {
            return "最高价格";
        }
        if (path.contains("salePrice")) {
            return "活动价";
        }
        if (path.contains("saleStart")) {
            return "活动开始时间";
        }
        if (path.contains("saleEnd")) {
            return "活动结束时间";
        }
        if (path.contains("price")) {
            return "价格";
        }
        if (path.contains("isActive")) {
            return "在线状态";
        }
        if (path.contains("idWarranty")) {
            return "质保";
        }
        if (path.contains("offerNote")) {
            return "Offer 备注";
        }
        if (path.contains("keyAttributes")) {
            return "商品属性";
        }
        return path;
    }

    private JsonNode toComparableBusinessJson(ProductMasterSnapshotView snapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, null, true)));
        return node;
    }

    private JsonNode toComparableScopedJson(ProductMasterSnapshotView snapshot, String siteCode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, siteCode, true)));
        return node;
    }

    private JsonNode toPublishComparableScopedJson(ProductMasterSnapshotView snapshot, String siteCode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, siteCode, false)));
        return node;
    }

    private Map<String, Object> sharedComparableView(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("identity", publishComparableIdentity(snapshot));
        comparable.put("taxonomy", publishComparableTaxonomy(snapshot));
        comparable.put("content", publishComparableContent(snapshot));
        comparable.put("keyAttributes", publishComparableKeyAttributes(snapshot));
        comparable.put("group", publishComparableGroup(snapshot));
        comparable.put("variants", publishComparableVariants(snapshot));
        return comparable;
    }

    private Map<String, Object> sharedZskuComparableView(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("identity", publishComparableIdentity(snapshot));
        comparable.put("taxonomy", publishComparableTaxonomy(snapshot));
        comparable.put("content", publishComparableContent(snapshot));
        comparable.put("keyAttributes", publishComparableKeyAttributes(snapshot));
        comparable.put("variants", publishComparableVariants(snapshot));
        return comparable;
    }

    private Map<String, Object> publishComparableGroup(ProductMasterSnapshotView snapshot) {
        return publishComparableGroup(snapshot != null ? snapshot.getGroup() : null);
    }

    private Map<String, Object> publishComparableGroup(Map<String, Object> group) {
        Map<String, Object> comparable = groupStructureComparable(group);
        comparable.put("memberAxisValues", groupMemberAxisValuesComparable(group, "en"));
        comparable.put("memberAxisValuesAr", groupMemberAxisValuesComparable(group, "ar"));
        return comparable;
    }

    private Map<String, Object> groupStructureComparable(Map<String, Object> group) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (group == null) {
            return comparable;
        }
        comparable.put("skuGroup", textValue(group.get("skuGroup")));
        comparable.put("groupRef", textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = textValue(firstNonNull(axis.get("axisCode"), axis.get("axis_code")));
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", textValue(firstNonNull(axis.get("axisName"), axis.get("axis_name"))));
            axes.add(axisComparable);
        }
        axes.sort((left, right) -> textValue(left.get("axisCode")).compareTo(textValue(right.get("axisCode"))));
        comparable.put("axes", axes);

        List<String> memberSkuParents = new ArrayList<>();
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (StringUtils.hasText(skuParent)) {
                memberSkuParents.add(skuParent);
            }
        }
        memberSkuParents.sort(String::compareTo);
        comparable.put("memberSkuParents", memberSkuParents);
        return comparable;
    }

    private List<Map<String, Object>> groupMemberAxisValuesComparable(Map<String, Object> group, String lang) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (group == null) {
            return comparable;
        }
        List<String> axisCodes = groupAxisCodes(recordListValue(group.get("axes")));
        if (axisCodes.isEmpty()) {
            return comparable;
        }
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String axisCode : axisCodes) {
                values.put(axisCode, groupMemberAxisValue(member, axisCode, lang));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuParent", skuParent);
            row.put("values", values);
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("skuParent")).compareTo(textValue(right.get("skuParent"))));
        return comparable;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recordListValue(Object value) {
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                records.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return records;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String groupMemberSkuParent(Map<String, Object> member) {
        if (member == null) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("skuParent")),
                textValue(member.get("parentSku")),
                textValue(member.get("sku")),
                textValue(member.get("childSku")),
                textValue(member.get("partnerSku"))
        );
    }

    @SuppressWarnings("unchecked")
    private String groupMemberAxisValue(Map<String, Object> member, String axisCode, String lang) {
        if (member == null || !StringUtils.hasText(axisCode)) {
            return null;
        }
        String normalizedLang = normalize(lang);
        if ("ar".equalsIgnoreCase(normalizedLang)) {
            Map<String, Object> axisValuesAr = member.get("axisValuesAr") instanceof Map<?, ?>
                    ? (Map<String, Object>) member.get("axisValuesAr")
                    : Map.of();
            String axisSpecificArValue = firstNonBlank(
                    textValue(axisValuesAr.get(axisCode)),
                    textValue(member.get(axisCode + "Ar"))
            );
            if (StringUtils.hasText(axisSpecificArValue)) {
                return axisSpecificArValue;
            }
            if (!axisValuesAr.isEmpty()) {
                return null;
            }
            return firstNonBlank(
                    textValue(member.get("axisValueAr"))
            );
        }
        Map<String, Object> axisValues = member.get("axisValues") instanceof Map<?, ?>
                ? (Map<String, Object>) member.get("axisValues")
                : Map.of();
        String axisSpecificValue = firstNonBlank(
                textValue(member.get(axisCode)),
                textValue(axisValues.get(axisCode))
        );
        if (StringUtils.hasText(axisSpecificValue)) {
            return axisSpecificValue;
        }
        if (!axisValues.isEmpty()) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("axisValue"))
        );
    }

    private Map<String, Object> publishComparableIdentity(ProductMasterSnapshotView snapshot) {
        Map<String, Object> identity = snapshot != null && snapshot.getIdentity() != null
                ? snapshot.getIdentity()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("brand", textValue(identity.get("brand")));
        return comparable;
    }

    private Map<String, Object> publishComparableTaxonomy(ProductMasterSnapshotView snapshot) {
        Map<String, Object> taxonomy = snapshot != null && snapshot.getTaxonomy() != null
                ? snapshot.getTaxonomy()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("family", textValue(taxonomy.get("family")));
        comparable.put("productType", textValue(taxonomy.get("productType")));
        comparable.put("productSubtype", textValue(taxonomy.get("productSubtype")));
        comparable.put("productFulltype", textValue(taxonomy.get("productFulltype")));
        comparable.put("grade", textValue(taxonomy.get("grade")));
        comparable.put("itemCondition", textValue(taxonomy.get("itemCondition")));
        return comparable;
    }

    private Map<String, Object> publishComparableContent(ProductMasterSnapshotView snapshot) {
        Map<String, Object> content = snapshot != null && snapshot.getContent() != null
                ? snapshot.getContent()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("titleEn", textValue(content.get("titleEn")));
        comparable.put("titleAr", textValue(content.get("titleAr")));
        comparable.put("descriptionEn", textValue(content.get("descriptionEn")));
        comparable.put("descriptionAr", textValue(content.get("descriptionAr")));
        comparable.put("highlightsEn", stringList(content.get("highlightsEn")));
        comparable.put("highlightsAr", stringList(content.get("highlightsAr")));
        comparable.put("images", stringList(content.get("images")));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableKeyAttributes(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getKeyAttributes() != null
                ? snapshot.getKeyAttributes()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> attribute : source) {
            if (attribute == null) {
                continue;
            }
            String code = normalizeAttributeCode(textValue(attribute.get("code")));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("commonValue", attribute.get("commonValue"));
            row.put("enValue", attribute.get("enValue"));
            row.put("arValue", attribute.get("arValue"));
            row.put("unit", textValue(attribute.get("unit")));
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("code")).compareTo(textValue(right.get("code"))));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableVariants(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getVariants() != null
                ? snapshot.getVariants()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> variant : source) {
            if (variant == null) {
                continue;
            }
            String childSku = textValue(variant.get("childSku"));
            if (!StringUtils.hasText(childSku)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childSku", childSku);
            row.put("sizeEn", textValue(variant.get("sizeEn")));
            row.put("sizeAr", textValue(variant.get("sizeAr")));
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("childSku")).compareTo(textValue(right.get("childSku"))));
        return comparable;
    }

    private List<Map<String, Object>> siteOfferComparableList(
            ProductMasterSnapshotView snapshot,
            String siteCode,
            boolean includeUnsupportedFields
    ) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (snapshot == null || snapshot.getSiteOffers() == null) {
            return comparable;
        }

        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = siteOfferCode(siteOffer);
            if (StringUtils.hasText(siteCode) && !storeCode.equalsIgnoreCase(siteCode)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            putIfNotNull(row, "storeCode", storeCode);
            putIfNotNull(row, "site", siteOffer.get("site"));
            putComparableDecimalIfPresent(row, "price", siteOffer.get("price"));
            putComparableDecimalIfPresent(row, "salePrice", siteOffer.get("salePrice"));
            putIfNotNull(row, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
            putIfNotNull(row, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
            putComparableDecimalIfPresent(row, "priceMin", siteOffer.get("priceMin"));
            putComparableDecimalIfPresent(row, "priceMax", siteOffer.get("priceMax"));
            putComparableBooleanIfPresent(row, "isActive", siteOffer.get("isActive"));
            putComparableDecimalIfPresent(row, "idWarranty", siteOffer.get("idWarranty"));
            putIfNotNull(row, "offerNote", siteOffer.get("offerNote"));
            if (includeUnsupportedFields) {
                putIfNotNull(row, "barcode", siteOffer.get("barcode"));
            }
            comparable.add(row);
        }
        return comparable;
    }

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            map.put(siteOfferCode(siteOffer), new LinkedHashMap<>(siteOffer));
        }
        return map;
    }

    private Map<String, Object> siteOfferComparable(Map<String, Object> siteOffer, boolean includeUnsupportedFields) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (siteOffer == null) {
            return comparable;
        }
        putIfNotNull(comparable, "storeCode", siteOffer.get("storeCode"));
        putIfNotNull(comparable, "site", siteOffer.get("site"));
        putComparableDecimalIfPresent(comparable, "price", siteOffer.get("price"));
        putComparableDecimalIfPresent(comparable, "salePrice", siteOffer.get("salePrice"));
        putIfNotNull(comparable, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
        putIfNotNull(comparable, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
        putComparableDecimalIfPresent(comparable, "priceMin", siteOffer.get("priceMin"));
        putComparableDecimalIfPresent(comparable, "priceMax", siteOffer.get("priceMax"));
        putComparableBooleanIfPresent(comparable, "isActive", siteOffer.get("isActive"));
        putComparableDecimalIfPresent(comparable, "idWarranty", siteOffer.get("idWarranty"));
        putIfNotNull(comparable, "offerNote", siteOffer.get("offerNote"));
        if (includeUnsupportedFields) {
            putIfNotNull(comparable, "barcode", siteOffer.get("barcode"));
        }
        return comparable;
    }

    private List<String> groupAxisCodes(List<Map<String, Object>> axes) {
        List<String> axisCodes = new ArrayList<>();
        for (Map<String, Object> axis : axes) {
            String axisCode = textValue(axis.get("axisCode"));
            if (StringUtils.hasText(axisCode)) {
                axisCodes.add(axisCode);
            }
        }
        return axisCodes;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private String extractFetchedAt(ProductMasterSnapshotView snapshot) {
        if (snapshot == null || snapshot.getStoreContext() == null) {
            return null;
        }
        return textValue(snapshot.getStoreContext().get("fetchedAt"));
    }

    private boolean isLocalProductImageAssetUrl(String url) {
        return StringUtils.hasText(url) && url.trim().startsWith("/api/product-master/image-assets/");
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        } else if (StringUtils.hasText(textValue(value))) {
            values.add(textValue(value));
        }
        return values;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
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
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private void putComparableDecimalIfPresent(Map<String, Object> target, String key, Object value) {
        BigDecimal decimal = asBigDecimal(value);
        if (decimal != null) {
            target.put(key, decimal.stripTrailingZeros().toPlainString());
        } else if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, String.valueOf(value).trim());
        }
    }

    private void putComparableBooleanIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, truthy(value));
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeAttributeCode(String value) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase() : null;
    }
}
