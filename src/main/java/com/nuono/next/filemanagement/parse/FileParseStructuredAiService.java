package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiInputAttachment;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import java.math.BigDecimal;
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
            @Value("${nuono.file-management.parse.ai.timeout-seconds:180}") Integer aiTimeoutSeconds
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

        List<FileParseStructuredItem> parsedItems = parseItems(parsedJson, targetPlan, itemStandards, extractedText);
        List<FileParseStructuredItem> supplementedItems = supplementCommissionTierItemsFromSource(
                parsedItems,
                targetPlan,
                itemStandards,
                extractedText
        );
        List<FileParseStructuredItem> logisticsStabilizedItems = stabilizeLogisticsItems(
                supplementedItems,
                targetPlan,
                itemStandards,
                extractedText
        );
        List<FileParseStructuredItem> outboundFeeStabilizedItems = stabilizeOfficialOutboundFeeItems(
                logisticsStabilizedItems,
                targetPlan,
                itemStandards,
                extractedText
        );
        List<FileParseStructuredItem> items = deduplicateItems(outboundFeeStabilizedItems);
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

    private boolean isOutboundFeePlan(FileParseTargetPlanRow targetPlan) {
        if (targetPlan == null) {
            return false;
        }
        String code = text(targetPlan.getCode());
        String documentType = text(targetPlan.getDocumentType());
        return (StringUtils.hasText(code) && code.toLowerCase(Locale.ROOT).startsWith("outbound_fee"))
                || "official_outbound_fee".equalsIgnoreCase(documentType);
    }

    private boolean isStructuredOutboundFeePlan(
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards
    ) {
        return isOutboundFeePlan(targetPlan)
                && itemStandards != null
                && itemStandards.stream()
                .filter(item -> item != null)
                .anyMatch(item -> FileParseOfficialOutboundFeeStandard.structuredItemTypeNames().contains(item.getItemType()));
    }

    private String expectedOutboundCountry(FileParseTargetPlanRow targetPlan) {
        String scope = outboundScopeText(targetPlan);
        if (scope.contains("KSA") || scope.contains("SAUDI")) {
            return "KSA";
        }
        if (scope.contains("UAE") || scope.contains("UNITED ARAB")) {
            return "UAE";
        }
        if (scope.contains("EGY") || scope.contains("EGYPT")) {
            return "EGY";
        }
        return "";
    }

    private String expectedOutboundCurrency(FileParseTargetPlanRow targetPlan) {
        String scope = outboundScopeText(targetPlan);
        if (scope.contains("KSA") || scope.contains("SAUDI")) {
            return "SAR";
        }
        if (scope.contains("UAE") || scope.contains("UNITED ARAB")) {
            return "AED";
        }
        if (scope.contains("EGY") || scope.contains("EGYPT")) {
            return "EGP";
        }
        return "";
    }

    private String outboundScopeText(FileParseTargetPlanRow targetPlan) {
        if (targetPlan == null) {
            return "";
        }
        return (text(targetPlan.getCode()) + " " + text(targetPlan.getLabel()))
                .trim()
                .toUpperCase(Locale.ROOT);
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
        if (result == null) {
            return result;
        }
        if (isStructuredOutboundFeePlan(targetPlan, itemStandards)) {
            return stabilizeOfficialOutboundFeeResult(result, targetPlan, itemStandards, sourceText);
        }
        if (result.getItems() == null || result.getItems().isEmpty()) {
            return result;
        }
        if (isLogisticsPlan(targetPlan, itemStandards)) {
            return stabilizeLogisticsResult(result, targetPlan, itemStandards, sourceText);
        }
        if (!isCommissionPlan(targetPlan)) {
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

    private FileParseStructuredAiResult stabilizeLogisticsResult(
            FileParseStructuredAiResult result,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        List<FileParseStructuredItem> stabilizedItems = stabilizeLogisticsItems(
                result.getItems(),
                targetPlan,
                itemStandards,
                sourceText
        );
        List<FileParseStructuredItem> deduplicatedItems = deduplicateItems(stabilizedItems);
        int sortNo = 1;
        for (FileParseStructuredItem item : deduplicatedItems) {
            item.setSortNo(sortNo++);
        }
        result.setSummaryJson(writeJson(Map.of(
                "itemCount", deduplicatedItems.size(),
                "hardErrorCount", deduplicatedItems.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count(),
                "source", "logistics_context_stabilized"
        )));
        result.setValidationSummaryJson(writeJson(validationSummary(deduplicatedItems)));
        result.setItems(deduplicatedItems);
        return result;
    }

    private FileParseStructuredAiResult stabilizeOfficialOutboundFeeResult(
            FileParseStructuredAiResult result,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        List<FileParseStructuredItem> stabilizedItems = stabilizeOfficialOutboundFeeItems(
                result.getItems(),
                targetPlan,
                itemStandards,
                sourceText
        );
        List<FileParseStructuredItem> deduplicatedItems = deduplicateItems(stabilizedItems);
        int sortNo = 1;
        for (FileParseStructuredItem item : deduplicatedItems) {
            item.setSortNo(sortNo++);
        }
        result.setSummaryJson(writeJson(Map.of(
                "itemCount", deduplicatedItems.size(),
                "hardErrorCount", deduplicatedItems.stream().filter(item -> "hard_error".equals(item.getValidationStatus())).count(),
                "source", "official_outbound_fee_context_stabilized"
        )));
        result.setValidationSummaryJson(writeJson(validationSummary(deduplicatedItems)));
        result.setItems(deduplicatedItems);
        return result;
    }

    private List<FileParseStructuredItem> stabilizeOfficialOutboundFeeItems(
            List<FileParseStructuredItem> items,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        List<FileParseStructuredItem> safeItems = items == null ? List.of() : items;
        if (!isStructuredOutboundFeePlan(targetPlan, itemStandards)) {
            return safeItems;
        }
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards.stream()
                .filter(item -> item != null)
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, item -> item, (left, right) -> left));
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        OfficialOutboundContext context = officialOutboundContext(targetPlan, sourceText, sourceLines);
        boolean hasOfficialOutboundSection = sourceContainsOfficialOutboundFee(sourceText, sourceLines);

        List<FileParseStructuredItem> stabilized = new ArrayList<>();
        for (FileParseStructuredItem item : safeItems) {
            if (item == null) {
                continue;
            }
            if (FileParseOfficialOutboundFeeStandard.LEGACY_OUTBOUND_FEE_RULE.equals(item.getItemType())) {
                continue;
            }
            if (!FileParseOfficialOutboundFeeStandard.structuredItemTypeNames().contains(item.getItemType())) {
                stabilized.add(item);
                continue;
            }
            Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
            backfillOfficialOutboundPayload(payload, item.getItemType(), context, hasOfficialOutboundSection);
            OfficialOutboundClassification classification = officialOutboundClassification(text(payload.get("classificationName")));
            if (hasOfficialOutboundSection
                    && FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION.equals(item.getItemType())
                    && classification != null) {
                applyOfficialOutboundClassificationDefaults(payload, classification);
            }
            applyOfficialOutboundPayload(
                    item,
                    standardsByType.get(item.getItemType()),
                    targetPlan,
                    payload,
                    item.getSourceRowIds(),
                    "ai_structured_text_stabilized"
            );
            stabilized.add(item);
        }

        if (!hasOfficialOutboundSection) {
            return stabilized;
        }
        List<FileParseStructuredItem> sourceDerivedItems = deriveOfficialOutboundItemsFromSource(
                standardsByType,
                targetPlan,
                context,
                sourceLines
        );
        return mergeOfficialOutboundSourceDerivedItems(stabilized, sourceDerivedItems);
    }

    private void backfillOfficialOutboundPayload(
            Map<String, Object> payload,
            String itemType,
            OfficialOutboundContext context,
            boolean authoritativeSource
    ) {
        if (payload == null || context == null) {
            return;
        }
        if (!authoritativeSource) {
            return;
        }
        if (StringUtils.hasText(context.country)) {
            payload.put("country", context.country);
        }
        if (authoritativeSource) {
            payload.put("platform", "NOON");
            payload.put("fulfillmentType", "FBN");
        }
        if (StringUtils.hasText(context.effectiveDate)) {
            putIfMissing(payload, "effectiveDate", context.effectiveDate);
        }
        if (StringUtils.hasText(context.sourceVersion)) {
            putIfMissing(payload, "sourceVersion", context.sourceVersion);
        }
        if (FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION.equals(itemType)) {
            putIfMissing(payload, "dimensionUnit", "cm");
            putIfMissing(payload, "weightUnit", "grams");
        } else if (FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB.equals(itemType)) {
            if (StringUtils.hasText(context.currency)) {
                payload.put("currency", context.currency);
                putIfMissing(payload, "thresholdCurrency", context.currency);
            }
            putIfMissing(payload, "salesPriceThresholdAmount", "25");
            putIfMissing(payload, "weightMinInclusive", "true");
            putIfMissing(payload, "weightMaxInclusive", "true");
        } else if (FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY.equals(itemType)) {
            putIfMissing(payload, "policyName", "Noon FBN official outbound policy");
            putIfMissing(payload, "shippingWeightFormula", "physical_weight_plus_packaging_weight");
            putIfMissing(payload, "dimensionSortRule", "sort_desc_longest_median_shortest");
            putIfMissing(payload, "weightBoundaryRule", "min_exclusive_max_inclusive");
            putIfMissing(payload, "roundingRule", "calculator_50g_ceiling");
            putIfMissing(payload, "salesPriceThresholdAmount", "25");
            if (StringUtils.hasText(context.currency)) {
                payload.put("thresholdCurrency", context.currency);
            }
            putIfMissing(payload, "dimensionUnit", "cm");
            putIfMissing(payload, "weightUnit", "grams");
        }
    }

    private List<FileParseStructuredItem> deriveOfficialOutboundItemsFromSource(
            Map<String, FileParseItemStandardRow> standardsByType,
            FileParseTargetPlanRow targetPlan,
            OfficialOutboundContext context,
            List<SourceLine> sourceLines
    ) {
        List<FileParseStructuredItem> items = new ArrayList<>();
        List<Long> sectionSourceRowIds = outboundSectionRowIds(sourceLines);
        FileParseItemStandardRow classificationStandard = standardsByType.get(FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION);
        if (classificationStandard != null) {
            for (OfficialOutboundClassification classification : officialOutboundClassifications()) {
                Map<String, Object> payload = baseOfficialOutboundPayload(context);
                payload.put("classificationName", classification.name);
                applyOfficialOutboundClassificationDefaults(payload, classification);
                items.add(newOfficialOutboundItem(
                        FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION,
                        classificationStandard,
                        targetPlan,
                        payload,
                        sectionSourceRowIds,
                        "official_fbn_calculator_size_defaults"
                ));
            }
        }

        FileParseItemStandardRow slabStandard = standardsByType.get(FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB);
        if (slabStandard != null) {
            for (OfficialOutboundSourceSlab sourceSlab : parseOfficialOutboundFeeSlabs(sourceLines, context)) {
                Map<String, Object> payload = baseOfficialOutboundPayload(context);
                payload.put("classificationName", sourceSlab.classificationName);
                payload.put("weightMinGrams", sourceSlab.weightMinGrams);
                payload.put("weightMinInclusive", sourceSlab.weightMinInclusive);
                payload.put("weightMaxGrams", sourceSlab.weightMaxGrams);
                payload.put("weightMaxInclusive", sourceSlab.weightMaxInclusive);
                payload.put("standardFeeAmount", sourceSlab.standardFeeAmount);
                payload.put("highAspFeeAmount", sourceSlab.highAspFeeAmount);
                payload.put("salesPriceThresholdAmount", "25");
                payload.put("thresholdCurrency", context.currency);
                payload.put("currency", context.currency);
                if (sourceSlab.extraWeightStepGrams != null) {
                    payload.put("extraWeightStepGrams", sourceSlab.extraWeightStepGrams);
                }
                if (sourceSlab.extraFeeAmount != null) {
                    payload.put("extraFeeAmount", sourceSlab.extraFeeAmount);
                }
                items.add(newOfficialOutboundItem(
                        FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB,
                        slabStandard,
                        targetPlan,
                        payload,
                        sourceSlab.sourceRowIds,
                        "official_fbn_outbound_fee_source_row"
                ));
            }
        }

        FileParseItemStandardRow policyStandard = standardsByType.get(FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY);
        if (policyStandard != null) {
            Map<String, Object> payload = baseOfficialOutboundPayload(context);
            backfillOfficialOutboundPayload(payload, FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY, context, true);
            items.add(newOfficialOutboundItem(
                    FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY,
                    policyStandard,
                    targetPlan,
                    payload,
                    sectionSourceRowIds,
                    "official_fbn_outbound_fee_policy_defaults"
            ));
        }
        return items;
    }

    private Map<String, Object> baseOfficialOutboundPayload(OfficialOutboundContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", context.country);
        payload.put("platform", "NOON");
        payload.put("fulfillmentType", "FBN");
        if (StringUtils.hasText(context.effectiveDate)) {
            payload.put("effectiveDate", context.effectiveDate);
        }
        payload.put("sourceVersion", context.sourceVersion);
        return payload;
    }

    private FileParseStructuredItem newOfficialOutboundItem(
            String itemType,
            FileParseItemStandardRow standard,
            FileParseTargetPlanRow targetPlan,
            Map<String, Object> payload,
            List<Long> sourceRowIds,
            String evidenceSource
    ) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType(itemType);
        item.setConfidence("source_context");
        applyOfficialOutboundPayload(item, standard, targetPlan, payload, sourceRowIds, evidenceSource);
        return item;
    }

    private void applyOfficialOutboundPayload(
            FileParseStructuredItem item,
            FileParseItemStandardRow standard,
            FileParseTargetPlanRow targetPlan,
            Map<String, Object> payload,
            List<Long> sourceRowIds,
            String evidenceSource
    ) {
        Map<String, Object> normalized = FileParseOutboundFeePayloadNormalizer.normalize(item.getItemType(), payload);
        item.setSourceRowIds(sourceRowIds == null ? List.of() : sourceRowIds.stream().distinct().collect(Collectors.toList()));
        item.setEvidenceJson(writeJson(Map.of(
                "source", evidenceSource,
                "sourceRowIds", item.getSourceRowIds()
        )));
        String naturalKey = FileParseNaturalKeySupport.buildNaturalKey(item.getItemType(), normalized);
        if (StringUtils.hasText(naturalKey)) {
            item.setNaturalKey(naturalKey);
            item.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash(item.getItemType(), naturalKey));
        }
        item.setNormalizedPayloadJson(writeJson(normalized));
        item.setValidationStatus("pass");
        item.setReviewStatus("pending");
        item.setValidationErrorJson(null);
        if (standard != null) {
            applyValidation(item, targetPlan, standard, normalized);
        }
    }

    private List<FileParseStructuredItem> mergeOfficialOutboundSourceDerivedItems(
            List<FileParseStructuredItem> aiItems,
            List<FileParseStructuredItem> sourceDerivedItems
    ) {
        List<FileParseStructuredItem> merged = new ArrayList<>();
        Set<String> sourceKeys = new LinkedHashSet<>();
        Set<String> authoritativeSourceTypes = new LinkedHashSet<>();
        if (sourceDerivedItems != null) {
            for (FileParseStructuredItem item : sourceDerivedItems) {
                if (item == null) {
                    continue;
                }
                merged.add(item);
                sourceKeys.add(item.getItemType() + "|" + item.getNaturalKeyHash());
                if (FileParseOfficialOutboundFeeStandard.structuredItemTypeNames().contains(item.getItemType())) {
                    authoritativeSourceTypes.add(item.getItemType());
                }
            }
        }
        if (aiItems != null) {
            for (FileParseStructuredItem item : aiItems) {
                if (item == null) {
                    continue;
                }
                if (authoritativeSourceTypes.contains(item.getItemType())) {
                    continue;
                }
                String key = item.getItemType() + "|" + item.getNaturalKeyHash();
                if (!sourceKeys.contains(key)) {
                    merged.add(item);
                }
            }
        }
        return merged;
    }

    private OfficialOutboundContext officialOutboundContext(
            FileParseTargetPlanRow targetPlan,
            String sourceText,
            List<SourceLine> sourceLines
    ) {
        String country = expectedOutboundCountry(targetPlan);
        if (!StringUtils.hasText(country)) {
            country = inferCountry(sourceText);
        }
        String currency = expectedOutboundCurrency(targetPlan);
        if (!StringUtils.hasText(currency)) {
            currency = inferCurrency(sourceText, "");
        }
        String effectiveDate = inferOfficialOutboundEffectiveDate(sourceText, sourceLines);
        if (!StringUtils.hasText(effectiveDate) && sourceContainsOfficialOutboundFee(sourceText, sourceLines)) {
            effectiveDate = defaultOfficialOutboundEffectiveDate(country, currency);
        }
        String sourceVersion = StringUtils.hasText(effectiveDate)
                ? "official_fbn_outbound_fee_" + effectiveDate
                : "official_fbn_outbound_fee";
        return new OfficialOutboundContext(country, currency, effectiveDate, sourceVersion);
    }

    private String defaultOfficialOutboundEffectiveDate(String country, String currency) {
        if ("KSA".equalsIgnoreCase(text(country)) || "SAR".equalsIgnoreCase(text(currency))) {
            return "2025-09-01";
        }
        return "";
    }

    private boolean sourceContainsOfficialOutboundFee(String sourceText, List<SourceLine> sourceLines) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sourceText)) {
            builder.append(sourceText).append(' ');
        }
        if (sourceLines != null) {
            for (SourceLine sourceLine : sourceLines) {
                builder.append(sourceLine.text).append(' ');
            }
        }
        String lower = normalizeSpaces(builder.toString()).toLowerCase(Locale.ROOT);
        return (lower.contains("fbn outbound fee") || lower.contains("fbn outbound fees"))
                && (lower.contains("fulfilled by noon") || lower.contains("one rate") || lower.contains("size tier"));
    }

    private String inferOfficialOutboundEffectiveDate(String sourceText, List<SourceLine> sourceLines) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(sourceText)) {
            builder.append(sourceText).append(' ');
        }
        if (sourceLines != null) {
            for (SourceLine sourceLine : sourceLines) {
                builder.append(sourceLine.text).append(' ');
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{4})")
                .matcher(builder.toString());
        if (!matcher.find()) {
            return "";
        }
        int month = monthNumber(matcher.group(2));
        if (month <= 0) {
            return "";
        }
        try {
            int day = Integer.parseInt(matcher.group(1));
            int year = Integer.parseInt(matcher.group(3));
            return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private int monthNumber(String monthName) {
        if (!StringUtils.hasText(monthName)) {
            return 0;
        }
        switch (monthName.toLowerCase(Locale.ROOT)) {
            case "january":
                return 1;
            case "february":
                return 2;
            case "march":
                return 3;
            case "april":
                return 4;
            case "may":
                return 5;
            case "june":
                return 6;
            case "july":
                return 7;
            case "august":
                return 8;
            case "september":
                return 9;
            case "october":
                return 10;
            case "november":
                return 11;
            case "december":
                return 12;
            default:
                return 0;
        }
    }

    private List<Long> outboundSectionRowIds(List<SourceLine> sourceLines) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        boolean inSection = false;
        for (SourceLine sourceLine : sourceLines) {
            String lower = normalizeSpaces(sourceLine.text).toLowerCase(Locale.ROOT);
            if (lower.contains("fbn outbound fee")) {
                inSection = true;
            }
            if (inSection && sourceLine.sourceRowId != null) {
                ids.add(sourceLine.sourceRowId);
            }
            if (inSection && lower.contains("monthly storage fee")) {
                break;
            }
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private List<OfficialOutboundSourceSlab> parseOfficialOutboundFeeSlabs(
            List<SourceLine> sourceLines,
            OfficialOutboundContext context
    ) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return List.of();
        }
        List<OfficialOutboundSourceSlab> slabs = new ArrayList<>();
        Map<String, BigDecimal> lastMaxByClassification = new LinkedHashMap<>();
        String currentClassification = "";
        String pendingClassificationPrefix = "";
        OfficialOutboundSourceSlab lastSlab = null;
        boolean inSection = false;
        for (SourceLine sourceLine : sourceLines) {
            String line = normalizeSpaces(sourceLine.text);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("fbn outbound fee")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (lower.contains("monthly storage fee")) {
                break;
            }
            if (!StringUtils.hasText(line) || lower.contains("size tier") || lower.contains("weight slab")) {
                continue;
            }
            if ("standard".equalsIgnoreCase(line)) {
                pendingClassificationPrefix = "Standard";
                continue;
            }
            if (StringUtils.hasText(pendingClassificationPrefix)
                    && ("envelope".equalsIgnoreCase(line) || "parcel".equalsIgnoreCase(line))) {
                currentClassification = pendingClassificationPrefix + " " + line.toLowerCase(Locale.ROOT);
                pendingClassificationPrefix = "";
                continue;
            }

            ClassificationPrefix prefix = classificationPrefix(line);
            String rateText = line;
            if (prefix != null) {
                currentClassification = prefix.classificationName;
                rateText = prefix.remainder;
                if (!StringUtils.hasText(rateText)) {
                    continue;
                }
            }
            if (!StringUtils.hasText(currentClassification)) {
                continue;
            }
            if (lower.contains("additional")) {
                applyAdditionalFee(lastSlab, line, sourceLine.sourceRowId);
                continue;
            }
            OfficialOutboundSourceSlab slab = parseOfficialOutboundFeeSlabLine(
                    currentClassification,
                    rateText,
                    sourceLine.sourceRowId,
                    lastMaxByClassification
            );
            if (slab == null) {
                continue;
            }
            slab.currency = context.currency;
            slabs.add(slab);
            lastSlab = slab;
            if (slab.weightMaxGrams != null) {
                lastMaxByClassification.put(currentClassification, slab.weightMaxGrams);
            }
        }
        return slabs;
    }

    private OfficialOutboundSourceSlab parseOfficialOutboundFeeSlabLine(
            String classificationName,
            String line,
            Long sourceRowId,
            Map<String, BigDecimal> lastMaxByClassification
    ) {
        String normalized = normalizeSpaces(line);
        List<BigDecimal> numbers = decimalNumbers(normalized);
        if (numbers.size() < 2) {
            return null;
        }
        BigDecimal standardFee = numbers.get(numbers.size() - 2);
        BigDecimal highAspFee = numbers.get(numbers.size() - 1);
        OfficialOutboundSourceSlab slab = new OfficialOutboundSourceSlab();
        slab.classificationName = classificationName;
        slab.standardFeeAmount = standardFee;
        slab.highAspFeeAmount = highAspFee;
        slab.weightMaxInclusive = true;
        slab.sourceRowIds = sourceRowId == null ? List.of() : List.of(sourceRowId);

        java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern
                .compile("(?i)>\\s*([0-9]+(?:\\.[0-9]+)?)\\s*kg\\s*&\\s*<=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*kg")
                .matcher(normalized);
        if (rangeMatcher.find()) {
            slab.weightMinGrams = kgToGrams(rangeMatcher.group(1));
            slab.weightMinInclusive = false;
            slab.weightMaxGrams = kgToGrams(rangeMatcher.group(2));
            return slab;
        }
        java.util.regex.Matcher maxMatcher = java.util.regex.Pattern
                .compile("(?i)<=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*kg")
                .matcher(normalized);
        if (maxMatcher.find()) {
            BigDecimal previousMax = lastMaxByClassification.get(classificationName);
            slab.weightMinGrams = previousMax == null ? BigDecimal.ZERO : previousMax;
            slab.weightMinInclusive = previousMax == null;
            slab.weightMaxGrams = kgToGrams(maxMatcher.group(1));
            return slab;
        }
        if (normalized.toLowerCase(Locale.ROOT).contains("one rate")) {
            OfficialOutboundClassification classification = officialOutboundClassification(classificationName);
            slab.weightMinGrams = BigDecimal.ZERO;
            slab.weightMinInclusive = true;
            slab.weightMaxGrams = classification == null ? null : classification.maxShippingWeightGrams;
            return slab;
        }
        return null;
    }

    private void applyAdditionalFee(OfficialOutboundSourceSlab slab, String line, Long sourceRowId) {
        if (slab == null || !StringUtils.hasText(line)) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)additional\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(?:sar|aed|egp)?\\s+per\\s+([0-9]+(?:\\.[0-9]+)?)?\\s*kg")
                .matcher(line);
        if (!matcher.find()) {
            return;
        }
        slab.extraFeeAmount = decimal(matcher.group(1));
        String stepKg = StringUtils.hasText(matcher.group(2)) ? matcher.group(2) : "1";
        slab.extraWeightStepGrams = kgToGrams(stepKg);
        slab.sourceRowIds = mergeSourceRowIds(
                slab.sourceRowIds,
                sourceRowId == null ? List.of() : List.of(sourceRowId)
        );
    }

    private List<BigDecimal> decimalNumbers(String value) {
        List<BigDecimal> values = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9]+(?:\\.[0-9]+)?")
                .matcher(value == null ? "" : value);
        while (matcher.find()) {
            values.add(decimal(matcher.group()));
        }
        return values;
    }

    private BigDecimal kgToGrams(String value) {
        return decimal(value).multiply(BigDecimal.valueOf(1000));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private ClassificationPrefix classificationPrefix(String line) {
        String normalized = normalizeSpaces(line);
        for (OfficialOutboundClassification classification : officialOutboundClassifications()) {
            if (startsWithClassification(normalized, classification.name)) {
                return new ClassificationPrefix(
                        classification.name,
                        normalizeSpaces(normalized.substring(classification.name.length()))
                );
            }
        }
        return null;
    }

    private boolean startsWithClassification(String line, String classificationName) {
        String lowerLine = normalizeSpaces(line).toLowerCase(Locale.ROOT);
        String lowerClassification = normalizeSpaces(classificationName).toLowerCase(Locale.ROOT);
        return lowerLine.equals(lowerClassification) || lowerLine.startsWith(lowerClassification + " ");
    }

    private void applyOfficialOutboundClassificationDefaults(
            Map<String, Object> payload,
            OfficialOutboundClassification classification
    ) {
        payload.put("classificationName", classification.name);
        payload.put("longestSideMaxCm", classification.longestSideMaxCm);
        payload.put("medianSideMaxCm", classification.medianSideMaxCm);
        payload.put("shortestSideMaxCm", classification.shortestSideMaxCm);
        payload.put("maxShippingWeightGrams", classification.maxShippingWeightGrams);
        payload.put("packagingWeightGrams", classification.packagingWeightGrams);
        payload.put("priority", classification.priority);
        payload.put("dimensionUnit", "cm");
        payload.put("weightUnit", "grams");
    }

    private OfficialOutboundClassification officialOutboundClassification(String classificationName) {
        String normalized = normalizeSpaces(classificationName).toLowerCase(Locale.ROOT);
        for (OfficialOutboundClassification classification : officialOutboundClassifications()) {
            if (classification.name.toLowerCase(Locale.ROOT).equals(normalized)) {
                return classification;
            }
        }
        if ("oversize".equals(normalized)) {
            return officialOutboundClassification("Oversize parcel");
        }
        return null;
    }

    private List<OfficialOutboundClassification> officialOutboundClassifications() {
        List<OfficialOutboundClassification> classifications = new ArrayList<>();
        classifications.add(new OfficialOutboundClassification("Small envelope", "20", "15", "1", "100", "20", 1));
        classifications.add(new OfficialOutboundClassification("Standard envelope", "33", "23", "2.5", "500", "40", 2));
        classifications.add(new OfficialOutboundClassification("Large envelope", "33", "23", "5", "1000", "40", 3));
        classifications.add(new OfficialOutboundClassification("Standard parcel", "45", "34", "26", "12000", "100", 4));
        classifications.add(new OfficialOutboundClassification("Oversize parcel", "130", "34", "26", "30000", "240", 5));
        classifications.add(new OfficialOutboundClassification("Extra oversize", "130", "130", "130", "30000", "240", 6));
        classifications.add(new OfficialOutboundClassification("Bulky", null, null, null, null, "400", 7));
        return classifications;
    }

    private List<FileParseStructuredItem> stabilizeLogisticsItems(
            List<FileParseStructuredItem> items,
            FileParseTargetPlanRow targetPlan,
            List<FileParseItemStandardRow> itemStandards,
            String sourceText
    ) {
        if (items == null || items.isEmpty() || !isLogisticsPlan(targetPlan, itemStandards)) {
            return items == null ? List.of() : items;
        }
        Map<String, FileParseItemStandardRow> standardsByType = itemStandards == null
                ? Map.of()
                : itemStandards.stream()
                .collect(Collectors.toMap(FileParseItemStandardRow::getItemType, item -> item, (left, right) -> left));
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        String defaultForwarderCode = inferDefaultForwarderCode(targetPlan, sourceText);
        List<LogisticsServiceProfile> serviceProfiles = new ArrayList<>();
        List<FileParseStructuredItem> stabilized = new ArrayList<>(items);

        for (FileParseStructuredItem item : stabilized) {
            if (item == null || !FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
            String context = logisticsItemContext(item, payload, sourceLines);
            backfillServiceLinePayload(payload, defaultForwarderCode, context);
            applyLogisticsPayload(item, standardsByType.get(item.getItemType()), payload);
            serviceProfiles.add(LogisticsServiceProfile.from(readMap(item.getNormalizedPayloadJson()), item.getSourceRowIds()));
        }

        if (serviceProfiles.isEmpty()) {
            LogisticsServiceProfile fallbackProfile = fallbackServiceProfile(defaultForwarderCode, sourceText);
            if (fallbackProfile != null) {
                serviceProfiles.add(fallbackProfile);
            }
        }

        for (FileParseStructuredItem item : stabilized) {
            if (item == null || FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(item.getItemType())) {
                continue;
            }
            if (!isLogisticsItemType(item.getItemType())) {
                continue;
            }
            Map<String, Object> payload = readMap(item.getNormalizedPayloadJson());
            String context = logisticsItemContext(item, payload, sourceLines);
            LogisticsServiceProfile serviceProfile = bestServiceProfile(item, payload, serviceProfiles, context);
            backfillLogisticsDependentPayload(payload, item.getItemType(), serviceProfile, defaultForwarderCode, context);
            applyLogisticsPayload(item, standardsByType.get(item.getItemType()), payload);
        }
        return stabilized;
    }

    private boolean isLogisticsPlan(FileParseTargetPlanRow targetPlan, List<FileParseItemStandardRow> itemStandards) {
        boolean hasLogisticsStandard = itemStandards != null && itemStandards.stream()
                .anyMatch(item -> item != null && isLogisticsItemType(item.getItemType()));
        if (hasLogisticsStandard) {
            return true;
        }
        if (targetPlan == null) {
            return false;
        }
        String code = text(targetPlan.getCode());
        String documentType = text(targetPlan.getDocumentType());
        return startsWithIgnoreCase(code, "logistics") || startsWithIgnoreCase(documentType, "logistics");
    }

    private boolean isLogisticsItemType(String itemType) {
        return itemType != null && FileParseLogisticsQuoteStandard.supportedItemTypeNames().contains(itemType);
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private void backfillServiceLinePayload(Map<String, Object> payload, String defaultForwarderCode, String context) {
        putIfMissing(payload, "forwarderCode", defaultForwarderCode);
        putIfMissing(payload, "country", inferCountry(context));
        putIfMissing(payload, "fulfillmentMode", inferFulfillmentMode(context));
        putIfMissing(payload, "transportMode", inferTransportMode(context));
        putIfMissing(payload, "serviceScope", inferServiceScope(context));
        payload.putAll(FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.SERVICE_LINE, payload));
        putIfMissing(payload, "destinationNode", defaultDestinationNode(text(payload.get("country")), context));
        payload.putAll(FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.SERVICE_LINE, payload));
        putIfMissing(payload, "serviceLineKey", serviceLineKey(payload));
    }

    private void backfillLogisticsDependentPayload(
            Map<String, Object> payload,
            String itemType,
            LogisticsServiceProfile serviceProfile,
            String defaultForwarderCode,
            String context
    ) {
        String forwarderCode = serviceProfile == null ? defaultForwarderCode : serviceProfile.forwarderCode;
        putIfMissing(payload, "forwarderCode", forwarderCode);
        if (requiresServiceLineKey(itemType) && serviceProfile != null) {
            putIfMissing(payload, "serviceLineKey", serviceProfile.serviceLineKey);
        }
        if (FileParseLogisticsQuoteStandard.CARGO_CATEGORY.equals(itemType)) {
            putIfMissing(payload, "categoryCode", inferCategoryCode(context));
            putIfMissing(payload, "categoryName", defaultCategoryName(text(payload.get("categoryCode")), context));
        } else if (FileParseLogisticsQuoteStandard.BASE_PRICE.equals(itemType)) {
            putIfMissing(payload, "cargoCategoryKey", inferCategoryCode(context));
            putIfMissing(payload, "currency", inferCurrency(context, forwarderCode));
            putIfMissing(payload, "billingUnit", inferBillingUnit(context, serviceProfile));
            putIfMissing(payload, "pricingModel", inferPricingModel(text(payload.get("billingUnit")), context));
            putIfMissing(payload, "priceStatus", inferPriceStatus(context, payload));
        } else if (FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE.equals(itemType) && serviceProfile != null) {
            putIfMissing(payload, "country", serviceProfile.country);
            putIfMissing(payload, "warehouseNode", firstText(
                    text(payload.get("originWarehouse")),
                    text(payload.get("destinationWarehouse")),
                    defaultDestinationNode(serviceProfile.country, context)
            ));
        }
    }

    private boolean requiresServiceLineKey(String itemType) {
        return FileParseLogisticsQuoteStandard.CARGO_CATEGORY.equals(itemType)
                || FileParseLogisticsQuoteStandard.BASE_PRICE.equals(itemType)
                || FileParseLogisticsQuoteStandard.SURCHARGE.equals(itemType)
                || FileParseLogisticsQuoteStandard.BILLING_RULE.equals(itemType)
                || FileParseLogisticsQuoteStandard.RESTRICTION.equals(itemType);
    }

    private void applyLogisticsPayload(
            FileParseStructuredItem item,
            FileParseItemStandardRow standard,
            Map<String, Object> payload
    ) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(item.getItemType(), payload);
        item.setNormalizedPayloadJson(writeJson(normalized));
        String naturalKey = FileParseNaturalKeySupport.buildNaturalKey(item.getItemType(), normalized);
        if (StringUtils.hasText(naturalKey)) {
            item.setNaturalKey(naturalKey);
            item.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash(item.getItemType(), naturalKey));
        }
        item.setValidationStatus("pass");
        item.setReviewStatus("pending");
        item.setValidationErrorJson(null);
        if (standard != null) {
            applyValidation(item, standard, normalized);
        }
    }

    private LogisticsServiceProfile bestServiceProfile(
            FileParseStructuredItem item,
            Map<String, Object> payload,
            List<LogisticsServiceProfile> serviceProfiles,
            String context
    ) {
        if (serviceProfiles == null || serviceProfiles.isEmpty()) {
            return null;
        }
        if (serviceProfiles.size() == 1) {
            return serviceProfiles.get(0);
        }
        String explicitServiceLineKey = normalizeSpaces(text(payload.get("serviceLineKey")));
        String inferredCountry = inferCountry(context);
        String inferredTransportMode = inferTransportMode(context);
        LogisticsServiceProfile best = serviceProfiles.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (LogisticsServiceProfile profile : serviceProfiles) {
            int score = 0;
            if (StringUtils.hasText(explicitServiceLineKey)
                    && (profile.serviceLineKey.equalsIgnoreCase(explicitServiceLineKey)
                    || profile.naturalKey.equalsIgnoreCase(explicitServiceLineKey))) {
                score += 100;
            }
            if (StringUtils.hasText(inferredCountry) && inferredCountry.equals(profile.country)) {
                score += 20;
            }
            if (StringUtils.hasText(inferredTransportMode) && inferredTransportMode.equals(profile.transportMode)) {
                score += 20;
            }
            long distance = minSourceRowDistance(item.getSourceRowIds(), profile.sourceRowIds);
            if (distance != Long.MAX_VALUE) {
                score += Math.max(0, 20 - (int) Math.min(20, distance));
            }
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        return best;
    }

    private long minSourceRowDistance(List<Long> itemRows, List<Long> serviceRows) {
        if (itemRows == null || itemRows.isEmpty() || serviceRows == null || serviceRows.isEmpty()) {
            return Long.MAX_VALUE;
        }
        long min = Long.MAX_VALUE;
        for (Long itemRow : itemRows) {
            if (itemRow == null) {
                continue;
            }
            for (Long serviceRow : serviceRows) {
                if (serviceRow != null) {
                    min = Math.min(min, Math.abs(itemRow - serviceRow));
                }
            }
        }
        return min;
    }

    private LogisticsServiceProfile fallbackServiceProfile(String defaultForwarderCode, String sourceText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfMissing(payload, "forwarderCode", defaultForwarderCode);
        putIfMissing(payload, "country", inferCountry(sourceText));
        putIfMissing(payload, "fulfillmentMode", inferFulfillmentMode(sourceText));
        putIfMissing(payload, "transportMode", inferTransportMode(sourceText));
        putIfMissing(payload, "serviceScope", inferServiceScope(sourceText));
        payload.putAll(FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.SERVICE_LINE, payload));
        putIfMissing(payload, "destinationNode", defaultDestinationNode(text(payload.get("country")), sourceText));
        payload.putAll(FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.SERVICE_LINE, payload));
        if (!StringUtils.hasText(text(payload.get("forwarderCode")))
                || !StringUtils.hasText(text(payload.get("country")))
                || !StringUtils.hasText(text(payload.get("transportMode")))) {
            return null;
        }
        payload.put("serviceLineKey", serviceLineKey(payload));
        return LogisticsServiceProfile.from(payload, List.of());
    }

    private String logisticsItemContext(
            FileParseStructuredItem item,
            Map<String, Object> payload,
            List<SourceLine> sourceLines
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append(text(item.getNaturalKey())).append(' ');
        if (payload != null) {
            for (Object value : payload.values()) {
                builder.append(text(value)).append(' ');
            }
        }
        if (item.getSourceRowIds() != null && sourceLines != null) {
            Set<Long> rowIds = new LinkedHashSet<>(item.getSourceRowIds());
            for (int index = 0; index < sourceLines.size(); index++) {
                SourceLine sourceLine = sourceLines.get(index);
                if (sourceLine.sourceRowId != null && rowIds.contains(sourceLine.sourceRowId)) {
                    int from = Math.max(0, index - 80);
                    for (int contextIndex = from; contextIndex <= index; contextIndex++) {
                        builder.append(sourceLines.get(contextIndex).text).append(' ');
                    }
                    builder.append(sourceLine.text).append(' ');
                }
            }
        }
        return builder.toString();
    }

    private String inferDefaultForwarderCode(FileParseTargetPlanRow targetPlan, String sourceText) {
        String context = lowerText((targetPlan == null ? "" : targetPlan.getCode())
                + " " + (targetPlan == null ? "" : targetPlan.getLabel())
                + " " + sourceText);
        if (context.contains("logistics_et") || context.contains("易通") || context.contains(" et")) {
            return "et";
        }
        if (context.contains("logistics_yite") || context.contains("义特") || context.contains("yite")) {
            return "yite";
        }
        return "";
    }

    private String inferCountry(String context) {
        String lower = lowerText(context);
        if (lower.contains("ksa") || lower.contains("saudi") || lower.contains("沙特") || lower.contains("riyadh")) {
            return "KSA";
        }
        if (lower.contains("uae") || lower.contains("emirates") || lower.contains("阿联酋") || lower.contains("dubai")) {
            return "UAE";
        }
        return "";
    }

    private String inferFulfillmentMode(String context) {
        String lower = lowerText(context);
        if (lower.contains("fbn") || lower.contains("仓到仓") || lower.contains("送仓")) {
            return "FBN";
        }
        return "";
    }

    private String inferTransportMode(String context) {
        String lower = lowerText(context);
        if (lower.contains("空运大货") || lower.contains("cargo air") || lower.contains("air cargo")) {
            return "cargo_air";
        }
        if (lower.contains("快递") || lower.contains("express")) {
            return "express";
        }
        if (lower.contains("海运") || lower.contains("sea")) {
            return "sea";
        }
        if (lower.contains("空运") || lower.equals("air")) {
            return "air";
        }
        if (lower.contains("仓") || lower.contains("warehouse")) {
            return "warehouse";
        }
        return "";
    }

    private String inferServiceScope(String context) {
        String lower = lowerText(context);
        if (lower.contains("仓到仓") || lower.contains("warehouse to fbn") || lower.contains("warehouse-to-fbn")) {
            return "warehouse_to_fbn";
        }
        if (lower.contains("fbn") || lower.contains("送仓")) {
            return "fbn_delivery";
        }
        if (lower.contains("海外仓") || lower.contains("warehouse")) {
            return "overseas_warehouse";
        }
        return "";
    }

    private String defaultDestinationNode(String country, String context) {
        String normalizedCountry = StringUtils.hasText(country) ? country : inferCountry(context);
        if ("KSA".equals(normalizedCountry)) {
            return "KSA FBN warehouse";
        }
        if ("UAE".equals(normalizedCountry)) {
            return "UAE FBN warehouse";
        }
        return "";
    }

    private String serviceLineKey(Map<String, Object> payload) {
        List<String> parts = new ArrayList<>();
        String forwarderCode = text(payload.get("forwarderCode"));
        if (StringUtils.hasText(forwarderCode)) {
            parts.add(forwarderCode.toUpperCase(Locale.ROOT));
        }
        addIfText(parts, text(payload.get("country")));
        addIfText(parts, text(payload.get("transportMode")));
        addIfText(parts, text(payload.get("serviceScope")));
        return String.join(" ", parts);
    }

    private String inferCategoryCode(String context) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b([A-G])\\s*(?:类|CLASS)?\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(normalizeSpaces(context).toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String defaultCategoryName(String categoryCode, String context) {
        String normalizedCode = inferCategoryCode(categoryCode);
        if (StringUtils.hasText(normalizedCode)) {
            return normalizedCode + "类";
        }
        String inferredCode = inferCategoryCode(context);
        return StringUtils.hasText(inferredCode) ? inferredCode + "类" : "";
    }

    private String inferCurrency(String context, String forwarderCode) {
        String upper = normalizeSpaces(context).toUpperCase(Locale.ROOT);
        if (upper.contains("RMB") || upper.contains("CNY") || upper.contains("人民币") || upper.contains("¥")) {
            return "CNY";
        }
        if (upper.contains("AED")) {
            return "AED";
        }
        if (upper.contains("SAR")) {
            return "SAR";
        }
        if (StringUtils.hasText(forwarderCode) && (upper.matches(".*\\d+.*") || upper.contains("KG"))) {
            return "CNY";
        }
        return "";
    }

    private String inferBillingUnit(String context, LogisticsServiceProfile serviceProfile) {
        String lower = lowerText(context);
        if (lower.contains("cbm") || lower.contains("m3") || lower.contains("m³") || lower.contains("立方")) {
            return "cbm";
        }
        if (lower.contains("kg") || lower.contains("公斤") || lower.contains("千克")) {
            return "kg";
        }
        if (lower.contains("pcs") || lower.contains("piece") || lower.contains("件")) {
            return "piece";
        }
        if (serviceProfile != null && ("express".equals(serviceProfile.transportMode)
                || "air".equals(serviceProfile.transportMode)
                || "cargo_air".equals(serviceProfile.transportMode))) {
            return "kg";
        }
        return "";
    }

    private String inferPricingModel(String billingUnit, String context) {
        String lower = lowerText(context);
        if ("cbm".equals(billingUnit) || lower.contains("体积") || lower.contains("立方")) {
            return "per_volume";
        }
        if ("kg".equals(billingUnit) || lower.contains("重量") || lower.contains("kg") || lower.contains("公斤")) {
            return "per_weight";
        }
        if ("piece".equals(billingUnit)) {
            return "per_piece";
        }
        return "";
    }

    private String inferPriceStatus(String context, Map<String, Object> payload) {
        String lower = lowerText(context);
        if (lower.contains("另询") || lower.contains("询价") || lower.contains("inquire")) {
            return "inquiry_required";
        }
        if (lower.contains("逐个确认") || lower.contains("人工确认") || lower.contains("manual")) {
            return "manual_confirm";
        }
        if (StringUtils.hasText(text(payload.get("unitPrice")))) {
            return "active";
        }
        return "";
    }

    private void putIfMissing(Map<String, Object> payload, String key, String value) {
        if (payload == null || !StringUtils.hasText(value)) {
            return;
        }
        if (!StringUtils.hasText(text(payload.get(key)))) {
            payload.put(key, value);
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    private String lowerText(String value) {
        return normalizeSpaces(value).toLowerCase(Locale.ROOT);
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
                + buildCommissionTierGuidance(itemStandards)
                + buildOutboundFeeGuidance(targetPlan, itemStandards)
                + buildLogisticsGuidance(targetPlan, itemStandards);
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

    private String buildOutboundFeeGuidance(FileParseTargetPlanRow targetPlan, List<FileParseItemStandardRow> itemStandards) {
        boolean hasOutboundRule = itemStandards.stream()
                .anyMatch(item -> FileParseOfficialOutboundFeeStandard.supportedItemTypeNames().contains(item.getItemType()));
        if (!hasOutboundRule || !isOutboundFeePlan(targetPlan)) {
            return "";
        }
        String expectedCountry = expectedOutboundCountry(targetPlan);
        String expectedCurrency = expectedOutboundCurrency(targetPlan);
        return "\n出仓费规则特殊要求："
                + "\n- 出仓费目标只解析 FBN Outbound fees / FBN Outbound Fee 这一节；不要解析 Referral Fees、commission、Monthly Storage Fees、Inventory Removal Fee、Value Added Services、FAQ 等其他菜单或费用表。"
                + "\n- 如果同一文件同时出现佣金和出仓费，只输出出仓费规则；Referral Fees 的类目百分比不能映射为 outbound_fee_rule。"
                + "\n- 新标准必须输出三类事实：outbound_size_classification_rule 表示规格分级，outbound_fee_weight_slab_rule 表示重量段费用，outbound_fee_calculation_policy 表示计算策略。"
                + "\n- 规格分级要填写 classificationName、三边尺寸边界、maxShippingWeightGrams、packagingWeightGrams；重量段费用要填写 classificationName、weightMin/MaxGrams、standardFeeAmount、currency。"
                + "\n- 计算策略要填写 shippingWeightFormula、dimensionSortRule、weightBoundaryRule、roundingRule、salesPriceThresholdAmount 和 thresholdCurrency；不要把策略写成费用行。"
                + "\n- feeAmount 必须是可比较的金额数字，不能填写百分比、公式、IF/INDEX/SWITCH 表达式或整段说明。"
                + "\n- Calculator 工作表里的 expected fees / formula 汇总行不是可发布规则；应优先使用明细费率表。"
                + (StringUtils.hasText(expectedCountry) ? "\n- 当前目标国家必须是 " + expectedCountry + "。" : "")
                + (StringUtils.hasText(expectedCurrency) ? "\n- 当前目标币种必须是 " + expectedCurrency + "。" : "");
    }

    private String buildLogisticsGuidance(FileParseTargetPlanRow targetPlan, List<FileParseItemStandardRow> itemStandards) {
        if (!isLogisticsPlan(targetPlan, itemStandards)) {
            return "";
        }
        return "\n物流报价特殊要求："
                + "\n- 每条 logistics_* 的 payload 都必须尽量补齐 forwarderCode；易通写 et，义特写 yite。"
                + "\n- 先抽取 logistics_service_line，再让分类、价格、附加费、计费规则、限制项复制同一条服务线的 serviceLineKey。"
                + "\n- ET PDF 的中国到沙特/阿联酋仓到仓、FBN 送仓线路，serviceScope 使用 warehouse_to_fbn，fulfillmentMode 使用 FBN。"
                + "\n- ET 货物分类覆盖 A-G；A-G 类都要输出 categoryCode，不要只覆盖 A/B/C。"
                + "\n- 价格行必须把 44 RMB/KG、92 RMB/KG 等拆成 unitPrice、currency=CNY、billingUnit=kg、pricingModel=per_weight。"
                + "\n- 计费规则和限制项也必须带 forwarderCode 与 serviceLineKey，避免成为无法发布的孤立规则。";
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
            FileParseTargetPlanRow targetPlan,
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
            payload = FileParseLogisticsPayloadNormalizer.normalize(itemType, payload);
            payload = FileParseOutboundFeePayloadNormalizer.normalize(itemType, payload);
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
            applyValidation(structuredItem, targetPlan, standard, payload);
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
        if (FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(itemType)) {
            return true;
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
        applyValidation(structuredItem, null, standard, payload);
    }

    private void applyValidation(
            FileParseStructuredItem structuredItem,
            FileParseTargetPlanRow targetPlan,
            FileParseItemStandardRow standard,
            Map<String, Object> payload
    ) {
        Map<String, Object> validationRule = readMap(standard.getValidationRuleJson());
        Object requiredValue = validationRule.get("required");
        List<String> missing = new ArrayList<>();
        Map<String, Object> errors = new LinkedHashMap<>();
        Map<String, Object> warnings = new LinkedHashMap<>();
        if (requiredValue instanceof List) {
            for (Object field : (List<?>) requiredValue) {
                if (field instanceof String
                        && !StringUtils.hasText(text(payload.get(field)))
                        && !isOptionalUnboundedOfficialOutboundSizeField(standard.getItemType(), payload, (String) field)) {
                    missing.add((String) field);
                }
            }
        }
        if (!missing.isEmpty()) {
            errors.put("missingRequiredFields", missing);
        }
        applyCommissionValidation(standard, payload, errors);
        applyOutboundFeeValidation(targetPlan, standard, payload, errors);
        applyLogisticsValidation(standard, payload, errors, warnings);
        if (!errors.isEmpty()) {
            structuredItem.setValidationStatus("hard_error");
            structuredItem.setReviewStatus("needs_fix");
            structuredItem.setValidationErrorJson(writeJson(errors));
        } else if (!warnings.isEmpty()) {
            structuredItem.setValidationStatus("warning");
            structuredItem.setReviewStatus("pending");
            structuredItem.setValidationErrorJson(writeJson(warnings));
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

    private void applyOutboundFeeValidation(
            FileParseTargetPlanRow targetPlan,
            FileParseItemStandardRow standard,
            Map<String, Object> payload,
            Map<String, Object> errors
    ) {
        String itemType = standard.getItemType();
        if (!FileParseOfficialOutboundFeeStandard.supportedItemTypeNames().contains(itemType)) {
            return;
        }
        String country = text(payload.get("country"));
        String currency = FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY.equals(itemType)
                ? text(payload.get("thresholdCurrency"))
                : text(payload.get("currency"));
        if (containsNonOutboundFeeText(text(payload.get("feeItem")))
                || containsNonOutboundFeeText(text(payload.get("classificationName")))
                || containsNonOutboundFeeText(text(payload.get("policyName")))) {
            errors.put("outboundFeeSection", "出仓费规则不能来自 Referral Fees / commission 佣金章节。");
        }
        validateNoFormulaAmount(payload, errors, "feeAmount");
        validateNoFormulaAmount(payload, errors, "standardFeeAmount");
        validateNoFormulaAmount(payload, errors, "highAspFeeAmount");
        if (FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION.equals(itemType)) {
            boolean unboundedBulky = isUnboundedBulkyClassification(payload);
            if (!unboundedBulky
                    && (!StringUtils.hasText(text(payload.get("longestSideMaxCm")))
                    || !StringUtils.hasText(text(payload.get("medianSideMaxCm")))
                    || !StringUtils.hasText(text(payload.get("shortestSideMaxCm"))))) {
                errors.put("classificationBoundary", "规格分级必须包含 longest/median/shortest 三边尺寸边界。");
            }
            if (!unboundedBulky && !StringUtils.hasText(text(payload.get("maxShippingWeightGrams")))) {
                errors.put("maxShippingWeightGrams", "规格分级必须包含最大 shipping weight。");
            }
        } else if (FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB.equals(itemType)) {
            if (!StringUtils.hasText(text(payload.get("weightMaxGrams")))) {
                errors.put("weightRange", "重量费用规则必须包含明确的重量上限。");
            }
            if (!StringUtils.hasText(text(payload.get("standardFeeAmount")))) {
                errors.put("standardFeeAmount", "重量费用规则必须包含标准出仓费金额。");
            }
            if (!StringUtils.hasText(text(payload.get("currency")))) {
                errors.put("currency", "重量费用规则必须包含币种。");
            }
        } else if (FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY.equals(itemType)) {
            if (!StringUtils.hasText(text(payload.get("shippingWeightFormula")))) {
                errors.put("shippingWeightFormula", "计算策略必须包含发货重量公式。");
            }
        }
        String expectedCountry = expectedOutboundCountry(targetPlan);
        if (StringUtils.hasText(expectedCountry)
                && StringUtils.hasText(country)
                && !expectedCountry.equalsIgnoreCase(country)) {
            errors.put("country", "出仓费目标方案只允许国家 " + expectedCountry + "。");
        }
        String expectedCurrency = expectedOutboundCurrency(targetPlan);
        if (StringUtils.hasText(expectedCurrency)
                && StringUtils.hasText(currency)
                && !expectedCurrency.equalsIgnoreCase(currency)) {
            errors.put("currency", "出仓费目标方案只允许币种 " + expectedCurrency + "。");
        }
    }

    private boolean isOptionalUnboundedOfficialOutboundSizeField(
            String itemType,
            Map<String, Object> payload,
            String field
    ) {
        return FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION.equals(itemType)
                && isUnboundedBulkyClassification(payload)
                && Set.of(
                "longestSideMaxCm",
                "medianSideMaxCm",
                "shortestSideMaxCm",
                "maxShippingWeightGrams"
        ).contains(field);
    }

    private boolean isUnboundedBulkyClassification(Map<String, Object> payload) {
        return payload != null
                && "bulky".equalsIgnoreCase(normalizeSpaces(text(payload.get("classificationName"))));
    }

    private boolean containsNonOutboundFeeText(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return text.contains("referral")
                || text.contains("commission")
                || text.contains("sale price")
                || text.contains("sales price");
    }

    private void validateNoFormulaAmount(Map<String, Object> payload, Map<String, Object> errors, String field) {
        String value = text(payload.get(field));
        if (!StringUtils.hasText(value)) {
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.contains("%")
                || lower.contains("if(")
                || lower.contains("index(")
                || lower.contains("switch(")
                || lower.contains("_xlfn.switch")) {
            errors.put(field, "出仓费金额必须是可比较的金额数字，不能是百分比或 Excel 公式。");
        }
    }

    private void applyLogisticsValidation(
            FileParseItemStandardRow standard,
            Map<String, Object> payload,
            Map<String, Object> errors,
            Map<String, Object> warnings
    ) {
        if (!FileParseLogisticsQuoteStandard.CARGO_CATEGORY.equals(standard.getItemType())) {
            if (FileParseLogisticsQuoteStandard.BASE_PRICE.equals(standard.getItemType())) {
                applyBasePriceValidation(payload, errors);
            } else if (FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE.equals(standard.getItemType())) {
                applyWarehouseFeeValidation(payload, errors, warnings);
            } else if (FileParseLogisticsQuoteStandard.RESTRICTION.equals(standard.getItemType())) {
                applyRestrictionValidation(payload, errors, warnings);
            }
            return;
        }
        if (Boolean.TRUE.equals(payload.get("manualConfirmRequired"))) {
            warnings.put("manualConfirmRequired", "货物分类需要人工确认。确认后可发布。");
        }
    }

    private void applyBasePriceValidation(Map<String, Object> payload, Map<String, Object> errors) {
        String priceStatus = text(payload.get("priceStatus"));
        boolean concretePrice = !("manual_confirm".equals(priceStatus) || "inquiry_required".equals(priceStatus));
        if (concretePrice && !StringUtils.hasText(text(payload.get("unitPrice")))) {
            errors.put("unitPrice", "基础价格必须填写可比较的单价。");
        }

        String pricingModel = text(payload.get("pricingModel"));
        String billingUnit = text(payload.get("billingUnit"));
        if ("per_volume".equals(pricingModel) && "kg".equals(billingUnit)) {
            errors.put("incompatibleUnit", "按体积计费不能使用 kg 作为计费单位。");
        }
        if ("per_weight".equals(pricingModel) && "cbm".equals(billingUnit)) {
            errors.put("incompatibleUnit", "按重量计费不能使用 cbm 作为计费单位。");
        }
    }

    private void applyWarehouseFeeValidation(
            Map<String, Object> payload,
            Map<String, Object> errors,
            Map<String, Object> warnings
    ) {
        boolean hasAmountOrRate = StringUtils.hasText(text(payload.get("amount")))
                || StringUtils.hasText(text(payload.get("rate")));
        if (!hasAmountOrRate) {
            warnings.put("amount", "海外仓服务费缺少金额或费率；确认后可发布。");
        }
        if (StringUtils.hasText(text(payload.get("amount")))
                && !StringUtils.hasText(text(payload.get("currency")))) {
            errors.put("currency", "海外仓服务费金额存在时必须填写币种。");
        }
        String freeCondition = text(payload.get("freeCondition"));
        if (StringUtils.hasText(freeCondition)
                && (freeCondition.contains("待确认") || freeCondition.toLowerCase().contains("ambiguous"))) {
            errors.put("freeCondition", "免收费条件不明确，需要人工确认。");
        }
    }

    private void applyRestrictionValidation(
            Map<String, Object> payload,
            Map<String, Object> errors,
            Map<String, Object> warnings
    ) {
        if ("blocking".equals(text(payload.get("severity")))) {
            errors.put("blockingRestriction", "阻断级禁限运或合规条款需要人工确认后才能发布。");
        }
        if (Boolean.TRUE.equals(payload.get("manualConfirmRequired"))) {
            warnings.put("manualConfirmRequired", "禁限运或合规条款需要人工确认。确认后可发布。");
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

    private static final class OfficialOutboundContext {
        private final String country;
        private final String currency;
        private final String effectiveDate;
        private final String sourceVersion;

        private OfficialOutboundContext(String country, String currency, String effectiveDate, String sourceVersion) {
            this.country = country;
            this.currency = currency;
            this.effectiveDate = effectiveDate;
            this.sourceVersion = sourceVersion;
        }
    }

    private static final class OfficialOutboundClassification {
        private final String name;
        private final BigDecimal longestSideMaxCm;
        private final BigDecimal medianSideMaxCm;
        private final BigDecimal shortestSideMaxCm;
        private final BigDecimal maxShippingWeightGrams;
        private final BigDecimal packagingWeightGrams;
        private final Integer priority;

        private OfficialOutboundClassification(
                String name,
                String longestSideMaxCm,
                String medianSideMaxCm,
                String shortestSideMaxCm,
                String maxShippingWeightGrams,
                String packagingWeightGrams,
                Integer priority
        ) {
            this.name = name;
            this.longestSideMaxCm = decimalOrNull(longestSideMaxCm);
            this.medianSideMaxCm = decimalOrNull(medianSideMaxCm);
            this.shortestSideMaxCm = decimalOrNull(shortestSideMaxCm);
            this.maxShippingWeightGrams = decimalOrNull(maxShippingWeightGrams);
            this.packagingWeightGrams = decimalOrNull(packagingWeightGrams);
            this.priority = priority;
        }

        private static BigDecimal decimalOrNull(String value) {
            return StringUtils.hasText(value) ? new BigDecimal(value) : null;
        }
    }

    private static final class OfficialOutboundSourceSlab {
        private String classificationName;
        private BigDecimal weightMinGrams;
        private Boolean weightMinInclusive;
        private BigDecimal weightMaxGrams;
        private Boolean weightMaxInclusive;
        private BigDecimal standardFeeAmount;
        private BigDecimal highAspFeeAmount;
        private BigDecimal extraWeightStepGrams;
        private BigDecimal extraFeeAmount;
        private String currency;
        private List<Long> sourceRowIds = List.of();
    }

    private static final class ClassificationPrefix {
        private final String classificationName;
        private final String remainder;

        private ClassificationPrefix(String classificationName, String remainder) {
            this.classificationName = classificationName;
            this.remainder = remainder;
        }
    }

    private static final class SourceLine {
        private final Long sourceRowId;
        private final String text;

        private SourceLine(Long sourceRowId, String text) {
            this.sourceRowId = sourceRowId;
            this.text = text;
        }
    }

    private static final class LogisticsServiceProfile {
        private final String forwarderCode;
        private final String country;
        private final String transportMode;
        private final String serviceLineKey;
        private final String naturalKey;
        private final List<Long> sourceRowIds;

        private LogisticsServiceProfile(
                String forwarderCode,
                String country,
                String transportMode,
                String serviceLineKey,
                String naturalKey,
                List<Long> sourceRowIds
        ) {
            this.forwarderCode = forwarderCode;
            this.country = country;
            this.transportMode = transportMode;
            this.serviceLineKey = serviceLineKey;
            this.naturalKey = naturalKey;
            this.sourceRowIds = sourceRowIds == null ? List.of() : List.copyOf(sourceRowIds);
        }

        private static LogisticsServiceProfile from(Map<String, Object> payload, List<Long> sourceRowIds) {
            Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                    FileParseLogisticsQuoteStandard.SERVICE_LINE,
                    payload
            );
            String naturalKey = FileParseNaturalKeySupport.buildNaturalKey(FileParseLogisticsQuoteStandard.SERVICE_LINE, normalized);
            String serviceLineKey = text(normalized.get("serviceLineKey"));
            if (!StringUtils.hasText(serviceLineKey)) {
                serviceLineKey = String.join(" ", serviceLineKeyParts(normalized));
            }
            return new LogisticsServiceProfile(
                    text(normalized.get("forwarderCode")),
                    text(normalized.get("country")),
                    text(normalized.get("transportMode")),
                    serviceLineKey,
                    StringUtils.hasText(naturalKey) ? naturalKey : serviceLineKey,
                    sourceRowIds
            );
        }

        private static List<String> serviceLineKeyParts(Map<String, Object> payload) {
            List<String> parts = new ArrayList<>();
            String forwarderCode = text(payload.get("forwarderCode"));
            if (StringUtils.hasText(forwarderCode)) {
                parts.add(forwarderCode.toUpperCase(Locale.ROOT));
            }
            addIfText(parts, text(payload.get("country")));
            addIfText(parts, text(payload.get("transportMode")));
            addIfText(parts, text(payload.get("serviceScope")));
            return parts;
        }

        private static void addIfText(List<String> values, String value) {
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }

        private static String text(Object value) {
            return value == null ? null : String.valueOf(value).trim();
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
