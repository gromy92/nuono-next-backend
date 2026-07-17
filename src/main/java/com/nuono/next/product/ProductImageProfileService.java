package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.operationsskin.OperationsSkinComponentRecord;
import com.nuono.next.operationsskin.OperationsSkinRecord;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductImageProfileService {
    private static final DateTimeFormatter API_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TASK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HERO_MAIN_TEMPLATE_ROLE = "HERO_MAIN";
    private static final List<String> REQUIRED_HERO_COMPONENT_KEYS = List.of(
            "FRAME",
            "BRAND_LOCKUP",
            "SPEC_BG",
            "MAIN_TITLE_BG"
    );
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ProductImageProfileMapper mapper;
    private final OperationsSkinMapper operationsSkinMapper;
    private final ProductPublicDetailMapper productPublicDetailMapper;
    private final AiCapabilityService aiCapabilityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoonImageTechnicalComplianceEvaluator noonComplianceEvaluator = new NoonImageTechnicalComplianceEvaluator();

    @Autowired
    public ProductImageProfileService(
            ProductImageProfileMapper mapper,
            OperationsSkinMapper operationsSkinMapper,
            ProductPublicDetailMapper productPublicDetailMapper,
            AiCapabilityService aiCapabilityService
    ) {
        this.mapper = mapper;
        this.operationsSkinMapper = operationsSkinMapper;
        this.productPublicDetailMapper = productPublicDetailMapper;
        this.aiCapabilityService = aiCapabilityService;
    }

    ProductImageProfileService(ProductImageProfileMapper mapper, OperationsSkinMapper operationsSkinMapper) {
        this(mapper, operationsSkinMapper, null, null);
    }

    @Transactional
    public ProductImageProfileListView list(ProductImageProfileListCommand command) {
        Long ownerUserId = requireOwnerUserId(command == null ? null : command.getOwnerUserId());
        String storeCode = requireStoreCode(command == null ? null : command.getStoreCode());
        String keyword = trimToNull(command == null ? null : command.getKeyword());
        Long operatorUserId = command == null ? null : command.getOperatorUserId();

        ensureStoreProfiles(ownerUserId, storeCode, operatorUserId);

        ProductImageProfileListView view = new ProductImageProfileListView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);

        List<ProductImageProfileRecord> profiles = safeList(mapper.selectProfilesForStore(ownerUserId, storeCode, keyword));
        Map<String, ProductImageProfileDetailView> byIdentity = new LinkedHashMap<>();
        for (ProductImageProfileRecord profile : profiles) {
            ProductImageProfileDetailView item = toDetailView(profile);
            byIdentity.put(identityKey(profile.getPskuCode(), profile.getProductIdentityKey()), item);
        }

        List<ProductImageProductCandidateRecord> candidates = safeList(mapper.selectProductCandidates(ownerUserId, storeCode, keyword));
        for (ProductImageProductCandidateRecord candidate : candidates) {
            String candidateKey = identityKey(candidate.getPskuCode(), candidate.getProductIdentityKey());
            byIdentity.putIfAbsent(candidateKey, toTransientDetailView(ownerUserId, storeCode, candidate));
        }

        view.setItems(new ArrayList<>(byIdentity.values()));
        return view;
    }

    @Transactional
    public ProductImageProfileSummaryListView listSummaries(ProductImageProfileListCommand command) {
        Long ownerUserId = requireOwnerUserId(command == null ? null : command.getOwnerUserId());
        String storeCode = requireStoreCode(command == null ? null : command.getStoreCode());
        String keyword = trimToNull(command == null ? null : command.getKeyword());
        Long operatorUserId = command == null ? null : command.getOperatorUserId();

        ensureStoreProfiles(ownerUserId, storeCode, operatorUserId);

        ProductImageProfileSummaryListView view = new ProductImageProfileSummaryListView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);

        Map<String, ProductImageProfileSummaryView> byIdentity = new LinkedHashMap<>();
        for (ProductImageProfileSummaryRecord summary : safeList(mapper.selectProfileSummariesForStore(ownerUserId, storeCode, keyword))) {
            ProductImageProfileSummaryView item = toSummaryView(summary);
            byIdentity.put(identityKey(summary.getPskuCode(), summary.getProductIdentityKey()), item);
        }

        for (ProductImageProductCandidateRecord candidate : safeList(mapper.selectProductCandidates(ownerUserId, storeCode, keyword))) {
            String candidateKey = identityKey(candidate.getPskuCode(), candidate.getProductIdentityKey());
            byIdentity.putIfAbsent(candidateKey, toTransientSummaryView(ownerUserId, storeCode, candidate));
        }

        view.setItems(new ArrayList<>(byIdentity.values()));
        return view;
    }

    private void ensureStoreProfiles(Long ownerUserId, String storeCode, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        for (ProductImageProductCandidateRecord candidate : safeList(mapper.selectAllProductCandidatesForStore(ownerUserId, storeCode))) {
            String pskuCode = trimToNull(candidate.getPskuCode());
            String productIdentityKey = trimToNull(candidate.getProductIdentityKey());
            if (pskuCode == null || productIdentityKey == null) {
                continue;
            }
            ProductImageProfileRecord existing = mapper.selectProfileByIdentity(ownerUserId, storeCode, pskuCode, productIdentityKey);
            if (existing == null) {
                mapper.insertProfile(newInitialProfile(ownerUserId, storeCode, candidate, operatorUserId, now));
                continue;
            }
            ProductImageProfileRecord refreshed = refreshedProfile(existing, candidate, operatorUserId, now);
            if (shouldRefreshProfile(existing, refreshed)) {
                mapper.updateProfile(refreshed);
            }
        }
    }

    private ProductImageProfileRecord newInitialProfile(
            Long ownerUserId,
            String storeCode,
            ProductImageProductCandidateRecord candidate,
            Long operatorUserId,
            LocalDateTime now
    ) {
        ProductImageProfileRecord record = new ProductImageProfileRecord();
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setPskuCode(trimToNull(candidate.getPskuCode()));
        record.setProductIdentityKey(trimToNull(candidate.getProductIdentityKey()));
        record.setProductMasterId(candidate.getProductMasterId());
        record.setProductVariantId(candidate.getProductVariantId());
        record.setProductTitle(trimToNull(candidate.getProductTitle()));
        record.setBrand(trimToNull(candidate.getBrand()));
        record.setTitleEn(trimToNull(candidate.getProductTitle()));
        record.setTitleAr(null);
        record.setSpecSummary(null);
        record.setProductFactText(buildInitialProductFactText(candidate));
        record.setHeroSellingPointsJson("[]");
        record.setProfileStatus("ACTIVE");
        record.setCreatedBy(operatorUserId);
        record.setUpdatedBy(operatorUserId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(false);
        return record;
    }

    private ProductImageProfileRecord refreshedProfile(
            ProductImageProfileRecord existing,
            ProductImageProductCandidateRecord candidate,
            Long operatorUserId,
            LocalDateTime now
    ) {
        String candidateTitle = trimToNull(candidate.getProductTitle());
        String candidateBrand = trimToNull(candidate.getBrand());
        ProductImageProfileRecord record = new ProductImageProfileRecord();
        record.setId(existing.getId());
        record.setOwnerUserId(existing.getOwnerUserId());
        record.setStoreCode(existing.getStoreCode());
        record.setLogicalStoreId(existing.getLogicalStoreId());
        record.setPskuCode(existing.getPskuCode());
        record.setProductIdentityKey(existing.getProductIdentityKey());
        record.setProductMasterId(candidate.getProductMasterId());
        record.setProductVariantId(candidate.getProductVariantId());
        record.setProductTitle(candidateTitle == null ? trimToNull(existing.getProductTitle()) : candidateTitle);
        record.setBrand(candidateBrand == null ? trimToNull(existing.getBrand()) : candidateBrand);
        record.setTitleAr(trimToNull(existing.getTitleAr()));
        record.setTitleEn(candidateTitle == null ? trimToNull(existing.getTitleEn()) : candidateTitle);
        record.setSpecSummary(trimToNull(existing.getSpecSummary()));
        record.setProductFactText(trimToNull(existing.getProductFactText()) == null
                ? buildInitialProductFactText(candidate)
                : existing.getProductFactText());
        record.setHeroSellingPointsJson(trimToNull(existing.getHeroSellingPointsJson()) == null
                ? "[]"
                : existing.getHeroSellingPointsJson());
        record.setProfileStatus(trimToNull(existing.getProfileStatus()) == null ? "ACTIVE" : existing.getProfileStatus());
        record.setCreatedBy(existing.getCreatedBy());
        record.setUpdatedBy(operatorUserId == null ? existing.getUpdatedBy() : operatorUserId);
        record.setCreatedAt(existing.getCreatedAt());
        record.setUpdatedAt(now);
        record.setDeleted(false);
        return record;
    }

    private boolean shouldRefreshProfile(ProductImageProfileRecord existing, ProductImageProfileRecord refreshed) {
        return !Objects.equals(existing.getProductMasterId(), refreshed.getProductMasterId())
                || !Objects.equals(existing.getProductVariantId(), refreshed.getProductVariantId())
                || !Objects.equals(trimToNull(existing.getProductTitle()), refreshed.getProductTitle())
                || !Objects.equals(trimToNull(existing.getBrand()), refreshed.getBrand())
                || !Objects.equals(trimToNull(existing.getTitleEn()), refreshed.getTitleEn())
                || trimToNull(existing.getProductFactText()) == null
                || trimToNull(existing.getHeroSellingPointsJson()) == null
                || trimToNull(existing.getProfileStatus()) == null;
    }

    private String buildInitialProductFactText(ProductImageProductCandidateRecord candidate) {
        List<String> lines = new ArrayList<>();
        appendFactLine(lines, "商品", candidate.getProductTitle());
        appendFactLine(lines, "PSKU", candidate.getPskuCode());
        appendFactLine(lines, "品牌", candidate.getBrand());
        appendFactLine(lines, "英文完整标题", candidate.getProductTitle());
        return String.join("\n", lines);
    }

    private void appendFactLine(List<String> lines, String label, String value) {
        String text = trimToNull(value);
        if (text != null) {
            lines.add(label + "：" + text);
        }
    }

    public ProductImageProfileDetailView detail(Long ownerUserId, String storeCode, Long profileId) {
        ProductImageProfileRecord record = requireAccessibleProfile(profileId, ownerUserId, storeCode);
        return toDetailView(record);
    }

    public ProductImageAiExtractionSuggestionView extractImageFacts(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        if (aiCapabilityService == null) {
            throw new IllegalStateException("AI 商品资料提取服务暂时不可用。");
        }
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode("PRODUCT_IMAGE_PROFILE");
        command.setOperationCode("EXTRACT_IMAGE_FACTS");
        command.setOperatorUserId(operatorUserId);
        command.setReasoningEffort("low");
        command.setMaxOutputTokens(1400);
        command.setTimeoutSeconds(45);
        command.setSchemaName("product_image_fact_extraction_v1");
        command.setSchema(productImageFactExtractionSchema());
        command.setInstructions(productImageFactExtractionInstructions());
        command.setPrompt(buildProductImageFactExtractionPrompt(profile, latestPublicDetail(profile, resolvedOwnerUserId, normalizedStoreCode)));
        AiStructuredTextResult result = aiCapabilityService.createStructuredText(command);
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("AI 商品资料提取失败：" + safeText(result.getErrorMessage()));
        }
        return toExtractionSuggestion(result.getParsedJson());
    }

    private ProductPublicDetailSnapshot latestPublicDetail(
            ProductImageProfileRecord profile,
            Long ownerUserId,
            String storeCode
    ) {
        if (productPublicDetailMapper == null) {
            return null;
        }
        String pskuCode = trimToNull(profile.getPskuCode());
        if (pskuCode != null) {
            ProductPublicDetailSnapshot snapshot =
                    productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(ownerUserId, storeCode, pskuCode);
            if (snapshot != null) {
                return snapshot;
            }
        }
        String siteCode = siteCodeFromStoreCode(storeCode);
        if (profile.getProductMasterId() != null && profile.getProductVariantId() != null && siteCode != null) {
            ProductPublicDetailSnapshot snapshot = productPublicDetailMapper.selectLatestSnapshot(
                    ownerUserId,
                    storeCode,
                    siteCode,
                    profile.getProductMasterId(),
                    profile.getProductVariantId()
            );
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    private String siteCodeFromStoreCode(String storeCode) {
        String value = trimToNull(storeCode);
        if (value == null) {
            return null;
        }
        int index = value.lastIndexOf('-');
        if (index < 0 || index == value.length() - 1) {
            return null;
        }
        String suffix = value.substring(index + 1).trim().toUpperCase(Locale.ROOT);
        if ("NAE".equals(suffix)) {
            return "AE";
        }
        if ("NSA".equals(suffix)) {
            return "SA";
        }
        return suffix;
    }

    private String productImageFactExtractionInstructions() {
        return String.join("\n",
                "你是电商商品图资料提取助手，只从输入的当前商品详情中提取确认事实。",
                "输出必须是 JSON，字段固定为 specSummary、titleEn、titleAr、sizeText、heroSellingPoints、packageText。",
                "specSummary 是主图规格短句，优先放数量、颜色、尺寸、型号、pack count 等需要单独展示的规格。",
                "titleEn 和 titleAr 是图片标题，不是平台完整标题；必须去掉已经写入 specSummary 的规格、数量、尺寸、颜色、pack count，避免图片里重复展示。",
                "sizeText 是一段尺寸文案，不拆字段。",
                "heroSellingPoints 只输出英文短句，每条只讲一个卖点，最多 5 条。",
                "packageText 只写确认的套装数量、配件、颜色组合或包装内容。",
                "没有可信来源的尺寸、包装、数量、材质、功能、认证或使用效果必须输出空字符串或空数组，不要猜。"
        );
    }

    private String buildProductImageFactExtractionPrompt(
            ProductImageProfileRecord profile,
            ProductPublicDetailSnapshot snapshot
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("请从以下当前商品详情中提取商品图左侧商品资料。");
        lines.add("");
        lines.add("当前资料库字段：");
        appendPromptLine(lines, "PSKU", profile.getPskuCode());
        appendPromptLine(lines, "商品标题", profile.getProductTitle());
        appendPromptLine(lines, "品牌", profile.getBrand());
        appendPromptLine(lines, "已有英文标题", profile.getTitleEn());
        appendPromptLine(lines, "已有阿语标题", profile.getTitleAr());
        appendPromptLine(lines, "已有规格", profile.getSpecSummary());
        appendPromptLine(lines, "已有商品资料", profile.getProductFactText());
        lines.add("");
        lines.add("当前商品详情快照：");
        if (snapshot == null) {
            lines.add("无可用当前商品详情快照；只能基于当前资料库字段提取。");
        } else {
            appendPromptLine(lines, "详情英文标题", snapshot.getTitleEn());
            appendPromptLine(lines, "详情阿语标题", snapshot.getTitleAr());
            appendPromptLine(lines, "详情品牌", snapshot.getBrand());
            appendPromptLine(lines, "详情分类", snapshot.getCategoryPath());
            if (sameSite(profile, snapshot)) {
                appendPromptLine(lines, "详情原始 JSON", truncate(snapshot.getRawPayloadJson(), 9000));
            } else {
                lines.add("详情原始 JSON：已省略；该快照来自同店铺的兄弟站点，只用于共享商品标题、品牌和分类。");
            }
        }
        lines.add("");
        lines.add("输出 JSON 示例：");
        lines.add("{\"specSummary\":\"Black · 0.5mm · 12 Pieces\",\"titleEn\":\"Retractable Gel Ink Pens\",\"titleAr\":\"أقلام حبر جل قابلة للسحب\",\"sizeText\":\"\",\"heroSellingPoints\":[\"Smooth 0.5mm writing\"],\"packageText\":\"12 pens per pack\"}");
        return String.join("\n", lines);
    }

    private boolean sameSite(ProductImageProfileRecord profile, ProductPublicDetailSnapshot snapshot) {
        if (profile == null || snapshot == null) {
            return false;
        }
        String profileStore = trimToNull(profile.getStoreCode());
        String snapshotStore = trimToNull(snapshot.getStoreCode());
        String profileSite = siteCodeFromStoreCode(profileStore);
        String snapshotSite = trimToNull(snapshot.getSiteCode());
        return profileStore != null
                && snapshotStore != null
                && profileStore.equalsIgnoreCase(snapshotStore)
                && profileSite != null
                && snapshotSite != null
                && profileSite.equalsIgnoreCase(snapshotSite);
    }

    private Map<String, Object> productImageFactExtractionSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("specSummary", stringSchema());
        properties.put("titleEn", stringSchema());
        properties.put("titleAr", stringSchema());
        properties.put("sizeText", stringSchema());
        Map<String, Object> sellingPoints = new LinkedHashMap<>();
        sellingPoints.put("type", "array");
        sellingPoints.put("items", stringSchema());
        properties.put("heroSellingPoints", sellingPoints);
        properties.put("packageText", stringSchema());
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(
                "specSummary",
                "titleEn",
                "titleAr",
                "sizeText",
                "heroSellingPoints",
                "packageText"
        ));
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> stringSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        return schema;
    }

    private ProductImageAiExtractionSuggestionView toExtractionSuggestion(Map<String, Object> parsedJson) {
        if (parsedJson == null) {
            throw new IllegalArgumentException("AI 商品资料提取失败：输出不是 JSON。");
        }
        String specSummary = extractedText(parsedJson, "specSummary");
        ProductImageAiExtractionSuggestionView view = new ProductImageAiExtractionSuggestionView();
        view.setSpecSummary(specSummary);
        view.setTitleEn(removeSpecSummaryFromTitle(extractedText(parsedJson, "titleEn"), specSummary));
        view.setTitleAr(removeSpecSummaryFromTitle(extractedText(parsedJson, "titleAr"), specSummary));
        view.setSizeText(extractedText(parsedJson, "sizeText"));
        view.setHeroSellingPoints(extractedList(parsedJson, "heroSellingPoints", 5));
        view.setPackageText(extractedText(parsedJson, "packageText"));
        return view;
    }

    private String removeSpecSummaryFromTitle(String title, String specSummary) {
        String originalTitle = trimToNull(title);
        String normalizedSpec = trimToNull(specSummary);
        if (originalTitle == null) {
            return "";
        }
        if (normalizedSpec == null) {
            return originalTitle;
        }

        String cleaned = originalTitle;
        for (String fragment : specFragments(normalizedSpec)) {
            cleaned = removeLiteralTitleFragment(cleaned, fragment);
            cleaned = removeStructuredSpecFragment(cleaned, fragment);
        }
        cleaned = cleanupExtractedTitle(cleaned);
        return StringUtils.hasText(cleaned) ? cleaned : originalTitle;
    }

    private Set<String> specFragments(String specSummary) {
        Set<String> fragments = new LinkedHashSet<>();
        String normalized = specSummary.replace('•', '·');
        for (String fragment : normalized.split("[·,/|，；;]+")) {
            String text = trimToNull(fragment);
            if (text != null) {
                fragments.add(text);
            }
        }
        return fragments;
    }

    private String removeLiteralTitleFragment(String title, String fragment) {
        if (fragment.length() < 2) {
            return title;
        }
        String regex = "(?iu)(?<![\\p{L}\\p{N}])" + whitespaceFlexibleLiteral(fragment) + "(?![\\p{L}\\p{N}])";
        return title.replaceAll(regex, " ");
    }

    private String removeStructuredSpecFragment(String title, String fragment) {
        String cleaned = title;
        Matcher sizeMatcher = Pattern.compile("(?iu)(\\d+(?:\\.\\d+)?)\\s*(mm|cm|m|in|inch|inches|ml|g|kg)").matcher(fragment);
        while (sizeMatcher.find()) {
            cleaned = removeByRegex(cleaned, "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(sizeMatcher.group(1)) + "\\s*" + Pattern.quote(sizeMatcher.group(2)) + "(?![\\p{L}\\p{N}])");
        }

        Matcher quantityMatcher = Pattern.compile("(?iu)(\\d+)\\s*(pieces?|pcs|packs?|sets?|stems?|colors?|colours?|count|ct)").matcher(fragment);
        while (quantityMatcher.find()) {
            String number = quantityMatcher.group(1);
            cleaned = removeByRegex(cleaned, "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(number) + "\\s*(?:pieces?|pcs|packs?|sets?|stems?|colors?|colours?|count|ct)(?![\\p{L}\\p{N}])");
            cleaned = removeByRegex(cleaned, "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(number) + "\\s*(?:قطعة|قطع|حبة|حبات|عبوة|ألوان|لون)(?![\\p{L}\\p{N}])");
            String arabicIndicNumber = toArabicIndicDigits(number);
            cleaned = removeByRegex(cleaned, "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(arabicIndicNumber) + "\\s*(?:قطعة|قطع|حبة|حبات|عبوة|ألوان|لون)(?![\\p{L}\\p{N}])");
        }
        return cleaned;
    }

    private String whitespaceFlexibleLiteral(String value) {
        String[] parts = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index += 1) {
            if (index > 0) {
                builder.append("\\s+");
            }
            builder.append(Pattern.quote(parts[index]));
        }
        return builder.toString();
    }

    private String removeByRegex(String value, String regex) {
        return value.replaceAll(regex, " ");
    }

    private String toArabicIndicDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            if (character >= '0' && character <= '9') {
                builder.append((char) ('\u0660' + character - '0'));
            } else {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private String cleanupExtractedTitle(String value) {
        String cleaned = safeText(value)
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.;:،؛])", "$1")
                .replaceAll("([\\[(（])\\s*([\\])）])", "")
                .replaceAll("^[\\s,.;:،؛/|·\\-–—]+", "")
                .replaceAll("[\\s,.;:،؛/|·\\-–—]+$", "")
                .trim();
        return cleaned.replaceAll("\\s{2,}", " ");
    }

    private String extractedText(Map<String, Object> parsedJson, String key) {
        Object value = parsedJson.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private List<String> extractedList(Map<String, Object> parsedJson, String key, int max) {
        Object value = parsedJson.get(key);
        if (!(value instanceof List)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) {
                continue;
            }
            String text = trimToNull((String) item);
            if (text != null) {
                result.add(text);
            }
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private String truncate(String value, int maxChars) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }

    @Transactional
    public ProductImageProfileDetailView save(ProductImageProfileSaveCommand command) {
        if (command == null) {
            command = new ProductImageProfileSaveCommand();
        }
        Long ownerUserId = requireOwnerUserId(command.getOwnerUserId());
        String storeCode = requireStoreCode(command.getStoreCode());
        String pskuCode = requirePskuCode(command.getPskuCode());
        String productIdentityKey = requireProductIdentityKey(command.getProductIdentityKey(), command.getProductVariantId(), pskuCode);
        Long operatorUserId = command.getOperatorUserId();

        ProductImageProfileRecord existing = command.getId() == null
                ? mapper.selectProfileByIdentity(ownerUserId, storeCode, pskuCode, productIdentityKey)
                : mapper.selectProfileById(command.getId(), ownerUserId, storeCode);
        ProductImageProfileRecord record = existing == null ? new ProductImageProfileRecord() : existing;
        LocalDateTime now = LocalDateTime.now();
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setPskuCode(pskuCode);
        record.setProductIdentityKey(productIdentityKey);
        record.setProductMasterId(command.getProductMasterId());
        record.setProductVariantId(command.getProductVariantId());
        record.setProductTitle(trimToNull(command.getProductTitle()));
        record.setBrand(trimToNull(command.getBrand()));
        record.setTitleAr(trimToNull(command.getTitleAr()));
        record.setTitleEn(trimToNull(command.getTitleEn()));
        record.setSpecSummary(trimToNull(command.getSpecSummary()));
        record.setProductFactText(trimToNull(command.getProductFactText()));
        record.setHeroSellingPointsJson(toJson(normalizeHeroSellingPoints(command.getHeroSellingPoints())));
        record.setProfileStatus("ACTIVE");
        record.setUpdatedBy(operatorUserId);
        record.setUpdatedAt(now);
        record.setDeleted(false);

        if (existing == null) {
            record.setCreatedBy(operatorUserId);
            record.setCreatedAt(now);
            mapper.insertProfile(record);
            if (record.getId() == null) {
                throw new IllegalStateException("商品图资料 ID 生成失败。");
            }
        } else if (mapper.updateProfile(record) == 0) {
            throw notFound();
        }

        if (command.getSections() != null) {
            mapper.replaceSectionsAsDeleted(record.getId());
            for (ProductImageSectionRecord section : normalizeSections(record.getId(), command.getSections(), operatorUserId, now)) {
                mapper.insertSection(section);
            }
        }
        return detail(ownerUserId, storeCode, record.getId());
    }

    @Transactional
    public ProductImageProfileDetailView saveAndSyncAssetRoles(
            ProductImageProfileSaveCommand command,
            List<ProductImageAssetRoleUpdateCommand> assetRoles
    ) {
        ProductImageProfileDetailView profile = save(command);
        Long ownerUserId = requireOwnerUserId(command == null ? null : command.getOwnerUserId());
        String storeCode = requireStoreCode(command == null ? null : command.getStoreCode());
        Long profileId = profile.getId();
        if (profileId == null) {
            return profile;
        }
        Long operatorUserId = command == null ? null : command.getOperatorUserId();
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenUrls = new LinkedHashSet<>();
        int normalizedIndex = 0;
        for (ProductImageAssetRoleUpdateCommand roleCommand : safeList(assetRoles)) {
            if (roleCommand == null) {
                continue;
            }
            String imageUrl = trimToNull(roleCommand.getImageUrl());
            ProductImageRole imageRole = roleCommand.getImageRole();
            if (imageUrl == null || imageRole == null || !seenUrls.add(imageUrl)) {
                continue;
            }
            Integer sortOrder = roleCommand.getSortOrder() == null ? normalizedIndex : roleCommand.getSortOrder();
            int updated = mapper.updateAssetRoleAndSortOrderByUrl(
                    profileId,
                    imageUrl,
                    imageRole,
                    sortOrder,
                    operatorUserId
            );
            if (updated == 0) {
                insertProfileAsset(
                        profileId,
                        imageUrl,
                        null,
                        null,
                        null,
                        null,
                        imageRole,
                        sortOrder,
                        operatorUserId,
                        now
                );
            }
            normalizedIndex++;
        }
        return detail(ownerUserId, storeCode, profileId);
    }

    @Transactional
    public ProductImageProfileDetailView addAsset(ProductImageAssetCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("基础图信息不能为空。");
        }
        Long ownerUserId = requireOwnerUserId(command.getOwnerUserId());
        String storeCode = requireStoreCode(command.getStoreCode());
        ProductImageProfileRecord profile = requireAccessibleProfile(command.getProfileId(), ownerUserId, storeCode);
        String imageUrl = trimToNull(command.getImageUrl());
        if (imageUrl == null) {
            throw new IllegalArgumentException("图片链接不能为空。");
        }

        LocalDateTime now = LocalDateTime.now();
        insertProfileAsset(
                profile.getId(),
                imageUrl,
                trimToNull(command.getContentType()),
                command.getSizeBytes(),
                command.getWidthPx(),
                command.getHeightPx(),
                command.getHorizontalPpi(),
                command.getVerticalPpi(),
                trimToNull(command.getColorSpace()),
                command.getImageRole() == null ? ProductImageRole.MAIN : command.getImageRole(),
                command.getSortOrder() == null ? 0 : command.getSortOrder(),
                command.getOperatorUserId(),
                now
        );
        return detail(ownerUserId, storeCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView addAssetsFromUrls(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            ProductImageAssetUrlImportCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        List<String> imageUrls = normalizeImportImageUrls(command == null ? null : command.getImageUrls());
        ProductImageRole imageRole = command == null || command.getImageRole() == null
                ? ProductImageRole.MAIN
                : command.getImageRole();
        LocalDateTime now = LocalDateTime.now();
        for (String imageUrl : imageUrls) {
            insertProfileAsset(
                    profile.getId(),
                    imageUrl,
                    null,
                    null,
                    null,
                    null,
                    imageRole,
                    0,
                    operatorUserId,
                    now
            );
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    private ProductImageProfileAssetRecord insertProfileAsset(
            Long profileId,
            String imageUrl,
            String contentType,
            Long sizeBytes,
            Integer widthPx,
            Integer heightPx,
            ProductImageRole imageRole,
            Integer sortOrder,
            Long operatorUserId,
            LocalDateTime now
    ) {
        return insertProfileAsset(
                profileId,
                imageUrl,
                contentType,
                sizeBytes,
                widthPx,
                heightPx,
                null,
                null,
                null,
                imageRole,
                sortOrder,
                operatorUserId,
                now
        );
    }

    private ProductImageProfileAssetRecord insertProfileAsset(
            Long profileId,
            String imageUrl,
            String contentType,
            Long sizeBytes,
            Integer widthPx,
            Integer heightPx,
            java.math.BigDecimal horizontalPpi,
            java.math.BigDecimal verticalPpi,
            String colorSpace,
            ProductImageRole imageRole,
            Integer sortOrder,
            Long operatorUserId,
            LocalDateTime now
    ) {
        ProductImageProfileAssetRecord record = new ProductImageProfileAssetRecord();
        record.setProfileId(profileId);
        record.setImageUrl(imageUrl);
        record.setContentType(contentType);
        record.setSizeBytes(sizeBytes);
        record.setWidthPx(widthPx);
        record.setHeightPx(heightPx);
        record.setHorizontalPpi(horizontalPpi);
        record.setVerticalPpi(verticalPpi);
        record.setColorSpace(colorSpace);
        record.setImageRole(imageRole == null ? ProductImageRole.MAIN : imageRole);
        record.setSortOrder(sortOrder == null ? 0 : sortOrder);
        record.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        record.setCreatedBy(operatorUserId);
        record.setUpdatedBy(operatorUserId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insertAsset(record);
        if (record.getId() != null) {
            ensureAssetUsage(record, record.getImageRole(), operatorUserId, now);
        }
        return record;
    }

    @Transactional
    public ProductImageProfileDetailView addAssetUsages(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            ProductImageAssetUsageCreateCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        List<ProductImageRole> roles = safeList(command == null ? null : command.getImageRoles()).stream()
                .filter(Objects::nonNull)
                .filter(role -> role != ProductImageRole.OTHER)
                .distinct()
                .collect(Collectors.toList());
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("复用目标不能为空。");
        }
        ProductImageRole sourceRole = command == null || command.getSourceRole() == null
                ? ProductImageRole.MAIN
                : command.getSourceRole();
        ProductImageProfileAssetRecord asset = resolveUsageAsset(
                profile,
                command == null ? null : command.getAssetId(),
                command == null ? null : command.getImageUrl(),
                sourceRole,
                operatorUserId
        );
        LocalDateTime now = LocalDateTime.now();
        ensureAssetUsage(asset, sourceRole, operatorUserId, now);
        for (ProductImageRole role : roles) {
            ensureAssetUsage(asset, role, operatorUserId, now);
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView updateAssetUsage(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long usageId,
            ProductImageAssetUsageUpdateCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        ProductImageProfileAssetUsageRecord usage = mapper.selectAssetUsageById(profile.getId(), usageId);
        if (usage == null) {
            throw notFound();
        }
        ProductImageRole nextRole = command == null || command.getImageRole() == null
                ? usage.getImageRole()
                : command.getImageRole();
        if (nextRole == ProductImageRole.OTHER) {
            throw new IllegalArgumentException("图片用途不能为空。");
        }
        if (nextRole != usage.getImageRole()
                && mapper.countActiveAssetUsage(profile.getId(), usage.getAssetId(), nextRole) > 0) {
            throw new IllegalArgumentException("该图片已用于目标分类。");
        }
        ProductImageProcessingStatus nextStatus = command == null || command.getProcessingStatus() == null
                ? usage.getProcessingStatus()
                : command.getProcessingStatus();
        if (nextStatus == null) {
            nextStatus = ProductImageProcessingStatus.PENDING;
        }
        usage.setImageRole(nextRole);
        usage.setProcessingNote(trimToNull(command == null ? null : command.getProcessingNote()));
        usage.setProcessingStatus(nextStatus);
        usage.setUpdatedBy(operatorUserId);
        if (nextStatus == ProductImageProcessingStatus.PROCESSED) {
            usage.setProcessedBy(operatorUserId);
            usage.setProcessedAt(LocalDateTime.now());
        } else {
            usage.setProcessedBy(null);
            usage.setProcessedAt(null);
        }
        if (mapper.updateAssetUsage(usage) == 0) {
            throw notFound();
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView removeAssetUsage(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long usageId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        if (mapper.softDeleteAssetUsage(profile.getId(), usageId, operatorUserId) == 0) {
            throw notFound();
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    private ProductImageProfileAssetRecord resolveUsageAsset(
            ProductImageProfileRecord profile,
            Long assetId,
            String rawImageUrl,
            ProductImageRole sourceRole,
            Long operatorUserId
    ) {
        ProductImageProfileAssetRecord existing = assetId == null
                ? null
                : mapper.selectAssetById(profile.getId(), assetId);
        String imageUrl = trimToNull(rawImageUrl);
        if (existing != null && imageUrl != null && !imageUrl.equals(existing.getImageUrl())) {
            existing = null;
        }
        if (existing == null && imageUrl != null) {
            existing = mapper.selectAssetByUrl(profile.getId(), imageUrl);
        }
        if (existing != null) {
            return existing;
        }
        if (imageUrl == null || profile.getProductMasterId() == null) {
            throw notFound();
        }
        ProductImageProfileAssetRecord current = mapper.selectCurrentProductImageByUrl(profile.getProductMasterId(), imageUrl);
        if (current == null) {
            throw notFound();
        }
        return insertProfileAsset(
                profile.getId(),
                imageUrl,
                current.getContentType(),
                current.getSizeBytes(),
                current.getWidthPx(),
                current.getHeightPx(),
                current.getHorizontalPpi(),
                current.getVerticalPpi(),
                current.getColorSpace(),
                sourceRole,
                current.getSortOrder(),
                operatorUserId,
                LocalDateTime.now()
        );
    }

    private void ensureAssetUsage(
            ProductImageProfileAssetRecord asset,
            ProductImageRole role,
            Long operatorUserId,
            LocalDateTime now
    ) {
        if (asset == null || asset.getId() == null || role == null || role == ProductImageRole.OTHER) {
            return;
        }
        if (mapper.countActiveAssetUsage(asset.getProfileId(), asset.getId(), role) > 0) {
            return;
        }
        ProductImageProfileAssetUsageRecord usage = new ProductImageProfileAssetUsageRecord();
        usage.setProfileId(asset.getProfileId());
        usage.setAssetId(asset.getId());
        usage.setImageRole(role);
        usage.setSortOrder(asset.getSortOrder() == null ? 0 : asset.getSortOrder());
        usage.setProcessingStatus(ProductImageProcessingStatus.PENDING);
        usage.setCreatedBy(operatorUserId);
        usage.setUpdatedBy(operatorUserId);
        usage.setCreatedAt(now);
        usage.setUpdatedAt(now);
        usage.setDeleted(false);
        mapper.insertAssetUsage(usage);
    }

    @Transactional
    public ProductImageProfileDetailView updateAssetRole(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            ProductImageAssetRoleUpdateCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        ProductImageRole imageRole = command == null ? null : command.getImageRole();
        if (imageRole == null) {
            throw new IllegalArgumentException("图片分类不能为空。");
        }
        String imageUrl = trimToNull(command.getImageUrl());
        boolean updated = false;
        if (command.getAssetId() != null) {
            ProductImageProfileAssetRecord target = mapper.selectAssetById(profile.getId(), command.getAssetId());
            if (imageUrl == null || (target != null && imageUrl.equals(target.getImageUrl()))) {
                updated = mapper.updateAssetRole(profile.getId(), command.getAssetId(), imageRole, operatorUserId) > 0;
            }
        }
        if (!updated && imageUrl != null) {
            updated = mapper.updateAssetRoleByUrl(profile.getId(), imageUrl, imageRole, operatorUserId) > 0;
            if (!updated) {
                insertCurrentImageRoleOverlay(profile, imageUrl, imageRole, operatorUserId);
                updated = true;
            }
        }
        if (!updated) {
            throw notFound();
        }
        ProductImageProfileAssetRecord updatedAsset = imageUrl == null
                ? mapper.selectAssetById(profile.getId(), command.getAssetId())
                : mapper.selectAssetByUrl(profile.getId(), imageUrl);
        if (updatedAsset != null) {
            List<ProductImageProfileAssetUsageRecord> usages = safeList(mapper.selectAssetUsages(profile.getId())).stream()
                    .filter(usage -> Objects.equals(updatedAsset.getId(), usage.getAssetId()))
                    .collect(Collectors.toList());
            if (usages.isEmpty()) {
                ensureAssetUsage(updatedAsset, imageRole, operatorUserId, LocalDateTime.now());
            } else if (usages.size() == 1) {
                ProductImageProfileAssetUsageRecord usage = usages.get(0);
                usage.setImageRole(imageRole);
                usage.setUpdatedBy(operatorUserId);
                mapper.updateAssetUsage(usage);
            }
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    private void insertCurrentImageRoleOverlay(
            ProductImageProfileRecord profile,
            String imageUrl,
            ProductImageRole imageRole,
            Long operatorUserId
    ) {
        if (profile.getProductMasterId() == null) {
            throw notFound();
        }
        ProductImageProfileAssetRecord current = mapper.selectCurrentProductImageByUrl(profile.getProductMasterId(), imageUrl);
        if (current == null) {
            throw notFound();
        }
        insertProfileAsset(
                profile.getId(),
                imageUrl,
                current.getContentType(),
                current.getSizeBytes(),
                current.getWidthPx(),
                current.getHeightPx(),
                current.getHorizontalPpi(),
                current.getVerticalPpi(),
                current.getColorSpace(),
                imageRole,
                current.getSortOrder(),
                operatorUserId,
                LocalDateTime.now()
        );
    }

    @Transactional
    public ProductImageProfileDetailView removeAsset(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long assetId,
            Long operatorUserId
    ) {
        ProductImageAssetRemoveItemCommand item = new ProductImageAssetRemoveItemCommand();
        item.setAssetId(assetId);
        ProductImageAssetBatchRemoveCommand command = new ProductImageAssetBatchRemoveCommand();
        command.setAssets(List.of(item));
        return removeAssets(ownerUserId, storeCode, profileId, command, operatorUserId);
    }

    @Transactional
    public ProductImageProfileDetailView removeAssets(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            ProductImageAssetBatchRemoveCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        List<ProductImageAssetRemoveItemCommand> items = command == null ? List.of() : safeList(command.getAssets());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("图片 ID 不能为空。");
        }
        for (ProductImageAssetRemoveItemCommand item : items) {
            removeAssetItem(profile, item, operatorUserId);
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    private void removeAssetItem(
            ProductImageProfileRecord profile,
            ProductImageAssetRemoveItemCommand item,
            Long operatorUserId
    ) {
        if (item == null) {
            throw new IllegalArgumentException("图片 ID 不能为空。");
        }
        String imageUrl = trimToNull(item.getImageUrl());
        boolean removed = false;
        if (item.getAssetId() != null) {
            ProductImageProfileAssetRecord target = mapper.selectAssetById(profile.getId(), item.getAssetId());
            if (imageUrl == null || (target != null && imageUrl.equals(target.getImageUrl()))) {
                removed = mapper.updateAssetStatus(
                        profile.getId(),
                        item.getAssetId(),
                        ProductImageAssetStatus.REMOVED,
                        operatorUserId
                ) > 0;
            }
        }
        if (!removed && imageUrl != null) {
            removed = mapper.updateAssetStatusByUrl(
                    profile.getId(),
                    imageUrl,
                    ProductImageAssetStatus.REMOVED,
                    operatorUserId
            ) > 0;
            if (!removed) {
                insertCurrentImageRemovalMarker(profile, imageUrl, operatorUserId);
                removed = true;
            }
        }
        if (!removed) {
            throw notFound();
        }
    }

    private void insertCurrentImageRemovalMarker(
            ProductImageProfileRecord profile,
            String imageUrl,
            Long operatorUserId
    ) {
        if (profile.getProductMasterId() == null
                || mapper.countCurrentProductImageByUrl(profile.getProductMasterId(), imageUrl) == 0) {
            throw notFound();
        }
        if (mapper.countProfileAssetByUrl(profile.getId(), imageUrl) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ProductImageProfileAssetRecord marker = new ProductImageProfileAssetRecord();
        marker.setProfileId(profile.getId());
        marker.setImageUrl(imageUrl);
        marker.setImageRole(ProductImageRole.OTHER);
        marker.setSortOrder(0);
        marker.setAssetStatus(ProductImageAssetStatus.REMOVED);
        marker.setCreatedBy(operatorUserId);
        marker.setUpdatedBy(operatorUserId);
        marker.setCreatedAt(now);
        marker.setUpdatedAt(now);
        mapper.insertAsset(marker);
    }

    @Transactional
    public ProductImageProfileDetailView adoptSuite(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long suiteId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        requireSuite(profile.getId(), suiteId);
        mapper.markAdoptedSuitesHistorical(profile.getId(), operatorUserId);
        if (mapper.updateSuiteStatus(suiteId, profile.getId(), ProductImageSuiteStatus.ADOPTED, operatorUserId) == 0) {
            throw notFound();
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView discardSuite(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long suiteId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        requireSuite(profile.getId(), suiteId);
        if (mapper.updateSuiteStatus(suiteId, profile.getId(), ProductImageSuiteStatus.DISCARDED, operatorUserId) == 0) {
            throw notFound();
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView deleteSuite(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long suiteId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        requireSuite(profile.getId(), suiteId);
        if (mapper.softDeleteSuite(suiteId, profile.getId(), operatorUserId) == 0) {
            throw notFound();
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView deleteSuiteAsset(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long suiteId,
            Long assetId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        requireSuite(profile.getId(), suiteId);
        requireSuiteAsset(suiteId, assetId);
        if (mapper.deleteSuiteAsset(suiteId, assetId) == 0) {
            throw notFound();
        }
        normalizeSuiteAssetSortOrders(profile.getId(), suiteId, operatorUserId);
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView moveSuiteAsset(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long suiteId,
            Long assetId,
            ProductImageSuiteAssetMoveCommand command,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        ProductImageSuiteRecord sourceSuite = requireSuite(profile.getId(), suiteId);
        ProductImageSuiteAssetRecord asset = requireSuiteAsset(sourceSuite.getId(), assetId);
        Long targetSuiteId = command == null || command.getTargetSuiteId() == null
                ? sourceSuite.getId()
                : command.getTargetSuiteId();
        ProductImageSuiteRecord targetSuite = requireSuite(profile.getId(), targetSuiteId);
        int targetIndex = command == null || command.getTargetIndex() == null ? Integer.MAX_VALUE : command.getTargetIndex();

        if (!Objects.equals(sourceSuite.getId(), targetSuite.getId())) {
            int nextSortOrder = mapper.selectMaxSuiteAssetSortOrder(targetSuite.getId()) + 10;
            if (mapper.moveSuiteAssetToSuite(sourceSuite.getId(), asset.getId(), targetSuite.getId(), nextSortOrder) == 0) {
                throw notFound();
            }
            normalizeSuiteAssetSortOrders(profile.getId(), sourceSuite.getId(), operatorUserId);
        }

        normalizeSuiteAssetSortOrders(profile.getId(), targetSuite.getId(), operatorUserId, asset.getId(), targetIndex);
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    @Transactional
    public ProductImageProfileDetailView createAiSuiteDraft(
            Long ownerUserId,
            String storeCode,
            Long profileId,
            Long operatorUserId
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord profile = requireAccessibleProfile(profileId, resolvedOwnerUserId, normalizedStoreCode);
        ActiveHeroSkin activeSkin = requireActiveHeroSkin(resolvedOwnerUserId, normalizedStoreCode);
        List<ProductImageSectionRecord> sections = safeList(mapper.selectSections(profile.getId()));
        List<ProductImageProfileAssetView> baseImages = combineBaseAndProfileAssets(profile);
        List<String> sellingPoints = parseHeroSellingPoints(profile.getHeroSellingPointsJson());
        String draftPromptText = buildDraftPromptText(
                profile,
                activeSkin.skin(),
                activeSkin.components(),
                sections,
                baseImages,
                sellingPoints
        );
        String draftPackageJson = buildDraftPackageJson(
                profile,
                activeSkin.skin(),
                activeSkin.components(),
                sections,
                baseImages,
                sellingPoints,
                draftPromptText
        );

        LocalDateTime now = LocalDateTime.now();
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setProfileId(profile.getId());
        suite.setSuiteName(buildDraftSuiteName(profile, now));
        suite.setSkinId(activeSkin.skin().getId());
        suite.setSkinName(activeSkin.skin().getSkinName());
        suite.setGenerationTaskId(buildDraftTaskId(profile, now));
        suite.setDraftPackageJson(draftPackageJson);
        suite.setDraftPromptText(draftPromptText);
        suite.setSuiteStatus(ProductImageSuiteStatus.DRAFT);
        suite.setCreatedBy(operatorUserId);
        suite.setUpdatedBy(operatorUserId);
        suite.setCreatedAt(now);
        suite.setUpdatedAt(now);
        suite.setDeleted(false);
        if (mapper.insertSuite(suite) == 0 || suite.getId() == null) {
            throw new IllegalStateException("AI 套图草稿保存失败。");
        }
        return detail(resolvedOwnerUserId, normalizedStoreCode, profile.getId());
    }

    private ProductImageProfileDetailView toDetailView(ProductImageProfileRecord record) {
        ProductImageProfileDetailView view = new ProductImageProfileDetailView();
        view.setId(record.getId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setPskuCode(record.getPskuCode());
        view.setProductIdentityKey(record.getProductIdentityKey());
        view.setProductMasterId(record.getProductMasterId());
        view.setProductVariantId(record.getProductVariantId());
        view.setProductTitle(record.getProductTitle());
        view.setBrand(record.getBrand());
        view.setTitleAr(record.getTitleAr());
        view.setTitleEn(record.getTitleEn());
        view.setSpecSummary(record.getSpecSummary());
        view.setProductFactText(record.getProductFactText());
        view.setHeroSellingPoints(parseHeroSellingPoints(record.getHeroSellingPointsJson()));
        view.setUpdatedAt(format(record.getUpdatedAt()));
        view.setAssets(combineBaseAndProfileAssets(record));
        view.setSections(safeList(mapper.selectSections(record.getId())).stream()
                .map(this::toSectionView)
                .collect(Collectors.toList()));
        view.setSuites(safeList(mapper.selectSuites(record.getId())).stream()
                .filter(suite -> suite.getSuiteStatus() != ProductImageSuiteStatus.DISCARDED)
                .map(this::toSuiteView)
                .collect(Collectors.toList()));
        return view;
    }

    private ProductImageProfileSummaryView toSummaryView(ProductImageProfileSummaryRecord record) {
        ProductImageProfileSummaryView view = new ProductImageProfileSummaryView();
        view.setId(record.getId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setPskuCode(record.getPskuCode());
        view.setProductIdentityKey(record.getProductIdentityKey());
        view.setProductMasterId(record.getProductMasterId());
        view.setProductVariantId(record.getProductVariantId());
        view.setProductTitle(record.getProductTitle());
        view.setBrand(record.getBrand());
        view.setTitleAr(record.getTitleAr());
        view.setTitleEn(record.getTitleEn());
        view.setSpecSummary(record.getSpecSummary());
        view.setCoverImageUrl(record.getCoverImageUrl());
        view.setAssetCount(record.getAssetCount() == null ? 0 : record.getAssetCount());
        view.setSuiteCount(record.getSuiteCount() == null ? 0 : record.getSuiteCount());
        view.setHasAdoptedSuite(Boolean.TRUE.equals(record.getHasAdoptedSuite()));
        view.setUpdatedAt(format(record.getUpdatedAt()));
        return view;
    }

    private ProductImageProfileDetailView toTransientDetailView(
            Long ownerUserId,
            String storeCode,
            ProductImageProductCandidateRecord candidate
    ) {
        ProductImageProfileDetailView view = new ProductImageProfileDetailView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);
        view.setPskuCode(candidate.getPskuCode());
        view.setProductIdentityKey(candidate.getProductIdentityKey());
        view.setProductMasterId(candidate.getProductMasterId());
        view.setProductVariantId(candidate.getProductVariantId());
        view.setProductTitle(candidate.getProductTitle());
        view.setBrand(candidate.getBrand());
        view.setAssets(baseAssets(candidate.getProductMasterId(), candidate.getCoverImageUrl()));
        return view;
    }

    private ProductImageProfileSummaryView toTransientSummaryView(
            Long ownerUserId,
            String storeCode,
            ProductImageProductCandidateRecord candidate
    ) {
        ProductImageProfileSummaryView view = new ProductImageProfileSummaryView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);
        view.setPskuCode(candidate.getPskuCode());
        view.setProductIdentityKey(candidate.getProductIdentityKey());
        view.setProductMasterId(candidate.getProductMasterId());
        view.setProductVariantId(candidate.getProductVariantId());
        view.setProductTitle(candidate.getProductTitle());
        view.setBrand(candidate.getBrand());
        view.setCoverImageUrl(candidate.getCoverImageUrl());
        view.setAssetCount(StringUtils.hasText(candidate.getCoverImageUrl()) ? 1 : 0);
        view.setSuiteCount(0);
        view.setHasAdoptedSuite(false);
        return view;
    }

    private List<ProductImageProfileAssetView> combineBaseAndProfileAssets(ProductImageProfileRecord record) {
        Set<String> removedUrls = safeList(mapper.selectRemovedAssetUrls(record.getId())).stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ProductImageProfileAssetRecord> profileAssets = safeList(mapper.selectAssets(record.getId()));
        Map<Long, List<ProductImageProfileAssetUsageRecord>> usagesByAsset = safeList(mapper.selectAssetUsages(record.getId())).stream()
                .filter(Objects::nonNull)
                .filter(usage -> usage.getAssetId() != null)
                .collect(Collectors.groupingBy(
                        ProductImageProfileAssetUsageRecord::getAssetId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<String> profileAssetUrls = profileAssets.stream()
                .map(ProductImageProfileAssetRecord::getImageUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> baseSkippedUrls = new LinkedHashSet<>(removedUrls);
        baseSkippedUrls.addAll(profileAssetUrls);
        List<ProductImageProfileAssetView> result = baseAssets(record.getProductMasterId(), null, baseSkippedUrls);
        for (ProductImageProfileAssetRecord asset : profileAssets) {
            if (asset == null || !StringUtils.hasText(asset.getImageUrl())) {
                continue;
            }
            List<ProductImageProfileAssetUsageRecord> usages = safeList(usagesByAsset.get(asset.getId()));
            if (!usages.isEmpty()) {
                for (ProductImageProfileAssetUsageRecord usage : usages) {
                    result.add(toAssetView(asset, true, usage));
                }
            } else if (asset.getId() == null || mapper.countAssetUsageHistory(record.getId(), asset.getId()) == 0) {
                result.add(toAssetView(asset, true));
            }
        }
        return result;
    }

    private List<ProductImageProfileAssetView> baseAssets(Long productMasterId, String coverImageUrl) {
        return baseAssets(productMasterId, coverImageUrl, Set.of());
    }

    private List<ProductImageProfileAssetView> baseAssets(
            Long productMasterId,
            String coverImageUrl,
            Set<String> removedUrls
    ) {
        List<ProductImageProfileAssetView> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> skippedUrls = removedUrls == null ? Set.of() : removedUrls;
        if (StringUtils.hasText(coverImageUrl) && !skippedUrls.contains(coverImageUrl)) {
            ProductImageProfileAssetView cover = new ProductImageProfileAssetView();
            cover.setImageUrl(coverImageUrl);
            cover.setImageRole(ProductImageRole.MAIN);
            cover.setSortOrder(0);
            cover.setAssetStatus(ProductImageAssetStatus.ACTIVE);
            cover.setRemovable(false);
            result.add(cover);
            seen.add(coverImageUrl);
        }
        if (productMasterId == null) {
            return result;
        }
        for (ProductImageProfileAssetRecord asset : safeList(mapper.selectCurrentProductImages(productMasterId))) {
            if (asset == null || !StringUtils.hasText(asset.getImageUrl())) {
                continue;
            }
            if (skippedUrls.contains(asset.getImageUrl())) {
                continue;
            }
            if (seen.add(asset.getImageUrl())) {
                result.add(toAssetView(asset, false));
            }
        }
        return result;
    }

    private ProductImageProfileAssetView toAssetView(ProductImageProfileAssetRecord record, boolean removable) {
        ProductImageProfileAssetView view = new ProductImageProfileAssetView();
        view.setId(removable ? record.getId() : null);
        view.setImageUrl(record.getImageUrl());
        view.setContentType(record.getContentType());
        view.setSizeBytes(record.getSizeBytes());
        view.setWidthPx(record.getWidthPx());
        view.setHeightPx(record.getHeightPx());
        view.setHorizontalPpi(record.getHorizontalPpi());
        view.setVerticalPpi(record.getVerticalPpi());
        view.setColorSpace(record.getColorSpace());
        view.setImageRole(record.getImageRole() == null ? ProductImageRole.OTHER : record.getImageRole());
        view.setSortOrder(record.getSortOrder() == null ? 0 : record.getSortOrder());
        view.setAssetStatus(record.getAssetStatus() == null ? ProductImageAssetStatus.ACTIVE : record.getAssetStatus());
        view.setRemovable(removable);
        view.setProcessingStatus(ProductImageProcessingStatus.PENDING);
        view.setNoonTechnicalCompliance(noonComplianceEvaluator.evaluate(record));
        return view;
    }

    private ProductImageProfileAssetView toAssetView(
            ProductImageProfileAssetRecord record,
            boolean removable,
            ProductImageProfileAssetUsageRecord usage
    ) {
        ProductImageProfileAssetView view = toAssetView(record, removable);
        view.setUsageId(usage.getId());
        view.setImageRole(usage.getImageRole() == null ? view.getImageRole() : usage.getImageRole());
        view.setSortOrder(usage.getSortOrder() == null ? view.getSortOrder() : usage.getSortOrder());
        view.setProcessingNote(usage.getProcessingNote());
        view.setProcessingStatus(usage.getProcessingStatus() == null
                ? ProductImageProcessingStatus.PENDING
                : usage.getProcessingStatus());
        view.setProcessedAt(format(usage.getProcessedAt()));
        return view;
    }

    private ProductImageSectionView toSectionView(ProductImageSectionRecord record) {
        ProductImageSectionView view = new ProductImageSectionView();
        view.setId(record.getId());
        view.setSectionType(record.getSectionType());
        view.setTitleAr(record.getTitleAr());
        view.setTitleEn(record.getTitleEn());
        view.setDescriptionAr(record.getDescriptionAr());
        view.setDescriptionEn(record.getDescriptionEn());
        view.setAttributesText(record.getAttributesText());
        view.setFocusPart(record.getFocusPart());
        view.setSortOrder(record.getSortOrder());
        view.setEnabled(record.getEnabled());
        return view;
    }

    private ProductImageSuiteView toSuiteView(ProductImageSuiteRecord record) {
        ProductImageSuiteView view = new ProductImageSuiteView();
        view.setId(record.getId());
        view.setSuiteName(record.getSuiteName());
        view.setSkinId(record.getSkinId());
        view.setSkinName(record.getSkinName());
        view.setGenerationTaskId(record.getGenerationTaskId());
        view.setDraftPackageJson(record.getDraftPackageJson());
        view.setDraftPromptText(record.getDraftPromptText());
        view.setSuiteStatus(record.getSuiteStatus());
        view.setAdoptedAt(format(record.getAdoptedAt()));
        view.setUpdatedAt(format(record.getUpdatedAt()));
        view.setAssets(safeList(mapper.selectSuiteAssets(record.getId())).stream()
                .map(this::toSuiteAssetView)
                .collect(Collectors.toList()));
        return view;
    }

    private ProductImageSuiteAssetView toSuiteAssetView(ProductImageSuiteAssetRecord record) {
        ProductImageSuiteAssetView view = new ProductImageSuiteAssetView();
        view.setId(record.getId());
        view.setImageRole(record.getImageRole());
        view.setImageUrl(record.getImageUrl());
        view.setSortOrder(record.getSortOrder());
        return view;
    }

    private List<ProductImageSectionRecord> normalizeSections(
            Long profileId,
            List<ProductImageSectionCommand> rawSections,
            Long operatorUserId,
            LocalDateTime now
    ) {
        List<ProductImageSectionRecord> result = new ArrayList<>();
        if (rawSections == null) {
            return result;
        }
        int fallbackSort = 0;
        for (ProductImageSectionCommand raw : rawSections) {
            if (raw == null || raw.getSectionType() == null) {
                continue;
            }
            ProductImageSectionRecord record = new ProductImageSectionRecord();
            record.setProfileId(profileId);
            record.setSectionType(raw.getSectionType());
            record.setTitleAr(trimToNull(raw.getTitleAr()));
            record.setTitleEn(trimToNull(raw.getTitleEn()));
            record.setDescriptionAr(trimToNull(raw.getDescriptionAr()));
            record.setDescriptionEn(trimToNull(raw.getDescriptionEn()));
            record.setAttributesText(trimToNull(raw.getAttributesText()));
            record.setFocusPart(trimToNull(raw.getFocusPart()));
            record.setSortOrder(raw.getSortOrder() == null ? fallbackSort : raw.getSortOrder());
            record.setEnabled(raw.getEnabled() == null || raw.getEnabled());
            record.setCreatedBy(operatorUserId);
            record.setUpdatedBy(operatorUserId);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setDeleted(false);
            result.add(record);
            fallbackSort += 10;
        }
        return result;
    }

    private ProductImageProfileRecord requireAccessibleProfile(Long profileId, Long ownerUserId, String storeCode) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        ProductImageProfileRecord record = profileId == null
                ? null
                : mapper.selectProfileById(profileId, resolvedOwnerUserId, normalizedStoreCode);
        if (record == null) {
            throw notFound();
        }
        return record;
    }

    private ProductImageSuiteRecord requireSuite(Long profileId, Long suiteId) {
        ProductImageSuiteRecord suite = suiteId == null ? null : mapper.selectSuiteById(suiteId, profileId);
        if (suite == null) {
            throw notFound();
        }
        return suite;
    }

    private ProductImageSuiteAssetRecord requireSuiteAsset(Long suiteId, Long assetId) {
        ProductImageSuiteAssetRecord asset = assetId == null ? null : mapper.selectSuiteAssetById(suiteId, assetId);
        if (asset == null) {
            throw notFound();
        }
        return asset;
    }

    private void normalizeSuiteAssetSortOrders(Long profileId, Long suiteId, Long operatorUserId) {
        normalizeSuiteAssetSortOrders(profileId, suiteId, operatorUserId, null, Integer.MAX_VALUE);
    }

    private void normalizeSuiteAssetSortOrders(
            Long profileId,
            Long suiteId,
            Long operatorUserId,
            Long pinnedAssetId,
            int targetIndex
    ) {
        List<ProductImageSuiteAssetRecord> assets = new ArrayList<>(safeList(mapper.selectSuiteAssets(suiteId)));
        if (pinnedAssetId != null) {
            ProductImageSuiteAssetRecord pinned = null;
            List<ProductImageSuiteAssetRecord> remaining = new ArrayList<>();
            for (ProductImageSuiteAssetRecord asset : assets) {
                if (Objects.equals(asset.getId(), pinnedAssetId)) {
                    pinned = asset;
                } else {
                    remaining.add(asset);
                }
            }
            if (pinned != null) {
                int insertIndex = Math.max(0, Math.min(targetIndex, remaining.size()));
                remaining.add(insertIndex, pinned);
                assets = remaining;
            }
        }

        int sortOrder = 10;
        for (ProductImageSuiteAssetRecord asset : assets) {
            mapper.updateSuiteAssetSortOrder(suiteId, asset.getId(), sortOrder);
            sortOrder += 10;
        }
        mapper.touchSuite(suiteId, profileId, operatorUserId);
    }

    private ActiveHeroSkin requireActiveHeroSkin(Long ownerUserId, String storeCode) {
        List<ActiveHeroSkin> candidates = new ArrayList<>();
        for (OperationsSkinRecord skin : safeList(operationsSkinMapper.selectSkins(ownerUserId, storeCode, null, "ACTIVE"))) {
            if (skin == null || skin.getId() == null) {
                continue;
            }
            List<OperationsSkinComponentRecord> components = completedSkinComponents(
                    operationsSkinMapper.selectComponents(skin.getId(), ownerUserId, storeCode)
            );
            if (hasRequiredHeroComponents(completedHeroComponents(components))) {
                candidates.add(new ActiveHeroSkin(skin, components, effectiveSkinUpdatedAt(skin, components)));
            }
        }
        if (!candidates.isEmpty()) {
            candidates.sort((left, right) -> {
                int updatedAtOrder = right.effectiveUpdatedAt().compareTo(left.effectiveUpdatedAt());
                if (updatedAtOrder != 0) {
                    return updatedAtOrder;
                }
                return Long.compare(right.skin().getId(), left.skin().getId());
            });
            return candidates.get(0);
        }
        throw new IllegalArgumentException("当前店铺没有完整的主图皮肤。");
    }

    private LocalDateTime effectiveSkinUpdatedAt(
            OperationsSkinRecord skin,
            List<OperationsSkinComponentRecord> components
    ) {
        LocalDateTime effectiveUpdatedAt = latestTime(skin.getCreatedAt(), skin.getUpdatedAt());
        for (OperationsSkinComponentRecord component : safeList(components)) {
            effectiveUpdatedAt = latestTime(effectiveUpdatedAt, component.getCreatedAt(), component.getUpdatedAt());
        }
        return effectiveUpdatedAt == null ? LocalDateTime.MIN : effectiveUpdatedAt;
    }

    private LocalDateTime latestTime(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) {
                latest = value;
            }
        }
        return latest;
    }

    private List<OperationsSkinComponentRecord> completedSkinComponents(List<OperationsSkinComponentRecord> rawComponents) {
        return safeList(rawComponents).stream()
                .filter(Objects::nonNull)
                .filter(component -> trimToNull(component.getTemplateRole()) != null)
                .filter(component -> trimToNull(component.getComponentKey()) != null)
                .filter(component -> trimToNull(component.getImageUrl()) != null)
                .sorted((left, right) -> {
                    int leftRoleOrder = templateRoleOrder(left.getTemplateRole());
                    int rightRoleOrder = templateRoleOrder(right.getTemplateRole());
                    if (leftRoleOrder != rightRoleOrder) {
                        return Integer.compare(leftRoleOrder, rightRoleOrder);
                    }
                    int leftOrder = componentOrder(left.getComponentKey());
                    int rightOrder = componentOrder(right.getComponentKey());
                    if (leftOrder != rightOrder) {
                        return Integer.compare(leftOrder, rightOrder);
                    }
                    int leftZIndex = left.getZIndex() == null ? 0 : left.getZIndex();
                    int rightZIndex = right.getZIndex() == null ? 0 : right.getZIndex();
                    if (leftZIndex != rightZIndex) {
                        return Integer.compare(leftZIndex, rightZIndex);
                    }
                    return Long.compare(left.getId() == null ? 0L : left.getId(), right.getId() == null ? 0L : right.getId());
                })
                .collect(Collectors.toList());
    }

    private List<OperationsSkinComponentRecord> completedHeroComponents(List<OperationsSkinComponentRecord> components) {
        return safeList(components).stream()
                .filter(component -> HERO_MAIN_TEMPLATE_ROLE.equalsIgnoreCase(trimToNull(component.getTemplateRole())))
                .collect(Collectors.toList());
    }

    private boolean hasRequiredHeroComponents(List<OperationsSkinComponentRecord> components) {
        Set<String> keys = safeList(components).stream()
                .map(component -> trimToNull(component.getComponentKey()))
                .filter(Objects::nonNull)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return keys.containsAll(REQUIRED_HERO_COMPONENT_KEYS);
    }

    private int templateRoleOrder(String templateRole) {
        String normalized = trimToNull(templateRole);
        if (normalized == null) {
            return Integer.MAX_VALUE;
        }
        switch (normalized.toUpperCase(Locale.ROOT)) {
            case HERO_MAIN_TEMPLATE_ROLE:
                return 0;
            case "SIZE_IMAGE":
                return 10;
            case "DETAIL_IMAGE":
                return 20;
            case "SCENE_IMAGE":
                return 30;
            case "PACKAGE_IMAGE":
                return 40;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private int componentOrder(String componentKey) {
        String normalized = trimToNull(componentKey);
        if (normalized == null) {
            return Integer.MAX_VALUE;
        }
        int index = REQUIRED_HERO_COMPONENT_KEYS.indexOf(normalized.toUpperCase(Locale.ROOT));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private String buildDraftSuiteName(ProductImageProfileRecord profile, LocalDateTime now) {
        String pskuCode = trimToNull(profile.getPskuCode());
        String prefix = pskuCode == null ? "AI 套图草稿" : pskuCode + " AI 套图草稿";
        return prefix + " " + TASK_TIME_FORMATTER.format(now);
    }

    private String buildDraftTaskId(ProductImageProfileRecord profile, LocalDateTime now) {
        return "AI-DRAFT-"
                + (profile.getId() == null ? "0" : profile.getId())
                + "-"
                + TASK_TIME_FORMATTER.format(now)
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildDraftPromptText(
            ProductImageProfileRecord profile,
            OperationsSkinRecord skin,
            List<OperationsSkinComponentRecord> components,
            List<ProductImageSectionRecord> sections,
            List<ProductImageProfileAssetView> baseImages,
            List<String> sellingPoints
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("AI 套图草稿");
        lines.add("店铺皮肤：" + safeText(skin.getSkinName()));
        lines.add("PSKU：" + safeText(profile.getPskuCode()));
        lines.add("");
        lines.add("图片要求");
        lines.add("第1张 主图");
        lines.add("文案");
        appendPromptLine(lines, "品牌", profile.getBrand());
        appendPromptLine(lines, "英文标题", profile.getTitleEn());
        appendPromptLine(lines, "阿语标题", profile.getTitleAr());
        appendPromptLine(lines, "规格", profile.getSpecSummary());
        if (!safeList(sellingPoints).isEmpty()) {
            lines.add("精简卖点：" + String.join(" / ", sellingPoints));
        }
        lines.add("要求：使用当前店铺皮肤的 HERO_MAIN 主图组件，商品主体清晰完整。");
        lines.add("");
        lines.add("第2张 尺寸图");
        lines.add("文案：" + firstOrPlaceholder(sectionTexts(sections, ProductImageSectionType.SIZE, 1), "尺寸信息待补充"));
        lines.add("要求：使用 SIZE_IMAGE 尺寸图皮肤，展示尺寸关系和比例参考，不新增未确认尺寸。");
        lines.add("");
        lines.add("第3部分 细节图 2-4张");
        for (String value : combinedSectionTexts(sections, 4)) {
            lines.add("- " + value);
        }
        lines.add("要求：使用 DETAIL_IMAGE 细节图皮肤；每张细节图单独成图，必须是一处商品局部大图/特写，不做场景图、不做多细节拼版。");
        lines.add("");
        lines.add("第4部分 场景图 1-2张");
        for (String value : sectionTexts(sections, ProductImageSectionType.USAGE_SCENE, 2)) {
            lines.add("- " + value);
        }
        lines.add("要求：场景图使用 SCENE_IMAGE 场景图皮肤，场景必须符合商品真实用途。");
        lines.add("");
        lines.add("第5部分 包装图 1张");
        lines.add("文案：" + firstOrPlaceholder(sectionTexts(sections, ProductImageSectionType.PACKAGE_LIST, 1), "包装/套装清单待补充"));
        lines.add("要求：使用 PACKAGE_IMAGE 包装图皮肤，只展示确认包含的商品和配件。");
        lines.add("");
        lines.add("整体要求");
        lines.add("尺寸：跟随当前店铺皮肤或平台目标画布，整套图统一比例。");
        lines.add("风格：沿用店铺皮肤的品牌色、边框、标题区、规格条、字体和留白。");
        lines.add("素材边界：基础图只作为商品外观、结构和事实参考，不作为整套图风格来源。");
        lines.add("事实边界：不得编造规格、材质、数量、功能、认证或使用场景。");
        lines.add("基础图数量：" + safeList(baseImages).size());
        lines.add("");
        lines.add("商品资料参考");
        lines.add(safeText(profile.getProductFactText()));
        lines.add("");
        lines.add("皮肤组件");
        for (OperationsSkinComponentRecord component : safeList(components)) {
            lines.add("- " + safeText(component.getTemplateRole()) + "/" + safeText(component.getComponentKey()) + "：" + safeText(component.getImageUrl()));
        }
        return String.join("\n", lines);
    }

    private String buildDraftPackageJson(
            ProductImageProfileRecord profile,
            OperationsSkinRecord skin,
            List<OperationsSkinComponentRecord> components,
            List<ProductImageSectionRecord> sections,
            List<ProductImageProfileAssetView> baseImages,
            List<String> sellingPoints,
            String draftPromptText
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        root.put("draftType", "AI_SUITE_DRAFT");
        root.put("profile", profilePayload(profile, sellingPoints));
        root.put("skin", skinPayload(skin, components));
        root.put("imageRequirements", imageRequirementsPayload(profile, sections, sellingPoints));
        root.put("overallRequirements", overallRequirementsPayload());
        root.put("baseImages", baseImagesPayload(baseImages));
        root.put("draftPromptText", draftPromptText);
        return toJsonValue(root, "AI 套图草稿格式生成失败。");
    }

    private Map<String, Object> profilePayload(ProductImageProfileRecord profile, List<String> sellingPoints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profileId", profile.getId());
        payload.put("pskuCode", profile.getPskuCode());
        payload.put("productIdentityKey", profile.getProductIdentityKey());
        payload.put("productMasterId", profile.getProductMasterId());
        payload.put("productVariantId", profile.getProductVariantId());
        payload.put("productTitle", profile.getProductTitle());
        payload.put("brand", profile.getBrand());
        payload.put("titleAr", profile.getTitleAr());
        payload.put("titleEn", profile.getTitleEn());
        payload.put("specSummary", profile.getSpecSummary());
        payload.put("heroSellingPoints", safeList(sellingPoints));
        payload.put("productFactText", profile.getProductFactText());
        return payload;
    }

    private Map<String, Object> skinPayload(OperationsSkinRecord skin, List<OperationsSkinComponentRecord> components) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skinId", skin.getId());
        payload.put("skinName", skin.getSkinName());
        payload.put("storeCode", skin.getStoreCode());
        payload.put("styleDescription", skin.getStyleDescription());
        payload.put("components", safeList(components).stream()
                .map(this::componentPayload)
                .collect(Collectors.toList()));
        return payload;
    }

    private Map<String, Object> componentPayload(OperationsSkinComponentRecord component) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateRole", component.getTemplateRole());
        payload.put("componentKey", component.getComponentKey());
        payload.put("imageUrl", component.getImageUrl());
        payload.put("x", component.getX());
        payload.put("y", component.getY());
        payload.put("width", component.getWidth());
        payload.put("height", component.getHeight());
        payload.put("zIndex", component.getZIndex());
        payload.put("styleJson", component.getStyleJson());
        return payload;
    }

    private Map<String, Object> imageRequirementsPayload(
            ProductImageProfileRecord profile,
            List<ProductImageSectionRecord> sections,
            List<String> sellingPoints
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("main", Map.of(
                "copy", Map.of(
                        "brand", safeText(profile.getBrand()),
                        "titleEn", safeText(profile.getTitleEn()),
                        "titleAr", safeText(profile.getTitleAr()),
                        "spec", safeText(profile.getSpecSummary()),
                        "sellingPoints", safeList(sellingPoints)
                ),
                "skinTemplateRole", "HERO_MAIN",
                "requirement", "使用当前店铺皮肤的 HERO_MAIN 主图组件。"
        ));
        payload.put("size", Map.of(
                "skinTemplateRole", "SIZE_IMAGE",
                "copies", sectionTexts(sections, ProductImageSectionType.SIZE, 1),
                "requirement", "第二张尺寸图使用 SIZE_IMAGE 尺寸图皮肤。"
        ));
        payload.put("details", Map.of(
                "skinTemplateRole", "DETAIL_IMAGE",
                "copies", combinedSectionTexts(sections, 4),
                "targetCount", "2-4",
                "requirement", "细节图使用 DETAIL_IMAGE 细节图皮肤；每张细节图单独成图，必须是一处商品局部大图/特写，不做场景图、不做多细节拼版。"
        ));
        payload.put("usageScenes", Map.of(
                "skinTemplateRole", "SCENE_IMAGE",
                "copies", sectionTexts(sections, ProductImageSectionType.USAGE_SCENE, 2),
                "targetCount", "1-2",
                "requirement", "场景图使用 SCENE_IMAGE 场景图皮肤。"
        ));
        payload.put("packageList", Map.of(
                "skinTemplateRole", "PACKAGE_IMAGE",
                "copies", sectionTexts(sections, ProductImageSectionType.PACKAGE_LIST, 1),
                "targetCount", "1",
                "requirement", "包装图使用 PACKAGE_IMAGE 包装图皮肤。"
        ));
        return payload;
    }

    private Map<String, Object> overallRequirementsPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("canvas", "跟随当前店铺皮肤或平台目标画布。");
        payload.put("style", "整套图统一品牌色、边框、标题区、规格条、字体和留白。");
        payload.put("copyBoundary", "只有 imageRequirements.copy 内的素材允许直接上图。");
        payload.put("factBoundary", "productFactText 只做参考，不得编造未确认事实。");
        return payload;
    }

    private List<Map<String, Object>> baseImagesPayload(List<ProductImageProfileAssetView> baseImages) {
        return safeList(baseImages).stream()
                .map(asset -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("imageUrl", asset.getImageUrl());
                    payload.put("imageRole", asset.getImageRole());
                    payload.put("widthPx", asset.getWidthPx());
                    payload.put("heightPx", asset.getHeightPx());
                    payload.put("sizeBytes", asset.getSizeBytes());
                    payload.put("removable", asset.getRemovable());
                    return payload;
                })
                .collect(Collectors.toList());
    }

    private List<String> combinedSectionTexts(List<ProductImageSectionRecord> sections, int max) {
        List<String> values = new ArrayList<>();
        values.addAll(sectionTexts(sections, ProductImageSectionType.MATERIAL_DETAIL, max));
        values.addAll(sectionTexts(sections, ProductImageSectionType.CORE_FEATURE, max));
        return values.stream().limit(max).collect(Collectors.toList());
    }

    private List<String> sectionTexts(List<ProductImageSectionRecord> sections, ProductImageSectionType sectionType, int max) {
        return safeList(sections).stream()
                .filter(Objects::nonNull)
                .filter(section -> sectionType == section.getSectionType())
                .filter(section -> section.getEnabled() == null || section.getEnabled())
                .map(this::sectionText)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .limit(max)
                .collect(Collectors.toList());
    }

    private String sectionText(ProductImageSectionRecord section) {
        List<String> values = new ArrayList<>();
        appendIfPresent(values, section.getTitleEn());
        appendIfPresent(values, section.getTitleAr());
        appendIfPresent(values, section.getAttributesText());
        appendIfPresent(values, section.getFocusPart());
        appendIfPresent(values, section.getDescriptionEn());
        appendIfPresent(values, section.getDescriptionAr());
        return String.join(" | ", values);
    }

    private void appendIfPresent(List<String> values, String raw) {
        String value = trimToNull(raw);
        if (value != null) {
            values.add(value);
        }
    }

    private String firstOrPlaceholder(List<String> values, String placeholder) {
        return safeList(values).stream().findFirst().orElse(placeholder);
    }

    private void appendPromptLine(List<String> lines, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            lines.add(label + "：" + normalized);
        }
    }

    private Long requireOwnerUserId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("老板上下文不能为空。");
        }
        return value;
    }

    private String requireStoreCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException("店铺编码不能为空。");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String requirePskuCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException("PSKU 不能为空。");
        }
        return value;
    }

    private String requireProductIdentityKey(String raw, Long variantId, String pskuCode) {
        String value = trimToNull(raw);
        if (value != null) {
            return value;
        }
        if (variantId != null) {
            return "variant:" + variantId;
        }
        return "psku:" + pskuCode;
    }

    private List<String> normalizeHeroSellingPoints(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : raw) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                result.add(normalized);
            }
            if (result.size() >= 5) {
                break;
            }
        }
        return result;
    }

    private List<String> normalizeImportImageUrls(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("图片链接不能为空。");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : raw) {
            String imageUrl = trimToNull(value);
            if (imageUrl == null) {
                continue;
            }
            requireHttpImageUrl(imageUrl);
            if (seen.add(imageUrl)) {
                result.add(imageUrl);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("图片链接不能为空。");
        }
        return result;
    }

    private void requireHttpImageUrl(String imageUrl) {
        try {
            URI uri = new URI(imageUrl);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(uri.getHost())
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("图片链接只支持 HTTP 或 HTTPS。");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("图片链接格式不合法。", exception);
        }
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("精简卖点格式不合法。", exception);
        }
    }

    private String toJsonValue(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private List<String> parseHeroSellingPoints(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String identityKey(String pskuCode, String productIdentityKey) {
        return String.valueOf(pskuCode) + "::" + String.valueOf(productIdentityKey);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeText(String raw) {
        String value = trimToNull(raw);
        return value == null ? "-" : value;
    }

    private String format(LocalDateTime value) {
        return value == null ? null : API_TIME_FORMATTER.format(value);
    }

    private ProductImageProfileNotFoundException notFound() {
        return new ProductImageProfileNotFoundException("商品图资料不存在或无权访问。");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static final class ActiveHeroSkin {
        private final OperationsSkinRecord skin;
        private final List<OperationsSkinComponentRecord> components;
        private final LocalDateTime effectiveUpdatedAt;

        private ActiveHeroSkin(
                OperationsSkinRecord skin,
                List<OperationsSkinComponentRecord> components,
                LocalDateTime effectiveUpdatedAt
        ) {
            this.skin = skin;
            this.components = components;
            this.effectiveUpdatedAt = effectiveUpdatedAt;
        }

        private OperationsSkinRecord skin() {
            return skin;
        }

        private List<OperationsSkinComponentRecord> components() {
            return components;
        }

        private LocalDateTime effectiveUpdatedAt() {
            return effectiveUpdatedAt;
        }
    }
}
