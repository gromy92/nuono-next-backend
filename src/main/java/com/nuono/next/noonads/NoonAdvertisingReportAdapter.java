package com.nuono.next.noonads;

import com.nuono.next.noonpull.NoonAdvertisingLegacyNumericContract;
import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonAdvertisingReportAdapter {
    private static final Pattern STORE_PARTNER_ID = Pattern.compile("^STR([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);

    private final NoonAdvertisingImportService importService;

    public NoonAdvertisingReportAdapter(NoonAdvertisingImportService importService) {
        this.importService = importService;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        try {
            List<List<String>> rows = NoonAdvertisingCsvDecoder.parse(file);
            if (rows.isEmpty() || rows.get(0).isEmpty()) {
                return NoonReportProcessResult.emptyReport();
            }
            Map<String, Integer> headerIndex = headerIndex(rows.get(0));
            if (!hasRequiredColumns(headerIndex)) {
                return NoonReportProcessResult.missingColumns("missing required Ads columns");
            }

            Map<String, NoonAdvertisingCampaignFact> campaignsByIdentity = new LinkedHashMap<>();
            Map<String, NoonAdvertisingQueryFact> queriesByIdentity = new LinkedHashMap<>();
            int exceptions = 0;
            String projectCode = null;
            for (int rowNumber = 1; rowNumber < rows.size(); rowNumber++) {
                List<String> row = rows.get(rowNumber);
                if (isBlankRow(row)) {
                    continue;
                }
                try {
                    projectCode = mergeProjectCode(projectCode, optionalValue(row, headerIndex, "project_code"));
                    String rowType = rowType(row, headerIndex);
                    if ("campaign".equals(rowType)) {
                        NoonAdvertisingCampaignFact fact = campaignFact(row, headerIndex);
                        if (campaignsByIdentity.putIfAbsent(campaignIdentity(fact), fact) != null) {
                            exceptions++;
                        }
                    } else if ("query".equals(rowType)) {
                        NoonAdvertisingQueryFact fact = queryFact(row, headerIndex);
                        if (queriesByIdentity.putIfAbsent(queryIdentity(fact), fact) != null) {
                            exceptions++;
                        }
                    } else {
                        throw new IllegalArgumentException("Unsupported Noon Ads row_type: " + rowType);
                    }
                } catch (IllegalArgumentException exception) {
                    exceptions++;
                }
            }
            List<NoonAdvertisingCampaignFact> campaignRows = new ArrayList<>(campaignsByIdentity.values());
            List<NoonAdvertisingQueryFact> queryRows = new ArrayList<>(queriesByIdentity.values());
            if (campaignRows.isEmpty() && queryRows.isEmpty()) {
                return exceptions > 0
                        ? NoonReportProcessResult.mappingFailed(exceptions)
                        : NoonReportProcessResult.emptyReport();
            }

            NoonReportPullRequest request = file.getRequest();
            projectCode = projectCodeOrFallback(projectCode, request == null ? null : request.getStoreCode());
            if (!StringUtils.hasText(projectCode)) {
                return NoonReportProcessResult.mappingFailed(
                        Math.max(1, exceptions),
                        "missing Noon Ads project_code"
                );
            }
            NoonAdvertisingImportResult importResult = importService.importReport(new NoonAdvertisingReportImportCommand(
                    request == null ? null : request.getOwnerUserId(),
                    request == null ? null : request.getOwnerUserId(),
                    projectCode,
                    request == null ? null : request.getStoreCode(),
                    request == null ? null : request.getSiteCode(),
                    request == null ? null : request.getDateFrom(),
                    request == null ? null : request.getDateTo(),
                    StringUtils.hasText(file.getExportId()) ? file.getExportId() : NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE,
                    file.getDigestSha256(),
                    file.getSourceBatchId(),
                    campaignRows,
                    queryRows
            ));
            int imported = importResult.getCampaignRowCount() + importResult.getQueryRowCount();
            if (exceptions > 0) {
                return NoonReportProcessResult.partialSuccess(imported, exceptions);
            }
            return NoonReportProcessResult.succeeded(imported, 0);
        } catch (RuntimeException exception) {
            return NoonReportProcessResult.mappingFailed(1, exception.getMessage());
        }
    }

    private NoonAdvertisingCampaignFact campaignFact(List<String> row, Map<String, Integer> headerIndex) {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode(requiredValue(row, headerIndex, "campaign_code"));
        fact.setCampaignName(optionalValue(row, headerIndex, "campaign_name"));
        fact.setPrimaryAdSkuCode(optionalValue(row, headerIndex, "primary_ad_sku_code", "ad_sku_code", "sku"));
        fact.setPrimaryPartnerSku(optionalValue(row, headerIndex, "primary_partner_sku", "partner_sku"));
        fact.setCampaignStatus(optionalValue(row, headerIndex, "campaign_status", "status"));
        fact.setQcStatus(optionalValue(row, headerIndex, "qc_status"));
        fact.setAdgroupCode(optionalValue(row, headerIndex, "adgroup_code"));
        fact.setCampaignStartDate(dateValue(row, headerIndex, "campaign_start_date", "start_date"));
        fact.setCampaignEndDate(dateValue(row, headerIndex, "campaign_end_date", "end_date"));
        fact.setViews(longValue(row, headerIndex, "views", "impressions"));
        fact.setClicks(longValue(row, headerIndex, "clicks"));
        fact.setOrdersCount(longValue(row, headerIndex, "orders_count", "orders"));
        fact.setAssistedOrders(longValue(row, headerIndex, "assisted_orders"));
        fact.setAtcCount(longValue(row, headerIndex, "atc_count", "atc"));
        fact.setSpendAmount(decimalValue(row, headerIndex, "spend_amount", "spend", "spends"));
        fact.setAdRevenue(decimalValue(row, headerIndex, "ad_revenue", "revenue"));
        fact.setCtrPercentage(percentageValue(row, headerIndex, "ctr_percentage", "ctr"));
        fact.setRoas(decimalValue(row, headerIndex, "roas"));
        fact.setCpc(decimalValue(row, headerIndex, "cpc"));
        fact.setCps(decimalValue(row, headerIndex, "cps"));
        fact.setCvrPercentage(percentageValue(row, headerIndex, "cvr_percentage", "cvr"));
        fact.setZeroOrderSpendAmount(decimalValue(row, headerIndex, "zero_order_spend_amount"));
        fact.setZeroOrderSpendShare(decimalValue(row, headerIndex, "zero_order_spend_share"));
        fact.setRawPayloadJson(optionalValue(row, headerIndex, "raw_payload_json"));
        return fact;
    }

    private NoonAdvertisingQueryFact queryFact(List<String> row, Map<String, Integer> headerIndex) {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode(requiredValue(row, headerIndex, "campaign_code"));
        fact.setCampaignName(optionalValue(row, headerIndex, "campaign_name"));
        fact.setAdSkuCode(optionalValue(row, headerIndex, "ad_sku_code", "sku"));
        fact.setPartnerSku(optionalValue(row, headerIndex, "partner_sku"));
        fact.setQueryText(requiredValue(row, headerIndex, "query_text", "query"));
        fact.setQueryKind(optionalValue(row, headerIndex, "query_kind", "query_type", "match_type"));
        fact.setViews(longValue(row, headerIndex, "views", "impressions"));
        fact.setClicks(longValue(row, headerIndex, "clicks"));
        fact.setOrdersCount(longValue(row, headerIndex, "orders_count", "orders"));
        fact.setAssistedOrders(longValue(row, headerIndex, "assisted_orders"));
        fact.setAtcCount(longValue(row, headerIndex, "atc_count", "atc"));
        fact.setSpendAmount(decimalValue(row, headerIndex, "spend_amount", "spend", "spends"));
        fact.setAdRevenue(decimalValue(row, headerIndex, "ad_revenue", "revenue"));
        fact.setCtrPercentage(percentageValue(row, headerIndex, "ctr_percentage", "ctr"));
        fact.setRoas(decimalValue(row, headerIndex, "roas"));
        fact.setCpc(decimalValue(row, headerIndex, "cpc"));
        fact.setCps(decimalValue(row, headerIndex, "cps"));
        fact.setCvrPercentage(percentageValue(row, headerIndex, "cvr_percentage", "cvr"));
        fact.setRawPayloadJson(optionalValue(row, headerIndex, "raw_payload_json"));
        return fact;
    }

    private boolean hasRequiredColumns(Map<String, Integer> headerIndex) {
        return headerIndex.containsKey("campaign_code")
                && (headerIndex.containsKey("row_type")
                || headerIndex.containsKey("query")
                || headerIndex.containsKey("query_text")
                || headerIndex.containsKey("campaign_name"));
    }

    private String rowType(List<String> row, Map<String, Integer> headerIndex) {
        String explicit = optionalValue(row, headerIndex, "row_type").toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (headerIndex.containsKey("query_text") || headerIndex.containsKey("query")) {
            return "query";
        }
        return "campaign";
    }

    private String projectCodeOrFallback(String projectCode, String storeCode) {
        if (StringUtils.hasText(projectCode)) {
            return projectCode.trim();
        }
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        Matcher matcher = STORE_PARTNER_ID.matcher(storeCode.trim());
        return matcher.find() ? "PRJ" + matcher.group(1) : null;
    }

    private String mergeProjectCode(String current, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return current;
        }
        String normalized = candidate.trim();
        if (!StringUtils.hasText(current)) {
            return normalized;
        }
        if (!current.equals(normalized)) {
            throw new IllegalArgumentException("Mixed Noon Ads project_code values in one report.");
        }
        return current;
    }

    private String campaignIdentity(NoonAdvertisingCampaignFact fact) {
        return normalizeIdentityPart(fact.getCampaignCode());
    }

    private String queryIdentity(NoonAdvertisingQueryFact fact) {
        return String.join("\u001f",
                normalizeIdentityPart(fact.getCampaignCode()),
                normalizeIdentityPart(fact.getPartnerSku()),
                normalizeIdentityPart(fact.getAdSkuCode()),
                normalizeIdentityPart(fact.getQueryText()),
                normalizeIdentityPart(fact.getQueryKind())
        );
    }

    private String normalizeIdentityPart(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String raw = headers.get(i) == null ? "" : headers.get(i).trim();
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            result.put(raw.toLowerCase(Locale.ROOT), i);
            String normalized = normalizeHeader(raw);
            result.put(normalized, i);
            result.put(normalized.replace("_", ""), i);
        }
        return result;
    }

    private boolean isBlankRow(List<String> row) {
        return row == null || row.stream().noneMatch(StringUtils::hasText);
    }

    private String requiredValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(row, headerIndex, keys);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing Noon Ads column value: " + String.join("/", keys));
        }
        return value;
    }

    private String optionalValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        Integer index = index(headerIndex, keys);
        if (index == null || row == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index).trim();
    }

    private long longValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(row, headerIndex, keys);
        return StringUtils.hasText(value)
                ? NoonAdvertisingLegacyNumericContract.requireIntCount(value)
                : 0L;
    }

    private BigDecimal decimalValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(row, headerIndex, keys);
        return StringUtils.hasText(value)
                ? new BigDecimal(NoonAdvertisingLegacyNumericContract.normalize(value))
                : BigDecimal.ZERO;
    }

    private BigDecimal percentageValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        MatchedValue matched = matchedValue(row, headerIndex, keys);
        if (!StringUtils.hasText(matched.value())) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = new BigDecimal(
                NoonAdvertisingLegacyNumericContract.normalize(matched.value())
        );
        String matchedKey = matched.key();
        if ("ctr".equals(matchedKey) || "cvr".equals(matchedKey)) {
            return value.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        return value;
    }

    private LocalDate dateValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(row, headerIndex, keys);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Integer index(Map<String, Integer> headerIndex, String... keys) {
        for (String key : keys) {
            Integer index = headerIndex.get(key);
            if (index != null) {
                return index;
            }
            index = headerIndex.get(normalizeHeader(key));
            if (index != null) {
                return index;
            }
            index = headerIndex.get(normalizeHeader(key).replace("_", ""));
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private MatchedValue matchedValue(List<String> row, Map<String, Integer> headerIndex, String... keys) {
        for (String key : keys) {
            Integer index = index(headerIndex, key);
            if (index != null && row != null && index >= 0 && index < row.size()) {
                return new MatchedValue(normalizeHeader(key), row.get(index).trim());
            }
        }
        return new MatchedValue("", "");
    }

    private String normalizeHeader(String value) {
        return NoonAdvertisingHeaderContract.normalize(value);
    }

    private static final class MatchedValue {
        private final String key;
        private final String value;

        private MatchedValue(String key, String value) {
            this.key = key;
            this.value = value;
        }

        private String key() {
            return key;
        }

        private String value() {
            return value;
        }
    }

}
