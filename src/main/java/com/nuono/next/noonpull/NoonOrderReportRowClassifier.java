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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** DP-02 row contract shared by the unified Runtime and the legacy direct-write adapter. */
@Service
public final class NoonOrderReportRowClassifier {
    private static final DateTimeFormatter NOON_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ORDER_LINE_SUFFIX = Pattern.compile("-\\d+$");

    private final NoonOrderReportWindowPolicy windowPolicy;

    public NoonOrderReportRowClassifier() {
        this(Clock.systemUTC());
    }

    public NoonOrderReportRowClassifier(Clock clock) {
        this.windowPolicy = new NoonOrderReportWindowPolicy(clock);
    }

    public NoonReportProcessResult emptyOrNotReady() {
        return windowPolicy.emptyOrNotReady();
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
        Map<String, Integer> indexes = headerIndex(header == null ? new String[0] : header);
        if (!indexes.keySet().containsAll(NoonOrderReportDescriptor.requiredColumns())) {
            throw new IllegalArgumentException(missingColumnsDiagnostic(indexes));
        }
    }

    public List<NoonReportRowDecision<NoonOrderLineFact>> classifyRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonOrderLineFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlank(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stableIdentity(NoonOrderLineFact fact) {
        return "noon_order_report|"
                + fact.getIdPartner() + '|'
                + fact.getCountryCode() + '|'
                + fact.getOrderLineIdentity();
    }

    private NoonReportRowDecision<NoonOrderLineFact> classifyRow(
            NoonReportDownloadedFile file,
            String[] columns,
            Map<String, Integer> headerIndex
    ) {
        try {
            return toFact(file, columns, headerIndex);
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private NoonReportRowDecision<NoonOrderLineFact> toFact(
            NoonReportDownloadedFile file,
            String[] columns,
            Map<String, Integer> headerIndex
    ) {
        NoonReportPullRequest request = file.getRequest();
        String idPartner = value(columns, headerIndex, "id_partner");
        String sourceCountry = value(columns, headerIndex, "src_country");
        String countryCode = value(columns, headerIndex, "country_code");
        String destinationCountry = value(columns, headerIndex, "dest_country");
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

        String orderLineIdentity = value(columns, headerIndex, "item_nr");
        String partnerSku = value(columns, headerIndex, "partner_sku");
        String sku = value(columns, headerIndex, "sku");
        String status = value(columns, headerIndex, "status");
        String currencyCode = value(columns, headerIndex, "currency_code");
        String brandCode = value(columns, headerIndex, "brand_code");
        String family = value(columns, headerIndex, "family");
        String fulfillmentModel = value(columns, headerIndex, "fulfillment_model");
        BigDecimal offerPrice = decimalValue(columns, headerIndex, "offer_price");
        BigDecimal gmvLcy = decimalValue(columns, headerIndex, "gmv_lcy");
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
                        nullableValue(columns, headerIndex, "bayan_nr"),
                        orderLineIdentity,
                        ORDER_LINE_SUFFIX.matcher(orderLineIdentity).replaceFirst(""),
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

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.length; index++) {
            result.put(headers[index].trim().toLowerCase(Locale.ROOT), index);
        }
        return result;
    }

    private String missingColumnsDiagnostic(Map<String, Integer> indexes) {
        String missing = NoonOrderReportDescriptor.requiredColumns().stream()
                .filter(column -> !indexes.containsKey(column))
                .collect(Collectors.joining(","));
        String actual = indexes.keySet().stream().limit(40).collect(Collectors.joining(","));
        return "missing=" + missing + "; actual_headers=" + actual;
    }

    private String value(String[] columns, Map<String, Integer> indexes, String key) {
        Integer index = indexes.get(key);
        if (index == null || index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + key);
        }
        return columns[index].trim();
    }

    private String nullableValue(String[] columns, Map<String, Integer> indexes, String key) {
        Integer index = indexes.get(key);
        if (index == null) {
            return "";
        }
        if (index < 0 || index >= columns.length) {
            throw new IllegalArgumentException("Missing column value: " + key);
        }
        return columns[index].trim();
    }

    private BigDecimal decimalValue(String[] columns, Map<String, Integer> indexes, String key) {
        String value = value(columns, indexes, key);
        return StringUtils.hasText(value) ? new BigDecimal(value) : null;
    }

    private LocalDateTime timestampValue(
            String[] columns,
            Map<String, Integer> indexes,
            String key
    ) {
        String value = value(columns, indexes, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, NOON_TIMESTAMP);
        } catch (DateTimeParseException invalidTimestamp) {
            throw new IllegalArgumentException("Invalid timestamp value: " + key, invalidTimestamp);
        }
    }
}
