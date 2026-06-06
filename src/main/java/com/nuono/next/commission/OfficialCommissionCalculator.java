package com.nuono.next.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OfficialCommissionCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public OfficialCommissionCalculationView calculate(
            OfficialCommissionProductContext context,
            List<OfficialCommissionRule> rules,
            String country,
            String platform,
            String fulfillmentType,
            BigDecimal salePrice,
            String marketCurrency,
            LocalDate calculationDate
    ) {
        OfficialCommissionCalculationView view = baseView(context, country, platform, fulfillmentType, salePrice, marketCurrency);
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return fail(view, "MISSING_SALE_PRICE", "缺少有效售价，无法计算佣金。");
        }
        if (!StringUtils.hasText(context.getProductFulltype())) {
            return fail(view, "MISSING_CATEGORY", "商品缺少 fulltype 类目，无法匹配佣金规则。");
        }
        if (rules == null || rules.isEmpty()) {
            return fail(view, "RULE_NOT_FOUND", "当前站点没有可用的 active 官方佣金规则。");
        }

        Match bestMatch = null;
        for (OfficialCommissionRule rule : rules) {
            Match match = matchRule(rule, context, salePrice, marketCurrency);
            if (match == null) {
                continue;
            }
            if (bestMatch == null || match.isBetterThan(bestMatch)) {
                bestMatch = match;
            }
        }
        if (bestMatch == null) {
            return fail(view, "RULE_NOT_MATCHED", "商品类目、品牌或售价未命中当前 active 官方佣金规则。");
        }
        BigDecimal rate = parseCommissionRate(bestMatch.rule.getCommissionRate());
        if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0) {
            return fail(view, "INVALID_RATE", "命中的佣金规则缺少有效佣金率。");
        }
        BigDecimal commissionAmount = salePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxMultiplier = taxMultiplier(view.getSite());
        BigDecimal taxIncludedCommissionAmount = taxMultiplier == null ? null : commissionAmount.multiply(taxMultiplier).setScale(2, RoundingMode.HALF_UP);
        OfficialCommissionRule rule = bestMatch.rule;

        view.setReady(true);
        view.setStatus("CALCULATED");
        view.setMessage("已命中官方佣金规则。");
        view.setCategoryPath(rule.getCategoryPath());
        view.setCategoryName(rule.getCategoryName());
        view.setBrandRestriction(rule.getBrandRestriction());
        view.setAmountRangeLabel(rule.getAmountRangeLabel());
        view.setAmountMin(rule.getAmountMin());
        view.setAmountMax(rule.getAmountMax());
        view.setAmountCurrency(rule.getAmountCurrency());
        view.setCommissionRate(rate);
        view.setCommissionAmount(commissionAmount);
        view.setCurrency(marketCurrency);
        view.setTaxMultiplier(taxMultiplier);
        view.setTaxIncludedCommissionAmount(taxIncludedCommissionAmount);
        view.setMatchedRuleNaturalKey(rule.getNaturalKey());
        view.setSourceVersionId(rule.getSourceVersionId());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("calculationDate", calculationDate == null ? null : calculationDate.toString());
        evidence.put("productFulltype", context.getProductFulltype());
        evidence.put("brand", context.getBrand());
        evidence.put("categoryScore", bestMatch.categoryScore);
        evidence.put("brandScore", bestMatch.brandScore);
        evidence.put("sourceVersionItemId", rule.getSourceVersionItemId());
        evidence.put("commissionRateRaw", rule.getCommissionRate());
        view.setEvidence(evidence);
        return view;
    }

    private OfficialCommissionCalculationView baseView(
            OfficialCommissionProductContext context,
            String country,
            String platform,
            String fulfillmentType,
            BigDecimal salePrice,
            String marketCurrency
    ) {
        OfficialCommissionCalculationView view = new OfficialCommissionCalculationView();
        view.setVariantId(context.getVariantId());
        view.setSkuId(context.getSkuId());
        view.setPartnerSku(context.getPartnerSku());
        view.setChildSku(context.getChildSku());
        view.setSite(context.getSite());
        view.setCountry(country);
        view.setPlatform(platform);
        view.setFulfillmentType(fulfillmentType);
        view.setSalePrice(salePrice);
        view.setMarketCurrency(marketCurrency);
        view.setBrand(context.getBrand());
        view.setProductFulltype(context.getProductFulltype());
        return view;
    }

    private Match matchRule(OfficialCommissionRule rule, OfficialCommissionProductContext context, BigDecimal salePrice, String marketCurrency) {
        if (!currencyMatches(rule.getAmountCurrency(), marketCurrency)) {
            return null;
        }
        if (!amountMatches(rule, salePrice)) {
            return null;
        }
        int categoryScore = categoryScore(rule, context.getProductFulltype());
        if (categoryScore <= 0) {
            return null;
        }
        int brandScore = brandScore(rule.getBrandRestriction(), context.getBrand());
        if (brandScore <= 0) {
            return null;
        }
        return new Match(rule, categoryScore, brandScore, rangeWidth(rule));
    }

    private int categoryScore(OfficialCommissionRule rule, String productFulltype) {
        String productPath = normalizeCategory(productFulltype);
        String productLeaf = leafCategory(productPath);
        String productRoot = rootCategory(productPath);
        String rulePath = normalizeCategory(rule.getCategoryPath());
        String ruleName = normalizeCategory(rule.getCategoryName());
        String ruleRoot = rootCategory(rulePath);
        String ruleLeaf = leafCategory(rulePath);
        if (!StringUtils.hasText(productPath)) {
            return 0;
        }
        if (StringUtils.hasText(rulePath) && rulePath.equals(productPath)) {
            return 300;
        }
        if (StringUtils.hasText(ruleName) && ruleName.equals(productPath)) {
            return 280;
        }
        if (StringUtils.hasText(rulePath) && rulePath.equals(productLeaf)) {
            return 220;
        }
        if (StringUtils.hasText(ruleName) && ruleName.equals(productLeaf)) {
            return 200;
        }
        if (StringUtils.hasText(rulePath) && productPath.endsWith(">" + rulePath)) {
            return 160;
        }
        if (StringUtils.hasText(ruleName) && productPath.endsWith(">" + ruleName)) {
            return 140;
        }
        if (StringUtils.hasText(ruleLeaf) && sameCategoryAlias(productRoot, ruleLeaf)) {
            return 120;
        }
        if (StringUtils.hasText(ruleName) && sameCategoryAlias(productRoot, ruleName)) {
            return 110;
        }
        if (StringUtils.hasText(ruleRoot) && sameCategoryAlias(productRoot, ruleRoot)) {
            return 100;
        }
        if (categoryTokenOverlap(productPath, rulePath) || categoryTokenOverlap(productPath, ruleName)) {
            return 80;
        }
        return 0;
    }

    private int brandScore(String brandRestriction, String productBrand) {
        String restriction = normalizePlain(brandRestriction);
        if (!StringUtils.hasText(restriction)
                || "全部".equals(restriction)
                || "all".equals(restriction)
                || "allbrands".equals(restriction)
                || "allotherbrands".equals(restriction)
                || "otherbrands".equals(restriction)) {
            return 10;
        }
        String brand = normalizePlain(productBrand);
        if (!StringUtils.hasText(brand)) {
            return 0;
        }
        if ("genericbrand".equals(restriction)) {
            return brand.contains("generic") ? 60 : 0;
        }
        return restriction.equals(brand) ? 100 : 0;
    }

    private boolean amountMatches(OfficialCommissionRule rule, BigDecimal salePrice) {
        if (rule.getAmountMin() != null) {
            int compare = salePrice.compareTo(rule.getAmountMin());
            boolean inclusive = rule.getAmountMinInclusive() == null || rule.getAmountMinInclusive();
            if (compare < 0 || (compare == 0 && !inclusive)) {
                return false;
            }
        }
        if (rule.getAmountMax() != null) {
            int compare = salePrice.compareTo(rule.getAmountMax());
            boolean inclusive = rule.getAmountMaxInclusive() == null || rule.getAmountMaxInclusive();
            if (compare > 0 || (compare == 0 && !inclusive)) {
                return false;
            }
        }
        return true;
    }

    private boolean currencyMatches(String ruleCurrency, String marketCurrency) {
        if (!StringUtils.hasText(ruleCurrency) || !StringUtils.hasText(marketCurrency)) {
            return true;
        }
        return ruleCurrency.trim().equalsIgnoreCase(marketCurrency.trim());
    }

    private BigDecimal parseCommissionRate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().replace("%", "").replace(",", "");
        try {
            BigDecimal rate = new BigDecimal(normalized);
            if (raw.contains("%") || rate.compareTo(BigDecimal.ONE) > 0) {
                return rate.divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
            }
            return rate;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private BigDecimal taxMultiplier(String site) {
        String normalized = site == null ? "" : site.trim().toUpperCase(Locale.ROOT);
        if ("SA".equals(normalized) || "KSA".equals(normalized)) {
            return new BigDecimal("1.15");
        }
        if ("AE".equals(normalized) || "UAE".equals(normalized)) {
            return new BigDecimal("1.05");
        }
        return null;
    }

    private OfficialCommissionCalculationView fail(OfficialCommissionCalculationView view, String failureCode, String message) {
        view.setReady(false);
        view.setStatus("FAILED");
        view.setFailureCode(failureCode);
        view.setMessage(message);
        return view;
    }

    private BigDecimal rangeWidth(OfficialCommissionRule rule) {
        if (rule.getAmountMin() == null || rule.getAmountMax() == null) {
            return new BigDecimal("999999999999");
        }
        return rule.getAmountMax().subtract(rule.getAmountMin()).abs();
    }

    private String normalizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replaceAll("\\s*>\\s*", ">")
                .replaceAll("\\s*-\\s*", ">")
                .replaceAll("\\s+", " ");
    }

    private String leafCategory(String normalizedPath) {
        if (!StringUtils.hasText(normalizedPath)) {
            return "";
        }
        int index = normalizedPath.lastIndexOf('>');
        return index < 0 ? normalizedPath : normalizedPath.substring(index + 1);
    }

    private String rootCategory(String normalizedPath) {
        if (!StringUtils.hasText(normalizedPath)) {
            return "";
        }
        int index = normalizedPath.indexOf('>');
        return index < 0 ? normalizedPath : normalizedPath.substring(0, index);
    }

    private boolean sameCategoryAlias(String left, String right) {
        String normalizedLeft = normalizeCategoryAlias(left);
        String normalizedRight = normalizeCategoryAlias(right);
        return StringUtils.hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private String normalizeCategoryAlias(String value) {
        String normalized = normalizePlain(value);
        if ("stationary".equals(normalized) || "stationery".equals(normalized)) {
            return "stationery";
        }
        if ("videogame".equals(normalized) || "videogames".equals(normalized)) {
            return "videogames";
        }
        return normalized;
    }

    private boolean categoryTokenOverlap(String productPath, String rulePath) {
        if (!StringUtils.hasText(productPath) || !StringUtils.hasText(rulePath)) {
            return false;
        }
        java.util.Set<String> productTokens = categoryTokens(productPath);
        java.util.Set<String> ruleTokens = categoryTokens(rulePath);
        for (String token : ruleTokens) {
            if (productTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private java.util.Set<String> categoryTokens(String path) {
        java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        String[] parts = path.split("[>\\s]+");
        for (String part : parts) {
            String token = normalizeCategoryAlias(part);
            if (!StringUtils.hasText(token)
                    || token.length() <= 2
                    || "all".equals(token)
                    || "other".equals(token)
                    || "others".equals(token)
                    || "category".equals(token)
                    || "categories".equals(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private String normalizePlain(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "");
    }

    private static final class Match {
        private final OfficialCommissionRule rule;
        private final int categoryScore;
        private final int brandScore;
        private final BigDecimal rangeWidth;

        private Match(OfficialCommissionRule rule, int categoryScore, int brandScore, BigDecimal rangeWidth) {
            this.rule = rule;
            this.categoryScore = categoryScore;
            this.brandScore = brandScore;
            this.rangeWidth = rangeWidth;
        }

        private boolean isBetterThan(Match other) {
            int score = categoryScore + brandScore;
            int otherScore = other.categoryScore + other.brandScore;
            if (score != otherScore) {
                return score > otherScore;
            }
            int rangeCompare = rangeWidth.compareTo(other.rangeWidth);
            if (rangeCompare != 0) {
                return rangeCompare < 0;
            }
            LocalDate effectiveDate = rule.getEffectiveDate();
            LocalDate otherEffectiveDate = other.rule.getEffectiveDate();
            if (effectiveDate != null && otherEffectiveDate != null && !effectiveDate.equals(otherEffectiveDate)) {
                return effectiveDate.isAfter(otherEffectiveDate);
            }
            if (effectiveDate != null && otherEffectiveDate == null) {
                return true;
            }
            Long sourceItemId = rule.getSourceVersionItemId();
            Long otherSourceItemId = other.rule.getSourceVersionItemId();
            if (sourceItemId == null || otherSourceItemId == null) {
                return false;
            }
            return sourceItemId < otherSourceItemId;
        }
    }
}
