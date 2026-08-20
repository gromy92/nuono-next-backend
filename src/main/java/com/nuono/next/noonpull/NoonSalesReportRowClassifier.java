package com.nuono.next.noonpull;

import com.nuono.next.datapull.report.ReportFactColumnContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** DP-01 row contract shared by the unified Runtime and the legacy direct-write adapter. */
@Service
public final class NoonSalesReportRowClassifier {
    public NoonReportProcessResult emptyOrNotReady(String csvHeaderState) {
        String headerState = StringUtils.hasText(csvHeaderState) ? csvHeaderState : "unknown";
        return NoonReportProcessResult.emptyReportPendingConfirmation(
                "csvHeader=" + headerState
                        + "; importedRows=0; proof=provider_poll_row_count_required"
        );
    }

    public boolean isBlank(String[] record) {
        for (String value : record) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    public void requireHeader(String[] header) {
        if (!hasRequiredColumns(headerIndex(header == null ? new String[0] : header))) {
            throw new IllegalArgumentException("sales report required columns are missing");
        }
    }

    public List<NoonReportRowDecision<NoonSalesDailyFact>> classifyRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonSalesDailyFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlank(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stableIdentity(NoonSalesDailyFact fact) {
        return fact.key();
    }

    private NoonReportRowDecision<NoonSalesDailyFact> classifyRow(
            NoonReportDownloadedFile file,
            String[] columns,
            Map<String, Integer> headerIndex
    ) {
        try {
            NoonReportPullRequest request = file.getRequest();
            String currency = optionalValue(columns, headerIndex, "currency", "currency_code");
            String contentSite = NoonReportContainerContract.siteForCurrency(currency);
            String requestedSite = NoonReportContainerContract.recognizedSite(
                    request == null ? null : request.getSiteCode()
            );
            if (!StringUtils.hasText(requestedSite)
                    || (StringUtils.hasText(contentSite) && !contentSite.equals(requestedSite))) {
                return NoonReportRowDecision.containerContractError();
            }
            LocalDate factDate = dateValue(columns, headerIndex, "date", "visit_date");
            if (outsideRequestedWindow(factDate, request)) {
                return NoonReportRowDecision.containerContractError();
            }
            if (!StringUtils.hasText(contentSite)) {
                return StringUtils.hasText(currency)
                        ? NoonReportRowDecision.containerContractError()
                        : NoonReportRowDecision.businessSkip();
            }
            if (request == null) {
                return NoonReportRowDecision.businessSkip();
            }
            String partnerSku = ReportFactColumnContract.text(
                    optionalValue(columns, headerIndex, "sku_parent", "partner_sku"), 160);
            String sku = ReportFactColumnContract.text(
                    optionalValue(columns, headerIndex, "sku"), 160);
            long unitsSold = ReportFactColumnContract.signedInt(
                    Long.parseLong(value(columns, headerIndex, "units_sold", "shipped_units")));
            BigDecimal salesAmount = ReportFactColumnContract.decimal(
                    new BigDecimal(value(
                            columns, headerIndex, "sales_amount", "revenue_shipped")),
                    18,
                    6
            );
            if (!StringUtils.hasText(partnerSku)) {
                return NoonReportRowDecision.businessSkip();
            }
            if (!StringUtils.hasText(sku)) {
                sku = partnerSku;
            }
            return NoonReportRowDecision.accept(NoonSalesFactColumnContract.requirePersistable(
                    new NoonSalesDailyFact(
                            request.getOwnerUserId(),
                            request.getStoreCode(),
                            request.getSiteCode(),
                            factDate,
                            partnerSku,
                            sku,
                            unitsSold,
                            salesAmount,
                            currency,
                            file.getSourceBatchId()
                    )));
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.length; index++) {
            result.put(headers[index].trim().toLowerCase(Locale.ROOT), index);
        }
        return result;
    }

    private boolean hasRequiredColumns(Map<String, Integer> headerIndex) {
        boolean simplified = headerIndex.containsKey("date")
                && headerIndex.containsKey("sku_parent")
                && headerIndex.containsKey("units_sold")
                && headerIndex.containsKey("sales_amount")
                && headerIndex.containsKey("currency");
        boolean productViewsAndSales = headerIndex.containsKey("visit_date")
                && headerIndex.containsKey("partner_sku")
                && headerIndex.containsKey("shipped_units")
                && headerIndex.containsKey("revenue_shipped")
                && headerIndex.containsKey("currency_code");
        return simplified || productViewsAndSales;
    }

    private String value(String[] columns, Map<String, Integer> indexes, String... keys) {
        Integer index = index(indexes, keys);
        if (index == null || index < 0 || index >= columns.length
                || !StringUtils.hasText(columns[index])) {
            throw new IllegalArgumentException("Missing column value: " + String.join("/", keys));
        }
        return columns[index].trim();
    }

    private LocalDate dateValue(String[] columns, Map<String, Integer> indexes, String... keys) {
        try {
            return LocalDate.parse(value(columns, indexes, keys));
        } catch (DateTimeParseException invalidDate) {
            throw new IllegalArgumentException("Invalid sales date", invalidDate);
        }
    }

    private String optionalValue(String[] columns, Map<String, Integer> indexes, String... keys) {
        Integer index = index(indexes, keys);
        return index == null || index < 0 || index >= columns.length
                ? ""
                : columns[index].trim();
    }

    private Integer index(Map<String, Integer> indexes, String... keys) {
        for (String key : keys) {
            if (indexes.containsKey(key)) {
                return indexes.get(key);
            }
        }
        return null;
    }

    private boolean outsideRequestedWindow(LocalDate date, NoonReportPullRequest request) {
        return request != null
                && request.getDateFrom() != null
                && request.getDateTo() != null
                && (date.isBefore(request.getDateFrom()) || date.isAfter(request.getDateTo()));
    }
}
