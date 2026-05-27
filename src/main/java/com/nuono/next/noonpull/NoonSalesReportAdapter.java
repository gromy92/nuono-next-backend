package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonSalesReportAdapter {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int EMPTY_REPORT_CONFIRMATION_DAYS = 3;

    private final NoonSalesFactWriter factWriter;
    private final ObjectProvider<NoonSalesFactWriter> factWriterProvider;
    private final Clock clock;

    public NoonSalesReportAdapter(NoonSalesFactWriter factWriter) {
        this(factWriter, Clock.system(SHANGHAI));
    }

    NoonSalesReportAdapter(NoonSalesFactWriter factWriter, Clock clock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
    }

    @Autowired
    public NoonSalesReportAdapter(ObjectProvider<NoonSalesFactWriter> factWriterProvider) {
        this.factWriter = null;
        this.factWriterProvider = factWriterProvider;
        this.clock = Clock.system(SHANGHAI);
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        NoonSalesFactWriter writer = factWriter();
        String csv = new String(file.getContent(), StandardCharsets.UTF_8);
        List<List<String>> records = parseRecords(csv);
        if (records.isEmpty() || records.get(0).stream().noneMatch(StringUtils::hasText)) {
            return emptyResult(file);
        }
        Map<String, Integer> headerIndex = headerIndex(records.get(0));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.missingColumns();
        }
        int imported = 0;
        int exceptions = 0;
        for (int i = 1; i < records.size(); i++) {
            List<String> columns = records.get(i);
            if (columns.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            try {
                NoonReportPullRequest request = file.getRequest();
                String partnerSku = value(columns, headerIndex, "sku_parent", "partner_sku");
                String sku = optionalValue(columns, headerIndex, "sku");
                if (!StringUtils.hasText(sku)) {
                    sku = partnerSku;
                }
                writer.upsert(new NoonSalesDailyFact(
                        request.getOwnerUserId(),
                        request.getStoreCode(),
                        request.getSiteCode(),
                        LocalDate.parse(value(columns, headerIndex, "date", "visit_date")),
                        partnerSku,
                        sku,
                        longValue(columns, headerIndex, "units_sold", "shipped_units"),
                        decimalValue(columns, headerIndex, "sales_amount", "revenue_shipped"),
                        value(columns, headerIndex, "currency", "currency_code"),
                        file.getSourceBatchId()
                ));
                imported++;
            } catch (RuntimeException exception) {
                exceptions++;
            }
        }
        if (imported == 0 && exceptions == 0) {
            return emptyResult(file);
        }
        if (imported == 0) {
            return NoonReportProcessResult.mappingFailed(exceptions);
        }
        if (exceptions > 0) {
            return NoonReportProcessResult.partialSuccess(imported, exceptions);
        }
        return NoonReportProcessResult.succeeded(imported, 0);
    }

    private NoonReportProcessResult emptyResult(NoonReportDownloadedFile file) {
        NoonReportPullRequest request = file == null ? null : file.getRequest();
        if (request != null
                && request.getDataDomain() == NoonPullDataDomain.SALES
                && request.getDateTo() != null
                && !request.getDateTo().isBefore(LocalDate.now(clock).minusDays(EMPTY_REPORT_CONFIRMATION_DAYS))) {
            return NoonReportProcessResult.emptyReportPendingConfirmation();
        }
        return NoonReportProcessResult.emptyReport();
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

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            result.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
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

    private String value(List<String> columns, Map<String, Integer> headerIndex, String... keys) {
        Integer index = index(headerIndex, keys);
        if (index == null || index < 0 || index >= columns.size()) {
            throw new IllegalArgumentException("Missing column value: " + String.join("/", keys));
        }
        String value = columns.get(index).trim();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Blank column value: " + String.join("/", keys));
        }
        return value;
    }

    private long longValue(List<String> columns, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(columns, headerIndex, keys);
        return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
    }

    private BigDecimal decimalValue(List<String> columns, Map<String, Integer> headerIndex, String... keys) {
        String value = optionalValue(columns, headerIndex, keys);
        return StringUtils.hasText(value) ? new BigDecimal(value) : BigDecimal.ZERO;
    }

    private String optionalValue(List<String> columns, Map<String, Integer> headerIndex, String... keys) {
        Integer index = index(headerIndex, keys);
        if (index == null || index < 0 || index >= columns.size()) {
            return "";
        }
        return columns.get(index).trim();
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

    private List<List<String>> parseRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    boolean escapedQuote = i + 1 < csv.length() && csv.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        currentValue.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    currentValue.append(ch);
                }
                continue;
            }

            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                currentRecord.add(currentValue.toString());
                currentValue.setLength(0);
            } else if (ch == '\n') {
                currentRecord.add(currentValue.toString());
                records.add(currentRecord);
                currentRecord = new ArrayList<>();
                currentValue.setLength(0);
            } else if (ch != '\r') {
                currentValue.append(ch);
            }
        }

        currentRecord.add(currentValue.toString());
        if (!(currentRecord.size() == 1 && currentRecord.get(0).isBlank() && !records.isEmpty())) {
            records.add(currentRecord);
        }
        return records;
    }
}
