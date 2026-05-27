package com.nuono.next.filemanagement.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class FileParseLogisticsPayloadNormalizer {

    private static final Pattern LEAD_TIME_RANGE = Pattern.compile("(\\d+)\\s*[-~至到]\\s*(\\d+)\\s*(?:days?|天)?", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-M-d")
                    .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                    .toFormatter()
    };

    private FileParseLogisticsPayloadNormalizer() {
    }

    static Map<String, Object> normalize(String itemType, Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (payload != null) {
            normalized.putAll(payload);
        }
        if (!FileParseLogisticsQuoteStandard.SERVICE_LINE.equals(itemType)) {
            if (FileParseLogisticsQuoteStandard.CARGO_CATEGORY.equals(itemType)) {
                normalizeCargoCategory(normalized);
            } else if (FileParseLogisticsQuoteStandard.BASE_PRICE.equals(itemType)) {
                normalizeBasePrice(normalized);
            } else if (FileParseLogisticsQuoteStandard.SURCHARGE.equals(itemType)) {
                normalizeSurcharge(normalized);
            } else if (FileParseLogisticsQuoteStandard.BILLING_RULE.equals(itemType)) {
                normalizeBillingRule(normalized);
            } else if (FileParseLogisticsQuoteStandard.WAREHOUSE_SERVICE_FEE.equals(itemType)) {
                normalizeWarehouseServiceFee(normalized);
            } else if (FileParseLogisticsQuoteStandard.RESTRICTION.equals(itemType)) {
                normalizeRestriction(normalized);
            }
            return normalized;
        }

        normalizeServiceLine(normalized);
        return normalized;
    }

    private static void normalizeServiceLine(Map<String, Object> payload) {
        String forwarderCode = normalizeForwarderCode(text(payload.get("forwarderCode")), text(payload.get("forwarderName")));
        if (StringUtils.hasText(forwarderCode)) {
            payload.put("forwarderCode", forwarderCode);
            payload.put("forwarderName", normalizeForwarderName(forwarderCode, text(payload.get("forwarderName"))));
        }
        putIfText(payload, "country", normalizeCountry(text(payload.get("country"))));
        putIfText(payload, "fulfillmentMode", normalizeFulfillmentMode(text(payload.get("fulfillmentMode"))));
        if (!StringUtils.hasText(text(payload.get("fulfillmentMode")))) {
            payload.put("fulfillmentMode", "FBN");
        }
        putIfText(payload, "transportMode", normalizeTransportMode(text(payload.get("transportMode"))));
        putIfText(payload, "serviceScope", normalizeServiceScope(text(payload.get("serviceScope"))));
        if ("FBN".equals(text(payload.get("fulfillmentMode")))
                && !StringUtils.hasText(text(payload.get("serviceScope")))) {
            payload.put("serviceScope", "fbn_delivery");
        }
        putIfText(payload, "effectiveDate", normalizeDate(text(payload.get("effectiveDate"))));
        normalizeLeadTime(payload);
    }

    private static void normalizeCargoCategory(Map<String, Object> payload) {
        String forwarderCode = normalizeForwarderCode(text(payload.get("forwarderCode")), text(payload.get("forwarderName")));
        if (StringUtils.hasText(forwarderCode)) {
            payload.put("forwarderCode", forwarderCode);
            payload.put("forwarderName", normalizeForwarderName(forwarderCode, text(payload.get("forwarderName"))));
        }
        putIfText(payload, "serviceLineKey", normalizeSpaces(text(payload.get("serviceLineKey"))));
        String categoryCode = normalizeCategoryCode(text(payload.get("categoryCode")), text(payload.get("categoryName")));
        putIfText(payload, "categoryCode", categoryCode);
        putIfText(payload, "categoryName", normalizeSpaces(text(payload.get("categoryName"))));
        putIfText(payload, "productExamples", normalizeDelimitedText(text(payload.get("productExamples"))));
        putIfText(payload, "keywords", normalizeDelimitedText(text(payload.get("keywords"))));
        putIfText(payload, "electricType", normalizeElectricType(text(payload.get("electricType"))));
        putIfText(payload, "sensitiveTags", normalizeDelimitedText(text(payload.get("sensitiveTags"))));
        putIfText(payload, "packingPolicy", normalizeSpaces(text(payload.get("packingPolicy"))));
        Object manualConfirmRequired = payload.get("manualConfirmRequired");
        Boolean manualConfirm = normalizeBoolean(manualConfirmRequired);
        if (manualConfirm != null) {
            payload.put("manualConfirmRequired", manualConfirm);
        }
    }

    private static void normalizeBasePrice(Map<String, Object> payload) {
        String forwarderCode = normalizeForwarderCode(text(payload.get("forwarderCode")), text(payload.get("forwarderName")));
        if (StringUtils.hasText(forwarderCode)) {
            payload.put("forwarderCode", forwarderCode);
            payload.put("forwarderName", normalizeForwarderName(forwarderCode, text(payload.get("forwarderName"))));
        }
        putIfText(payload, "serviceLineKey", normalizeSpaces(text(payload.get("serviceLineKey"))));
        String cargoCategoryKey = normalizeCategoryCode(text(payload.get("cargoCategoryKey")), text(payload.get("cargoCategoryKey")));
        putIfText(payload, "cargoCategoryKey", cargoCategoryKey);

        String unitPriceText = text(payload.get("unitPrice"));
        BigDecimal unitPrice = normalizeDecimal(unitPriceText);
        if (unitPrice != null) {
            payload.put("unitPrice", unitPrice);
        }
        String currency = normalizeCurrency(text(payload.get("currency")));
        if (!StringUtils.hasText(currency)) {
            currency = inferCurrency(unitPriceText);
        }
        putIfText(payload, "currency", currency);

        putIfText(payload, "billingUnit", normalizeBillingUnit(text(payload.get("billingUnit")), unitPriceText));
        putIfText(payload, "pricingModel", normalizePricingModel(text(payload.get("pricingModel")), text(payload.get("billingUnit")), unitPriceText));

        String minimumText = text(payload.get("minimumBillableUnit"));
        BigDecimal minimum = normalizeDecimal(minimumText);
        if (minimum != null) {
            payload.put("minimumBillableUnit", minimum);
        }
        String minimumType = normalizeBillingUnit(text(payload.get("minimumBillableUnitType")), minimumText);
        putIfText(payload, "minimumBillableUnitType", minimumType);

        Integer volumeDivisor = normalizeInteger(text(payload.get("volumeDivisor")));
        if (volumeDivisor != null) {
            payload.put("volumeDivisor", volumeDivisor);
        }
        putIfText(payload, "seaWeightRatio", normalizeSeaWeightRatio(text(payload.get("seaWeightRatio"))));
        putIfText(payload, "roundingRule", normalizeSpaces(text(payload.get("roundingRule"))));
        putIfText(payload, "priceStatus", normalizePriceStatus(text(payload.get("priceStatus"))));
        putIfText(payload, "effectiveDate", normalizeDate(text(payload.get("effectiveDate"))));
    }

    private static void normalizeSurcharge(Map<String, Object> payload) {
        normalizeForwarder(payload);
        putIfText(payload, "serviceLineKey", normalizeSpaces(text(payload.get("serviceLineKey"))));
        putIfText(payload, "surchargeName", normalizeSpaces(text(payload.get("surchargeName"))));
        putIfText(payload, "surchargeType", normalizeSurchargeType(text(payload.get("surchargeType")), text(payload.get("surchargeName"))));
        putIfText(payload, "triggerCondition", normalizeSpaces(text(payload.get("triggerCondition"))));
        normalizeAmountRateCurrencyAndUnit(payload, "amount", "rate", "currency", "billingUnit");
        Boolean included = normalizeBoolean(payload.get("includedInBasePrice"));
        if (included != null) {
            payload.put("includedInBasePrice", included);
        }
    }

    private static void normalizeBillingRule(Map<String, Object> payload) {
        normalizeForwarder(payload);
        putIfText(payload, "serviceLineKey", normalizeSpaces(text(payload.get("serviceLineKey"))));
        putIfText(payload, "ruleName", normalizeSpaces(text(payload.get("ruleName"))));
        putIfText(payload, "conditionText", normalizeSpaces(text(payload.get("conditionText"))));
        putIfText(payload, "actionText", normalizeSpaces(text(payload.get("actionText"))));
        putIfText(payload, "operator", normalizeOperator(text(payload.get("operator")), text(payload.get("conditionText"))));
        BigDecimal threshold = normalizeDecimal(text(payload.get("thresholdValue")));
        if (threshold != null) {
            payload.put("thresholdValue", threshold);
        }
        putIfText(payload, "thresholdUnit", normalizeBillingUnit(text(payload.get("thresholdUnit")), text(payload.get("conditionText"))));
        putIfText(payload, "severity", normalizeSeverity(text(payload.get("severity"))));
    }

    private static void normalizeWarehouseServiceFee(Map<String, Object> payload) {
        normalizeForwarder(payload);
        putIfText(payload, "country", normalizeCountry(text(payload.get("country"))));
        putIfText(payload, "warehouseNode", normalizeSpaces(text(payload.get("warehouseNode"))));
        putIfText(payload, "serviceName", normalizeSpaces(text(payload.get("serviceName"))));
        putIfText(payload, "serviceType", normalizeWarehouseServiceType(text(payload.get("serviceType")), text(payload.get("serviceName"))));
        putIfText(payload, "processingScope", normalizeSpaces(text(payload.get("processingScope"))));
        putIfText(payload, "feeType", normalizeFeeType(text(payload.get("feeType")), text(payload.get("billingUnit"))));
        normalizeAmountRateCurrencyAndUnit(payload, "amount", "rate", "currency", "billingUnit");
        putIfText(payload, "conditionText", normalizeSpaces(text(payload.get("conditionText"))));
        putIfText(payload, "freeCondition", normalizeSpaces(text(payload.get("freeCondition"))));
    }

    private static void normalizeRestriction(Map<String, Object> payload) {
        normalizeForwarder(payload);
        putIfText(payload, "serviceLineKey", normalizeSpaces(text(payload.get("serviceLineKey"))));
        putIfText(payload, "restrictionType", normalizeRestrictionType(text(payload.get("restrictionType"))));
        putIfText(payload, "itemText", normalizeSpaces(text(payload.get("itemText"))));
        putIfText(payload, "requirementText", normalizeSpaces(text(payload.get("requirementText"))));
        putIfText(payload, "applicabilityScope", normalizeSpaces(text(payload.get("applicabilityScope"))));
        putIfText(payload, "severity", normalizeSeverity(text(payload.get("severity"))));
        Boolean manualConfirm = normalizeBoolean(payload.get("manualConfirmRequired"));
        if (manualConfirm != null) {
            payload.put("manualConfirmRequired", manualConfirm);
        }
    }

    private static void normalizeForwarder(Map<String, Object> payload) {
        String forwarderCode = normalizeForwarderCode(text(payload.get("forwarderCode")), text(payload.get("forwarderName")));
        if (StringUtils.hasText(forwarderCode)) {
            payload.put("forwarderCode", forwarderCode);
            payload.put("forwarderName", normalizeForwarderName(forwarderCode, text(payload.get("forwarderName"))));
        }
    }

    private static void normalizeAmountRateCurrencyAndUnit(
            Map<String, Object> payload,
            String amountKey,
            String rateKey,
            String currencyKey,
            String unitKey
    ) {
        String amountText = text(payload.get(amountKey));
        BigDecimal amount = normalizeDecimal(amountText);
        if (amount != null) {
            payload.put(amountKey, amount);
        }
        BigDecimal rate = normalizeDecimal(text(payload.get(rateKey)));
        if (rate != null) {
            payload.put(rateKey, rate);
        }
        String currency = normalizeCurrency(text(payload.get(currencyKey)));
        if (!StringUtils.hasText(currency)) {
            currency = inferCurrency(amountText);
        }
        putIfText(payload, currencyKey, currency);
        putIfText(payload, unitKey, normalizeBillingUnit(text(payload.get(unitKey)), amountText));
    }

    private static void normalizeLeadTime(Map<String, Object> payload) {
        String leadTimeText = text(payload.get("leadTimeText"));
        if (!StringUtils.hasText(leadTimeText)) {
            return;
        }
        Matcher matcher = LEAD_TIME_RANGE.matcher(leadTimeText);
        if (!matcher.find()) {
            return;
        }
        payload.put("leadTimeMinDays", Integer.parseInt(matcher.group(1)));
        payload.put("leadTimeMaxDays", Integer.parseInt(matcher.group(2)));
    }

    private static String normalizeForwarderCode(String explicitCode, String name) {
        String code = lower(explicitCode);
        if ("et".equals(code) || "yite".equals(code)) {
            return code;
        }
        String lowerName = lower(name);
        if (lowerName.contains("易通") || lowerName.contains("et")) {
            return "et";
        }
        if (lowerName.contains("义特") || lowerName.contains("yite")) {
            return "yite";
        }
        return normalizeToken(explicitCode);
    }

    private static String normalizeForwarderName(String code, String currentName) {
        if ("et".equals(code)) {
            return "ET/易通";
        }
        if ("yite".equals(code)) {
            return StringUtils.hasText(currentName) ? currentName.trim() : "义特物流";
        }
        return currentName;
    }

    private static String normalizeCountry(String value) {
        String lower = lower(value);
        if (lower.equals("uae") || lower.equals("ae") || lower.contains("emirates") || lower.contains("阿联酋")) {
            return "UAE";
        }
        if (lower.equals("ksa") || lower.equals("sa") || lower.contains("saudi") || lower.contains("沙特")) {
            return "KSA";
        }
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String normalizeFulfillmentMode(String value) {
        String upper = normalizeSpaces(value).toUpperCase(Locale.ROOT);
        if (upper.contains("FBN")) {
            return "FBN";
        }
        return upper;
    }

    private static String normalizeTransportMode(String value) {
        String lower = lower(value);
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
        return normalizeToken(value);
    }

    private static String normalizeServiceScope(String value) {
        String lower = lower(value);
        if ("warehouse_to_fbn".equals(lower)
                || "fbn_delivery".equals(lower)
                || "overseas_warehouse".equals(lower)) {
            return lower;
        }
        if (lower.contains("仓到仓")
                || lower.contains("仓到 fbn")
                || lower.contains("warehouse to fbn")
                || lower.contains("warehouse-to-fbn")) {
            return "warehouse_to_fbn";
        }
        if (lower.contains("fbn")) {
            return "fbn_delivery";
        }
        if (lower.contains("海外仓") || lower.contains("warehouse")) {
            return "overseas_warehouse";
        }
        return normalizeToken(value);
    }

    private static String normalizeCategoryCode(String explicitCode, String categoryName) {
        String explicit = normalizeSpaces(explicitCode).toUpperCase(Locale.ROOT);
        if (explicit.matches("[A-G]")) {
            return explicit;
        }
        Matcher explicitMatcher = Pattern.compile("\\b([A-G])\\s*(?:类|CLASS)?\\b", Pattern.CASE_INSENSITIVE).matcher(explicit);
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        String name = normalizeSpaces(categoryName).toUpperCase(Locale.ROOT);
        Matcher nameMatcher = Pattern.compile("\\b([A-G])\\s*(?:类|CLASS)?\\b", Pattern.CASE_INSENSITIVE).matcher(name);
        if (nameMatcher.find()) {
            return nameMatcher.group(1).toUpperCase(Locale.ROOT);
        }
        return explicit;
    }

    private static String normalizeElectricType(String value) {
        String lower = lower(value);
        if (!StringUtils.hasText(lower)) {
            return "";
        }
        if ("non_electric".equals(lower) || "pure_electric".equals(lower) || "electric".equals(lower)) {
            return lower;
        }
        if (lower.contains("不带电")
                || lower.contains("无电")
                || lower.contains("非电")
                || lower.contains("no battery")
                || lower.contains("non electric")
                || lower.contains("non-electric")) {
            return "non_electric";
        }
        if (lower.contains("纯电") || lower.contains("pure battery")) {
            return "pure_electric";
        }
        if (lower.contains("带电")
                || lower.contains("电池")
                || lower.contains("battery")
                || lower.contains("electric")) {
            return "electric";
        }
        return normalizeToken(value);
    }

    private static String normalizeCurrency(String value) {
        String upper = normalizeSpaces(value).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(upper)) {
            return "";
        }
        if ("RMB".equals(upper) || "CNY".equals(upper) || upper.contains("人民币")) {
            return "CNY";
        }
        if ("AED".equals(upper) || upper.contains("迪拉姆")) {
            return "AED";
        }
        if ("SAR".equals(upper) || upper.contains("里亚尔")) {
            return "SAR";
        }
        if ("USD".equals(upper) || upper.contains("美元")) {
            return "USD";
        }
        return upper;
    }

    private static String inferCurrency(String value) {
        String upper = normalizeSpaces(value).toUpperCase(Locale.ROOT);
        if (upper.contains("RMB") || upper.contains("CNY") || upper.contains("人民币")) {
            return "CNY";
        }
        if (upper.contains("AED")) {
            return "AED";
        }
        if (upper.contains("SAR")) {
            return "SAR";
        }
        if (upper.contains("USD")) {
            return "USD";
        }
        return "";
    }

    private static String normalizeBillingUnit(String explicitUnit, String context) {
        String lower = lower(StringUtils.hasText(explicitUnit) ? explicitUnit : context);
        if (!StringUtils.hasText(lower)) {
            return "";
        }
        if (lower.contains("kg") || lower.contains("公斤") || lower.contains("千克")) {
            return "kg";
        }
        if (lower.contains("cbm") || lower.contains("m3") || lower.contains("m³") || lower.contains("立方")) {
            return "cbm";
        }
        if (lower.contains("pcs") || lower.contains("piece") || lower.contains("件")) {
            return "piece";
        }
        if (lower.contains("carton") || lower.contains("箱")) {
            return "carton";
        }
        if (lower.contains("shipment") || lower.contains("票")) {
            return "shipment";
        }
        if (lower.contains("%") || lower.contains("rate")) {
            return "rate";
        }
        return normalizeToken(explicitUnit);
    }

    private static String normalizePricingModel(String explicitModel, String billingUnit, String context) {
        String lower = lower(StringUtils.hasText(explicitModel) ? explicitModel : billingUnit + " " + context);
        if (lower.contains("重量") || lower.contains("kg") || lower.contains("公斤") || lower.contains("per weight")) {
            return "per_weight";
        }
        if (lower.contains("体积") || lower.contains("cbm") || lower.contains("m3") || lower.contains("立方") || lower.contains("per volume")) {
            return "per_volume";
        }
        if (lower.contains("件") || lower.contains("piece") || lower.contains("pcs")) {
            return "per_piece";
        }
        if (lower.contains("固定") || lower.contains("fixed")) {
            return "fixed";
        }
        if (lower.contains("%") || lower.contains("费率") || lower.contains("rate")) {
            return "rate";
        }
        return normalizeToken(explicitModel);
    }

    private static String normalizePriceStatus(String value) {
        String lower = lower(value);
        if (!StringUtils.hasText(lower)) {
            return "";
        }
        if (lower.contains("起") || lower.contains("from") || lower.contains("starting")) {
            return "starting_price";
        }
        if (lower.contains("另询") || lower.contains("询价") || lower.contains("inquire")) {
            return "inquiry_required";
        }
        if (lower.contains("逐个确认") || lower.contains("人工确认") || lower.contains("manual")) {
            return "manual_confirm";
        }
        if (lower.contains("正常") || lower.contains("active") || lower.contains("standard")) {
            return "active";
        }
        return normalizeToken(value);
    }

    private static String normalizeSurchargeType(String explicitType, String name) {
        String lower = lower(StringUtils.hasText(explicitType) ? explicitType : name);
        if (lower.contains("超长") || lower.contains("超大") || lower.contains("oversize")) {
            return "oversize";
        }
        if (lower.contains("pallet") || lower.contains("托盘")) {
            return "pallet";
        }
        if (lower.contains("wood") || lower.contains("木箱")) {
            return "wooden_box";
        }
        if (lower.contains("war") || lower.contains("战争")) {
            return "war_risk";
        }
        if (lower.contains("custom") || lower.contains("清关") || lower.contains("关税")) {
            return "customs";
        }
        return normalizeToken(explicitType);
    }

    private static String normalizeOperator(String explicitOperator, String conditionText) {
        String lower = lower(StringUtils.hasText(explicitOperator) ? explicitOperator : conditionText);
        if (lower.contains(">=") || lower.contains("大于等于") || lower.contains("at least")) {
            return ">=";
        }
        if (lower.contains("<=") || lower.contains("小于等于") || lower.contains("at most")) {
            return "<=";
        }
        if (lower.contains(">") || lower.contains("greater than") || lower.contains("超过") || lower.contains("大于")) {
            return ">";
        }
        if (lower.contains("<") || lower.contains("less than") || lower.contains("低于") || lower.contains("小于")) {
            return "<";
        }
        if (lower.contains("=") || lower.contains("等于")) {
            return "=";
        }
        return normalizeToken(explicitOperator);
    }

    private static String normalizeWarehouseServiceType(String explicitType, String name) {
        String lower = lower(StringUtils.hasText(explicitType) ? explicitType : name);
        if (lower.contains("入仓") || lower.contains("inbound")) {
            return "inbound";
        }
        if (lower.contains("上架") || lower.contains("shelving")) {
            return "shelving";
        }
        if (lower.contains("拣货") || lower.contains("picking")) {
            return "picking";
        }
        if (lower.contains("包装") || lower.contains("packing")) {
            return "packing";
        }
        if (lower.contains("贴标") || lower.contains("label")) {
            return "labeling";
        }
        if (lower.contains("拍照") || lower.contains("photo")) {
            return "photo";
        }
        if (lower.contains("盘点") || lower.contains("inventory")) {
            return "inventory_count";
        }
        if (lower.contains("发单") || lower.contains("出库") || lower.contains("dispatch")) {
            return "dispatch";
        }
        if (lower.contains("退货") || lower.contains("return")) {
            return "return";
        }
        if (lower.contains("销毁") || lower.contains("disposal")) {
            return "disposal";
        }
        if (lower.contains("无人认领") || lower.contains("unclaimed")) {
            return "unclaimed_goods";
        }
        return normalizeToken(explicitType);
    }

    private static String normalizeFeeType(String explicitType, String billingUnit) {
        String lower = lower(StringUtils.hasText(explicitType) ? explicitType : billingUnit);
        if (lower.contains("piece") || lower.contains("pcs") || lower.contains("件")) {
            return "per_piece";
        }
        if (lower.contains("kg") || lower.contains("公斤")) {
            return "per_kg";
        }
        if (lower.contains("cbm") || lower.contains("立方")) {
            return "per_cbm";
        }
        if (lower.contains("票") || lower.contains("shipment")) {
            return "per_shipment";
        }
        if (lower.contains("%") || lower.contains("rate")) {
            return "rate";
        }
        if (lower.contains("fixed") || lower.contains("固定")) {
            return "fixed";
        }
        return normalizeToken(explicitType);
    }

    private static String normalizeRestrictionType(String value) {
        String lower = lower(value);
        if (lower.contains("禁发") || lower.contains("prohibit") || lower.contains("forbidden")) {
            return "prohibited";
        }
        if (lower.contains("限发") || lower.contains("restricted") || lower.contains("限制")) {
            return "restricted";
        }
        if (lower.contains("合规") || lower.contains("compliance") || lower.contains("标识") || lower.contains("msds")) {
            return "compliance";
        }
        if (lower.contains("结算") || lower.contains("settlement")) {
            return "settlement";
        }
        if (lower.contains("不可抗力") || lower.contains("force")) {
            return "force_majeure";
        }
        return normalizeToken(value);
    }

    private static String normalizeSeverity(String value) {
        String lower = lower(value);
        if (lower.contains("hard") || lower.contains("block") || lower.contains("禁止") || lower.contains("禁发")) {
            return "blocking";
        }
        if (lower.contains("warn") || lower.contains("提示") || lower.contains("警告")) {
            return "warning";
        }
        if (lower.contains("info") || lower.contains("说明")) {
            return "info";
        }
        return normalizeToken(value);
    }

    private static BigDecimal normalizeDecimal(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("[-+]?[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?|[-+]?[0-9]+(?:\\.[0-9]+)?").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal decimal = new BigDecimal(matcher.group().replace(",", "")).stripTrailingZeros();
        return new BigDecimal(decimal.toPlainString());
    }

    private static Integer normalizeInteger(String value) {
        BigDecimal decimal = normalizeDecimal(value);
        if (decimal == null) {
            return null;
        }
        return decimal.intValue();
    }

    private static String normalizeSeaWeightRatio(String value) {
        String text = normalizeSpaces(value);
        Matcher matcher = Pattern.compile("(\\d+)\\s*[:：/]\\s*(\\d+)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + ":" + matcher.group(2);
        }
        return text;
    }

    private static Boolean normalizeBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String lower = lower(text(value));
        if (!StringUtils.hasText(lower)) {
            return null;
        }
        if ("false".equals(lower)
                || "no".equals(lower)
                || "n".equals(lower)
                || "0".equals(lower)
                || lower.contains("不需要")
                || lower.contains("无需")
                || lower.contains("否")) {
            return false;
        }
        if ("true".equals(lower)
                || "yes".equals(lower)
                || "y".equals(lower)
                || "1".equals(lower)
                || "是".equals(lower)
                || lower.contains("需要")
                || lower.contains("需人工")
                || lower.contains("待确认")
                || lower.contains("manual confirm")
                || lower.contains("ambiguous")) {
            return true;
        }
        return null;
    }

    private static String normalizeDelimitedText(String value) {
        return normalizeSpaces(value)
                .replace("；", ";")
                .replace("，", ",")
                .replace("、", ",")
                .replaceAll("\\s*[,;]\\s*", ", ");
    }

    private static String normalizeDate(String value) {
        String text = normalizeSpaces(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).toString();
            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }
        return text;
    }

    private static void putIfText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value);
        }
    }

    private static String normalizeToken(String value) {
        return normalizeSpaces(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String lower(String value) {
        return normalizeSpaces(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
