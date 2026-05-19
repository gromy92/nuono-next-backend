package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiInputAttachment;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseStructuredAiService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AiCapabilityService aiCapabilityService;
    private final ObjectMapper objectMapper;
    private final String aiModel;
    private final String aiReasoningEffort;
    private final Integer aiMaxOutputTokens;
    private final Integer aiTimeoutSeconds;

    FileParseStructuredAiService(
            AiCapabilityService aiCapabilityService,
            ObjectMapper objectMapper
    ) {
        this(aiCapabilityService, objectMapper, "gpt-5.4-mini", "low", 1000, 90);
    }

    @Autowired
    public FileParseStructuredAiService(
            AiCapabilityService aiCapabilityService,
            ObjectMapper objectMapper,
            @Value("${nuono.file-management.parse.ai.model:gpt-5.4-mini}") String aiModel,
            @Value("${nuono.file-management.parse.ai.reasoning-effort:low}") String aiReasoningEffort,
            @Value("${nuono.file-management.parse.ai.max-output-tokens:1000}") Integer aiMaxOutputTokens,
            @Value("${nuono.file-management.parse.ai.timeout-seconds:90}") Integer aiTimeoutSeconds
    ) {
        this.aiCapabilityService = aiCapabilityService;
        this.objectMapper = objectMapper;
        this.aiModel = aiModel;
        this.aiReasoningEffort = aiReasoningEffort;
        this.aiMaxOutputTokens = aiMaxOutputTokens;
        this.aiTimeoutSeconds = aiTimeoutSeconds;
    }

    public FileParseStructuredAiResult parse(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            FileParseStandardVersionRow standardVersion,
            List<FileParseItemStandardRow> itemStandards,
            String extractedText,
            Long operatorUserId
    ) {
        return parse(
                task,
                targetPlan,
                standardVersion,
                itemStandards,
                new FileParseExtractionResult(List.of(), extractedText, false),
                operatorUserId
        );
    }

    public FileParseStructuredAiResult parse(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            FileParseStandardVersionRow standardVersion,
            List<FileParseItemStandardRow> itemStandards,
            FileParseExtractionResult extractionResult,
            Long operatorUserId
    ) {
        String extractedText = extractionResult == null ? null : extractionResult.getCombinedText();
        List<FileParseInputAttachment> attachments = extractionResult == null ? List.of() : extractionResult.getAttachments();
        if (!StringUtils.hasText(extractedText) && attachments.isEmpty()) {
            throw new FileParseAiParseException("EMPTY_EXTRACTED_INPUT", "没有可用于 AI 结构化解析的输入内容。");
        }
        if (itemStandards == null || itemStandards.isEmpty()) {
            throw new FileParseAiParseException("ITEM_STANDARD_MISSING", "目标输出方案缺少结果行标准。");
        }

        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode("file-management-parse");
        command.setOperationCode("structured-parse");
        command.setOperatorUserId(operatorUserId);
        command.setModel(aiModel);
        command.setReasoningEffort(aiReasoningEffort);
        command.setMaxOutputTokens(aiMaxOutputTokens);
        command.setTimeoutSeconds(aiTimeoutSeconds);
        command.setSchemaName("file_management_parse_result");
        command.setSchema(buildAiOutputSchema(itemStandards));
        command.setInstructions(buildInstructions(targetPlan, standardVersion, itemStandards));
        command.setPrompt(buildPrompt(task, targetPlan, itemStandards, extractedText, attachments));
        command.setInputAttachments(toAiAttachments(attachments));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", String.valueOf(task.getId()));
        metadata.put("targetPlanId", String.valueOf(targetPlan.getId()));
        metadata.put("standardVersionId", String.valueOf(standardVersion.getId()));
        metadata.put("attachmentCount", String.valueOf(attachments.size()));
        command.setMetadata(metadata);

        AiStructuredTextResult aiResult = aiCapabilityService.createStructuredText(command);
        if (!aiResult.isSuccess()) {
            throw new FileParseAiParseException(
                    StringUtils.hasText(aiResult.getErrorCode()) ? aiResult.getErrorCode() : aiResult.getStatus(),
                    StringUtils.hasText(aiResult.getErrorMessage()) ? aiResult.getErrorMessage() : "AI 结构化解析失败。"
            );
        }

        Map<String, Object> parsedJson = aiResult.getParsedJson();
        if (parsedJson == null) {
            throw new FileParseAiParseException("AI_OUTPUT_NOT_JSON", "AI 输出不是可落库的 JSON。");
        }

        List<FileParseStructuredItem> parsedItems = parseItems(parsedJson, itemStandards, extractedText);
        List<FileParseStructuredItem> supplementedItems = supplementCommissionTierItemsFromSource(
                parsedItems,
                targetPlan,
                itemStandards,
                extractedText
        );
        List<FileParseStructuredItem> items = deduplicateItems(supplementedItems);
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setParserModel(aiResult.getModel());
        result.setRawResultJson(writeJson(parsedJson));
        result.setSummaryJson(writeJson(summary(parsedJson, items)));
        result.setValidationSummaryJson(writeJson(validationSummary(items)));
        result.setItems(items);
        return result;
    }

    private List<FileParseStructuredItem> supplementCommissionTierItemsFromSource(
            List<FileParseStructuredItem> parsedItems,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        FileParseItemStandardRow commissionStandard = itemStandards.stream()
                .filter(item -> "commission_rule".equals(item.getItemType()))
                .findFirst()
                .orElse(null);
        if (commissionStandard == null || !isCommissionPlan(targetPlan)) {
            return parsedItems;
        }
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        if (sourceLines.isEmpty()) {
            return parsedItems;
        }
        List<FileParseStructuredItem> items = new ArrayList<>(parsedItems);
        Set<String> existingHashes = items.stream()
                .map(item -> item.getItemType() + "|" + item.getNaturalKeyHash())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (int index = 0; index < sourceLines.size(); index++) {
            SourceLine categoryLine = sourceLines.get(index);
            String categoryName = allRangeCategoryName(categoryLine.text);
            if (!StringUtils.hasText(categoryName)) {
                continue;
            }
            FallbackTierPair pair = adjacentTierPair(sourceLines, index);
            if (pair == null) {
                continue;
            }
            String parentCategoryName = fallbackParentCategory(sourceLines, index, categoryName);
            addFallbackCommissionTier(
                    items,
                    existingHashes,
                    commissionStandard,
                    sourceText,
                    parentCategoryName,
                    categoryName,
                    pair.lessOrEqual,
                    categoryLine.sourceRowId,
                    pair.lessOrEqualSourceRowIds
            );
            addFallbackCommissionTier(
                    items,
                    existingHashes,
                    commissionStandard,
                    sourceText,
                    parentCategoryName,
                    categoryName,
                    pair.greaterThan,
                    categoryLine.sourceRowId,
                    pair.greaterThanSourceRowIds
            );
        }
        return items;
    }

    private void addFallbackCommissionTier(
            List<FileParseStructuredItem> items,
            Set<String> existingHashes,
            FileParseItemStandardRow standard,
            String sourceText,
            String parentCategoryName,
            String categoryName,
            FallbackTier tier,
            Long categorySourceRowId,
            List<Long> tierSourceRowIds
    ) {
        if (tier == null) {
            return;
        }
        List<Long> sourceRowIds = new ArrayList<>();
        if (categorySourceRowId != null) {
            sourceRowIds.add(categorySourceRowId);
        }
        sourceRowIds.addAll(tierSourceRowIds);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", countryFromCurrency(tier.currency));
        payload.put("platform", null);
        payload.put("fulfillmentType", null);
        payload.put("parentCategoryName", parentCategoryName);
        payload.put("categoryName", categoryName);
        payload.put("categoryPath", StringUtils.hasText(parentCategoryName) ? parentCategoryName + " > " + categoryName : categoryName);
        payload.put("brandRestriction", "全部");
        payload.put("amountRangeLabel", tier.label);
        payload.put("amountMin", tier.min);
        payload.put("amountMinInclusive", tier.minInclusive);
        payload.put("amountMax", tier.max);
        payload.put("amountMaxInclusive", tier.maxInclusive);
        payload.put("amountCurrency", tier.currency);
        payload.put("commissionRate", tier.rate);
        payload.put("effectiveDate", null);
        payload = FileParseCommissionPayloadNormalizer.normalize("commission_rule", payload, sourceText, sourceRowIds);

        String naturalKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", payload);
        String hash = FileParseNaturalKeySupport.naturalKeyHash("commission_rule", naturalKey);
        String dedupeKey = "commission_rule|" + hash;
        if (existingHashes.contains(dedupeKey)) {
            return;
        }

        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("commission_rule");
        item.setNaturalKey(naturalKey);
        item.setNaturalKeyHash(hash);
        item.setConfidence("source_context");
        item.setNormalizedPayloadJson(writeJson(payload));
        item.setSourceRowIds(sourceRowIds.stream().distinct().collect(Collectors.toList()));
        item.setEvidenceJson(writeJson(Map.of(
                "source", "source_context_fallback",
                "sourceRowIds", item.getSourceRowIds()
        )));
        item.setSortNo(items.size() + 1);
        applyValidation(item, standard, payload);
        items.add(item);
        existingHashes.add(dedupeKey);
    }

    private boolean isCommissionPlan(FileParseTargetPlanRow targetPlan) {
        if (targetPlan == null) {
            return false;
        }
        String code = text(targetPlan.getCode());
        String documentType = text(targetPlan.getDocumentType());
        return (StringUtils.hasText(code) && code.toLowerCase(Locale.ROOT).startsWith("commission"))
                || "official_commission".equalsIgnoreCase(documentType);
    }

    private List<SourceLine> parseSourceLines(String sourceText) {
        if (!StringUtils.hasText(sourceText)) {
            return List.of();
        }
        List<SourceLine> sourceLines = new ArrayList<>();
        Long currentId = null;
        for (String line : sourceText.split("\\R")) {
            String markerId = markerSourceRowId(line);
            if (StringUtils.hasText(markerId)) {
                try {
                    currentId = Long.valueOf(markerId);
                } catch (NumberFormatException ignored) {
                    currentId = null;
                }
                continue;
            }
            if (currentId != null && StringUtils.hasText(line)) {
                sourceLines.add(new SourceLine(currentId, line.trim()));
                currentId = null;
            }
        }
        return sourceLines;
    }

    private String markerSourceRowId(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String marker = "SOURCE_ROW_ID=";
        int start = line.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = line.indexOf(';', valueStart);
        if (valueEnd < 0) {
            valueEnd = line.indexOf(']', valueStart);
        }
        if (valueEnd < 0) {
            valueEnd = line.length();
        }
        return line.substring(valueStart, valueEnd).trim();
    }

    private String allRangeCategoryName(String value) {
        String normalized = normalizeSpaces(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(.+?)\\s+All$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalized);
        if (!matcher.find()) {
            return "";
        }
        String categoryName = normalizeSpaces(matcher.group(1));
        return ignoredFallbackCategory(categoryName) ? "" : categoryName;
    }

    private FallbackTierPair adjacentTierPair(List<SourceLine> sourceLines, int categoryLineIndex) {
        if (categoryLineIndex + 4 >= sourceLines.size()) {
            return null;
        }
        SourceLine firstRateLine = sourceLines.get(categoryLineIndex + 1);
        SourceLine firstRangeLine = sourceLines.get(categoryLineIndex + 2);
        SourceLine secondRateLine = sourceLines.get(categoryLineIndex + 3);
        SourceLine secondRangeLine = sourceLines.get(categoryLineIndex + 4);
        FallbackTier lessOrEqual = parseLessOrEqualTier(firstRateLine.text + " " + firstRangeLine.text);
        FallbackTier greaterThan = parseGreaterThanTier(secondRateLine.text + " " + secondRangeLine.text);
        if (lessOrEqual == null || greaterThan == null || !lessOrEqual.currency.equals(greaterThan.currency)) {
            return null;
        }
        return new FallbackTierPair(
                lessOrEqual,
                List.of(firstRateLine.sourceRowId, firstRangeLine.sourceRowId),
                greaterThan,
                List.of(secondRateLine.sourceRowId, secondRangeLine.sourceRowId)
        );
    }

    private FallbackTier parseLessOrEqualTier(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*%.*?([0-9]+(?:\\.[0-9]+)?)\\s*(SAR|AED)\\s*(?:or\\s+)?less", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalizeSpaces(value));
        if (!matcher.find()) {
            return null;
        }
        String rate = normalizePercent(matcher.group(1));
        String max = matcher.group(2);
        String currency = matcher.group(3).toUpperCase(Locale.ROOT);
        return new FallbackTier(rate, "<= " + max + " " + currency, null, null, max, true, currency);
    }

    private FallbackTier parseGreaterThanTier(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*%.*?greater\\s+than\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(SAR|AED)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalizeSpaces(value));
        if (!matcher.find()) {
            return null;
        }
        String rate = normalizePercent(matcher.group(1));
        String min = matcher.group(2);
        String currency = matcher.group(3).toUpperCase(Locale.ROOT);
        return new FallbackTier(rate, "> " + min + " " + currency, min, false, null, null, currency);
    }

    private String fallbackParentCategory(List<SourceLine> sourceLines, int categoryLineIndex, String categoryName) {
        int scanStart = Math.max(0, categoryLineIndex - 8);
        for (int index = categoryLineIndex - 1; index >= scanStart; index--) {
            String candidate = normalizeSpaces(sourceLines.get(index).text);
            if (isKnownFallbackParentCategory(candidate) && !candidate.equalsIgnoreCase(categoryName)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean isKnownFallbackParentCategory(String value) {
        return Set.of(
                "Appliances",
                "Audio & Video",
                "Automotive",
                "Baby & Kids",
                "Beauty",
                "Books & Media",
                "Camera",
                "Cameras",
                "Electronics",
                "Fashion",
                "Fragrance",
                "Grocery",
                "Health & Beauty",
                "Home",
                "Mobiles",
                "Office Electronics",
                "Other Categories",
                "PC Store",
                "Stationery & Office Supplies",
                "Video Games",
                "Wearables"
        ).contains(normalizeSpaces(value));
    }

    private boolean ignoredFallbackCategory(String value) {
        String lower = normalizeSpaces(value).toLowerCase(Locale.ROOT);
        return !StringUtils.hasText(lower)
                || lower.contains("%")
                || Set.of("some exceptions", "to the exceptions", "sheet", "remarks").contains(lower);
    }

    private String countryFromCurrency(String currency) {
        if ("AED".equalsIgnoreCase(currency)) {
            return "UAE";
        }
        return "KSA";
    }

    private String normalizePercent(String value) {
        String normalized = normalizeSpaces(value);
        return normalized.endsWith("%") ? normalized : normalized + "%";
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public String getConfiguredModel() {
        return aiModel;
    }

    public String getConfiguredReasoningEffort() {
        return aiReasoningEffort;
    }

    public FileParseStructuredAiResult combineChunkResults(List<FileParseStructuredAiResult> chunkResults) {
        List<FileParseStructuredAiResult> safeChunkResults = chunkResults == null ? List.of() : chunkResults;
        List<FileParseStructuredItem> mergedItems = new ArrayList<>();
        List<String> rawChunks = new ArrayList<>();
        for (FileParseStructuredAiResult chunkResult : safeChunkResults) {
            if (chunkResult == null) {
                continue;
            }
            rawChunks.add(StringUtils.hasText(chunkResult.getRawResultJson()) ? chunkResult.getRawResultJson() : "{}");
            mergedItems.addAll(chunkResult.getItems());
        }
        List<FileParseStructuredItem> deduplicatedItems = deduplicateItems(mergedItems);
        int sortNo = 1;
        for (FileParseStructuredItem item : deduplicatedItems) {
            item.setSortNo(sortNo++);
        }

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setParserModel(safeChunkResults.stream()
                .filter(current -> current != null && StringUtils.hasText(current.getParserModel()))
                .map(FileParseStructuredAiResult::getParserModel)
                .findFirst()
                .orElse(aiModel));
        result.setRawResultJson("{\"chunks\":[" + String.join(",", rawChunks) + "]}");
        result.setSummaryJson(writeJson(summary(Map.of(
                "summary",
                Map.of(
                        "chunkCount", safeChunkResults.size(),
                        "source", "chunked_ai_structured_text"
                )
        ), deduplicatedItems)));
        result.setValidationSummaryJson(writeJson(validationSummary(deduplicatedItems)));
        result.setItems(deduplicatedItems);
        return result;
    }

    public FileParseStructuredAiResult stabilizeWithSourceContext(
            FileParseStructuredAiResult result,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        if (result == null || result.getItems() == null || result.getItems().isEmpty() || !isCommissionPlan(targetPlan)) {
            return result;
        }
        FileParseItemStandardRow commissionStandard = itemStandards == null ? null : itemStandards.stream()
                .filter(item -> "commission_rule".equals(item.getItemType()))
                .findFirst()
                .orElse(null);
        if (commissionStandard == null) {
            return result;
        }

        List<FileParseStructuredItem> stabilizedItems = new ArrayList<>();
        for (FileParseStructuredItem item : result.getItems()) {
            if (item == null || !"commission_rule".equals(item.getItemType())) {
                stabilizedItems.add(item);
                continue;
            }
            List<Long> sourceRowIds = mergeSourceRowIds(
                    item.getSourceRowIds(),
                    resolveSourceRowIds(readMap(item.getEvidenceJson()).get("sourceRowIds"))
            );
            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    item.getItemType(),
                    readMap(item.getNormalizedPayloadJson()),
                    sourceText,
                    sourceRowIds
            );
            item.setSourceRowIds(sourceRowIds);
            String naturalKey = FileParseNaturalKeySupport.buildNaturalKey(item.getItemType(), payload);
            if (StringUtils.hasText(naturalKey)) {
                item.setNaturalKey(naturalKey);
                item.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash(item.getItemType(), naturalKey));
            }
            item.setNormalizedPayloadJson(writeJson(payload));
            item.setValidationStatus("pass");
            item.setReviewStatus("pending");
            item.setValidationErrorJson(null);
            applyValidation(item, commissionStandard, payload);
            stabilizedItems.add(item);
        }

        List<FileParseStructuredItem> filteredItems = removeSupersededMissingCommissionRows(stabilizedItems);
        List<FileParseStructuredItem> sourceDerivedItems = deriveCommissionItemsFromSource(commissionStandard, sourceText);
        List<FileParseStructuredItem> canonicalItems = useSourceDerivedCommissionItems(filteredItems, sourceDerivedItems)
                ? sourceDerivedItems
                : mergeCommissionSourceDerivedItems(filteredItems, sourceDerivedItems);
        canonicalItems = removeRowsSupersededBySourceDerivedDate(canonicalItems, sourceDerivedItems);
        List<FileParseStructuredItem> deduplicatedItems = deduplicateItems(canonicalItems);
        refreshValidationStatus(deduplicatedItems, commissionStandard);
        int sortNo = 1;
        for (FileParseStructuredItem item : deduplicatedItems) {
            item.setSortNo(sortNo++);
        }
        result.setSummaryJson(writeJson(Map.of(
                "itemCount", deduplicatedItems.size(),
                "hardErrorCount", deduplicatedItems.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count(),
                "source", "source_context_stabilized"
        )));
        result.setValidationSummaryJson(writeJson(validationSummary(deduplicatedItems)));
        result.setItems(deduplicatedItems);
        return result;
    }

    private List<FileParseStructuredItem> removeRowsSupersededBySourceDerivedDate(
            List<FileParseStructuredItem> items,
            List<FileParseStructuredItem> sourceDerivedItems
    ) {
        if (items == null || items.isEmpty() || sourceDerivedItems == null || sourceDerivedItems.isEmpty()) {
            return items;
        }
        Set<String> sourceDateKeys = sourceDerivedItems.stream()
                .map(item -> commissionIdentityWithoutEffectiveDate(readMap(item.getNormalizedPayloadJson())))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> sourceLeafKeys = sourceDerivedItems.stream()
                .map(item -> commissionLeafIdentity(readMap(item.getNormalizedPayloadJson())))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sourceDateKeys.isEmpty() && sourceLeafKeys.isEmpty()) {
            return items;
        }
        return items.stream()
                .filter(item -> {
                    if (item == null || !"commission_rule".equals(item.getItemType())) {
                        return true;
                    }
                    if ("source_rate_line".equals(item.getConfidence())) {
                        return true;
                    }
                    Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
                    String dateKey = commissionIdentityWithoutEffectiveDate(payload);
                    String leafKey = commissionLeafIdentity(payload);
                    boolean leafOnlyPath = !StringUtils.hasText(text(payload.get("parentCategoryName")))
                            || normalizeSpaces(text(payload.get("categoryPath"))).equalsIgnoreCase(normalizeSpaces(text(payload.get("categoryName"))));
                    return !sourceDateKeys.contains(dateKey) && !(leafOnlyPath && sourceLeafKeys.contains(leafKey));
                })
                .collect(Collectors.toList());
    }

    private String commissionIdentityWithoutEffectiveDate(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        return String.join("|",
                text(payload.get("country")),
                text(payload.get("categoryPath")),
                text(payload.get("brandRestriction")),
                text(payload.get("amountRangeLabel")),
                text(payload.get("amountMin")),
                text(payload.get("amountMinInclusive")),
                text(payload.get("amountMax")),
                text(payload.get("amountMaxInclusive")),
                text(payload.get("amountCurrency"))
        );
    }

    private String commissionLeafIdentity(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        return String.join("|",
                text(payload.get("country")),
                text(payload.get("categoryName")),
                text(payload.get("brandRestriction")),
                text(payload.get("amountRangeLabel")),
                text(payload.get("amountMin")),
                text(payload.get("amountMinInclusive")),
                text(payload.get("amountMax")),
                text(payload.get("amountMaxInclusive")),
                text(payload.get("amountCurrency")),
                text(payload.get("effectiveDate"))
        );
    }

    private void refreshValidationStatus(List<FileParseStructuredItem> items, FileParseItemStandardRow standard) {
        if (items == null || standard == null) {
            return;
        }
        for (FileParseStructuredItem item : items) {
            if (item == null || !"commission_rule".equals(item.getItemType())) {
                continue;
            }
            item.setValidationStatus("pass");
            item.setReviewStatus("pending");
            item.setValidationErrorJson(null);
            applyValidation(item, standard, readMap(item.getNormalizedPayloadJson()));
        }
    }

    private boolean useSourceDerivedCommissionItems(
            List<FileParseStructuredItem> aiItems,
            List<FileParseStructuredItem> sourceDerivedItems
    ) {
        if (sourceDerivedItems == null || sourceDerivedItems.size() < 20) {
            return false;
        }
        int aiSize = aiItems == null ? 0 : aiItems.size();
        return aiSize == 0 || sourceDerivedItems.size() >= Math.max(20, (int) Math.ceil(aiSize * 0.98));
    }

    private List<FileParseStructuredItem> mergeCommissionSourceDerivedItems(
            List<FileParseStructuredItem> aiItems,
            List<FileParseStructuredItem> sourceDerivedItems
    ) {
        List<FileParseStructuredItem> merged = new ArrayList<>();
        if (aiItems != null) {
            merged.addAll(aiItems);
        }
        if (sourceDerivedItems != null) {
            merged.addAll(sourceDerivedItems);
        }
        return merged;
    }

    private List<FileParseStructuredItem> deriveCommissionItemsFromSource(
            FileParseItemStandardRow standard,
            String sourceText
    ) {
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        if (standard == null || sourceLines.isEmpty()) {
            return List.of();
        }
        List<FileParseStructuredItem> items = new ArrayList<>();
        Set<String> existingHashes = new LinkedHashSet<>();
        for (int index = 0; index < sourceLines.size(); index++) {
            SourceLine sourceLine = sourceLines.get(index);
            if (!sourceRateLineLooksLikeCommissionRule(sourceLine.text)) {
                continue;
            }
            String rate = firstPercentNumber(sourceLine.text);
            if (!StringUtils.hasText(rate)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("country", inferCommissionCountry(sourceText, sourceLine.text));
            payload.put("platform", null);
            payload.put("fulfillmentType", null);
            payload.put("parentCategoryName", null);
            payload.put("categoryName", null);
            payload.put("categoryPath", null);
            payload.put("brandRestriction", sourceBrandRestriction(sourceLine.text));
            payload.put("amountRangeLabel", null);
            payload.put("amountMin", null);
            payload.put("amountMinInclusive", null);
            payload.put("amountMax", null);
            payload.put("amountMaxInclusive", null);
            payload.put("amountCurrency", null);
            payload.put("commissionRate", normalizePercent(rate));
            payload.put("effectiveDate", null);

            List<Long> sourceRowIds = sourceContextRowIdsForRate(sourceLines, index);
            payload = FileParseCommissionPayloadNormalizer.normalize("commission_rule", payload, sourceText, sourceRowIds);
            if (!sourceDerivedCommissionPayloadLooksValid(payload)) {
                continue;
            }
            String naturalKey = FileParseNaturalKeySupport.buildNaturalKey("commission_rule", payload);
            String hash = FileParseNaturalKeySupport.naturalKeyHash("commission_rule", naturalKey);
            if (!existingHashes.add("commission_rule|" + hash)) {
                continue;
            }

            FileParseStructuredItem item = new FileParseStructuredItem();
            item.setItemType("commission_rule");
            item.setNaturalKey(naturalKey);
            item.setNaturalKeyHash(hash);
            item.setConfidence("source_rate_line");
            item.setNormalizedPayloadJson(writeJson(payload));
            item.setSourceRowIds(sourceRowIds);
            item.setEvidenceJson(writeJson(Map.of(
                    "source", "source_rate_line",
                    "sourceRowIds", sourceRowIds
            )));
            item.setSortNo(items.size() + 1);
            item.setValidationStatus("pass");
            item.setReviewStatus("pending");
            item.setValidationErrorJson(null);
            applyValidation(item, standard, payload);
            items.add(item);
        }
        return items;
    }

    private boolean sourceRateLineLooksLikeCommissionRule(String value) {
        String normalized = normalizeSpaces(value);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) || countPercentageRates(normalized) != 1) {
            return false;
        }
        return !lower.startsWith("http://")
                && !lower.startsWith("https://")
                && !lower.contains("minimum referral fee")
                && !lower.contains("exclusive of vat")
                && !lower.contains("referral fees is applicable");
    }

    private String sourceBrandRestriction(String value) {
        String lower = normalizeSpaces(value).toLowerCase(Locale.ROOT);
        if (lower.contains("generic")) {
            return "Generic brand";
        }
        if (lower.contains("other brand")) {
            return "All other brands";
        }
        if (lower.contains("top brand")) {
            return "Top brands";
        }
        return "全部";
    }

    private String inferCommissionCountry(String sourceText, String sourceLineText) {
        String combined = (StringUtils.hasText(sourceLineText) ? sourceLineText : "") + "\n" + (StringUtils.hasText(sourceText) ? sourceText : "");
        String upper = combined.toUpperCase(Locale.ROOT);
        if (upper.contains("AED") || upper.contains("UAE")) {
            return "UAE";
        }
        return "KSA";
    }

    private List<Long> sourceContextRowIdsForRate(List<SourceLine> sourceLines, int rateIndex) {
        int start = rateIndex;
        if (!isInlineSourceRateLine(sourceLines.get(rateIndex).text)) {
            int scanStart = Math.max(0, rateIndex - 12);
            for (int index = rateIndex - 1; index >= scanStart; index--) {
                if (countPercentageRates(sourceLines.get(index).text) > 0) {
                    break;
                }
                start = index;
            }
        }
        int end = Math.min(sourceLines.size() - 1, rateIndex + 4);
        List<Long> ids = new ArrayList<>();
        for (int index = start; index <= end; index++) {
            SourceLine line = sourceLines.get(index);
            if (line.sourceRowId != null) {
                ids.add(line.sourceRowId);
            }
            if (index > rateIndex && countPercentageRates(line.text) > 0) {
                break;
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private boolean isInlineSourceRateLine(String value) {
        String normalized = normalizeSpaces(value);
        if (!StringUtils.hasText(normalized) || normalized.startsWith("-")) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(.+?)\\s+[0-9]+(?:\\.[0-9]+)?\\s*%")
                .matcher(normalized);
        return matcher.find() && StringUtils.hasText(matcher.group(1));
    }

    private boolean sourceDerivedCommissionPayloadLooksValid(Map<String, Object> payload) {
        String categoryName = text(payload.get("categoryName"));
        String categoryPath = text(payload.get("categoryPath"));
        String commissionRate = text(payload.get("commissionRate"));
        if (!StringUtils.hasText(categoryName) || !StringUtils.hasText(categoryPath) || !StringUtils.hasText(commissionRate)) {
            return false;
        }
        String lowerCategory = normalizeSpaces(categoryName).toLowerCase(Locale.ROOT);
        return !lowerCategory.contains("sales price")
                && !lowerCategory.contains("sale price")
                && !lowerCategory.contains("kindly refer")
                && !lowerCategory.contains("exceptions");
    }

    private List<FileParseStructuredItem> removeSupersededMissingCommissionRows(List<FileParseStructuredItem> items) {
        Set<String> tieredCategoryKeys = new LinkedHashSet<>();
        for (FileParseStructuredItem item : items) {
            if (item == null || !"commission_rule".equals(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
            if (StringUtils.hasText(text(payload.get("commissionRate"))) && hasSpecificAmountRange(payload)) {
                String key = commissionCategoryKey(payload);
                if (StringUtils.hasText(key)) {
                    tieredCategoryKeys.add(key);
                }
            }
        }
        if (tieredCategoryKeys.isEmpty()) {
            return items;
        }
        return items.stream()
                .filter(item -> {
                    if (item == null || !"commission_rule".equals(item.getItemType())) {
                        return true;
                    }
                    Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
                    return StringUtils.hasText(text(payload.get("commissionRate")))
                            || !isAllAmountRange(payload)
                            || !tieredCategoryKeys.contains(commissionCategoryKey(payload));
                })
                .collect(Collectors.toList());
    }

    private boolean hasSpecificAmountRange(Map<String, Object> payload) {
        return StringUtils.hasText(text(payload.get("amountMin")))
                || StringUtils.hasText(text(payload.get("amountMax")))
                || !isAllAmountRange(payload);
    }

    private boolean isAllAmountRange(Map<String, Object> payload) {
        String rangeLabel = text(payload.get("amountRangeLabel"));
        return !StringUtils.hasText(text(payload.get("amountMin")))
                && !StringUtils.hasText(text(payload.get("amountMax")))
                && (!StringUtils.hasText(rangeLabel) || "全部".equals(rangeLabel) || "all".equalsIgnoreCase(rangeLabel));
    }

    private String commissionCategoryKey(Map<String, Object> payload) {
        return String.join("|",
                text(payload.get("country")).toUpperCase(Locale.ROOT),
                text(payload.get("categoryPath")),
                text(payload.get("parentCategoryName")),
                text(payload.get("categoryName")),
                text(payload.get("brandRestriction")),
                text(payload.get("amountCurrency")).toUpperCase(Locale.ROOT),
                text(payload.get("effectiveDate"))
        );
    }

    private Map<String, Object> buildAiOutputSchema(List<FileParseItemStandardRow> itemStandards) {
        Map<String, Object> payloadProperties = new LinkedHashMap<>();
        for (FileParseItemStandardRow standard : itemStandards) {
            Map<String, Object> fieldSchema = readMap(standard.getFieldSchemaJson());
            for (String fieldKey : fieldSchema.keySet()) {
                payloadProperties.putIfAbsent(fieldKey, Map.of("type", List.of("string", "number", "boolean", "null")));
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "object");
        payload.put("required", new ArrayList<>(payloadProperties.keySet()));
        payload.put("additionalProperties", false);
        payload.put("properties", payloadProperties);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("required", List.of("itemType", "naturalKey", "confidence", "sourceRowIds", "payload"));
        item.put("additionalProperties", false);
        item.put("properties", Map.of(
                "itemType", Map.of(
                        "type", "string",
                        "enum", itemStandards.stream().map(FileParseItemStandardRow::getItemType).collect(Collectors.toList())
                ),
                "naturalKey", Map.of("type", List.of("string", "null")),
                "confidence", Map.of("type", List.of("string", "null")),
                "sourceRowIds", Map.of(
                        "type", "array",
                        "items", Map.of("type", List.of("integer", "string"))
                ),
                "payload", payload
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("items"));
        schema.put("additionalProperties", false);
        schema.put("properties", Map.of(
                "items", Map.of("type", "array", "items", item)
        ));
        return schema;
    }

    private String buildInstructions(
            FileParseTargetPlanRow targetPlan,
            FileParseStandardVersionRow standardVersion,
            List<FileParseItemStandardRow> itemStandards
    ) {
        String itemText = itemStandards.stream()
                .map(item -> "- " + item.getItemType() + "：" + item.getItemLabel() + "，字段标准 " + item.getFieldSchemaJson())
                .collect(Collectors.joining("\n"));
        return "你是文件管理解析助手。只输出符合 schema 的 JSON，不要输出解释性文本。"
                + "\n目标输出方案：" + targetPlan.getLabel()
                + "\n文档标准版本：" + standardVersion.getStandardVersion()
                + "\n结果行标准：\n" + itemText
                + "\n每条 items 需要包含 itemType、payload，可包含 naturalKey、confidence。"
                + "\n输入文本会带 SOURCE_ROW_ID 标记；请在 sourceRowIds 中返回该结果实际依据的源内容行 ID。"
                + "\npayload 必须尽量使用字段标准中的英文字段名。无法确定的字段填 null，不要编造。"
                + buildCommissionTierGuidance(itemStandards);
    }

    private String buildCommissionTierGuidance(List<FileParseItemStandardRow> itemStandards) {
        boolean hasCommissionRule = itemStandards.stream()
                .anyMatch(item -> "commission_rule".equals(item.getItemType()) && item.getFieldSchemaJson().contains("amountRangeLabel"));
        if (!hasCommissionRule) {
            return "";
        }
        return "\n佣金规则特殊要求："
                + "\n- 佣金目标只解析 1. Referral Fees / Referral Fees as a % of Sale price 这一节；不要解析 FBN Outbound fees、Monthly Storage Fees、Inventory Removal Fee、Value Added Services、FAQ 等其他菜单或费用表。"
                + "\n- parentCategoryName 放原文表格中的一级/父级类目，例如 Mobiles、PC Store、Video Games；不要把父级类目写进 platform。"
                + "\n- categoryName 只放当前佣金行的末级类目名称，不要把一级类目、金额区间、币种或费率拼进类目。"
                + "\n- categoryPath 放可区分同名子类目的完整路径；至少用 parentCategoryName > categoryName，例如 Mobiles > Accessories。"
                + "\n- brandRestriction 表示品牌限制；无品牌限制填 全部。"
                + "\n- commissionRate 只放单一费率，例如 15%，不能放 up to、above、then、SAR/AED 等区间描述。"
                + "\n- 如果原文是阶梯费率，必须拆成多条 commission_rule，每条只表示一个金额区间。"
                + "\n- 如果同一类目同一金额区间按品牌给不同费率，也必须拆成多条 commission_rule，并分别填写 brandRestriction，例如 Generic brand、All other brands。"
                + "\n- 金额区间写入 amountRangeLabel、amountMin、amountMinInclusive、amountMax、amountMaxInclusive、amountCurrency。"
                + "\n- 示例：Colour Cosmetics / All / 15% for Generic brand, 10% for all other brands 应输出两条："
                + " 第一条 categoryName=Colour Cosmetics, amountRangeLabel=全部, brandRestriction=Generic brand, commissionRate=15%；"
                + " 第二条 categoryName=Colour Cosmetics, amountRangeLabel=全部, brandRestriction=All other brands, commissionRate=10%。"
                + "\n- 示例：Mobiles 小节下的 Accessories 与 Video Games 小节下的 Other Accessories 应用 parentCategoryName/categoryPath 区分，不能只用 Accessories 作为业务身份。"
                + "\n- 示例：15% up to 5000 SAR, then 5% above 5000 SAR 应输出两条："
                + " 第一条 amountRangeLabel=<= 5000 SAR, amountMin=null, amountMinInclusive=null, amountMax=5000, amountMaxInclusive=true, amountCurrency=SAR, commissionRate=15%；"
                + " 第二条 amountRangeLabel=> 5000 SAR, amountMin=5000, amountMinInclusive=false, amountMax=null, amountMaxInclusive=null, amountCurrency=SAR, commissionRate=5%。";
    }

    private String buildPrompt(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String extractedText,
            List<FileParseInputAttachment> attachments
    ) {
        String attachmentText = attachments.isEmpty()
                ? "无"
                : attachments.stream()
                .map(attachment -> "- " + attachment.getFileName() + "（" + attachment.getContentType() + "）")
                .collect(Collectors.joining("\n"));
        return "请从以下文件文本中抽取要落库的数据。"
                + "\n文档名称：" + task.getDocumentTitle()
                + "\n目标输出方案：" + targetPlan.getLabel()
                + "\n允许的 itemType：" + itemStandards.stream().map(FileParseItemStandardRow::getItemType).collect(Collectors.joining(", "))
                + "\nsourceRowIds 要填写本条结果引用的 SOURCE_ROW_ID。"
                + "\n附件：\n" + attachmentText
                + "\n\n输入文本：\n" + (StringUtils.hasText(extractedText) ? extractedText : "无文本输入，请直接读取附件内容。");
    }

    private List<AiInputAttachment> toAiAttachments(List<FileParseInputAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(attachment -> new AiInputAttachment(
                        attachment.getFileName(),
                        attachment.getContentType(),
                        attachment.getContent()
                ))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<FileParseStructuredItem> parseItems(
            Map<String, Object> parsedJson,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        Object itemsValue = parsedJson.get("items");
        if (!(itemsValue instanceof List)) {
            throw new FileParseAiParseException("AI_OUTPUT_ITEMS_MISSING", "AI 输出缺少 items 数组。");
        }
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, item -> item, (left, right) -> left));
        List<FileParseStructuredItem> items = new ArrayList<>();
        int index = 1;
        for (Object itemValue : (List<?>) itemsValue) {
            if (!(itemValue instanceof Map)) {
                continue;
            }
            Map<String, Object> itemMap = new LinkedHashMap<>((Map<String, Object>) itemValue);
            String itemType = text(itemMap.get("itemType"));
            if (!StringUtils.hasText(itemType) && itemStandards.size() == 1) {
                itemType = itemStandards.get(0).getItemType();
            }
            FileParseItemStandardRow standard = standardsByType.get(itemType);
            if (standard == null) {
                throw new FileParseAiParseException("AI_OUTPUT_ITEM_TYPE_INVALID", "AI 输出包含未定义的 itemType：" + itemType);
            }
            List<Long> sourceRowIds = resolveSourceRowIds(itemMap.get("sourceRowIds"));
            Map<String, Object> payload = FileParseCommissionPayloadNormalizer.normalize(
                    itemType,
                    resolvePayload(itemMap),
                    sourceText,
                    sourceRowIds
            );
            String naturalKey = FileParseNaturalKeySupport.buildNaturalKey(itemType, payload);
            if (!StringUtils.hasText(naturalKey)) {
                naturalKey = text(itemMap.get("naturalKey"));
            }
            if (!StringUtils.hasText(naturalKey)) {
                naturalKey = buildNaturalKey(standard, payload, index);
            }
            FileParseStructuredItem structuredItem = new FileParseStructuredItem();
            structuredItem.setItemType(itemType);
            structuredItem.setNaturalKey(naturalKey);
            structuredItem.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash(itemType, naturalKey));
            structuredItem.setConfidence(text(itemMap.get("confidence")));
            structuredItem.setNormalizedPayloadJson(writeJson(payload));
            structuredItem.setSourceRowIds(sourceRowIds);
            structuredItem.setEvidenceJson(writeJson(Map.of(
                    "source", "ai_structured_text",
                    "sourceRowIds", structuredItem.getSourceRowIds()
            )));
            applyValidation(structuredItem, standard, payload);
            structuredItem.setSortNo(index);
            items.add(structuredItem);
            index++;
        }
        return items;
    }

    private List<FileParseStructuredItem> deduplicateItems(List<FileParseStructuredItem> items) {
        Map<String, FileParseStructuredItem> dedupedByKey = new LinkedHashMap<>();
        Map<String, Integer> duplicateCounts = new LinkedHashMap<>();
        for (FileParseStructuredItem item : items) {
            String key = item.getItemType() + "|" + item.getNaturalKeyHash();
            FileParseStructuredItem existing = dedupedByKey.get(key);
            if (existing == null) {
                dedupedByKey.put(key, item);
                continue;
            }
            duplicateCounts.put(key, duplicateCounts.getOrDefault(key, 1) + 1);
            existing.setSourceRowIds(mergeSourceRowIds(existing.getSourceRowIds(), item.getSourceRowIds()));
            if (!samePayload(existing.getItemType(), existing.getNormalizedPayloadJson(), item.getNormalizedPayloadJson())) {
                markDuplicateConflict(existing, duplicateCounts.get(key));
            }
        }
        return new ArrayList<>(dedupedByKey.values());
    }

    private List<Long> mergeSourceRowIds(List<Long> left, List<Long> right) {
        Set<Long> merged = new LinkedHashSet<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return new ArrayList<>(merged);
    }

    private List<Long> resolveSourceRowIds(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : (List<?>) value) {
            Long id = toLong(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean samePayload(String itemType, String leftJson, String rightJson) {
        Map<String, Object> left = readMap(leftJson);
        Map<String, Object> right = readMap(rightJson);
        if ("commission_rule".equals(itemType)) {
            left.remove("platform");
            right.remove("platform");
        }
        return left.equals(right);
    }

    private void markDuplicateConflict(FileParseStructuredItem item, int duplicateCount) {
        Map<String, Object> errors = readMap(item.getValidationErrorJson());
        errors.put("message", "AI 解析到多条业务主键相同但字段值不同的结果，请编辑确认后再发布。");
        errors.put("duplicateItemCount", duplicateCount);
        item.setValidationStatus("hard_error");
        item.setReviewStatus("needs_fix");
        item.setValidationErrorJson(writeJson(errors));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvePayload(Map<String, Object> itemMap) {
        Object payloadValue = itemMap.get("payload");
        if (payloadValue instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) payloadValue);
        }
        Map<String, Object> payload = new LinkedHashMap<>(itemMap);
        payload.remove("itemType");
        payload.remove("naturalKey");
        payload.remove("confidence");
        payload.remove("evidence");
        return payload;
    }

    private String buildNaturalKey(FileParseItemStandardRow standard, Map<String, Object> payload, int index) {
        Map<String, Object> naturalKeyConfig = readMap(standard.getNaturalKeyJson());
        Object fieldsValue = naturalKeyConfig.get("fields");
        if (fieldsValue instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object field : (List<?>) fieldsValue) {
                if (field instanceof String) {
                    String value = text(payload.get(field));
                    if (StringUtils.hasText(value)) {
                        parts.add(value);
                    }
                }
            }
            if (!parts.isEmpty()) {
                return String.join(" + ", parts);
            }
        }
        return standard.getItemType() + "-" + index;
    }

    private void applyValidation(
            FileParseStructuredItem structuredItem,
            FileParseItemStandardRow standard,
            Map<String, Object> payload
    ) {
        Map<String, Object> validationRule = readMap(standard.getValidationRuleJson());
        Object requiredValue = validationRule.get("required");
        List<String> missing = new ArrayList<>();
        Map<String, Object> errors = new LinkedHashMap<>();
        if (requiredValue instanceof List) {
            for (Object field : (List<?>) requiredValue) {
                if (field instanceof String && !StringUtils.hasText(text(payload.get(field)))) {
                    missing.add((String) field);
                }
            }
        }
        if (!missing.isEmpty()) {
            errors.put("missingRequiredFields", missing);
        }
        applyCommissionValidation(standard, payload, errors);
        if (!errors.isEmpty()) {
            structuredItem.setValidationStatus("hard_error");
            structuredItem.setReviewStatus("needs_fix");
            structuredItem.setValidationErrorJson(writeJson(errors));
        }
    }

    private void applyCommissionValidation(
            FileParseItemStandardRow standard,
            Map<String, Object> payload,
            Map<String, Object> errors
    ) {
        if (!"commission_rule".equals(standard.getItemType())) {
            return;
        }
        String commissionRate = text(payload.get("commissionRate"));
        if (StringUtils.hasText(commissionRate)) {
            String lowerRate = commissionRate.toLowerCase();
            if (lowerRate.contains("up to")
                    || lowerRate.contains("above")
                    || lowerRate.contains("then")
                    || lowerRate.contains("sar")
                    || lowerRate.contains("aed")
                    || lowerRate.contains("<")
                    || lowerRate.contains(">")
                    || countPercentageRates(commissionRate) > 1) {
                errors.put("message", "佣金率必须只填写单一费率；阶梯金额区间需要拆成多条并写入金额区间字段；品牌限制费率需要拆成多条并写入品牌限制字段。");
            }
        }

        boolean hasRange = StringUtils.hasText(text(payload.get("amountRangeLabel")))
                || StringUtils.hasText(text(payload.get("amountMin")))
                || StringUtils.hasText(text(payload.get("amountMax")));
        if (hasRange && !StringUtils.hasText(text(payload.get("amountCurrency")))) {
            errors.put("amountCurrency", "金额区间存在时必须填写币种。");
        }
    }

    private Map<String, Object> summary(Map<String, Object> parsedJson, List<FileParseStructuredItem> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Object aiSummary = parsedJson.get("summary");
        if (aiSummary instanceof Map) {
            summary.put("aiSummary", aiSummary);
        }
        summary.put("itemCount", items.size());
        summary.put("hardErrorCount", items.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count());
        return summary;
    }

    private Map<String, Object> validationSummary(List<FileParseStructuredItem> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("pass", items.stream().filter(item -> "pass".equals(item.getValidationStatus())).count());
        summary.put("hardError", items.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count());
        return summary;
    }

    private int countPercentageRates(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9]+(?:\\.[0-9]+)?\\s*%")
                .matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String firstPercentNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*%")
                .matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            throw new FileParseAiParseException("STANDARD_JSON_INVALID", "结果标准 JSON 解析失败。");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new FileParseAiParseException("RESULT_JSON_WRITE_FAILED", "解析结果 JSON 序列化失败。");
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static final class SourceLine {
        private final Long sourceRowId;
        private final String text;

        private SourceLine(Long sourceRowId, String text) {
            this.sourceRowId = sourceRowId;
            this.text = text;
        }
    }

    private static final class FallbackTierPair {
        private final FallbackTier lessOrEqual;
        private final List<Long> lessOrEqualSourceRowIds;
        private final FallbackTier greaterThan;
        private final List<Long> greaterThanSourceRowIds;

        private FallbackTierPair(
                FallbackTier lessOrEqual,
                List<Long> lessOrEqualSourceRowIds,
                FallbackTier greaterThan,
                List<Long> greaterThanSourceRowIds
        ) {
            this.lessOrEqual = lessOrEqual;
            this.lessOrEqualSourceRowIds = lessOrEqualSourceRowIds;
            this.greaterThan = greaterThan;
            this.greaterThanSourceRowIds = greaterThanSourceRowIds;
        }
    }

    private static final class FallbackTier {
        private final String rate;
        private final String label;
        private final String min;
        private final Boolean minInclusive;
        private final String max;
        private final Boolean maxInclusive;
        private final String currency;

        private FallbackTier(
                String rate,
                String label,
                String min,
                Boolean minInclusive,
                String max,
                Boolean maxInclusive,
                String currency
        ) {
            this.rate = rate;
            this.label = label;
            this.min = min;
            this.minInclusive = minInclusive;
            this.max = max;
            this.maxInclusive = maxInclusive;
            this.currency = currency;
        }
    }
}
