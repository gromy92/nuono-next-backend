package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.util.StringUtils;

class FileParseOutboundFeeSourceScopePolicy {

    <T> List<T> outboundFeeRows(
            FileParseTargetPlanRow targetPlan,
            List<T> rows,
            Function<T, String> textExtractor,
            Function<T, String> sourceTypeExtractor,
            Function<T, String> sheetNameExtractor
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        TargetScope scope = TargetScope.from(targetPlan);
        if (scope == null) {
            return rows;
        }

        List<T> nonSectionRows = new ArrayList<>();
        List<T> sectionRows = new ArrayList<>();
        List<T> manualRows = new ArrayList<>();
        boolean sawOutboundSection = false;
        boolean inOutboundSection = false;
        for (T row : rows) {
            String text = textExtractor == null ? "" : nullToEmpty(textExtractor.apply(row));
            String sourceType = sourceTypeExtractor == null ? "" : nullToEmpty(sourceTypeExtractor.apply(row));
            String sheetName = sheetNameExtractor == null ? "" : nullToEmpty(sheetNameExtractor.apply(row));

            if (isManualText(sourceType)) {
                manualRows.add(row);
                continue;
            }

            if (isOutboundSectionStart(text)) {
                sawOutboundSection = true;
                inOutboundSection = true;
                if (matchesTargetScope(text, scope, true)) {
                    sectionRows.add(row);
                }
                continue;
            }
            if (inOutboundSection && isOutboundSectionEnd(text)) {
                inOutboundSection = false;
                continue;
            }

            if (inOutboundSection) {
                if (shouldKeepOutboundRow(text, sheetName, scope, true)) {
                    sectionRows.add(row);
                }
            } else if (!sawOutboundSection && shouldKeepOutboundRow(text, sheetName, scope, false)) {
                nonSectionRows.add(row);
            }
        }

        List<T> scopedRows = new ArrayList<>(sawOutboundSection ? sectionRows : nonSectionRows);
        scopedRows.addAll(manualRows);
        return scopedRows;
    }

    private boolean shouldKeepOutboundRow(String text, String sheetName, TargetScope scope, boolean allowNeutralTarget) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (isCommissionOrReferralRow(text) || isNonOutboundOperationalRow(text) || isFormulaSummaryRow(text, sheetName)) {
            return false;
        }
        return matchesTargetScope(text, scope, allowNeutralTarget);
    }

    private boolean matchesTargetScope(String value, TargetScope scope, boolean allowNeutral) {
        String text = normalize(value);
        if (!StringUtils.hasText(text)) {
            return false;
        }
        boolean hasTarget = containsAny(text, scope.countryTokens) || containsAny(text, scope.currencyTokens);
        boolean hasOther = containsAny(text, scope.otherCountryTokens) || containsAny(text, scope.otherCurrencyTokens);
        if (hasOther && !hasTarget) {
            return false;
        }
        if (hasTarget) {
            return true;
        }
        return allowNeutral;
    }

    private boolean isManualText(String sourceType) {
        return "manual_text_block".equalsIgnoreCase(sourceType);
    }

    private boolean isOutboundSectionStart(String value) {
        String text = normalize(value);
        return text.matches("^2\\.\\s*FBN OUTBOUND FEES?\\b.*")
                || text.matches("^FBN OUTBOUND FEES?\\b.*");
    }

    private boolean isOutboundSectionEnd(String value) {
        String text = normalize(value);
        return text.matches("^(1\\.|3\\.|4\\.|I\\.|II\\.|III\\.|IV\\.)\\s*(REFERRAL FEES?|MONTHLY STORAGE FEES?|INVENTORY REMOVAL FEE|VALUE ADDED SERVICES?|FAQ)\\b.*")
                || text.matches("^(REFERRAL FEES?|MONTHLY STORAGE FEES?|INVENTORY REMOVAL FEE|VALUE ADDED SERVICES?|FAQ|FREQUENTLY ASKED QUESTIONS)\\b.*");
    }

    private boolean isCommissionOrReferralRow(String value) {
        String text = normalize(value);
        return text.contains("REFERRAL FEES")
                || text.contains("COMMISSION")
                || text.contains("SALE PRICE")
                || text.contains("SALES PRICE")
                || text.contains("%");
    }

    private boolean isNonOutboundOperationalRow(String value) {
        String text = normalize(value);
        return text.contains("MONTHLY STORAGE")
                || text.contains("STORAGE FEES")
                || text.contains("INVENTORY REMOVAL")
                || text.contains("REMOVAL FEE")
                || text.contains("VALUE ADDED SERVICES")
                || text.contains("FAQ")
                || text.contains("FREQUENTLY ASKED QUESTIONS");
    }

    private boolean isFormulaSummaryRow(String value, String sheetName) {
        String text = normalize(value);
        String sheet = normalize(sheetName);
        return (sheet.contains("CALCULATOR") && text.contains("EXPECTED FEES"))
                || text.contains("IF(")
                || text.contains("INDEX(")
                || text.contains("_XLFN.SWITCH")
                || text.contains("SWITCH(");
    }

    private boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return nullToEmpty(value)
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static class TargetScope {

        private final List<String> countryTokens;
        private final List<String> currencyTokens;
        private final List<String> otherCountryTokens;
        private final List<String> otherCurrencyTokens;

        private TargetScope(
                List<String> countryTokens,
                List<String> currencyTokens,
                List<String> otherCountryTokens,
                List<String> otherCurrencyTokens
        ) {
            this.countryTokens = countryTokens;
            this.currencyTokens = currencyTokens;
            this.otherCountryTokens = otherCountryTokens;
            this.otherCurrencyTokens = otherCurrencyTokens;
        }

        private static TargetScope from(FileParseTargetPlanRow targetPlan) {
            String code = targetPlan == null ? "" : normalizeStatic(targetPlan.getCode());
            String label = targetPlan == null ? "" : normalizeStatic(targetPlan.getLabel());
            String scopeText = code + " " + label;
            if (scopeText.contains("KSA") || scopeText.contains("SAUDI")) {
                return new TargetScope(
                        List.of("KSA", "SAUDI", "SAUDI ARABIA"),
                        List.of("SAR"),
                        List.of("UAE", "UNITED ARAB EMIRATES", "EGY", "EGYPT"),
                        List.of("AED", "EGP")
                );
            }
            if (scopeText.contains("UAE") || scopeText.contains("UNITED ARAB")) {
                return new TargetScope(
                        List.of("UAE", "UNITED ARAB EMIRATES"),
                        List.of("AED"),
                        List.of("KSA", "SAUDI", "SAUDI ARABIA", "EGY", "EGYPT"),
                        List.of("SAR", "EGP")
                );
            }
            if (scopeText.contains("EGY") || scopeText.contains("EGYPT")) {
                return new TargetScope(
                        List.of("EGY", "EGYPT"),
                        List.of("EGP"),
                        List.of("KSA", "SAUDI", "SAUDI ARABIA", "UAE", "UNITED ARAB EMIRATES"),
                        List.of("SAR", "AED")
                );
            }
            return null;
        }

        private static String normalizeStatic(String value) {
            return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        }
    }
}
