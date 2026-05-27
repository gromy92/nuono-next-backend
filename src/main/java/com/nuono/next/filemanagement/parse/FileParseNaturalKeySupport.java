package com.nuono.next.filemanagement.parse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class FileParseNaturalKeySupport {

    private FileParseNaturalKeySupport() {
    }

    static String buildNaturalKey(String itemType, Map<String, Object> payload) {
        if ("logistics_channel_rule".equals(itemType)) {
            return buildLogisticsNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(itemType)) {
            return buildLogisticsServiceLineNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.CARGO_CATEGORY.equals(itemType)) {
            return buildLogisticsCargoCategoryNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.BASE_PRICE.equals(itemType)) {
            return buildLogisticsBasePriceNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.SURCHARGE.equals(itemType)) {
            return buildLogisticsSurchargeNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.BILLING_RULE.equals(itemType)) {
            return buildLogisticsBillingRuleNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE.equals(itemType)) {
            return buildLogisticsWarehouseFeeNaturalKey(payload);
        }
        if (FileParseLogisticsQuoteStandard.RESTRICTION.equals(itemType)) {
            return buildLogisticsRestrictionNaturalKey(payload);
        }
        if (FileParseOfficialOutboundFeeStandard.SIZE_CLASSIFICATION.equals(itemType)) {
            return buildOfficialOutboundSizeClassificationNaturalKey(payload);
        }
        if (FileParseOfficialOutboundFeeStandard.FEE_WEIGHT_SLAB.equals(itemType)) {
            return buildOfficialOutboundFeeSlabNaturalKey(payload);
        }
        if (FileParseOfficialOutboundFeeStandard.CALCULATION_POLICY.equals(itemType)) {
            return buildOfficialOutboundPolicyNaturalKey(payload);
        }
        if ("commission_rule".equals(itemType)) {
            return buildCommissionNaturalKey(payload);
        }
        if ("outbound_fee_rule".equals(itemType)) {
            return buildOutboundFeeNaturalKey(payload);
        }
        return null;
    }

    private static String buildOutboundFeeNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String feeItem = normalizeText(text(payload.get("feeItem")));
        String sizeTier = normalizeText(text(payload.get("sizeTier")));
        if (!StringUtils.hasText(country) || !StringUtils.hasText(feeItem)) {
            return null;
        }
        return String.join("|",
                country,
                feeItem,
                StringUtils.hasText(sizeTier) ? sizeTier : "ALL",
                normalizeUpper(text(payload.get("currency"))),
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildOfficialOutboundSizeClassificationNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String platform = normalizeUpper(text(payload.get("platform")));
        String fulfillmentType = normalizeUpper(text(payload.get("fulfillmentType")));
        String classificationName = normalizeText(text(payload.get("classificationName")));
        if (!StringUtils.hasText(country)
                || !StringUtils.hasText(platform)
                || !StringUtils.hasText(fulfillmentType)
                || !StringUtils.hasText(classificationName)) {
            return null;
        }
        return String.join("|",
                country,
                platform,
                fulfillmentType,
                classificationName,
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildOfficialOutboundFeeSlabNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String platform = normalizeUpper(text(payload.get("platform")));
        String fulfillmentType = normalizeUpper(text(payload.get("fulfillmentType")));
        String classificationName = normalizeText(text(payload.get("classificationName")));
        String currency = normalizeUpper(text(payload.get("currency")));
        if (!StringUtils.hasText(country)
                || !StringUtils.hasText(platform)
                || !StringUtils.hasText(fulfillmentType)
                || !StringUtils.hasText(classificationName)
                || !StringUtils.hasText(currency)) {
            return null;
        }
        return String.join("|",
                country,
                platform,
                fulfillmentType,
                classificationName,
                normalizeWeightRange(payload),
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildOfficialOutboundPolicyNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String platform = normalizeUpper(text(payload.get("platform")));
        String fulfillmentType = normalizeUpper(text(payload.get("fulfillmentType")));
        if (!StringUtils.hasText(country)
                || !StringUtils.hasText(platform)
                || !StringUtils.hasText(fulfillmentType)) {
            return null;
        }
        return String.join("|",
                country,
                platform,
                fulfillmentType,
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildCommissionNaturalKey(Map<String, Object> payload) {
        String country = normalizeUpper(text(payload.get("country")));
        String categoryName = normalizeText(text(payload.get("categoryName")));
        if (!StringUtils.hasText(country)
                || !StringUtils.hasText(categoryName)) {
            return null;
        }
        return String.join("|",
                country,
                normalizeCategoryIdentity(payload),
                normalizeBrandRestriction(text(payload.get("brandRestriction"))),
                normalizeRange(payload),
                normalizeUpper(text(payload.get("amountCurrency"))),
                normalizeText(text(payload.get("effectiveDate")))
        );
    }

    private static String buildLogisticsNaturalKey(Map<String, Object> payload) {
        String channelKey = normalizeText(text(payload.get("channelKey")));
        String feeItem = normalizeText(text(payload.get("feeItem")));
        if (!StringUtils.hasText(channelKey) || !StringUtils.hasText(feeItem)) {
            return null;
        }
        return String.join("|",
                channelKey,
                normalizeUpper(text(payload.get("country"))),
                normalizeText(text(payload.get("city"))),
                normalizeText(text(payload.get("shippingMethod"))),
                feeItem
        );
    }

    private static String buildLogisticsServiceLineNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                FileParseLogisticsQuoteStandard.SERVICE_LINE,
                payload
        );
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String country = normalizeUpper(text(normalized.get("country")));
        String transportMode = normalizeText(text(normalized.get("transportMode")));
        String serviceScope = normalizeText(text(normalized.get("serviceScope")));
        String destinationNode = normalizeText(text(normalized.get("destinationNode")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(country)
                || !StringUtils.hasText(transportMode)
                || !StringUtils.hasText(serviceScope)
                || !StringUtils.hasText(destinationNode)) {
            return null;
        }
        return String.join("|", forwarderCode, country, transportMode, serviceScope, destinationNode);
    }

    private static String buildLogisticsCargoCategoryNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                FileParseLogisticsQuoteStandard.CARGO_CATEGORY,
                payload
        );
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String serviceLineKey = normalizeText(text(normalized.get("serviceLineKey")));
        String categoryCode = normalizeUpper(text(normalized.get("categoryCode")));
        String categoryName = normalizeText(text(normalized.get("categoryName")));
        String categoryIdentity = StringUtils.hasText(categoryCode) ? categoryCode : categoryName;
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(serviceLineKey)
                || !StringUtils.hasText(categoryIdentity)) {
            return null;
        }
        return String.join("|", forwarderCode, serviceLineKey, categoryIdentity);
    }

    private static String buildLogisticsBasePriceNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(
                FileParseLogisticsQuoteStandard.BASE_PRICE,
                payload
        );
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String serviceLineKey = normalizeText(text(normalized.get("serviceLineKey")));
        String cargoCategoryKey = normalizeUpper(text(normalized.get("cargoCategoryKey")));
        String pricingModel = normalizeText(text(normalized.get("pricingModel")));
        String billingUnit = normalizeText(text(normalized.get("billingUnit")));
        String currency = normalizeUpper(text(normalized.get("currency")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(serviceLineKey)
                || !StringUtils.hasText(pricingModel)
                || !StringUtils.hasText(billingUnit)
                || !StringUtils.hasText(currency)) {
            return null;
        }
        return String.join("|",
                forwarderCode,
                serviceLineKey,
                StringUtils.hasText(cargoCategoryKey) ? cargoCategoryKey : "*",
                pricingModel,
                billingUnit,
                currency
        );
    }

    private static String buildLogisticsSurchargeNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.SURCHARGE, payload);
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String serviceLineKey = normalizeText(text(normalized.get("serviceLineKey")));
        String surchargeName = normalizeText(text(normalized.get("surchargeName")));
        String triggerCondition = normalizeText(text(normalized.get("triggerCondition")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(serviceLineKey)
                || !StringUtils.hasText(surchargeName)) {
            return null;
        }
        return String.join("|", forwarderCode, serviceLineKey, surchargeName, triggerCondition);
    }

    private static String buildLogisticsBillingRuleNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.BILLING_RULE, payload);
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String serviceLineKey = normalizeText(text(normalized.get("serviceLineKey")));
        String ruleName = normalizeText(text(normalized.get("ruleName")));
        String conditionText = normalizeText(text(normalized.get("conditionText")));
        String actionText = normalizeText(text(normalized.get("actionText")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(serviceLineKey)
                || !StringUtils.hasText(ruleName)) {
            return null;
        }
        return String.join("|", forwarderCode, serviceLineKey, ruleName, conditionText, actionText);
    }

    private static String buildLogisticsWarehouseFeeNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE, payload);
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String warehouseNode = normalizeText(text(normalized.get("warehouseNode")));
        String serviceName = normalizeText(text(normalized.get("serviceName")));
        String feeType = normalizeText(text(normalized.get("feeType")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(warehouseNode)
                || !StringUtils.hasText(serviceName)
                || !StringUtils.hasText(feeType)) {
            return null;
        }
        return String.join("|", forwarderCode, warehouseNode, serviceName, feeType);
    }

    private static String buildLogisticsRestrictionNaturalKey(Map<String, Object> payload) {
        Map<String, Object> normalized = FileParseLogisticsPayloadNormalizer.normalize(FileParseLogisticsQuoteStandard.RESTRICTION, payload);
        String forwarderCode = normalizeText(text(normalized.get("forwarderCode")));
        String serviceLineKey = normalizeText(text(normalized.get("serviceLineKey")));
        String restrictionType = normalizeText(text(normalized.get("restrictionType")));
        String itemText = normalizeText(text(normalized.get("itemText")));
        if (!StringUtils.hasText(forwarderCode)
                || !StringUtils.hasText(serviceLineKey)
                || !StringUtils.hasText(restrictionType)
                || !StringUtils.hasText(itemText)) {
            return null;
        }
        return String.join("|", forwarderCode, serviceLineKey, restrictionType, itemText);
    }

    static String naturalKeyHash(String itemType, String naturalKey) {
        return sha256(itemType + "|" + naturalKey);
    }

    static String matchKey(String itemType, Map<String, Object> payload, String fallbackNaturalKeyHash) {
        String naturalKey = buildNaturalKey(itemType, payload);
        if (StringUtils.hasText(naturalKey)) {
            return itemType + "|" + naturalKey;
        }
        return itemType + "|" + (StringUtils.hasText(fallbackNaturalKeyHash) ? fallbackNaturalKeyHash : "");
    }

    private static String normalizeRange(Map<String, Object> payload) {
        String min = normalizeNumber(text(payload.get("amountMin")));
        String max = normalizeNumber(text(payload.get("amountMax")));
        String currency = normalizeUpper(text(payload.get("amountCurrency")));
        if (StringUtils.hasText(min) || StringUtils.hasText(max)) {
            String minInclusive = normalizeBoolean(text(payload.get("amountMinInclusive")));
            String maxInclusive = normalizeBoolean(text(payload.get("amountMaxInclusive")));
            return "MIN:" + (StringUtils.hasText(min) ? min : "*")
                    + ":" + minInclusive
                    + "|MAX:" + (StringUtils.hasText(max) ? max : "*")
                    + ":" + maxInclusive
                    + "|CUR:" + currency;
        }
        String label = normalizeText(text(payload.get("amountRangeLabel")));
        if (!StringUtils.hasText(label) || "全部".equals(label) || "ALL".equalsIgnoreCase(label)) {
            return "ALL";
        }
        return label.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String normalizeWeightRange(Map<String, Object> payload) {
        String min = normalizeNumber(text(payload.get("weightMinGrams")));
        String max = normalizeNumber(text(payload.get("weightMaxGrams")));
        String currency = normalizeUpper(text(payload.get("currency")));
        String minInclusive = normalizeBoolean(text(payload.get("weightMinInclusive")));
        String maxInclusive = normalizeBoolean(text(payload.get("weightMaxInclusive")));
        return "MIN:" + (StringUtils.hasText(min) ? min : "*")
                + ":" + minInclusive
                + "|MAX:" + (StringUtils.hasText(max) ? max : "*")
                + ":" + maxInclusive
                + "|CUR:" + currency;
    }

    private static String normalizeNumber(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        try {
            return new java.math.BigDecimal(text).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return text;
        }
    }

    private static String normalizeBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Boolean.parseBoolean(value.trim()) ? "1" : "0";
    }

    private static String normalizeUpper(String value) {
        String text = normalizeText(value);
        return StringUtils.hasText(text) ? text.toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeCategoryIdentity(Map<String, Object> payload) {
        String categoryPath = normalizeText(text(payload.get("categoryPath")));
        if (StringUtils.hasText(categoryPath)) {
            return normalizeCategoryPath(categoryPath);
        }
        String parentCategoryName = normalizeText(text(payload.get("parentCategoryName")));
        String categoryName = normalizeText(text(payload.get("categoryName")));
        if (StringUtils.hasText(parentCategoryName)
                && StringUtils.hasText(categoryName)
                && !parentCategoryName.equalsIgnoreCase(categoryName)
                && !categoryName.toLowerCase(Locale.ROOT).startsWith(parentCategoryName.toLowerCase(Locale.ROOT) + " >")) {
            return normalizeCategoryPath(parentCategoryName + " > " + categoryName);
        }
        return normalizeCategoryPath(categoryName);
    }

    private static String normalizeCategoryPath(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s*>\\s*", " > ");
    }

    private static String normalizeBrandRestriction(String value) {
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return "全部";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if ("all".equals(lower)
                || "all brands".equals(lower)
                || "any brand".equals(lower)
                || "no restriction".equals(lower)
                || "全部".equals(text)
                || "所有品牌".equals(text)
                || "不限品牌".equals(text)) {
            return "全部";
        }
        if (lower.contains("generic")) {
            return "Generic brand";
        }
        if (lower.contains("other brand")) {
            return "All other brands";
        }
        return text;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
