package com.nuono.next.filemanagement.parse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class FileParseCommissionPayloadNormalizer {

    private FileParseCommissionPayloadNormalizer() {
    }

    static Map<String, Object> normalize(String itemType, Map<String, Object> payload) {
        return normalize(itemType, payload, null, List.of());
    }

    static Map<String, Object> normalize(
            String itemType,
            Map<String, Object> payload,
            String sourceText,
            List<Long> sourceRowIds
    ) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (payload != null) {
            normalized.putAll(payload);
        }
        if ("logistics_channel_rule".equals(itemType)) {
            normalizeLogisticsRule(normalized, sourceText, sourceRowIds);
            return normalized;
        }
        if (!"commission_rule".equals(itemType)) {
            return normalized;
        }

        String sourceRowText = findSourceRowText(sourceText, sourceRowIds);
        if (StringUtils.hasText(sourceRowText)) {
            applyCommissionSourceRow(normalized, sourceRowText);
        }
        SourceCategoryContext sourceCategoryContext = inferSourceCategoryContext(sourceText, sourceRowIds, normalized);
        applySourceCategoryContext(normalized, sourceCategoryContext);
        applySourceRateContext(normalized, sourceCategoryContext);
        normalizeCommissionCategoryHierarchy(normalized, sourceText, sourceRowIds);
        normalizeUpperCase(normalized, "country");
        normalizeNoonPlatform(normalized);
        normalizeUpperCase(normalized, "fulfillmentType");
        normalizeCommissionBrandRestriction(normalized, sourceRowText);
        normalizeCommissionRate(normalized);
        normalizeCommissionFulfillmentType(normalized, sourceRowText);

        String amountCurrency = normalizeNullLike(text(normalized.get("amountCurrency")));
        if (!StringUtils.hasText(amountCurrency)) {
            amountCurrency = inferCurrency(text(normalized.get("amountRangeLabel")), text(normalized.get("country")));
        }
        if (StringUtils.hasText(amountCurrency)) {
            normalized.put("amountCurrency", amountCurrency.toUpperCase(Locale.ROOT));
        }
        normalizeCommissionEffectiveDate(normalized, sourceText, sourceRowText);

        String amountRangeLabel = text(normalized.get("amountRangeLabel"));
        if (isAllRange(amountRangeLabel)) {
            normalized.put("amountRangeLabel", "全部");
        } else if (!StringUtils.hasText(amountRangeLabel)) {
            String derivedRangeLabel = deriveRangeLabel(normalized, text(normalized.get("amountCurrency")));
            if (StringUtils.hasText(derivedRangeLabel)) {
                normalized.put("amountRangeLabel", derivedRangeLabel);
            }
        }

        normalizeCommissionAmountRangeFields(normalized);
        putIfAbsent(normalized, "amountMin", null);
        putIfAbsent(normalized, "amountMinInclusive", null);
        putIfAbsent(normalized, "amountMax", null);
        putIfAbsent(normalized, "amountMaxInclusive", null);
        return normalized;
    }

    private static void applySourceRateContext(Map<String, Object> payload, SourceCategoryContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.brandRestriction)) {
            payload.put("brandRestriction", context.brandRestriction);
        }
        if (StringUtils.hasText(context.amountRangeLabel)) {
            payload.put("amountRangeLabel", context.amountRangeLabel);
            payload.put("amountMin", context.amountMin);
            payload.put("amountMinInclusive", context.amountMinInclusive);
            payload.put("amountMax", context.amountMax);
            payload.put("amountMaxInclusive", context.amountMaxInclusive);
        }
        if (StringUtils.hasText(context.amountCurrency)) {
            payload.put("amountCurrency", context.amountCurrency);
        }
        if (StringUtils.hasText(context.effectiveDate)) {
            payload.put("effectiveDate", context.effectiveDate);
        }
    }

    private static void applySourceCategoryContext(Map<String, Object> payload, SourceCategoryContext context) {
        if (context == null || !StringUtils.hasText(context.categoryName)) {
            return;
        }
        String categoryName = text(payload.get("categoryName"));
        String parentCategoryName = text(payload.get("parentCategoryName"));
        String categoryPath = text(payload.get("categoryPath"));
        boolean categoryIsAll = "all".equalsIgnoreCase(categoryName) || "全部".equals(categoryName);
        boolean pathEndsWithAll = normalizeCategoryPath(categoryPath).toLowerCase(Locale.ROOT).endsWith(" > all");
        boolean categoryFragment = StringUtils.hasText(categoryName)
                && !sameText(categoryName, context.categoryName)
                && categoryName.length() < context.categoryName.length()
                && context.categoryName.toLowerCase(Locale.ROOT).contains(categoryName.toLowerCase(Locale.ROOT));
        boolean categoryLooksInvalid = StringUtils.hasText(categoryName) && shouldIgnoreCategoryCandidate(categoryName);
        boolean parentMismatch = StringUtils.hasText(context.parentCategoryName)
                && StringUtils.hasText(parentCategoryName)
                && !sameText(parentCategoryName, context.parentCategoryName);
        boolean categoryMismatchSameParent = StringUtils.hasText(context.parentCategoryName)
                && sameText(parentCategoryName, context.parentCategoryName)
                && StringUtils.hasText(categoryName)
                && !sameText(categoryName, context.categoryName);
        boolean pathMissingParent = StringUtils.hasText(context.parentCategoryName)
                && StringUtils.hasText(categoryPath)
                && sameText(categoryName, context.categoryName)
                && !categoryPathStartsWithParent(categoryPath, context.parentCategoryName);
        boolean pathDiffersFromSourceContext = context.strongEvidence
                && StringUtils.hasText(categoryPath)
                && sameText(categoryName, context.categoryName)
                && !sameText(categoryPath, buildCategoryPath(context.parentCategoryName, context.categoryName));
        boolean shouldOverride = !StringUtils.hasText(categoryName)
                || categoryIsAll
                || pathEndsWithAll
                || categoryFragment
                || categoryLooksInvalid
                || (context.strongEvidence && !sameText(categoryName, context.categoryName))
                || categoryMismatchSameParent
                || pathMissingParent
                || pathDiffersFromSourceContext
                || (parentMismatch && sourceContextShouldOverrideParentMismatch(context, categoryName));
        if (!shouldOverride) {
            return;
        }
        if (StringUtils.hasText(context.parentCategoryName)) {
            payload.put("parentCategoryName", context.parentCategoryName);
        }
        payload.put("categoryName", context.categoryName);
        payload.put("categoryPath", buildCategoryPath(context.parentCategoryName, context.categoryName));
    }

    private static boolean sourceContextIsMoreSpecific(SourceCategoryContext context, String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return true;
        }
        if (sameText(categoryName, context.categoryName)) {
            return true;
        }
        return context.categoryName.length() > categoryName.length()
                && context.categoryName.toLowerCase(Locale.ROOT).contains(categoryName.toLowerCase(Locale.ROOT));
    }

    private static boolean sourceContextShouldOverrideParentMismatch(SourceCategoryContext context, String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return true;
        }
        if (sourceContextIsMoreSpecific(context, categoryName)) {
            return true;
        }
        String category = normalizeCategoryName(categoryName).toLowerCase(Locale.ROOT);
        String parent = normalizeCategoryName(context.parentCategoryName).toLowerCase(Locale.ROOT);
        return StringUtils.hasText(parent)
                && (category.startsWith(parent + " ") || sameText(categoryName, context.parentCategoryName));
    }

    private static void normalizeCommissionEffectiveDate(
            Map<String, Object> payload,
            String sourceText,
            String sourceRowText
    ) {
        String configured = text(payload.get("effectiveDate"));
        String sourceSpecificDate = inferEffectiveDate(sourceRowText);
        if (StringUtils.hasText(sourceSpecificDate)) {
            payload.put("effectiveDate", sourceSpecificDate);
            return;
        }
        String documentDate = inferEffectiveDate(sourceText);
        if (StringUtils.hasText(documentDate)) {
            payload.put("effectiveDate", documentDate);
            return;
        }
        if (StringUtils.hasText(configured)) {
            payload.put("effectiveDate", configured);
        }
    }

    private static String inferEffectiveDate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?i)(?:applicable|effective)\\s+from\\s+(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]+)\\s+(\\d{4})"
        );
        java.util.regex.Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return "";
        }
        Integer month = monthNumber(matcher.group(2));
        if (month == null) {
            return "";
        }
        int day;
        int year;
        try {
            day = Integer.parseInt(matcher.group(1));
            year = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException ignored) {
            return "";
        }
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
    }

    private static Integer monthNumber(String monthName) {
        if (!StringUtils.hasText(monthName)) {
            return null;
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
                return null;
        }
    }

    private static void applyCommissionSourceRow(Map<String, Object> payload, String sourceRowText) {
        List<String> cells = splitCommissionCells(sourceRowText);
        if (cells.size() < 5 || "category".equalsIgnoreCase(cells.get(0)) || cells.get(0).contains("佣金")) {
            return;
        }
        putText(payload, "categoryName", cells.get(0));
        putText(payload, "amountRangeLabel", cells.get(1));
        putText(payload, "amountCurrency", cells.get(2));
        if (!containsMultiplePercentageRates(cells.get(3)) || !StringUtils.hasText(text(payload.get("commissionRate")))) {
            putText(payload, "commissionRate", cells.get(3));
        }
        putText(payload, "effectiveDate", cells.get(4));
    }

    private static List<String> splitCommissionCells(String sourceRowText) {
        String delimiter = sourceRowText.contains("|") ? "\\|" : "\\t";
        String[] parts = sourceRowText.split(delimiter);
        List<String> cells = new java.util.ArrayList<>();
        for (String part : parts) {
            String cell = text(part);
            if (StringUtils.hasText(cell)) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static void normalizeLogisticsRule(
            Map<String, Object> payload,
            String sourceText,
            List<Long> sourceRowIds
    ) {
        normalizeTextField(payload, "channelKey");
        normalizeUpperCase(payload, "country");
        normalizeTextField(payload, "city");
        normalizeTextField(payload, "shippingMethod");
        normalizeTextField(payload, "feeItem");
        normalizeTextField(payload, "billingRule");
        normalizeTextField(payload, "leadTime");

        String sourceRowText = findSourceRowText(sourceText, sourceRowIds);
        if (StringUtils.hasText(sourceRowText)) {
            applyLogisticsSourceRow(payload, sourceRowText);
        }
    }

    private static void applyLogisticsSourceRow(Map<String, Object> payload, String sourceRowText) {
        String[] cells = sourceRowText.split("\\t");
        if (cells.length < 8 || !"货代文档".equals(text(cells[0]))) {
            return;
        }
        putText(payload, "channelKey", cell(cells, 1));
        String destination = cell(cells, 4);
        if (StringUtils.hasText(destination)) {
            String[] parts = destination.split("[/／]", 2);
            putText(payload, "country", parts[0].toUpperCase(Locale.ROOT));
            if (parts.length > 1) {
                putText(payload, "city", parts[1]);
            }
        }
        putText(payload, "shippingMethod", cell(cells, 5));
        putText(payload, "feeItem", cell(cells, 6));
        String billingRule = buildLogisticsBillingRule(cells);
        if (StringUtils.hasText(billingRule)) {
            payload.put("billingRule", billingRule);
        }
    }

    private static String buildLogisticsBillingRule(String[] cells) {
        String billingModel = cell(cells, 7);
        String unit = cell(cells, 8);
        String price = cell(cells, 9);
        String currency = cell(cells, 10);
        if (isCurrency(unit) && !isNumber(price)) {
            currency = unit;
            unit = "";
            price = "";
        }
        List<String> parts = new java.util.ArrayList<>();
        if (StringUtils.hasText(billingModel)) {
            parts.add(billingModel);
        }
        if (StringUtils.hasText(unit)) {
            parts.add(unit);
        }
        if (isNumber(price)) {
            parts.add(normalizeDecimal(price) + (StringUtils.hasText(currency) ? " " + currency.toUpperCase(Locale.ROOT) : ""));
        } else if (StringUtils.hasText(currency)) {
            parts.add(currency.toUpperCase(Locale.ROOT));
        }
        return String.join("，", parts);
    }

    private static String findSourceRowText(String sourceText, List<Long> sourceRowIds) {
        if (!StringUtils.hasText(sourceText) || sourceRowIds == null || sourceRowIds.isEmpty()) {
            return "";
        }
        Set<String> idSet = sourceRowIds.stream()
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String currentId = null;
        List<String> matchedLines = new java.util.ArrayList<>();
        for (String line : sourceText.split("\\R")) {
            String markerId = markerSourceRowId(line);
            if (StringUtils.hasText(markerId)) {
                currentId = markerId;
                continue;
            }
            if (currentId != null && idSet.contains(currentId) && StringUtils.hasText(line)) {
                matchedLines.add(line);
            }
        }
        return String.join(" ", matchedLines);
    }

    private static String markerSourceRowId(String line) {
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

    private static void normalizeUpperCase(Map<String, Object> payload, String field) {
        String value = text(payload.get(field));
        if (StringUtils.hasText(value)) {
            payload.put(field, value.toUpperCase(Locale.ROOT));
        }
    }

    private static void normalizeNoonPlatform(Map<String, Object> payload) {
        String platform = text(payload.get("platform"));
        if ("noon".equalsIgnoreCase(platform)) {
            payload.put("platform", "Noon");
        }
    }

    private static void normalizeCommissionCategoryHierarchy(
            Map<String, Object> payload,
            String sourceText,
            List<Long> sourceRowIds
    ) {
        normalizeTextField(payload, "categoryName");
        normalizeTextField(payload, "parentCategoryName");
        normalizeTextField(payload, "categoryPath");

        String categoryName = text(payload.get("categoryName"));
        String parentCategoryName = text(payload.get("parentCategoryName"));
        String categoryPath = text(payload.get("categoryPath"));
        String platform = text(payload.get("platform"));
        if (!StringUtils.hasText(parentCategoryName) && isKnownCommissionParentCategory(platform)) {
            parentCategoryName = normalizeCategoryName(platform);
            payload.put("parentCategoryName", parentCategoryName);
            payload.put("platform", null);
        }

        if (StringUtils.hasText(categoryPath)) {
            categoryPath = normalizeCategoryPath(categoryPath);
            payload.put("categoryPath", categoryPath);
            if (!StringUtils.hasText(parentCategoryName)) {
                String derivedParent = parentFromCategoryPath(categoryPath);
                if (StringUtils.hasText(derivedParent)) {
                    parentCategoryName = derivedParent;
                    payload.put("parentCategoryName", parentCategoryName);
                }
            }
            if (!StringUtils.hasText(categoryName)) {
                String leaf = leafFromCategoryPath(categoryPath);
                if (StringUtils.hasText(leaf)) {
                    categoryName = leaf;
                    payload.put("categoryName", categoryName);
                }
            }
        }

        if (!StringUtils.hasText(parentCategoryName)) {
            String inferredParent = inferParentCategoryName(sourceText, sourceRowIds);
            if (StringUtils.hasText(inferredParent) && !sameText(inferredParent, categoryName)) {
                parentCategoryName = inferredParent;
                payload.put("parentCategoryName", parentCategoryName);
            }
        }

        if (!StringUtils.hasText(categoryName)) {
            String inferredCategory = inferCategoryName(sourceText, sourceRowIds, parentCategoryName);
            if (StringUtils.hasText(inferredCategory)) {
                categoryName = inferredCategory;
                payload.put("categoryName", categoryName);
            }
        }

        String rebuiltCategoryPath = buildCategoryPath(parentCategoryName, categoryName);
        boolean shouldRebuildPath = StringUtils.hasText(rebuiltCategoryPath)
                && StringUtils.hasText(parentCategoryName)
                && StringUtils.hasText(categoryName)
                && (!StringUtils.hasText(categoryPath)
                || sameText(categoryPath, categoryName)
                || !categoryPathStartsWithParent(categoryPath, parentCategoryName));
        if (shouldRebuildPath || !StringUtils.hasText(categoryPath)) {
            categoryPath = rebuiltCategoryPath;
            if (StringUtils.hasText(categoryPath)) {
                payload.put("categoryPath", categoryPath);
            }
        }
    }

    private static String inferParentCategoryName(String sourceText, List<Long> sourceRowIds) {
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        if (sourceLines.isEmpty() || sourceRowIds == null || sourceRowIds.isEmpty()) {
            return "";
        }
        Set<Long> targetIds = sourceRowIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int firstTargetIndex = -1;
        for (int index = 0; index < sourceLines.size(); index++) {
            if (targetIds.contains(sourceLines.get(index).sourceRowId)) {
                firstTargetIndex = index;
                break;
            }
        }
        if (firstTargetIndex <= 0) {
            return "";
        }
        int scanStart = Math.max(0, firstTargetIndex - 40);
        for (int index = firstTargetIndex - 1; index >= scanStart; index--) {
            String value = normalizeCategoryName(sourceLines.get(index).text);
            if (isKnownCommissionParentCategory(value)) {
                return value;
            }
        }
        return "";
    }

    private static SourceCategoryContext inferSourceCategoryContext(
            String sourceText,
            List<Long> sourceRowIds,
            Map<String, Object> payload
    ) {
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        if (sourceLines.isEmpty() || sourceRowIds == null || sourceRowIds.isEmpty()) {
            return null;
        }
        Set<Long> targetIds = sourceRowIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (targetIds.isEmpty()) {
            return null;
        }
        List<Integer> targetIndices = new java.util.ArrayList<>();
        for (int index = 0; index < sourceLines.size(); index++) {
            if (targetIds.contains(sourceLines.get(index).sourceRowId)) {
                if (sourceLines.get(index).text.contains("|") || sourceLines.get(index).text.contains("\t")) {
                    return null;
                }
                targetIndices.add(index);
            }
        }
        if (targetIndices.isEmpty()) {
            return null;
        }

        int firstRateIndex = selectBestSourceRateIndex(sourceLines, targetIndices, payload);

        CategoryContextParts contextParts;
        if (firstRateIndex >= 0) {
            String inlineCandidate = normalizeInlineCategoryWithRate(sourceLines.get(firstRateIndex).text);
            if (StringUtils.hasText(inlineCandidate) && !shouldIgnoreCategoryCandidate(inlineCandidate)) {
                contextParts = new CategoryContextParts(List.of(inlineCandidate), firstRateIndex);
            } else {
                contextParts = collectCategoryPartsBeforeRate(sourceLines, firstRateIndex);
            }
        } else {
            contextParts = collectCategoryPartsBeforeRate(sourceLines, targetIndices.get(targetIndices.size() - 1) + 1);
        }
        if (contextParts == null || contextParts.categoryParts.isEmpty()) {
            return null;
        }

        List<String> categoryParts = new java.util.ArrayList<>(contextParts.categoryParts);
        String parentCategoryName = "";
        if (categoryParts.size() >= 2 && isKnownCommissionParentCategory(categoryParts.get(0))) {
            parentCategoryName = categoryParts.remove(0);
        }
        String categoryName = normalizeCategoryName(String.join(" ", categoryParts));
        SourceCategoryContext splitContext = splitKnownParentPrefixedCategory(categoryName);
        if (splitContext != null) {
            parentCategoryName = splitContext.parentCategoryName;
            categoryName = splitContext.categoryName;
        }
        if (!StringUtils.hasText(parentCategoryName)) {
            parentCategoryName = inferParentBefore(sourceLines, contextParts.firstCategoryIndex, categoryName);
        }
        if (!StringUtils.hasText(categoryName)) {
            categoryName = inferCategoryName(sourceText, sourceRowIds, parentCategoryName);
        }
        if (sameText(parentCategoryName, categoryName)) {
            parentCategoryName = inferParentBefore(sourceLines, contextParts.firstCategoryIndex, parentCategoryName);
        }
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        SourceRateContext rateContext = firstRateIndex >= 0
                ? inferSourceRateContext(sourceLines, firstRateIndex, payload)
                : null;
        return new SourceCategoryContext(parentCategoryName, categoryName, rateContext, firstRateIndex >= 0);
    }

    private static int selectBestSourceRateIndex(
            List<SourceLine> sourceLines,
            List<Integer> targetIndices,
            Map<String, Object> payload
    ) {
        String desiredRate = firstPercentNumber(text(payload.get("commissionRate")));
        if (!StringUtils.hasText(desiredRate)) {
            desiredRate = firstNumber(text(payload.get("commissionRate")));
        }
        String desiredBrand = normalizeBrandRestrictionValue(text(payload.get("brandRestriction")));
        int minTargetIndex = targetIndices.stream().mapToInt(Integer::intValue).min().orElse(-1);
        int maxTargetIndex = targetIndices.stream().mapToInt(Integer::intValue).max().orElse(-1);
        if (minTargetIndex < 0 || maxTargetIndex < 0) {
            return -1;
        }
        int scanStart = Math.max(0, minTargetIndex - 12);
        int scanEnd = Math.min(sourceLines.size() - 1, maxTargetIndex + 12);
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int index = scanStart; index <= scanEnd; index++) {
            String text = sourceLines.get(index).text;
            if (!isCommissionRateText(text)) {
                continue;
            }
            boolean sameRate = !StringUtils.hasText(desiredRate) || containsRate(text, desiredRate);
            if (StringUtils.hasText(desiredRate) && !sameRate) {
                continue;
            }
            int score = 0;
            if (targetIndices.contains(index)) {
                score += 30;
            }
            if (sameRate) {
                score += 100;
            }
            score += brandMatchScore(text, desiredBrand);
            if (StringUtils.hasText(normalizeInlineCategoryWithRate(text))) {
                score += 8;
            }
            score -= nearestDistance(index, targetIndices);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }
        if (bestIndex >= 0) {
            return bestIndex;
        }
        for (Integer index : targetIndices) {
            if (isCommissionRateText(sourceLines.get(index).text)) {
                return index;
            }
        }
        return -1;
    }

    private static int brandMatchScore(String sourceText, String desiredBrand) {
        String lower = normalizeCategoryName(sourceText).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(desiredBrand) || "全部".equals(desiredBrand)) {
            return (lower.contains("generic") || lower.contains("other brand") || lower.contains("top brand")) ? -10 : 6;
        }
        String desired = desiredBrand.toLowerCase(Locale.ROOT);
        if (desired.contains("generic")) {
            return lower.contains("generic") ? 50 : -25;
        }
        if (desired.contains("other brand")) {
            return lower.contains("other brand") ? 50 : -25;
        }
        if (desired.contains("top brand")) {
            return lower.contains("top brand") ? 50 : -25;
        }
        return lower.contains(desired) ? 30 : 0;
    }

    private static int nearestDistance(int index, List<Integer> targetIndices) {
        int distance = Integer.MAX_VALUE;
        for (Integer targetIndex : targetIndices) {
            distance = Math.min(distance, Math.abs(index - targetIndex));
        }
        return distance == Integer.MAX_VALUE ? 0 : distance;
    }

    private static boolean containsRate(String sourceText, String desiredRate) {
        if (!StringUtils.hasText(sourceText) || !StringUtils.hasText(desiredRate)) {
            return false;
        }
        java.math.BigDecimal desired;
        try {
            desired = new java.math.BigDecimal(desiredRate.trim()).stripTrailingZeros();
        } catch (NumberFormatException ignored) {
            return sourceText.contains(desiredRate);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*%")
                .matcher(sourceText);
        while (matcher.find()) {
            try {
                java.math.BigDecimal current = new java.math.BigDecimal(matcher.group(1)).stripTrailingZeros();
                if (current.compareTo(desired) == 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Continue scanning other rate candidates.
            }
        }
        return false;
    }

    private static SourceRateContext inferSourceRateContext(
            List<SourceLine> sourceLines,
            int rateIndex,
            Map<String, Object> payload
    ) {
        String contextText = sourceRateContextText(sourceLines, rateIndex);
        SourceRateContext context = new SourceRateContext();
        context.brandRestriction = sourceBrandRestriction(contextText);
        context.effectiveDate = inferEffectiveDate(contextText);
        String country = text(payload.get("country"));
        String currency = inferCurrency(contextText, country);
        if (StringUtils.hasText(currency)) {
            context.amountCurrency = currency.toUpperCase(Locale.ROOT);
        }
        applyAmountRangeFromSourceText(context, contextText, country);
        if (!StringUtils.hasText(context.amountRangeLabel)) {
            context.amountRangeLabel = "全部";
        }
        return context;
    }

    private static String sourceRateContextText(List<SourceLine> sourceLines, int rateIndex) {
        List<String> parts = new java.util.ArrayList<>();
        int end = Math.min(sourceLines.size() - 1, rateIndex + 4);
        for (int index = rateIndex; index <= end; index++) {
            String value = sourceLines.get(index).text;
            if (index > rateIndex && countPercentageRates(value) > 0) {
                break;
            }
            String candidate = normalizeCategoryCandidate(value);
            if (index > rateIndex
                    && isKnownCommissionParentCategory(candidate)
                    && !isCommissionRateText(value)) {
                break;
            }
            parts.add(value);
        }
        return normalizeCategoryName(String.join(" ", parts));
    }

    private static String sourceBrandRestriction(String sourceText) {
        String lower = normalizeCategoryName(sourceText).toLowerCase(Locale.ROOT);
        if (lower.contains("generic")) {
            return "Generic brand";
        }
        if (lower.contains("other brand")) {
            return "All other brands";
        }
        if (lower.contains("top brand")) {
            return "Top brands";
        }
        return "";
    }

    private static void applyAmountRangeFromSourceText(SourceRateContext context, String sourceText, String country) {
        String normalized = normalizeCategoryName(sourceText);
        String currency = inferCurrency(normalized, country);
        String suffix = StringUtils.hasText(currency) ? " " + currency.toUpperCase(Locale.ROOT) : "";
        java.util.regex.Matcher upperBefore = java.util.regex.Pattern
                .compile("(?i)(?:up\\s+to|<=|less\\s+than\\s+or\\s+equal\\s+to)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(SAR|AED)?")
                .matcher(normalized);
        java.util.regex.Matcher upperAfter = java.util.regex.Pattern
                .compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(SAR|AED)?\\s*(?:or\\s+less|and\\s+below)")
                .matcher(normalized);
        if (upperBefore.find() || upperAfter.find()) {
            java.util.regex.Matcher matcher = upperBefore.find(0) ? upperBefore : upperAfter;
            String amount = normalizeLooseDecimal(matcher.group(1));
            String explicitCurrency = matcher.group(2);
            if (StringUtils.hasText(explicitCurrency)) {
                suffix = " " + explicitCurrency.toUpperCase(Locale.ROOT);
                context.amountCurrency = explicitCurrency.toUpperCase(Locale.ROOT);
            }
            context.amountRangeLabel = "<= " + amount + suffix;
            context.amountMin = null;
            context.amountMinInclusive = null;
            context.amountMax = amount;
            context.amountMaxInclusive = true;
            return;
        }
        java.util.regex.Matcher lower = java.util.regex.Pattern
                .compile("(?i)(?:greater\\s+than|above|>)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(SAR|AED)?")
                .matcher(normalized);
        if (lower.find()) {
            String amount = normalizeLooseDecimal(lower.group(1));
            String explicitCurrency = lower.group(2);
            if (StringUtils.hasText(explicitCurrency)) {
                suffix = " " + explicitCurrency.toUpperCase(Locale.ROOT);
                context.amountCurrency = explicitCurrency.toUpperCase(Locale.ROOT);
            }
            context.amountRangeLabel = "> " + amount + suffix;
            context.amountMin = amount;
            context.amountMinInclusive = false;
            context.amountMax = null;
            context.amountMaxInclusive = null;
        }
    }

    private static CategoryContextParts collectCategoryPartsBeforeRate(List<SourceLine> sourceLines, int rateIndex) {
        if (sourceLines == null || sourceLines.isEmpty()) {
            return null;
        }
        List<String> reversedParts = new java.util.ArrayList<>();
        int firstCategoryIndex = -1;
        int startIndex = Math.min(rateIndex - 1, sourceLines.size() - 1);
        int scanStart = Math.max(0, startIndex - 12);
        for (int index = startIndex; index >= scanStart; index--) {
            String rawText = sourceLines.get(index).text;
            if (rawText.contains("|") || rawText.contains("\t") || isCommissionRateText(rawText)) {
                if (!reversedParts.isEmpty()) {
                    break;
                }
                continue;
            }
            String candidate = normalizeCategoryCandidate(rawText);
            if (!StringUtils.hasText(candidate) || shouldIgnoreCategoryCandidate(candidate)) {
                if (!reversedParts.isEmpty()) {
                    break;
                }
                continue;
            }
            reversedParts.add(0, candidate);
            firstCategoryIndex = index;
            if (isKnownCommissionParentCategory(candidate) && reversedParts.size() > 1) {
                break;
            }
        }
        if (reversedParts.isEmpty()) {
            return null;
        }
        return new CategoryContextParts(reversedParts, firstCategoryIndex);
    }

    private static SourceCategoryContext splitKnownParentPrefixedCategory(String categoryName) {
        String category = normalizeCategoryName(categoryName);
        if (!StringUtils.hasText(category)) {
            return null;
        }
        for (String parent : knownCommissionParentCategories()) {
            if (!category.toLowerCase(Locale.ROOT).startsWith(parent.toLowerCase(Locale.ROOT) + " ")) {
                continue;
            }
            String leaf = normalizeCategoryName(category.substring(parent.length()));
            if (StringUtils.hasText(leaf) && !sameText(parent, leaf) && !"All".equalsIgnoreCase(leaf)) {
                return new SourceCategoryContext(parent, leaf);
            }
        }
        return null;
    }

    private static String normalizeInlineCategoryWithRate(String value) {
        String normalized = normalizeCategoryName(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        normalized = normalized.replaceAll("(?i)\\s+some\\s+exceptions.*$", "").trim();
        normalized = normalized.replaceAll("(?i)\\s+[0-9]+(?:\\.[0-9]+)?\\s*%.*$", "").trim();
        return normalizeCategoryCandidate(normalized);
    }

    private static boolean isCommissionRateText(String value) {
        String normalized = normalizeCategoryName(value).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.matches(".*[0-9]+(?:\\.[0-9]+)?\\s*%.*")
                || normalized.contains("sale price")
                || normalized.contains("sales price")
                || normalized.contains("greater than")
                || normalized.contains("up to")
                || normalized.contains("or less");
    }

    private static String inferParentBefore(List<SourceLine> sourceLines, int firstTargetIndex, String excluded) {
        int scanStart = Math.max(0, firstTargetIndex - 40);
        for (int index = firstTargetIndex - 1; index >= scanStart; index--) {
            String candidate = normalizeCategoryName(sourceLines.get(index).text);
            if (isKnownCommissionParentCategory(candidate) && !sameText(candidate, excluded)) {
                return candidate;
            }
        }
        return "";
    }

    private static String inferCategoryName(String sourceText, List<Long> sourceRowIds, String parentCategoryName) {
        List<SourceLine> sourceLines = parseSourceLines(sourceText);
        if (sourceLines.isEmpty() || sourceRowIds == null || sourceRowIds.isEmpty()) {
            return "";
        }
        Set<Long> targetIds = sourceRowIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int firstTargetIndex = -1;
        for (int index = 0; index < sourceLines.size(); index++) {
            if (targetIds.contains(sourceLines.get(index).sourceRowId)) {
                firstTargetIndex = index;
                break;
            }
        }
        if (firstTargetIndex <= 0) {
            return "";
        }
        int scanStart = Math.max(0, firstTargetIndex - 12);
        for (int index = firstTargetIndex - 1; index >= scanStart; index--) {
            String candidate = normalizeCategoryCandidate(sourceLines.get(index).text);
            if (!StringUtils.hasText(candidate) || shouldIgnoreCategoryCandidate(candidate)) {
                continue;
            }
            if (StringUtils.hasText(parentCategoryName) && sameText(candidate, parentCategoryName)) {
                continue;
            }
            return candidate;
        }
        return "";
    }

    private static String normalizeCategoryCandidate(String value) {
        String normalized = normalizeCategoryName(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        normalized = normalized.replaceAll("(?i)\\s+all$", "").trim();
        normalized = normalized.replaceAll("(?i)\\s+[0-9]+(?:\\.[0-9]+)?\\s*%.*$", "").trim();
        return normalizeCategoryName(normalized);
    }

    private static boolean shouldIgnoreCategoryCandidate(String value) {
        String lower = normalizeCategoryName(value).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(lower)) {
            return true;
        }
        if (!lower.matches(".*[a-z0-9].*")) {
            return true;
        }
        if (lower.matches(".*\\b(sar|aed)\\b.*") || lower.matches(".*[0-9]+\\s*%.*")) {
            return true;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return true;
        }
        if (lower.contains("referral fees is applicable")
                || lower.contains("exceptions sheet")
                || lower.contains("kindly refer")
                || lower.contains("minimum referral fee")
                || lower.contains("fulfilled by noon")
                || lower.contains("seller terms")
                || lower.contains("exclusive of vat")
                || lower.contains("sales price")
                || lower.contains("sale price")
                || lower.contains("greater than")
                || lower.contains("up to")
                || lower.contains("or less")
                || lower.contains("portion of the total")) {
            return true;
        }
        return Set.of(
                "some exceptions",
                "apply",
                "to the exceptions",
                "sheet",
                "find the top brands",
                "items with a sales price greater",
                "total sales price greater than",
                "sale price",
                "remarks",
                "product family product type"
        ).contains(lower);
    }

    private static List<SourceLine> parseSourceLines(String sourceText) {
        if (!StringUtils.hasText(sourceText)) {
            return List.of();
        }
        List<SourceLine> sourceLines = new java.util.ArrayList<>();
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
                sourceLines.add(new SourceLine(currentId, line));
                currentId = null;
            }
        }
        return sourceLines;
    }

    private static boolean isKnownCommissionParentCategory(String value) {
        String normalized = normalizeCategoryName(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return knownCommissionParentCategories().contains(normalized);
    }

    private static Set<String> knownCommissionParentCategories() {
        return Set.of(
                "Appliances",
                "Audio & Video",
                "Baby & Kids",
                "Automotive",
                "Bags & Luggage",
                "Beauty",
                "Books & Media",
                "Camera",
                "Cameras",
                "Electronics",
                "Fashion",
                "Fragrance",
                "Grocery",
                "Health & Beauty",
                "Hair & Personal Care",
                "Headphones",
                "Health & Nutrition",
                "Health Nutrition",
                "Home & Kitchen",
                "Home",
                "Jewelry",
                "Large Appliances",
                "Mobiles",
                "Office Electronics",
                "Other Categories",
                "PC Store",
                "Pets",
                "Small Appliances",
                "Sports & Outdoors",
                "Toys & Games",
                "Video Games",
                "Wearables"
        );
    }

    private static String buildCategoryPath(String parentCategoryName, String categoryName) {
        String parent = normalizeCategoryName(parentCategoryName);
        String leaf = normalizeCategoryName(categoryName);
        if (!StringUtils.hasText(leaf)) {
            return "";
        }
        if (!StringUtils.hasText(parent) || sameText(parent, leaf) || leaf.toLowerCase(Locale.ROOT).startsWith(parent.toLowerCase(Locale.ROOT) + " >")) {
            return normalizeCategoryPath(leaf);
        }
        return normalizeCategoryPath(parent + " > " + leaf);
    }

    private static String parentFromCategoryPath(String categoryPath) {
        List<String> parts = splitCategoryPath(categoryPath);
        return parts.size() >= 2 ? parts.get(parts.size() - 2) : "";
    }

    private static String leafFromCategoryPath(String categoryPath) {
        List<String> parts = splitCategoryPath(categoryPath);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }

    private static List<String> splitCategoryPath(String categoryPath) {
        if (!StringUtils.hasText(categoryPath)) {
            return List.of();
        }
        String[] parts = categoryPath.split("\\s*>\\s*");
        List<String> values = new java.util.ArrayList<>();
        for (String part : parts) {
            String value = normalizeCategoryName(part);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private static boolean categoryPathStartsWithParent(String categoryPath, String parentCategoryName) {
        List<String> parts = splitCategoryPath(categoryPath);
        return parts.size() >= 2 && sameText(parts.get(0), parentCategoryName);
    }

    private static String normalizeCategoryPath(String value) {
        String normalized = normalizeCategoryName(value);
        return StringUtils.hasText(normalized) ? normalized.replaceAll("\\s*>\\s*", " > ") : "";
    }

    private static String normalizeCategoryName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static boolean sameText(String left, String right) {
        return normalizeCategoryName(left).equalsIgnoreCase(normalizeCategoryName(right));
    }

    private static void normalizeCommissionRate(Map<String, Object> payload) {
        String commissionRate = text(payload.get("commissionRate"));
        if (!StringUtils.hasText(commissionRate)) {
            return;
        }
        String normalizedRate = commissionRate.replaceAll("\\s+", " ").trim();
        if (containsTierExpression(normalizedRate) || containsMultiplePercentageRates(normalizedRate)) {
            payload.put("commissionRate", normalizedRate);
            return;
        }
        String percentNumber = firstPercentNumber(normalizedRate);
        String plainRate = StringUtils.hasText(percentNumber) ? percentNumber : normalizedRate.replace("%", "").trim();
        try {
            plainRate = new java.math.BigDecimal(plainRate).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            plainRate = normalizedRate;
        }
        if (!plainRate.endsWith("%")) {
            plainRate = plainRate + "%";
        }
        payload.put("commissionRate", plainRate);
    }

    private static void normalizeCommissionBrandRestriction(Map<String, Object> payload, String sourceRowText) {
        String configured = normalizeBrandRestrictionValue(text(payload.get("brandRestriction")));
        if (!StringUtils.hasText(configured) || "全部".equals(configured)) {
            String commissionRate = text(payload.get("commissionRate"));
            String inferred = inferBrandRestrictionFromText(commissionRate);
            if (!StringUtils.hasText(inferred)) {
                inferred = inferBrandRestrictionFromSourceForRate(sourceRowText, commissionRate);
            }
            if (StringUtils.hasText(inferred)) {
                configured = inferred;
            }
        }
        payload.put("brandRestriction", StringUtils.hasText(configured) ? configured : "全部");
    }

    private static String normalizeBrandRestrictionValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("all".equals(lower)
                || "all brands".equals(lower)
                || "any brand".equals(lower)
                || "no restriction".equals(lower)
                || "全部".equals(normalized)
                || "所有品牌".equals(normalized)
                || "不限品牌".equals(normalized)) {
            return "全部";
        }
        if (lower.contains("generic")) {
            return "Generic brand";
        }
        if (lower.contains("other brand")) {
            return "All other brands";
        }
        return normalized;
    }

    private static String inferBrandRestrictionFromText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.contains("generic") && !lower.contains("other brand")) {
            return "";
        }
        String normalized = normalizeBrandRestrictionValue(value);
        return "全部".equals(normalized) ? "" : normalized;
    }

    private static String inferBrandRestrictionFromSourceForRate(String sourceRowText, String commissionRate) {
        if (!StringUtils.hasText(sourceRowText) || !StringUtils.hasText(commissionRate)) {
            return "";
        }
        String currentRate = firstPercentNumber(commissionRate);
        if (!StringUtils.hasText(currentRate)) {
            currentRate = firstNumber(commissionRate);
        }
        if (!StringUtils.hasText(currentRate)) {
            return "";
        }
        String source = sourceRowText.replaceAll("\\s+", " ");
        String genericRate = firstRateNearBrand(source, "generic\\s+brands?");
        if (sameRate(currentRate, genericRate)) {
            return "Generic brand";
        }
        String otherRate = firstRateNearBrand(source, "(?:all\\s+)?other\\s+brands?");
        if (sameRate(currentRate, otherRate)) {
            return "All other brands";
        }
        String inferred = inferBrandRestrictionFromText(source);
        return containsSingleBrandRestriction(source) ? inferred : "";
    }

    private static boolean containsSingleBrandRestriction(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        boolean generic = lower.contains("generic");
        boolean other = lower.contains("other brand");
        return generic ^ other;
    }

    private static String firstRateNearBrand(String source, String brandRegex) {
        java.util.regex.Pattern rateBeforeBrand = java.util.regex.Pattern.compile(
                "([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*(?:for\\s+)?" + brandRegex,
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher beforeMatcher = rateBeforeBrand.matcher(source);
        if (beforeMatcher.find()) {
            return beforeMatcher.group(1);
        }
        java.util.regex.Pattern brandBeforeRate = java.util.regex.Pattern.compile(
                brandRegex + "\\D{0,30}?([0-9]+(?:\\.[0-9]+)?)\\s*%",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher afterMatcher = brandBeforeRate.matcher(source);
        return afterMatcher.find() ? afterMatcher.group(1) : "";
    }

    private static boolean sameRate(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        try {
            java.math.BigDecimal leftDecimal = new java.math.BigDecimal(left.trim()).stripTrailingZeros();
            java.math.BigDecimal rightDecimal = new java.math.BigDecimal(right.trim()).stripTrailingZeros();
            return leftDecimal.compareTo(rightDecimal) == 0;
        } catch (NumberFormatException ignored) {
            return left.trim().equalsIgnoreCase(right.trim());
        }
    }

    private static boolean containsTierExpression(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.contains("up to")
                || lower.contains("above")
                || lower.contains("then")
                || lower.contains("sar")
                || lower.contains("aed")
                || lower.contains("<")
                || lower.contains(">");
    }

    private static boolean containsMultiplePercentageRates(String value) {
        return countPercentageRates(value) > 1;
    }

    private static int countPercentageRates(String value) {
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

    private static String firstPercentNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*%")
                .matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String firstNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)")
                .matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void normalizeCommissionFulfillmentType(Map<String, Object> payload, String sourceRowText) {
        if (!StringUtils.hasText(sourceRowText)) {
            return;
        }
        String upper = sourceRowText.toUpperCase(Locale.ROOT);
        if (upper.contains("FBN")) {
            payload.put("fulfillmentType", "FBN");
        } else if (upper.contains("FBP")) {
            payload.put("fulfillmentType", "FBP");
        } else if (upper.contains("FBM")) {
            payload.put("fulfillmentType", "FBM");
        } else {
            payload.put("fulfillmentType", null);
        }
    }

    private static void normalizeCommissionAmountRangeFields(Map<String, Object> payload) {
        String label = text(payload.get("amountRangeLabel"));
        if (!StringUtils.hasText(label)) {
            return;
        }
        String normalized = label.trim();
        if (isAllRange(normalized) || "全部".equals(normalized)) {
            payload.put("amountMin", null);
            payload.put("amountMinInclusive", null);
            payload.put("amountMax", null);
            payload.put("amountMaxInclusive", null);
            payload.put("amountRangeLabel", "全部");
            return;
        }
        java.util.regex.Matcher upperBound = java.util.regex.Pattern
                .compile("^(<=|<)\\s*([0-9]+(?:\\.[0-9]+)?)")
                .matcher(normalized);
        if (upperBound.find()) {
            payload.put("amountMin", null);
            payload.put("amountMinInclusive", null);
            payload.put("amountMax", normalizeLooseDecimal(upperBound.group(2)));
            payload.put("amountMaxInclusive", "<=".equals(upperBound.group(1)));
            return;
        }
        java.util.regex.Matcher lowerBound = java.util.regex.Pattern
                .compile("^(>=|>)\\s*([0-9]+(?:\\.[0-9]+)?)")
                .matcher(normalized);
        if (lowerBound.find()) {
            payload.put("amountMin", normalizeLooseDecimal(lowerBound.group(2)));
            payload.put("amountMinInclusive", ">=".equals(lowerBound.group(1)));
            payload.put("amountMax", null);
            payload.put("amountMaxInclusive", null);
        }
    }

    private static String inferCurrency(String amountRangeLabel, String country) {
        String rangeUpper = amountRangeLabel == null ? "" : amountRangeLabel.toUpperCase(Locale.ROOT);
        if (rangeUpper.contains("SAR")) {
            return "SAR";
        }
        if (rangeUpper.contains("AED")) {
            return "AED";
        }
        String countryUpper = country == null ? "" : country.toUpperCase(Locale.ROOT);
        if ("KSA".equals(countryUpper) || "SA".equals(countryUpper)) {
            return "SAR";
        }
        if ("UAE".equals(countryUpper) || "AE".equals(countryUpper)) {
            return "AED";
        }
        return null;
    }

    private static String deriveRangeLabel(Map<String, Object> payload, String currency) {
        String min = text(payload.get("amountMin"));
        String max = text(payload.get("amountMax"));
        String suffix = StringUtils.hasText(currency) ? " " + currency : "";
        if (!StringUtils.hasText(min) && !StringUtils.hasText(max)) {
            return "全部";
        }
        if (!StringUtils.hasText(min)) {
            boolean inclusive = booleanValue(payload.get("amountMaxInclusive"), true);
            return (inclusive ? "<= " : "< ") + max + suffix;
        }
        if (!StringUtils.hasText(max)) {
            boolean inclusive = booleanValue(payload.get("amountMinInclusive"), false);
            return (inclusive ? ">= " : "> ") + min + suffix;
        }
        return min + suffix + " - " + max + suffix;
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : defaultValue;
    }

    private static boolean isAllRange(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return "all".equalsIgnoreCase(normalized)
                || "全部".equals(normalized)
                || "全量".equals(normalized)
                || "所有".equals(normalized);
    }

    private static void putIfAbsent(Map<String, Object> payload, String field, Object value) {
        if (!payload.containsKey(field)) {
            payload.put(field, value);
        }
    }

    private static void normalizeTextField(Map<String, Object> payload, String field) {
        String value = text(payload.get(field));
        if (StringUtils.hasText(value)) {
            payload.put(field, value.replaceAll("\\s+", " "));
        }
    }

    private static void putText(Map<String, Object> payload, String field, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(field, value.replaceAll("\\s+", " ").trim());
        }
    }

    private static String cell(String[] cells, int index) {
        return index >= 0 && index < cells.length ? text(cells[index]) : null;
    }

    private static boolean isCurrency(String value) {
        String normalized = text(value);
        return "CNY".equalsIgnoreCase(normalized)
                || "USD".equalsIgnoreCase(normalized)
                || "SAR".equalsIgnoreCase(normalized)
                || "AED".equalsIgnoreCase(normalized);
    }

    private static boolean isNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            new java.math.BigDecimal(value.trim());
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String normalizeDecimal(String value) {
        try {
            return new java.math.BigDecimal(value.trim()).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static String normalizeLooseDecimal(String value) {
        try {
            return new java.math.BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private static String normalizeNullLike(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if ("null".equals(lower) || "n/a".equals(lower) || "none".equals(lower) || "-".equals(lower)) {
            return "";
        }
        return value.trim();
    }

    private static String text(Object value) {
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

    private static final class CategoryContextParts {
        private final List<String> categoryParts;
        private final int firstCategoryIndex;

        private CategoryContextParts(List<String> categoryParts, int firstCategoryIndex) {
            this.categoryParts = categoryParts;
            this.firstCategoryIndex = firstCategoryIndex;
        }
    }

    private static final class SourceCategoryContext {
        private final String parentCategoryName;
        private final String categoryName;
        private final String brandRestriction;
        private final String amountRangeLabel;
        private final String amountMin;
        private final Boolean amountMinInclusive;
        private final String amountMax;
        private final Boolean amountMaxInclusive;
        private final String amountCurrency;
        private final String effectiveDate;
        private final boolean strongEvidence;

        private SourceCategoryContext(String parentCategoryName, String categoryName) {
            this(parentCategoryName, categoryName, null, true);
        }

        private SourceCategoryContext(
                String parentCategoryName,
                String categoryName,
                SourceRateContext rateContext,
                boolean strongEvidence
        ) {
            this.parentCategoryName = parentCategoryName;
            this.categoryName = categoryName;
            this.brandRestriction = rateContext == null ? "" : rateContext.brandRestriction;
            this.amountRangeLabel = rateContext == null ? "" : rateContext.amountRangeLabel;
            this.amountMin = rateContext == null ? null : rateContext.amountMin;
            this.amountMinInclusive = rateContext == null ? null : rateContext.amountMinInclusive;
            this.amountMax = rateContext == null ? null : rateContext.amountMax;
            this.amountMaxInclusive = rateContext == null ? null : rateContext.amountMaxInclusive;
            this.amountCurrency = rateContext == null ? "" : rateContext.amountCurrency;
            this.effectiveDate = rateContext == null ? "" : rateContext.effectiveDate;
            this.strongEvidence = strongEvidence;
        }
    }

    private static final class SourceRateContext {
        private String brandRestriction;
        private String amountRangeLabel;
        private String amountMin;
        private Boolean amountMinInclusive;
        private String amountMax;
        private Boolean amountMaxInclusive;
        private String amountCurrency;
        private String effectiveDate;
    }
}
