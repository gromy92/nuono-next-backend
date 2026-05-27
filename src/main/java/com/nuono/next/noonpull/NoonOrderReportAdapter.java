package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonOrderReportAdapter {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final LocalTime ORDER_READY_AFTER = LocalTime.of(8, 30);
    private static final DateTimeFormatter NOON_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ORDER_LINE_SUFFIX = Pattern.compile("-\\d+$");

    private final NoonOrderFactWriter factWriter;
    private final ObjectProvider<NoonOrderFactWriter> factWriterProvider;
    private final Clock clock;

    public NoonOrderReportAdapter(NoonOrderFactWriter factWriter, Clock clock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Autowired
    public NoonOrderReportAdapter(ObjectProvider<NoonOrderFactWriter> factWriterProvider) {
        this.factWriter = null;
        this.factWriterProvider = factWriterProvider;
        this.clock = Clock.systemUTC();
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        NoonOrderFactWriter writer = factWriter();
        String csv = new String(file.getContent(), StandardCharsets.UTF_8);
        String[] lines = csv.split("\\r?\\n", -1);
        if (lines.length == 0 || !StringUtils.hasText(lines[0])) {
            return emptyOrNotReady();
        }

        Map<String, Integer> headerIndex = headerIndex(split(lines[0]));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.missingColumns(missingColumnsDiagnostic(headerIndex));
        }

        int imported = 0;
        int exceptions = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!StringUtils.hasText(lines[i])) {
                continue;
            }
            try {
                writer.upsertLine(toFact(file, split(lines[i]), headerIndex));
                imported++;
            } catch (RuntimeException exception) {
                exceptions++;
            }
        }
        if (imported == 0 && exceptions == 0) {
            return emptyOrNotReady();
        }
        if (imported == 0) {
            return NoonReportProcessResult.mappingFailed(exceptions);
        }
        if (exceptions > 0) {
            return NoonReportProcessResult.partialSuccess(imported, exceptions);
        }
        return NoonReportProcessResult.succeeded(imported, 0);
    }

    private NoonOrderLineFact toFact(NoonReportDownloadedFile file, String[] columns, Map<String, Integer> headerIndex) {
        NoonReportPullRequest request = file.getRequest();
        String orderLineIdentity = requiredValue(columns, headerIndex, "item_nr");
        return new NoonOrderLineFact(
                request.getOwnerUserId(),
                request.getStoreCode(),
                request.getSiteCode(),
                requiredValue(columns, headerIndex, "id_partner"),
                requiredValue(columns, headerIndex, "src_country"),
                requiredValue(columns, headerIndex, "country_code"),
                requiredValue(columns, headerIndex, "dest_country"),
                optionalValue(columns, headerIndex, "bayan_nr"),
                orderLineIdentity,
                deriveOrderIdentity(orderLineIdentity),
                requiredValue(columns, headerIndex, "partner_sku"),
                requiredValue(columns, headerIndex, "sku"),
                requiredValue(columns, headerIndex, "status"),
                decimalValue(columns, headerIndex, "offer_price"),
                decimalValue(columns, headerIndex, "gmv_lcy"),
                requiredValue(columns, headerIndex, "currency_code"),
                requiredValue(columns, headerIndex, "brand_code"),
                requiredValue(columns, headerIndex, "family"),
                requiredValue(columns, headerIndex, "fulfillment_model"),
                timestampValue(columns, headerIndex, "order_timestamp"),
                timestampValue(columns, headerIndex, "shipment_timestamp"),
                timestampValue(columns, headerIndex, "delivered_timestamp"),
                request.getDateFrom(),
                request.getDateTo(),
                file.getSourceBatchId()
        );
    }

    private NoonOrderFactWriter factWriter() {
        if (factWriter != null) {
            return factWriter;
        }
        NoonOrderFactWriter writer = factWriterProvider == null ? null : factWriterProvider.getIfAvailable();
        if (writer == null) {
            throw new IllegalStateException("Noon order fact writer is not available.");
        }
        return writer;
    }

    private NoonReportProcessResult emptyOrNotReady() {
        LocalTime localTime = LocalTime.now(clock.withZone(SHANGHAI));
        if (localTime.isBefore(ORDER_READY_AFTER)) {
            return NoonReportProcessResult.reportNotReady();
        }
        return NoonReportProcessResult.emptyReport();
    }

    private String[] split(String line) {
        return line.split(",", -1);
    }

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return result;
    }

    private boolean hasRequiredColumns(Map<String, Integer> headerIndex) {
        return headerIndex.keySet().containsAll(NoonOrderReportDescriptor.requiredColumns());
    }

    private String missingColumnsDiagnostic(Map<String, Integer> headerIndex) {
        String missing = NoonOrderReportDescriptor.requiredColumns().stream()
                .filter((column) -> !headerIndex.containsKey(column))
                .collect(Collectors.joining(","));
        String actualHeaders = headerIndex.keySet().stream()
                .limit(40)
                .collect(Collectors.joining(","));
        return "missing=" + missing + "; actual_headers=" + actualHeaders;
    }

    private String requiredValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        String value = optionalValue(columns, headerIndex, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Blank column value: " + key);
        }
        return value;
    }

    private String optionalValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        Integer index = headerIndex.get(key);
        if (index == null || index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + key);
        }
        return columns[index].trim();
    }

    private BigDecimal decimalValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        return new BigDecimal(requiredValue(columns, headerIndex, key));
    }

    private LocalDateTime timestampValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        String value = optionalValue(columns, headerIndex, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value, NOON_TIMESTAMP);
    }

    private String deriveOrderIdentity(String orderLineIdentity) {
        return ORDER_LINE_SUFFIX.matcher(orderLineIdentity).replaceFirst("");
    }
}
