package com.nuono.next.noonpull;

import com.nuono.next.datapull.report.ReportFactColumnContract;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonSalesReportAdapter {
    private final NoonSalesFactWriter factWriter;
    private final ObjectProvider<NoonSalesFactWriter> factWriterProvider;

    public NoonSalesReportAdapter(NoonSalesFactWriter factWriter) {
        this(factWriter, null);
    }

    NoonSalesReportAdapter(NoonSalesFactWriter factWriter, Clock clock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
    }

    @Autowired
    public NoonSalesReportAdapter(ObjectProvider<NoonSalesFactWriter> factWriterProvider) {
        this.factWriter = null;
        this.factWriterProvider = factWriterProvider;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        if (file == null || file.getRequest() == null) {
            return NoonReportProcessResult.mappingFailed(1, "missing_report_request");
        }
        List<String[]> records;
        try {
            records = NoonReportCsvRecords.parseRectangular(file.getContent());
        } catch (IllegalArgumentException invalidCsv) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_csv");
        }
        if (records.isEmpty() || isBlank(records.get(0))) {
            return emptyResult(file, "missing");
        }
        Map<String, Integer> headerIndex = headerIndex(records.get(0));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_header");
        }
        int businessSkips = 0;
        Map<String, NoonSalesDailyFact> acceptedByIdentity = new LinkedHashMap<>();
        for (int i = 1; i < records.size(); i++) {
            String[] columns = records.get(i);
            if (isBlank(columns)) {
                businessSkips++;
                continue;
            }
            NoonReportRowDecision<NoonSalesDailyFact> decision = classifyRow(
                    file, columns, headerIndex
            );
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonSalesDailyFact fact = decision.getAccepted();
            if (acceptedByIdentity.putIfAbsent(stableIdentity(fact), fact) != null) {
                businessSkips++;
            }
        }
        List<NoonSalesDailyFact> facts = new ArrayList<>(acceptedByIdentity.values());
        int imported = facts.size();
        if (imported == 0 && businessSkips == 0) {
            return emptyResult(file, "valid");
        }
        if (imported > 0) {
            factWriter().upsertAll(facts);
        }
        if (businessSkips > 0) {
            return NoonReportProcessResult.succeededWithBusinessSkips(imported, businessSkips);
        }
        return NoonReportProcessResult.succeeded(imported, 0);
    }

    /** Header Interface used by the bounded DP report Fact Writer before any fact mutation. */
    public void requireStageHeader(String[] header) {
        Map<String, Integer> indexes = headerIndex(header == null ? new String[0] : header);
        if (!hasRequiredColumns(indexes)) {
            throw new IllegalArgumentException("sales report required columns are missing");
        }
    }

    /** Pure row classifier used while the complete report container is staged. */
    public List<NoonReportRowDecision<NoonSalesDailyFact>> classifyStageRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireStageHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonSalesDailyFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlank(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stageIdentity(NoonSalesDailyFact fact) {
        return stableIdentity(fact);
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
            if (!StringUtils.hasText(requestedSite)) {
                return NoonReportRowDecision.containerContractError();
            }
            if (StringUtils.hasText(contentSite)
                    && !contentSite.equals(requestedSite)) {
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
                    longValue(columns, headerIndex, "units_sold", "shipped_units"));
            BigDecimal salesAmount = ReportFactColumnContract.decimal(decimalValue(
                    columns, headerIndex, "sales_amount", "revenue_shipped"), 18, 6);
            if (!StringUtils.hasText(partnerSku)) {
                return NoonReportRowDecision.businessSkip();
            }
            if (!StringUtils.hasText(sku)) {
                sku = partnerSku;
            }
            return NoonReportRowDecision.accept(NoonSalesFactColumnContract.requirePersistable(
                    new NoonSalesDailyFact(
                            request.getOwnerUserId(), request.getStoreCode(), request.getSiteCode(),
                            factDate, partnerSku, sku, unitsSold, salesAmount, currency,
                            file.getSourceBatchId()
                    )));
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private NoonReportProcessResult emptyResult(NoonReportDownloadedFile file, String csvHeaderState) {
        return NoonReportProcessResult.emptyReportPendingConfirmation(
                emptyDiagnostic(csvHeaderState)
        );
    }

    private String emptyDiagnostic(String csvHeaderState) {
        StringBuilder builder = new StringBuilder();
        builder.append("csvHeader=").append(StringUtils.hasText(csvHeaderState) ? csvHeaderState : "unknown");
        builder.append("; importedRows=0");
        builder.append("; proof=provider_poll_row_count_required");
        return builder.toString();
    }

    private NoonSalesFactWriter factWriter() {
        if (factWriter != null) {
            return factWriter;
        }
        NoonSalesFactWriter writer = factWriterProvider == null ? null : factWriterProvider.getIfAvailable();
        if (writer == null) {
            throw new IllegalStateException("Noon sales fact writer is not available.");
        }
        return writer;
    }

    private boolean isBlank(String[] record) {
        for (String value : record) {
            if (StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
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

    private String value(String[] columns, Map<String, Integer> headerIndex, String... keys) {
        Integer index = index(headerIndex, keys);
        if (index == null || index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + String.join("/", keys));
        }
        String value = columns[index].trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Blank column value: " + String.join("/", keys));
        }
        return value;
    }

    private long longValue(String[] columns, Map<String, Integer> headerIndex, String... keys) {
        return Long.parseLong(value(columns, headerIndex, keys));
    }

    private BigDecimal decimalValue(String[] columns, Map<String, Integer> headerIndex, String... keys) {
        return new BigDecimal(value(columns, headerIndex, keys));
    }

    private LocalDate dateValue(String[] columns, Map<String, Integer> headerIndex, String... keys) {
        try {
            return LocalDate.parse(value(columns, headerIndex, keys));
        } catch (DateTimeParseException invalidDate) {
            throw new IllegalArgumentException("Invalid sales date", invalidDate);
        }
    }

    private String optionalValue(String[] columns, Map<String, Integer> headerIndex, String... keys) {
        Integer index = index(headerIndex, keys);
        if (index == null || index < 0 || index >= columns.length) {
            return "";
        }
        return columns[index].trim();
    }

    private Integer index(Map<String, Integer> headerIndex, String... keys) {
        for (String key : keys) {
            Integer index = headerIndex.get(key);
            if (index != null) {
                return index;
            }
        }
        return null;
    }

    private boolean outsideRequestedWindow(LocalDate factDate, NoonReportPullRequest request) {
        return request != null
                && request.getDateFrom() != null
                && request.getDateTo() != null
                && (factDate.isBefore(request.getDateFrom()) || factDate.isAfter(request.getDateTo()));
    }

    private String stableIdentity(NoonSalesDailyFact fact) {
        return fact.key();
    }
}
