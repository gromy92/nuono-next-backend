package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductAttributeTemplateMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductAttributeTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ProductAttributeTemplateService.class);
    private static final String FULLTYPE_ATTRS_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-mpcatalog/catalog/get-fulltype-attributes-new";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final ProductAttributeTemplateMapper templateMapper;
    private final ProductManagementMapper productManagementMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, JsonNode> memoryCache = new ConcurrentHashMap<>();

    @Value("${nuono.product-management.attribute-template.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-management.attribute-template.scheduler.stale-days:7}")
    private int schedulerStaleDays;

    @Value("${nuono.product-management.attribute-template.scheduler.max-items-per-tick:5}")
    private int schedulerMaxItemsPerTick;

    public ProductAttributeTemplateService(
            ProductAttributeTemplateMapper templateMapper,
            ProductManagementMapper productManagementMapper,
            StoreSyncMapper storeSyncMapper,
            ProductNoonAdapter productNoonAdapter,
            ObjectMapper objectMapper
    ) {
        this.templateMapper = templateMapper;
        this.productManagementMapper = productManagementMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.objectMapper = objectMapper;
    }

    public JsonNode loadTemplate(
            NoonSession session,
            String projectCode,
            String storeCode,
            String productFulltype,
            Long operatorUserId,
            List<String> warnings
    ) {
        String normalizedProjectCode = normalize(projectCode);
        String normalizedStoreCode = normalize(storeCode);
        String normalizedFulltype = normalize(productFulltype);
        if (!StringUtils.hasText(normalizedFulltype)
                || !StringUtils.hasText(normalizedProjectCode)
                || !StringUtils.hasText(normalizedStoreCode)) {
            return MissingNode.getInstance();
        }

        String cacheKey = cacheKey(normalizedProjectCode, normalizedStoreCode, normalizedFulltype);
        JsonNode memoryTemplate = memoryCache.get(cacheKey);
        if (usable(memoryTemplate)) {
            return memoryTemplate;
        }

        ProductAttributeTemplateRecord storedTemplate =
                templateMapper.selectByScope(normalizedProjectCode, normalizedStoreCode, normalizedFulltype);
        if (storedTemplate != null && fresh(storedTemplate)) {
            JsonNode rawTemplate = parseStoredTemplate(storedTemplate, warnings);
            if (usable(rawTemplate)) {
                JsonNode templateWithDictionary = attachDictionary(
                        rawTemplate,
                        normalizedProjectCode,
                        normalizedStoreCode,
                        normalizedFulltype
                );
                memoryCache.put(cacheKey, templateWithDictionary);
                return templateWithDictionary;
            }
        }

        JsonNode liveTemplate = fetchTemplate(session, normalizedFulltype, warnings);
        if (usable(liveTemplate)) {
            upsertTemplate(
                    normalizedProjectCode,
                    normalizedStoreCode,
                    normalizedFulltype,
                    liveTemplate,
                    operatorUserId,
                    warnings
            );
            JsonNode templateWithDictionary = attachDictionary(
                    liveTemplate,
                    normalizedProjectCode,
                    normalizedStoreCode,
                    normalizedFulltype
            );
            memoryCache.put(cacheKey, templateWithDictionary);
            return templateWithDictionary;
        }

        if (storedTemplate != null) {
            JsonNode staleTemplate = parseStoredTemplate(storedTemplate, warnings);
            if (usable(staleTemplate)) {
                String warning = "fulltype 模板实时读取失败，已回退到本地过期模板缓存。";
                if (warnings != null && !warnings.contains(warning)) {
                    warnings.add(warning);
                }
                JsonNode templateWithDictionary = attachDictionary(
                        staleTemplate,
                        normalizedProjectCode,
                        normalizedStoreCode,
                        normalizedFulltype
                );
                memoryCache.put(cacheKey, templateWithDictionary);
                return templateWithDictionary;
            }
        }
        JsonNode dictionaryOnlyTemplate = attachDictionary(
                objectMapper.createObjectNode(),
                normalizedProjectCode,
                normalizedStoreCode,
                normalizedFulltype
        );
        return usable(dictionaryOnlyTemplate.path("_nuonoAttributeDictionary"))
                ? dictionaryOnlyTemplate
                : MissingNode.getInstance();
    }

    private JsonNode fetchTemplate(NoonSession session, String productFulltype, List<String> warnings) {
        if (session == null) {
            return MissingNode.getInstance();
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode fulltypeCodes = body.putArray("fulltypeCodes");
        fulltypeCodes.add(productFulltype);
        body.put("skuType", "zsku");
        body.put("attributesLevel", "parent");
        body.put("catalogType", "merchant");
        body.put("namespace", "noon");
        ObjectNode selectFields = body.putObject("selectFields");
        selectFields.putArray("attributeClass");
        selectFields.put("attributeGroup", true);
        selectFields.putArray("attributeProperties");
        selectFields.putArray("attributeSpecs");

        try {
            return postJson(session, FULLTYPE_ATTRS_URL, body);
        } catch (IllegalStateException exception) {
            if (warnings != null) {
                warnings.add("读取 fulltype 模板失败：" + noonFailureMessage(exception));
            }
            return MissingNode.getInstance();
        }
    }

    @Scheduled(
            initialDelayString = "${nuono.product-management.attribute-template.scheduler.initial-delay-ms:900000}",
            fixedDelayString = "${nuono.product-management.attribute-template.scheduler.fixed-delay-ms:86400000}"
    )
    public void refreshStaleTemplates() {
        if (!schedulerEnabled) {
            return;
        }
        int staleDays = Math.max(1, schedulerStaleDays);
        int limit = Math.max(1, schedulerMaxItemsPerTick);
        List<ProductAttributeTemplateRefreshCandidate> candidates;
        try {
            candidates = templateMapper.selectRefreshCandidates(staleDays, limit);
        } catch (RuntimeException exception) {
            log.debug("product attribute template scheduler skipped: {}", shrink(exception.getMessage()));
            return;
        }
        for (ProductAttributeTemplateRefreshCandidate candidate : candidates) {
            refreshTemplateCandidate(candidate);
        }
    }

    private void upsertTemplate(
            String projectCode,
            String storeCode,
            String productFulltype,
            JsonNode template,
            Long operatorUserId,
            List<String> warnings
    ) {
        try {
            String rawJson = objectMapper.writeValueAsString(template);
            List<Map<String, Object>> normalizedFields = normalizeDictionaryFields(template);
            String normalizedJson = objectMapper.writeValueAsString(normalizedFields);
            templateMapper.upsert(
                    productManagementMapper.nextProductManagementId("noon_attribute_template", 58000L),
                    projectCode,
                    storeCode,
                    productFulltype,
                    "noon",
                    "ready",
                    sha256(rawJson),
                    rawJson,
                    normalizedJson,
                    LocalDateTime.now(),
                    null,
                    operatorUserId
            );
            upsertDictionary(projectCode, storeCode, productFulltype, normalizedFields, operatorUserId);
        } catch (JsonProcessingException exception) {
            if (warnings != null) {
                warnings.add("保存 fulltype 模板缓存失败：" + shrink(exception.getMessage()));
            }
        }
    }

    private void refreshTemplateCandidate(ProductAttributeTemplateRefreshCandidate candidate) {
        if (candidate == null || candidate.getOwnerUserId() == null) {
            return;
        }
        LocalDateTime startedAt = LocalDateTime.now();
        String errorMessage = null;
        try {
            StoreSyncStoreRecord store = storeSyncMapper.selectOwnerStore(candidate.getOwnerUserId(), candidate.getStoreCode());
            StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(candidate.getOwnerUserId());
            String noonUser = firstNonBlank(
                    store != null ? store.getNoonPartnerUser() : null,
                    store != null ? store.getNoonPartnerProjectUser() : null,
                    owner != null ? owner.getNoonPartnerUser() : null,
                    owner != null ? owner.getNoonPartnerProjectUser() : null
            );
            String noonEmailAuthCode = firstNonBlank(
                    store != null ? store.getNoonPartnerMailAuthCode() : null,
                    owner != null ? owner.getNoonPartnerMailAuthCode() : null
            );
            String noonPassword = firstNonBlank(
                    store != null ? store.getNoonPartnerPwd() : null,
                    owner != null ? owner.getNoonPartnerPwd() : null
            );
            String cookie = firstNonBlank(
                    store != null ? store.getNoonPartnerCookie() : null,
                    owner != null ? owner.getNoonPartnerCookie() : null
            );
            NoonSession session = StringUtils.hasText(noonEmailAuthCode)
                    ? productNoonAdapter.loginWithEmailAuthCode(
                    candidate.getOwnerUserId(),
                    noonUser,
                    noonEmailAuthCode,
                    cookie,
                    candidate.getProjectCode(),
                    candidate.getStoreCode()
            )
                    : productNoonAdapter.hasConfiguredMerchantEmailLogin()
                    ? productNoonAdapter.loginWithConfiguredEmailAuthCode(
                    candidate.getOwnerUserId(),
                    cookie,
                    candidate.getProjectCode(),
                    candidate.getStoreCode()
            )
                    : productNoonAdapter.login(
                    candidate.getOwnerUserId(),
                    noonUser,
                    noonPassword,
                    cookie,
                    candidate.getProjectCode(),
                    candidate.getStoreCode()
            );
            List<String> warnings = new ArrayList<>();
            JsonNode template = fetchTemplate(session, candidate.getProductFulltype(), warnings);
            if (!usable(template)) {
                throw new IllegalStateException(warnings.isEmpty() ? "Noon 未返回可用模板。" : String.join("；", warnings));
            }
            upsertTemplate(
                    candidate.getProjectCode(),
                    candidate.getStoreCode(),
                    candidate.getProductFulltype(),
                    template,
                    candidate.getOwnerUserId(),
                    warnings
            );
            memoryCache.remove(cacheKey(candidate.getProjectCode(), candidate.getStoreCode(), candidate.getProductFulltype()));
            insertSyncLog(candidate, "scheduled", "success", null, startedAt, LocalDateTime.now());
        } catch (RuntimeException exception) {
            errorMessage = shrink(exception.getMessage());
            insertSyncLog(candidate, "scheduled", "failed", errorMessage, startedAt, LocalDateTime.now());
            log.warn(
                    "product attribute template refresh failed project={} store={} fulltype={} error={}",
                    candidate.getProjectCode(),
                    candidate.getStoreCode(),
                    candidate.getProductFulltype(),
                    errorMessage
            );
        }
    }

    private JsonNode postJson(NoonSession session, String url, JsonNode body) {
        if (productNoonAdapter == null) {
            return session.postJson(url, body, true);
        }
        return productNoonAdapter.postJson(session, url, body, true);
    }

    private String noonFailureMessage(RuntimeException exception) {
        if (productNoonAdapter == null) {
            return shrink(exception.getMessage());
        }
        return shrink(productNoonAdapter.userMessage(exception));
    }

    private void insertSyncLog(
            ProductAttributeTemplateRefreshCandidate candidate,
            String syncType,
            String status,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        try {
            templateMapper.insertSyncLog(
                    productManagementMapper.nextProductManagementId("noon_attribute_template_sync_log", 61000L),
                    candidate.getProjectCode(),
                    candidate.getStoreCode(),
                    candidate.getProductFulltype(),
                    syncType,
                    status,
                    errorMessage,
                    startedAt,
                    finishedAt,
                    candidate.getOwnerUserId()
            );
        } catch (RuntimeException exception) {
            log.debug("failed to write attribute template sync log: {}", shrink(exception.getMessage()));
        }
    }

    private void upsertDictionary(
            String projectCode,
            String storeCode,
            String productFulltype,
            List<Map<String, Object>> normalizedFields,
            Long operatorUserId
    ) {
        if (normalizedFields == null || normalizedFields.isEmpty()) {
            return;
        }
        LocalDateTime fetchedAt = LocalDateTime.now();
        templateMapper.markOfficialFieldsDeleted(projectCode, storeCode, productFulltype);
        int sortOrder = 0;
        for (Map<String, Object> field : normalizedFields) {
            String code = text(field.get("code"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            templateMapper.upsertDictionaryField(
                    productManagementMapper.nextProductManagementId("noon_attribute_field", 59000L),
                    projectCode,
                    storeCode,
                    productFulltype,
                    code,
                    text(field.get("labelEn")),
                    text(field.get("labelAr")),
                    text(field.get("groupName")),
                    firstNonBlank(text(field.get("kind")), "text"),
                    booleanValue(field.get("required")),
                    booleanValue(field.get("grouping")),
                    booleanValue(field.get("visibleSeller")),
                    "official-template",
                    sortOrder++,
                    fetchedAt,
                    operatorUserId
            );
            ProductAttributeDictionaryFieldRecord storedField = templateMapper.selectDictionaryFieldByScopeCode(
                    projectCode,
                    storeCode,
                    productFulltype,
                    code
            );
            if (storedField == null || storedField.getId() == null) {
                continue;
            }
            templateMapper.markOptionsDeleted(storedField.getId());
            templateMapper.markUnitOptionsDeleted(storedField.getId());
            upsertDictionaryOptions(storedField.getId(), listOfMaps(field.get("options")), false, operatorUserId);
            upsertDictionaryOptions(storedField.getId(), listOfMaps(field.get("unitOptions")), true, operatorUserId);
        }
    }

    private void upsertDictionaryOptions(
            Long fieldId,
            List<Map<String, Object>> options,
            boolean unit,
            Long operatorUserId
    ) {
        if (fieldId == null || options == null || options.isEmpty()) {
            return;
        }
        int sortOrder = 0;
        for (Map<String, Object> option : options) {
            String value = text(option.get("value"));
            String labelEn = firstNonBlank(text(option.get("en")), value);
            if (!StringUtils.hasText(value) || !StringUtils.hasText(labelEn)) {
                continue;
            }
            if (unit) {
                templateMapper.upsertDictionaryUnitOption(
                        productManagementMapper.nextProductManagementId("noon_attribute_unit_option", 60000L),
                        fieldId,
                        value,
                        labelEn,
                        text(option.get("ar")),
                        sortOrder++,
                        operatorUserId
                );
            } else {
                templateMapper.upsertDictionaryOption(
                        productManagementMapper.nextProductManagementId("noon_attribute_option", 59500L),
                        fieldId,
                        value,
                        labelEn,
                        text(option.get("ar")),
                        sortOrder++,
                        operatorUserId
                );
            }
        }
    }

    private JsonNode attachDictionary(JsonNode template, String projectCode, String storeCode, String productFulltype) {
        List<ProductAttributeDictionaryFieldRecord> fields;
        try {
            fields = templateMapper.selectDictionaryFields(projectCode, storeCode, productFulltype);
        } catch (RuntimeException exception) {
            log.debug("attribute dictionary lookup skipped: {}", shrink(exception.getMessage()));
            return template == null ? MissingNode.getInstance() : template;
        }
        if (fields == null || fields.isEmpty()) {
            return template == null ? MissingNode.getInstance() : template;
        }
        List<Long> fieldIds = fields.stream()
                .map(ProductAttributeDictionaryFieldRecord::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        Map<Long, List<ProductAttributeDictionaryOptionRecord>> optionsByFieldId =
                optionsByFieldId(fieldIds, false);
        Map<Long, List<ProductAttributeDictionaryOptionRecord>> unitOptionsByFieldId =
                optionsByFieldId(fieldIds, true);

        Map<String, ObjectNode> byCode = new LinkedHashMap<>();
        for (ProductAttributeDictionaryFieldRecord field : fields) {
            String code = normalizeKey(field.getAttributeCode());
            if (!StringUtils.hasText(code)) {
                continue;
            }
            ObjectNode node = byCode.computeIfAbsent(code, ignored -> fieldNode(field));
            mergeArrayIfEmpty(node, "options", optionArray(optionsByFieldId.get(field.getId())));
            mergeArrayIfEmpty(node, "unitOptions", optionArray(unitOptionsByFieldId.get(field.getId())));
            putIfMissing(node, "labelEn", field.getLabelEn());
            putIfMissing(node, "labelAr", field.getLabelAr());
            putIfMissing(node, "groupName", field.getGroupName());
            putIfMissing(node, "kind", field.getInputKind());
        }

        ObjectNode target = template != null && template.isObject()
                ? ((ObjectNode) template.deepCopy())
                : objectMapper.createObjectNode();
        ArrayNode dictionary = objectMapper.createArrayNode();
        byCode.values().forEach(dictionary::add);
        target.set("_nuonoAttributeDictionary", dictionary);
        return target;
    }

    private Map<Long, List<ProductAttributeDictionaryOptionRecord>> optionsByFieldId(List<Long> fieldIds, boolean unit) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Map.of();
        }
        List<ProductAttributeDictionaryOptionRecord> rows = unit
                ? templateMapper.selectDictionaryUnitOptions(fieldIds)
                : templateMapper.selectDictionaryOptions(fieldIds);
        return rows.stream().collect(Collectors.groupingBy(ProductAttributeDictionaryOptionRecord::getFieldId));
    }

    private ObjectNode fieldNode(ProductAttributeDictionaryFieldRecord field) {
        ObjectNode node = objectMapper.createObjectNode();
        putIfNotBlank(node, "code", field.getAttributeCode());
        putIfNotBlank(node, "labelEn", field.getLabelEn());
        putIfNotBlank(node, "labelAr", field.getLabelAr());
        putIfNotBlank(node, "groupName", field.getGroupName());
        putIfNotBlank(node, "kind", field.getInputKind());
        putIfNotBlank(node, "dictionarySource", field.getDictionarySource());
        if (field.getRequired() != null) {
            node.put("required", field.getRequired());
        }
        if (field.getGrouping() != null) {
            node.put("grouping", field.getGrouping());
        }
        if (field.getVisibleSeller() != null) {
            node.put("visibleSeller", field.getVisibleSeller());
        }
        return node;
    }

    private ArrayNode optionArray(List<ProductAttributeDictionaryOptionRecord> rows) {
        ArrayNode array = objectMapper.createArrayNode();
        if (rows == null) {
            return array;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ProductAttributeDictionaryOptionRecord row : rows) {
            String value = text(row.getOptionValue());
            String key = normalizeKey(value);
            if (!StringUtils.hasText(key) || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            ObjectNode node = objectMapper.createObjectNode();
            putIfNotBlank(node, "value", value);
            putIfNotBlank(node, "en", firstNonBlank(row.getLabelEn(), value));
            putIfNotBlank(node, "ar", row.getLabelAr());
            array.add(node);
        }
        return array;
    }

    private List<Map<String, Object>> normalizeDictionaryFields(JsonNode template) {
        JsonNode templateRoot = fulltypeTemplateDataNode(template);
        JsonNode attributePropertiesNode = templateRoot.path("fundamental").path("attribute_properties");
        if (!attributePropertiesNode.isObject()) {
            return List.of();
        }

        Set<String> mandatoryCodes = new LinkedHashSet<>();
        JsonNode mandatoryNode = templateRoot.path("fundamental").path("attribute_class").path("mandatory");
        if (mandatoryNode.isArray()) {
            for (JsonNode node : mandatoryNode) {
                if (node.isTextual()) {
                    mandatoryCodes.add(node.asText());
                }
            }
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = attributePropertiesNode.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String code = entry.getKey();
            JsonNode propertyNode = entry.getValue();
            List<Map<String, Object>> options = extractAttributeOptions(templateRoot, propertyNode, code);
            List<Map<String, Object>> unitOptions = extractAttributeUnitOptions(templateRoot, propertyNode, code);
            boolean grouping = propertyNode.path("is_grouping").asInt(0) == 1;
            boolean visibleSeller = propertyNode.path("is_visible_seller").asInt(0) == 1;

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("code", code);
            field.put("required", mandatoryCodes.contains(code));
            field.put("grouping", grouping);
            field.put("visibleSeller", visibleSeller);
            field.put("kind", resolveAttributeInputKind(code, propertyNode, options, unitOptions));
            putIfNotBlank(field, "labelEn", resolveAttributeLabel(propertyNode, code, "en"));
            putIfNotBlank(field, "labelAr", resolveAttributeLabel(propertyNode, code, "ar"));
            putIfNotBlank(field, "groupName", text(propertyNode.path("attribute_group_name"), "en"));
            putIfNotEmpty(field, "options", options);
            putIfNotEmpty(field, "unitOptions", unitOptions);
            fields.add(field);
        }
        return fields;
    }

    private JsonNode fulltypeTemplateDataNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return MissingNode.getInstance();
        }
        if (root.path("fundamental").isObject()) {
            return root;
        }
        JsonNode dataCandidate = fulltypeTemplateDataNodeFromContainer(root.path("data"));
        if (!dataCandidate.isMissingNode()) {
            return dataCandidate;
        }
        JsonNode rootCandidate = fulltypeTemplateDataNodeFromContainer(root);
        return rootCandidate.isMissingNode() ? root : rootCandidate;
    }

    private JsonNode fulltypeTemplateDataNodeFromContainer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        if (node.path("fundamental").isObject()) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(item);
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
            return MissingNode.getInstance();
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(iterator.next().getValue());
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private String resolveAttributeInputKind(
            String code,
            JsonNode propertyNode,
            List<Map<String, Object>> options,
            List<Map<String, Object>> unitOptions
    ) {
        if (!unitOptions.isEmpty() || isDimensionAttribute(code)) {
            return "dimension";
        }
        if (!options.isEmpty()) {
            return "select";
        }
        String valueType = firstNonBlank(
                text(propertyNode, "value_type"),
                text(propertyNode, "data_type"),
                text(propertyNode, "input_type"),
                text(propertyNode, "type")
        );
        if (!StringUtils.hasText(valueType)) {
            return "text";
        }
        String normalizedType = valueType.toLowerCase();
        if (normalizedType.contains("select") || normalizedType.contains("enum") || normalizedType.contains("option")) {
            return "select";
        }
        if (normalizedType.contains("textarea") || normalizedType.contains("rich") || normalizedType.contains("long")) {
            return "textarea";
        }
        return "text";
    }

    private List<Map<String, Object>> extractAttributeOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, firstExisting(
                propertyNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode specsNode = firstExisting(propertyNode, "specs", "attribute_specs", "attributeSpecs");
        collectOptionsFromNode(options, seen, firstExisting(
                specsNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode templateSpecs = firstExisting(
                templateRoot.path("fundamental").path("attribute_specs").path(code),
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        );
        collectOptionsFromNode(options, seen, templateSpecs);
        return options;
    }

    private List<Map<String, Object>> extractAttributeUnitOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> unitOptions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                propertyNode,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        JsonNode templateSpecs = templateRoot.path("fundamental").path("attribute_specs").path(code);
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                templateSpecs,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        return unitOptions;
    }

    private void collectOptionsFromNode(List<Map<String, Object>> options, Set<String> seen, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectSingleOption(options, seen, item);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode item = entry.getValue();
                if (item.isValueNode()) {
                    addOption(options, seen, entry.getKey(), item.asText(), null);
                } else {
                    collectSingleOption(options, seen, item);
                }
            }
            return;
        }
        collectSingleOption(options, seen, node);
    }

    private void collectSingleOption(List<Map<String, Object>> options, Set<String> seen, JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return;
        }
        if (item.isValueNode()) {
            String value = item.asText();
            addOption(options, seen, value, value, null);
            return;
        }
        String en = firstNonBlank(
                localizedText(item, "option_name", "en"),
                localizedText(item, "name", "en"),
                localizedText(item, "label", "en"),
                text(item, "option_name_en"),
                text(item, "name_en"),
                text(item, "label_en"),
                text(item, "en")
        );
        String ar = firstNonBlank(
                localizedText(item, "option_name", "ar"),
                localizedText(item, "name", "ar"),
                localizedText(item, "label", "ar"),
                text(item, "option_name_ar"),
                text(item, "name_ar"),
                text(item, "label_ar"),
                text(item, "ar")
        );
        String value = firstNonBlank(
                text(item, "value"),
                text(item, "option_value"),
                text(item, "code"),
                text(item, "option_code"),
                en
        );
        addOption(options, seen, value, firstNonBlank(en, value), ar);
    }

    private void addOption(List<Map<String, Object>> options, Set<String> seen, String value, String en, String ar) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(en)) {
            return;
        }
        String key = normalizeKey(value);
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value.trim());
        option.put("en", en.trim());
        putIfNotBlank(option, "ar", ar);
        options.add(option);
    }

    private JsonNode firstExisting(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return MissingNode.getInstance();
    }

    private String resolveAttributeLabel(JsonNode propertyNode, String code, String lang) {
        return firstNonBlank(
                localizedText(propertyNode, "attribute_name", lang),
                localizedText(propertyNode, "display_name", lang),
                localizedText(propertyNode, "name", lang),
                localizedText(propertyNode, "label", lang),
                text(propertyNode, "attribute_name_" + lang),
                text(propertyNode, "display_name_" + lang),
                text(propertyNode, "name_" + lang),
                text(propertyNode, "label_" + lang),
                "en".equals(lang) ? humanizeAttributeCode(code) : null
        );
    }

    private String localizedText(JsonNode node, String field, String lang) {
        JsonNode valueNode = node != null ? node.path(field) : MissingNode.getInstance();
        if (valueNode.isObject()) {
            return text(valueNode, lang);
        }
        if ("en".equals(lang) && valueNode.isValueNode()) {
            return valueNode.asText();
        }
        return null;
    }

    private boolean isDimensionAttribute(String code) {
        String normalizedCode = normalizeKey(code);
        return StringUtils.hasText(normalizedCode)
                && (normalizedCode.contains("height")
                || normalizedCode.contains("length")
                || normalizedCode.contains("weight")
                || normalizedCode.contains("width")
                || normalizedCode.contains("depth"));
    }

    private String humanizeAttributeCode(String code) {
        String normalizedCode = normalize(code);
        if (!StringUtils.hasText(normalizedCode)) {
            return null;
        }
        String[] parts = normalizedCode.replace('-', '_').split("_+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return label.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    private void mergeArrayIfEmpty(ObjectNode node, String field, ArrayNode value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        JsonNode existing = node.path(field);
        if (!existing.isArray() || existing.isEmpty()) {
            node.set(field, value);
        }
    }

    private void putIfMissing(ObjectNode node, String field, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        JsonNode existing = node.path(field);
        if (existing.isMissingNode() || existing.isNull() || !StringUtils.hasText(existing.asText())) {
            node.put(field, value.trim());
        }
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value.trim());
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private void putIfNotEmpty(Map<String, Object> target, String key, List<Map<String, Object>> value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : text(value);
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return StringUtils.hasText(result) ? result : null;
    }

    private JsonNode parseStoredTemplate(ProductAttributeTemplateRecord record, List<String> warnings) {
        if (record == null || !StringUtils.hasText(record.getRawJson())) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(record.getRawJson());
        } catch (JsonProcessingException exception) {
            if (warnings != null) {
                warnings.add("读取 fulltype 模板缓存失败：" + shrink(exception.getMessage()));
            }
            return MissingNode.getInstance();
        }
    }

    private boolean fresh(ProductAttributeTemplateRecord record) {
        return record.getFetchedAt() != null
                && record.getFetchedAt().isAfter(LocalDateTime.now().minus(CACHE_TTL));
    }

    private boolean usable(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private String cacheKey(String projectCode, String storeCode, String productFulltype) {
        return projectCode.toLowerCase() + "::" + storeCode.toLowerCase() + "::" + productFulltype.toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256。", exception);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[\\s-]+", "_");
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "未返回更多错误信息";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }
}
