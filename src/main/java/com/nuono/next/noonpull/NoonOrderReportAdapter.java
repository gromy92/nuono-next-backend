package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final DateTimeFormatter NOON_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ORDER_LINE_SUFFIX = Pattern.compile("-\\d+$");

    private final NoonOrderFactWriter factWriter;
    private final ObjectProvider<NoonOrderFactWriter> factWriterProvider;
    private final NoonOrderReportWindowPolicy windowPolicy;

    public NoonOrderReportAdapter(NoonOrderFactWriter factWriter, Clock clock) {
        this.factWriter = factWriter;
        this.factWriterProvider = null;
        this.windowPolicy = new NoonOrderReportWindowPolicy(clock);
    }

    @Autowired
    public NoonOrderReportAdapter(ObjectProvider<NoonOrderFactWriter> factWriterProvider) {
        this.factWriter = null;
        this.factWriterProvider = factWriterProvider;
        this.windowPolicy = new NoonOrderReportWindowPolicy(Clock.systemUTC());
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
            return windowPolicy.emptyOrNotReady();
        }

        Map<String, Integer> headerIndex = headerIndex(records.get(0));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.mappingFailed(1, missingColumnsDiagnostic(headerIndex));
        }

        int businessSkips = 0;
        Map<String, NoonOrderLineFact> factsByIdentity = new LinkedHashMap<>();
        for (int i = 1; i < records.size(); i++) {
            String[] columns = records.get(i);
            if (isBlank(columns)) {
                businessSkips++;
                continue;
            }
            NoonReportRowDecision<NoonOrderLineFact> decision = classifyRow(file, columns, headerIndex);
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonOrderLineFact fact = decision.getAccepted();
            if (factsByIdentity.putIfAbsent(stableIdentity(fact), fact) != null) {
                businessSkips++;
            }
        }
        List<NoonOrderLineFact> factsToWrite = new ArrayList<>(factsByIdentity.values());
        if (factsToWrite.isEmpty() && businessSkips == 0) {
            return windowPolicy.emptyOrNotReady();
        }

        int imported = factsToWrite.size();
        if (imported > 0) {
            factWriter().upsertLines(factsToWrite);
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
            throw new IllegalArgumentException(missingColumnsDiagnostic(indexes));
        }
    }

    /** Pure row classifier used while the complete report container is staged. */
    public List<NoonReportRowDecision<NoonOrderLineFact>> classifyStageRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireStageHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonOrderLineFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlank(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stageIdentity(NoonOrderLineFact fact) {
        return stableIdentity(fact);
    }

    private NoonReportRowDecision<NoonOrderLineFact> classifyRow(
            NoonReportDownloadedFile file, String[] columns, Map<String, Integer> headerIndex
    ) {
        try {
            return toFact(file, columns, headerIndex);
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private NoonReportRowDecision<NoonOrderLineFact> toFact(
            NoonReportDownloadedFile file, String[] columns, Map<String, Integer> headerIndex
    ) {
        NoonReportPullRequest request = file.getRequest();
        String idPartner = optionalValue(columns, headerIndex, "id_partner");
        String sourceCountry = optionalValue(columns, headerIndex, "src_country");
        String countryCode = optionalValue(columns, headerIndex, "country_code");
        String destinationCountry = optionalValue(columns, headerIndex, "dest_country");
        String requestedSite = NoonReportContainerContract.recognizedSite(request.getSiteCode());
        if (!StringUtils.hasText(requestedSite)) {
            return NoonReportRowDecision.containerContractError();
        }
        String sourceSite = NoonReportContainerContract.recognizedSite(sourceCountry);
        String contentSite = NoonReportContainerContract.recognizedSite(countryCode);
        if (NoonReportContainerContract.mismatch(requestedSite, sourceSite)
                || NoonReportContainerContract.mismatch(requestedSite, contentSite)) {
            return NoonReportRowDecision.containerContractError();
        }
        LocalDateTime orderTimestamp = timestampValue(columns, headerIndex, "order_timestamp");
        if (orderTimestamp != null
                && windowPolicy.outsideRequestedWindow(orderTimestamp.toLocalDate(), request)) {
            return NoonReportRowDecision.containerContractError();
        }
        if (!StringUtils.hasText(sourceSite) && !StringUtils.hasText(contentSite)) {
            return NoonReportRowDecision.businessSkip();
        }

        String orderLineIdentity = optionalValue(columns, headerIndex, "item_nr");
        String partnerSku = optionalValue(columns, headerIndex, "partner_sku");
        String sku = optionalValue(columns, headerIndex, "sku");
        String status = optionalValue(columns, headerIndex, "status");
        String currencyCode = optionalValue(columns, headerIndex, "currency_code");
        String brandCode = optionalValue(columns, headerIndex, "brand_code");
        String family = optionalValue(columns, headerIndex, "family");
        String fulfillmentModel = optionalValue(columns, headerIndex, "fulfillment_model");
        BigDecimal offerPrice = optionalDecimalValue(columns, headerIndex, "offer_price");
        BigDecimal gmvLcy = optionalDecimalValue(columns, headerIndex, "gmv_lcy");
        LocalDateTime shipmentTimestamp = timestampValue(columns, headerIndex, "shipment_timestamp");
        LocalDateTime deliveredTimestamp = timestampValue(columns, headerIndex, "delivered_timestamp");
        if (NoonReportContainerContract.hasBlank(
                idPartner, sourceCountry, countryCode, destinationCountry,
                orderLineIdentity, partnerSku, sku, status,
                currencyCode, brandCode, family, fulfillmentModel
        ) || offerPrice == null || gmvLcy == null || orderTimestamp == null) {
            return NoonReportRowDecision.businessSkip();
        }
        return NoonReportRowDecision.accept(NoonOrderFactColumnContract.requirePersistable(
                new NoonOrderLineFact(
                        request.getOwnerUserId(),
                        request.getStoreCode(),
                        request.getSiteCode(),
                        idPartner,
                        sourceCountry,
                        countryCode,
                        destinationCountry,
                        nullableColumnValue(columns, headerIndex, "bayan_nr"),
                        orderLineIdentity,
                        deriveOrderIdentity(orderLineIdentity),
                        partnerSku,
                        sku,
                        status,
                        offerPrice,
                        gmvLcy,
                        currencyCode,
                        brandCode,
                        family,
                        fulfillmentModel,
                        orderTimestamp,
                        shipmentTimestamp,
                        deliveredTimestamp,
                        request.getDateFrom(),
                        request.getDateTo(),
                        file.getSourceBatchId()
                )));
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

    private String optionalValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        Integer index = headerIndex.get(key);
        if (index == null || index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + key);
        }
        return columns[index].trim();
    }

    private String nullableColumnValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        Integer index = headerIndex.get(key);
        if (index == null) {
            return "";
        }
        if (index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + key);
        }
        return columns[index].trim();
    }

    private BigDecimal optionalDecimalValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        String value = optionalValue(columns, headerIndex, key);
        return StringUtils.hasText(value) ? new BigDecimal(value) : null;
    }

    private LocalDateTime timestampValue(String[] columns, Map<String, Integer> headerIndex, String key) {
        String value = optionalValue(columns, headerIndex, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, NOON_TIMESTAMP);
        } catch (DateTimeParseException invalidTimestamp) {
            throw new IllegalArgumentException("Invalid timestamp value: " + key, invalidTimestamp);
        }
    }

    private String deriveOrderIdentity(String orderLineIdentity) {
        return ORDER_LINE_SUFFIX.matcher(orderLineIdentity).replaceFirst("");
    }

    private String stableIdentity(NoonOrderLineFact fact) {
        return "noon_order_report|"
                + fact.getIdPartner() + '|'
                + fact.getCountryCode() + '|'
                + fact.getOrderLineIdentity();
    }
}
