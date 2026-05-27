package com.nuono.next.procurement;

import com.nuono.next.logisticsquote.LogisticsCargoCategoryFact;
import com.nuono.next.logisticsquote.LogisticsRestrictionRuleFact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public class LogisticsRestrictionEvaluator {

    private static final Pattern CM_THRESHOLD = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*cm", Pattern.CASE_INSENSITIVE);

    public EvaluationResult evaluate(
            CargoFacts cargo,
            LogisticsCargoCategoryFact category,
            List<LogisticsRestrictionRuleFact> restrictions
    ) {
        EvaluationResult result = new EvaluationResult();
        if (category != null && category.isManualConfirmRequired()) {
            result.riskPrompts.add(new RiskPrompt(
                    "manual_confirm",
                    "货物分类「" + category.getCategoryName() + "」需要人工确认后再发货。",
                    true
            ));
        }
        if (restrictions == null || restrictions.isEmpty()) {
            return result;
        }
        for (LogisticsRestrictionRuleFact restriction : restrictions) {
            if (!applies(cargo, restriction)) {
                continue;
            }
            String severity = normalizeSeverity(restriction);
            RiskPrompt prompt = new RiskPrompt(
                    severity,
                    message(restriction),
                    restriction.isManualConfirmRequired()
            );
            result.riskPrompts.add(prompt);
            if ("hard".equals(severity)) {
                result.hardRestricted = true;
            }
        }
        return result;
    }

    private boolean applies(CargoFacts cargo, LogisticsRestrictionRuleFact restriction) {
        if (restriction == null) {
            return false;
        }
        if (restriction.isManualConfirmRequired()) {
            return true;
        }
        String haystack = text(restriction).toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "oversized", "oversize", "超尺寸", "超长", "单边")) {
            return oversized(cargo, haystack);
        }
        if (containsAny(haystack, "overweight", "超重")) {
            return overweight(cargo, haystack);
        }
        String attributes = cargo == null || cargo.cargoAttributes == null
                ? ""
                : cargo.cargoAttributes.toLowerCase(Locale.ROOT);
        return tokenMatches(attributes, haystack);
    }

    private boolean tokenMatches(String attributes, String haystack) {
        if (!StringUtils.hasText(attributes)) {
            return false;
        }
        if (attributes.contains("battery") || attributes.contains("带电") || attributes.contains("电池")) {
            if (containsAny(haystack, "battery", "带电", "电池", "敏感")) {
                return true;
            }
        }
        if (attributes.contains("sensitive") || attributes.contains("敏感")) {
            if (containsAny(haystack, "sensitive", "敏感", "带电")) {
                return true;
            }
        }
        if (attributes.contains("liquid") || attributes.contains("液体")) {
            if (containsAny(haystack, "liquid", "液体")) {
                return true;
            }
        }
        if (attributes.contains("powder") || attributes.contains("粉末")) {
            if (containsAny(haystack, "powder", "粉末")) {
                return true;
            }
        }
        if (attributes.contains("magnetic") || attributes.contains("磁")) {
            if (containsAny(haystack, "magnetic", "磁")) {
                return true;
            }
        }
        return false;
    }

    private boolean oversized(CargoFacts cargo, String haystack) {
        if (cargo == null) {
            return false;
        }
        BigDecimal threshold = firstCmThreshold(haystack);
        if (threshold == null) {
            return containsAttribute(cargo, "oversized", "超尺寸", "超长");
        }
        BigDecimal maxSide = cargo.packageLengthCm.max(cargo.packageWidthCm).max(cargo.packageHeightCm);
        return maxSide.compareTo(threshold) > 0;
    }

    private boolean overweight(CargoFacts cargo, String haystack) {
        if (cargo == null) {
            return false;
        }
        return containsAttribute(cargo, "overweight", "超重");
    }

    private boolean containsAttribute(CargoFacts cargo, String... tokens) {
        String attributes = cargo.cargoAttributes == null ? "" : cargo.cargoAttributes.toLowerCase(Locale.ROOT);
        return containsAny(attributes, tokens);
    }

    private BigDecimal firstCmThreshold(String haystack) {
        Matcher matcher = CM_THRESHOLD.matcher(haystack);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1));
    }

    private String normalizeSeverity(LogisticsRestrictionRuleFact restriction) {
        if (restriction.isHardRestriction()) {
            return "hard";
        }
        if (restriction.isWarning()) {
            return "warning";
        }
        if (restriction.isManualConfirmRequired()) {
            return "manual_confirm";
        }
        return "info";
    }

    private String message(LogisticsRestrictionRuleFact restriction) {
        List<String> fragments = new ArrayList<>();
        if (StringUtils.hasText(restriction.getItemText())) {
            fragments.add(restriction.getItemText());
        }
        if (StringUtils.hasText(restriction.getRequirementText())
                && !restriction.getRequirementText().equals(restriction.getItemText())) {
            fragments.add(restriction.getRequirementText());
        }
        return fragments.isEmpty() ? "命中货代限制规则。" : String.join("：", fragments);
    }

    private String text(LogisticsRestrictionRuleFact restriction) {
        return join(
                restriction.getRestrictionType(),
                restriction.getItemText(),
                restriction.getRequirementText(),
                restriction.getApplicabilityScope()
        );
    }

    private String join(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static class CargoFacts {

        private final String cargoAttributes;
        private final BigDecimal packageLengthCm;
        private final BigDecimal packageWidthCm;
        private final BigDecimal packageHeightCm;
        private final BigDecimal unitWeightGrams;
        private final int quantity;

        public CargoFacts(
                String cargoAttributes,
                BigDecimal packageLengthCm,
                BigDecimal packageWidthCm,
                BigDecimal packageHeightCm,
                BigDecimal unitWeightGrams,
                int quantity
        ) {
            this.cargoAttributes = cargoAttributes;
            this.packageLengthCm = packageLengthCm;
            this.packageWidthCm = packageWidthCm;
            this.packageHeightCm = packageHeightCm;
            this.unitWeightGrams = unitWeightGrams;
            this.quantity = quantity;
        }
    }

    public static class EvaluationResult {

        private boolean hardRestricted;
        private final List<RiskPrompt> riskPrompts = new ArrayList<>();

        public boolean isHardRestricted() {
            return hardRestricted;
        }

        public List<RiskPrompt> getRiskPrompts() {
            return riskPrompts;
        }
    }

    public static class RiskPrompt {

        private final String severity;
        private final String message;
        private final boolean manualConfirmRequired;

        public RiskPrompt(String severity, String message, boolean manualConfirmRequired) {
            this.severity = severity;
            this.message = message;
            this.manualConfirmRequired = manualConfirmRequired;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        public boolean isManualConfirmRequired() {
            return manualConfirmRequired;
        }
    }
}
