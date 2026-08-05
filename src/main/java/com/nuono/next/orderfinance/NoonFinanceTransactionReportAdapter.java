package com.nuono.next.orderfinance;

import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.hasRequiredColumns;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.headerIndex;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.isBlankRow;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.missingColumnsDiagnostic;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.nullable;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.rowHash;
import static com.nuono.next.orderfinance.NoonFinanceReportRowSupport.value;

import com.nuono.next.noonpull.NoonReportDownloadedFile;
import com.nuono.next.noonpull.NoonReportCsvRecords;
import com.nuono.next.noonpull.NoonReportProcessResult;
import com.nuono.next.noonpull.NoonReportPullRequest;
import com.nuono.next.noonpull.NoonReportRowDecision;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonFinanceTransactionReportAdapter {
    private final NoonFinanceTransactionFactWriter factWriter;

    public NoonFinanceTransactionReportAdapter(NoonFinanceTransactionFactWriter factWriter) {
        this.factWriter = factWriter;
    }

    public NoonReportProcessResult process(NoonReportDownloadedFile file) {
        if (file == null || file.getRequest() == null) {
            return NoonReportProcessResult.mappingFailed(1, "missing_report_request");
        }
        List<String[]> rows;
        try {
            rows = NoonReportCsvRecords.parseRectangular(file.getContent());
        } catch (RuntimeException exception) {
            return NoonReportProcessResult.mappingFailed(1, "invalid_csv");
        }
        if (rows.isEmpty() || rows.get(0).length == 0) {
            return NoonReportProcessResult.emptyReport();
        }
        Map<String, Integer> headerIndex = headerIndex(rows.get(0));
        if (!hasRequiredColumns(headerIndex)) {
            return NoonReportProcessResult.mappingFailed(1, missingColumnsDiagnostic(headerIndex));
        }

        int businessSkips = 0;
        Map<String, NoonFinanceTransactionFact> acceptedByIdentity = new LinkedHashMap<>();
        for (int rowNumber = 1; rowNumber < rows.size(); rowNumber++) {
            String[] row = rows.get(rowNumber);
            if (isBlankRow(row)) {
                businessSkips++;
                continue;
            }
            NoonReportRowDecision<NoonFinanceTransactionFact> decision = classifyRow(
                    file, row, headerIndex
            );
            if (decision.getKind() == NoonReportRowDecision.Kind.CONTAINER_CONTRACT_ERROR) {
                return NoonReportProcessResult.mappingFailed(1, "row_outside_container");
            }
            if (decision.getKind() == NoonReportRowDecision.Kind.BUSINESS_SKIP) {
                businessSkips++;
                continue;
            }
            NoonFinanceTransactionFact fact = decision.getAccepted();
            if (acceptedByIdentity.putIfAbsent(fact.getRowHash(), fact) != null) {
                businessSkips++;
            }
        }
        List<NoonFinanceTransactionFact> facts = new ArrayList<>(acceptedByIdentity.values());
        int imported = facts.size();
        if (imported == 0 && businessSkips == 0) {
            return NoonReportProcessResult.emptyReport();
        }
        if (imported > 0) {
            factWriter.upsertAll(facts);
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
    public List<NoonReportRowDecision<NoonFinanceTransactionFact>> classifyStageRows(
            NoonReportDownloadedFile file,
            String[] header,
            List<String[]> rows
    ) {
        requireStageHeader(header);
        Map<String, Integer> indexes = headerIndex(header);
        List<NoonReportRowDecision<NoonFinanceTransactionFact>> result = new ArrayList<>();
        for (String[] row : rows) {
            result.add(isBlankRow(row)
                    ? NoonReportRowDecision.businessSkip()
                    : classifyRow(file, row, indexes));
        }
        return List.copyOf(result);
    }

    public String stageIdentity(NoonFinanceTransactionFact fact) {
        return fact.getRowHash();
    }

    private NoonReportRowDecision<NoonFinanceTransactionFact> classifyRow(
            NoonReportDownloadedFile file,
            String[] row,
            Map<String, Integer> headerIndex
    ) {
        try {
            return toFact(file, row, headerIndex);
        } catch (IllegalArgumentException invalidRow) {
            return NoonReportRowDecision.businessSkip();
        }
    }

    private NoonReportRowDecision<NoonFinanceTransactionFact> toFact(
            NoonReportDownloadedFile file,
            String[] row,
            Map<String, Integer> headerIndex
    ) {
        NoonReportPullRequest request = file.getRequest();
        String contractCode = nullable(value(row, headerIndex, "Contract"));
        String contractTitle = nullable(value(row, headerIndex, "Contract Title"));
        String rawCurrency = value(row, headerIndex, "Currency");
        String currency = StringUtils.hasText(rawCurrency)
                ? rawCurrency.toUpperCase(Locale.ROOT)
                : "";
        String detectedSiteCode = detectSiteCode(contractCode, contractTitle, currency);
        String requestedSiteCode = normalizeSiteCode(request.getSiteCode());
        if (!"SA".equals(requestedSiteCode) && !"AE".equals(requestedSiteCode)) {
            return NoonReportRowDecision.containerContractError();
        }
        if (StringUtils.hasText(detectedSiteCode)
                && StringUtils.hasText(requestedSiteCode)
                && !detectedSiteCode.equals(requestedSiteCode)) {
            return NoonReportRowDecision.containerContractError();
        }
        String siteCode = StringUtils.hasText(requestedSiteCode) ? requestedSiteCode : request.getSiteCode();
        LocalDate transactionDate = NoonFinanceReportValueParser.requiredBusinessDate(
                value(row, headerIndex, "Transaction Date"),
                "Transaction Date"
        );
        if (transactionDate != null && outsideRequestedWindow(transactionDate, request)) {
            return NoonReportRowDecision.containerContractError();
        }

        String referenceNr = value(row, headerIndex, "Reference Nr");
        String orderNr = value(row, headerIndex, "Order Nr");
        String transactionType = value(row, headerIndex, "Transaction Type");
        LocalDate orderDate = NoonFinanceReportValueParser.optionalDate(
                value(row, headerIndex, "Order Date")
        );
        BigDecimal netProceeds = amount(row, headerIndex, "Net Proceeds");
        BigDecimal referralFee = amount(row, headerIndex, "Referral Fee including VAT");
        BigDecimal fulfillmentFees = amount(row, headerIndex, "Fullfilment & Logistics Fees including VAT");
        BigDecimal shippingCredits = amount(row, headerIndex, "Shipping Credits including VAT");
        BigDecimal otherOrderFees = amount(row, headerIndex, "Other Order Fees including VAT");
        BigDecimal orderSubsidies = amount(row, headerIndex, "Order Subsidies including VAT");
        BigDecimal nonOrderFees = amount(row, headerIndex, "Non-Order Fees including VAT");
        BigDecimal nonOrderSubsidies = amount(row, headerIndex, "Non-Order Subsidies including VAT");
        BigDecimal others = amount(row, headerIndex, "Others including VAT");
        BigDecimal total = amount(row, headerIndex, "Total");
        if (!StringUtils.hasText(referenceNr)
                || !StringUtils.hasText(orderNr)
                || !StringUtils.hasText(transactionType)
                || !StringUtils.hasText(currency)
                || transactionDate == null
                || NoonFinanceReportValueParser.hasNullAmount(
                        netProceeds,
                        referralFee,
                        fulfillmentFees,
                        shippingCredits,
                        otherOrderFees,
                        orderSubsidies,
                        nonOrderFees,
                        nonOrderSubsidies,
                        others,
                        total
                )) {
            return NoonReportRowDecision.businessSkip();
        }
        return NoonReportRowDecision.accept(NoonFinanceFactColumnContract.requirePersistable(
                new NoonFinanceTransactionFact(
                        request.getOwnerUserId(),
                        request.getStoreCode(),
                        siteCode,
                        file.getSourceBatchId(),
                        file.getDigestSha256(),
                        rowHash(row),
                        contractCode,
                        contractTitle,
                        referenceNr,
                        orderNr,
                        value(row, headerIndex, "Item Nr"),
                        orderDate,
                        transactionDate,
                        nullable(value(row, headerIndex, "Title")),
                        nullable(value(row, headerIndex, "SKUs")),
                        nullable(value(row, headerIndex, "Partner SKUs")),
                        transactionType.toLowerCase(Locale.ROOT),
                        currency,
                        netProceeds,
                        referralFee,
                        fulfillmentFees,
                        shippingCredits,
                        otherOrderFees,
                        orderSubsidies,
                        nonOrderFees,
                        nonOrderSubsidies,
                        others,
                        total,
                        request.getDateFrom(),
                        request.getDateTo()
                )));
    }

    private String detectSiteCode(String contractCode, String contractTitle, String currency) {
        String normalizedCurrency = normalizeSiteCode(currency);
        if ("AED".equals(normalizedCurrency)) {
            return "AE";
        }
        if ("SAR".equals(normalizedCurrency)) {
            return "SA";
        }
        String contractText = (
                (contractCode == null ? "" : contractCode)
                        + " "
                        + (contractTitle == null ? "" : contractTitle)
        ).toUpperCase(Locale.ROOT);
        if (contractText.contains("NOON-SA") || contractText.contains("KSA") || contractText.endsWith("SA")) {
            return "SA";
        }
        if (contractText.contains("NOON AE") || contractText.contains("UAE") || contractText.endsWith("AE")) {
            return "AE";
        }
        return null;
    }

    private BigDecimal amount(String[] row, Map<String, Integer> headerIndex, String column) {
        return NoonFinanceReportValueParser.optionalDecimal(value(row, headerIndex, column));
    }

    private String normalizeSiteCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean outsideRequestedWindow(LocalDate transactionDate, NoonReportPullRequest request) {
        return request.getDateFrom() != null
                && request.getDateTo() != null
                && (transactionDate.isBefore(request.getDateFrom())
                || transactionDate.isAfter(request.getDateTo()));
    }

}
